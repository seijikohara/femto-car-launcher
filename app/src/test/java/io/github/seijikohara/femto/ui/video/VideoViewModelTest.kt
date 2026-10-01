package io.github.seijikohara.femto.ui.video

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.CreationExtras
import io.github.seijikohara.femto.data.common.UI_SUBSCRIPTION_GRACE_MS
import io.github.seijikohara.femto.data.location.VehicleMotion
import io.github.seijikohara.femto.data.video.VIDEO_PICTURE_STOP_DWELL_MS
import io.github.seijikohara.femto.testfixtures.FakeVideoPlayer
import io.github.seijikohara.femto.testfixtures.FakeVideoSettingsStore
import io.github.seijikohara.femto.testfixtures.FakeVideoSourceGrants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Pure JVM: the store, the grants and the player are in-memory fakes, and the
// motion verdict is staged directly.
@OptIn(ExperimentalCoroutinesApi::class)
class VideoViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val motion = MutableStateFlow(VehicleMotion.UNKNOWN)
    private val player = FakeVideoPlayer()
    private val grants = FakeVideoSourceGrants(held = setOf(FILE))
    private val store = FakeVideoSettingsStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a window turned off loads nothing`() =
        runTest(dispatcher) {
            store.setSourceUri(FILE)
            val viewModel = viewModel()
            subscribe(viewModel)

            assertFalse(viewModel.uiState.value.windowEnabled)
            assertNull(player.loaded)
        }

    @Test
    fun `a window turned on with a held file loads it without playing`() =
        runTest(dispatcher) {
            enableWith(FILE)
            val viewModel = viewModel()
            subscribe(viewModel)

            assertEquals(FILE, player.loaded)
            assertTrue(viewModel.uiState.value.fileReady)
            assertFalse(viewModel.uiState.value.playing)
        }

    @Test
    fun `a window with no file asks for one`() =
        runTest(dispatcher) {
            store.setWindowEnabled(true)
            val viewModel = viewModel()
            subscribe(viewModel)

            assertTrue(viewModel.uiState.value.windowEnabled)
            assertFalse(viewModel.uiState.value.fileReady)
            assertNull(player.loaded)
        }

    @Test
    fun `a file whose read grant is gone asks for a new one`() =
        runTest(dispatcher) {
            enableWith(FILE)
            grants.release(FILE)
            val viewModel = viewModel()
            subscribe(viewModel)

            assertFalse(viewModel.uiState.value.fileReady)
            assertNull(player.loaded)
        }

    @Test
    fun `a file the player cannot open asks for a new one`() =
        runTest(dispatcher) {
            enableWith(FILE)
            val viewModel = viewModel()
            subscribe(viewModel)

            player.fail()
            runCurrent()

            assertFalse(viewModel.uiState.value.fileReady)
        }

    @Test
    fun `toggling playback plays and then pauses`() =
        runTest(dispatcher) {
            enableWith(FILE)
            val viewModel = viewModel()
            subscribe(viewModel)

            viewModel.onAction(VideoAction.TogglePlayback)
            runCurrent()
            assertTrue(viewModel.uiState.value.playing)

            viewModel.onAction(VideoAction.TogglePlayback)
            runCurrent()
            assertFalse(viewModel.uiState.value.playing)
        }

    @Test
    fun `a picked file is kept and loaded`() =
        runTest(dispatcher) {
            enableWith(FILE)
            val viewModel = viewModel()
            subscribe(viewModel)

            viewModel.onAction(VideoAction.FilePicked(OTHER_FILE))
            advanceUntilIdle()

            assertEquals(OTHER_FILE, store.current.sourceUri)
            assertEquals(setOf(OTHER_FILE), grants.held)
            assertEquals(OTHER_FILE, player.loaded)
        }

    @Test
    fun `a file whose grant the provider refuses says so and keeps the current file`() =
        runTest(dispatcher) {
            enableWith(FILE)
            val refusing = FakeVideoSourceGrants(held = setOf(FILE), grantable = setOf(FILE))
            val viewModel = viewModel(grants = refusing)
            subscribe(viewModel)

            viewModel.onAction(VideoAction.FilePicked(OTHER_FILE))
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.pickFailed)
            assertEquals(FILE, player.loaded)
        }

    @Test
    fun `a pick that is kept clears the refusal`() =
        runTest(dispatcher) {
            enableWith(FILE)
            val refusing = FakeVideoSourceGrants(held = setOf(FILE), grantable = setOf(FILE))
            val viewModel = viewModel(grants = refusing)
            subscribe(viewModel)
            viewModel.onAction(VideoAction.FilePicked(OTHER_FILE))
            advanceUntilIdle()

            viewModel.onAction(VideoAction.FilePicked(FILE))
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.pickFailed)
        }

    @Test
    fun `picking the same file again after a failure loads it again`() =
        runTest(dispatcher) {
            enableWith(FILE)
            val viewModel = viewModel()
            subscribe(viewModel)
            player.fail()
            runCurrent()

            viewModel.onAction(VideoAction.FilePicked(FILE))
            advanceUntilIdle()

            assertEquals(2, player.loads)
            assertTrue(viewModel.uiState.value.fileReady)
        }

    @Test
    fun `turning the window off stops playback`() =
        runTest(dispatcher) {
            enableWith(FILE)
            val viewModel = viewModel()
            subscribe(viewModel)
            viewModel.onAction(VideoAction.TogglePlayback)
            runCurrent()

            store.setWindowEnabled(false)
            runCurrent()

            assertNull(player.loaded)
            assertFalse(viewModel.uiState.value.playing)
        }

    @Test
    fun `closing turns the window off and stops playback`() =
        runTest(dispatcher) {
            enableWith(FILE)
            val viewModel = viewModel()
            subscribe(viewModel)
            viewModel.onAction(VideoAction.TogglePlayback)
            runCurrent()

            viewModel.onAction(VideoAction.Close)
            advanceUntilIdle()

            assertFalse(store.current.windowEnabled)
            assertNull(player.loaded)
        }

    @Test
    fun `the picture hides while moving and audio keeps playing`() =
        runTest(dispatcher) {
            enableWith(FILE)
            motion.value = VehicleMotion.PARKED
            val viewModel = viewModel()
            subscribe(viewModel)
            viewModel.onAction(VideoAction.TogglePlayback)
            advanceTimeBy(VIDEO_PICTURE_STOP_DWELL_MS)
            runCurrent()
            assertTrue(viewModel.pictureVisible.value)

            motion.value = VehicleMotion.MOVING
            runCurrent()

            assertFalse(viewModel.pictureVisible.value)
            assertTrue(viewModel.uiState.value.playing)
        }

    @Test
    fun `a dashboard coming back while moving never starts from a visible picture`() =
        runTest(dispatcher) {
            enableWith(FILE)
            motion.value = VehicleMotion.PARKED
            val viewModel = viewModel()
            val watcher = backgroundScope.launch { viewModel.pictureVisible.collect {} }
            advanceTimeBy(VIDEO_PICTURE_STOP_DWELL_MS)
            runCurrent()
            assertTrue(viewModel.pictureVisible.value)

            // The launcher leaves the screen, the car drives off, and the
            // launcher comes back: the first value the surface sees must be
            // hidden, or it would attach and draw a frame while moving.
            watcher.cancel()
            advanceTimeBy(UI_SUBSCRIPTION_GRACE_MS + 1)
            motion.value = VehicleMotion.MOVING
            val first = async { viewModel.pictureVisible.first() }
            runCurrent()

            assertFalse(first.await())
        }

    @Test
    fun `the picture shows while moving once the gate is turned off`() =
        runTest(dispatcher) {
            enableWith(FILE)
            store.setHidePictureWhileDriving(false)
            motion.value = VehicleMotion.MOVING
            val viewModel = viewModel()
            subscribe(viewModel)

            assertTrue(viewModel.pictureVisible.value)
        }

    @Test
    fun `playback keeps going with no one watching the window`() =
        runTest(dispatcher) {
            enableWith(FILE)
            val viewModel = viewModel()
            // No uiState subscriber: the launcher is off screen, and the player
            // must still be driven by the store.
            runCurrent()
            assertEquals(FILE, player.loaded)

            viewModel.onAction(VideoAction.TogglePlayback)
            runCurrent()

            assertTrue(player.isPlaying.value)
        }

    @Test
    fun `clearing the ViewModel releases the player`() =
        runTest(dispatcher) {
            val owner = ViewModelStore()
            ViewModelProvider.create(
                owner,
                object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(
                        modelClass: Class<T>,
                        extras: CreationExtras,
                    ): T = viewModel() as T
                },
            )[VideoViewModel::class]

            owner.clear()

            assertTrue(player.released)
        }

    private suspend fun enableWith(uri: String) {
        store.setWindowEnabled(true)
        store.setSourceUri(uri)
    }

    private fun viewModel(grants: FakeVideoSourceGrants = this.grants) =
        VideoViewModel(
            store = store,
            grants = grants,
            motion = motion,
            player = player,
            ioDispatcher = dispatcher,
        )

    private fun TestScope.subscribe(viewModel: VideoViewModel) {
        backgroundScope.launch { viewModel.uiState.collect {} }
        backgroundScope.launch { viewModel.pictureVisible.collect {} }
        runCurrent()
    }

    private companion object {
        const val FILE = "content://com.example.documents/document/video%3A1"
        const val OTHER_FILE = "content://com.example.documents/document/video%3A2"
    }
}
