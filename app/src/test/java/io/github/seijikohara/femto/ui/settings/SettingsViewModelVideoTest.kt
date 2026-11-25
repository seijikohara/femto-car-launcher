package io.github.seijikohara.femto.ui.settings

import io.github.seijikohara.femto.data.display.SettingsSectionId
import io.github.seijikohara.femto.data.video.VideoSettings
import io.github.seijikohara.femto.data.video.adoptSource
import io.github.seijikohara.femto.testfixtures.FakeVideoSettingsStore
import io.github.seijikohara.femto.testfixtures.FakeVideoSourceGrants
import io.github.seijikohara.femto.testfixtures.fakeSettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

// The Panels category's video rows (issue #390). Pure JVM: the video store and
// the read grants are in-memory fakes.
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelVideoTest {
    private val dispatcher = StandardTestDispatcher()
    private val grants = FakeVideoSourceGrants(names = mapOf(FILE to FILE_NAME, OTHER_FILE to OTHER_NAME))
    private val videoStore = FakeVideoSettingsStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `SetVideoWindow writes the switch to the video store`() =
        runTest(dispatcher) {
            viewModel().onAction(SettingsAction.SetVideoWindow(true))
            advanceUntilIdle()

            assertEquals(true, videoStore.current.windowEnabled)
        }

    @Test
    fun `SetVideoHidePicture writes the gate to the video store`() =
        runTest(dispatcher) {
            viewModel().onAction(SettingsAction.SetVideoHidePicture(false))
            advanceUntilIdle()

            assertEquals(false, videoStore.current.hidePictureWhileDriving)
        }

    @Test
    fun `SetVideoFile keeps the picked file with its read grant`() =
        runTest(dispatcher) {
            viewModel().onAction(SettingsAction.SetVideoFile(FILE))
            advanceUntilIdle()

            assertEquals(FILE, videoStore.current.sourceUri)
            assertEquals(setOf(FILE), grants.held)
        }

    @Test
    fun `a file whose grant the provider refuses says so`() =
        runTest(dispatcher) {
            val refusing = FakeVideoSourceGrants(grantable = emptySet())
            val vm = viewModel(grants = refusing)
            subscribe(vm)

            vm.onAction(SettingsAction.SetVideoFile(FILE))
            advanceUntilIdle()

            assertEquals(true, vm.uiState.value.video.pickFailed)
            assertEquals(null, videoStore.current.sourceUri)
        }

    @Test
    fun `a file picked on the dashboard after a refused pick here clears the refusal`() =
        runTest(dispatcher) {
            val refusing = FakeVideoSourceGrants(grantable = emptySet())
            val vm = viewModel(grants = refusing)
            subscribe(vm)
            vm.onAction(SettingsAction.SetVideoFile(FILE))
            advanceUntilIdle()
            assertEquals(true, vm.uiState.value.video.pickFailed)

            // The dashboard adopts a file through the same store.
            videoStore.adoptSource(OTHER_FILE, FakeVideoSourceGrants())
            advanceUntilIdle()

            assertEquals(false, vm.uiState.value.video.pickFailed)
        }

    @Test
    fun `the state mirrors the video switches`() =
        runTest(dispatcher) {
            videoStore.setWindowEnabled(true)
            videoStore.setHidePictureWhileDriving(false)
            val vm = viewModel()
            subscribe(vm)

            assertEquals(true, vm.uiState.value.video.windowEnabled)
            assertEquals(false, vm.uiState.value.video.hidePictureWhileDriving)
        }

    @Test
    fun `no picked file reads as none`() =
        runTest(dispatcher) {
            val vm = viewModel()
            subscribe(vm)

            assertEquals(VideoFileSummary.None, vm.uiState.value.video.file)
        }

    @Test
    fun `a held file reads as its name`() =
        runTest(dispatcher) {
            val vm = viewModel()
            subscribe(vm)

            vm.onAction(SettingsAction.SetVideoFile(OTHER_FILE))
            advanceUntilIdle()

            assertEquals(VideoFileSummary.Named(OTHER_NAME), vm.uiState.value.video.file)
        }

    @Test
    fun `a file whose grant is gone reads as unavailable`() =
        runTest(dispatcher) {
            videoStore.setSourceUri(FILE)
            val vm = viewModel()
            subscribe(vm)

            assertEquals(VideoFileSummary.Unavailable, vm.uiState.value.video.file)
        }

    @Test
    fun `ResetSection(PANELS) restores the video switches and keeps the file`() =
        runTest(dispatcher) {
            videoStore.setWindowEnabled(true)
            videoStore.setHidePictureWhileDriving(false)
            videoStore.setSourceUri(FILE)

            viewModel().onAction(SettingsAction.ResetSection(SettingsSectionId.PANELS))
            advanceUntilIdle()

            assertEquals(VideoSettings.Default.copy(sourceUri = FILE), videoStore.current)
        }

    @Test
    fun `a reset with Panels ticked restores the video switches and keeps the file`() =
        runTest(dispatcher) {
            videoStore.setWindowEnabled(true)
            videoStore.setHidePictureWhileDriving(false)
            videoStore.setSourceUri(FILE)

            viewModel().onAction(
                SettingsAction.ResetSettings(setOf(SettingsSectionId.PANELS), deleteEnteredValues = false),
            )
            advanceUntilIdle()

            assertEquals(VideoSettings.Default.copy(sourceUri = FILE), videoStore.current)
        }

    @Test
    fun `a reset with Panels unticked leaves the video switches alone`() =
        runTest(dispatcher) {
            videoStore.setWindowEnabled(true)

            viewModel().onAction(
                SettingsAction.ResetSettings(
                    SettingsSectionId.entries.toSet() - SettingsSectionId.PANELS,
                    deleteEnteredValues = true,
                ),
            )
            advanceUntilIdle()

            assertEquals(true, videoStore.current.windowEnabled)
        }

    private fun TestScope.subscribe(vm: SettingsViewModel) {
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
    }

    private fun viewModel(grants: FakeVideoSourceGrants = this.grants) =
        fakeSettingsViewModel(videoPreferences = videoStore, videoGrants = grants, ioDispatcher = dispatcher)

    private companion object {
        const val FILE = "content://com.example.documents/document/video%3A1"
        const val OTHER_FILE = "content://com.example.documents/document/video%3A2"
        const val FILE_NAME = "drive.mp4"
        const val OTHER_NAME = "parked.mkv"
    }
}
