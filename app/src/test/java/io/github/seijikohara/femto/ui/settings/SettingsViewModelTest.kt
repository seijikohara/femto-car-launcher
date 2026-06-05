package io.github.seijikohara.femto.ui.settings

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.github.seijikohara.femto.data.DisplayPreferences
import io.github.seijikohara.femto.data.DisplaySettings
import io.github.seijikohara.femto.data.FontPreferences
import io.github.seijikohara.femto.data.FullscreenSetting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsViewModelTest {
    private val application: Application = ApplicationProvider.getApplicationContext()
    private val displayPreferences = DisplayPreferences(application)

    @Before
    fun setUp() {
        // Real Unconfined Main so a viewModelScope.launch from onAction runs its
        // body eagerly on the calling thread. UnconfinedTestDispatcher would queue
        // the body on a scheduler that only runTest pumps, so under runBlocking the
        // write never executed and the await timed out.
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `SetFullscreen persists via DisplayPreferences`() =
        runBlocking {
            viewModel().onAction(SettingsAction.SetFullscreen(FullscreenSetting.ON))
            assertEquals(
                FullscreenSetting.ON,
                awaitSetting { it.fullscreen == FullscreenSetting.ON }.fullscreen,
            )
        }

    @Test
    fun `SetMapFps persists via DisplayPreferences`() =
        runBlocking {
            viewModel().onAction(SettingsAction.SetMapFps(30))
            assertEquals(30, awaitSetting { it.mapFps == 30 }.mapFps)
        }

    @Test
    fun `SetShowMusic persists via DisplayPreferences`() =
        runBlocking {
            viewModel().onAction(SettingsAction.SetShowMusic(false))
            assertEquals(false, awaitSetting { !it.showMusic }.showMusic)
        }

    private fun viewModel() = SettingsViewModel(displayPreferences, FontPreferences(application))

    // Wait for the persisted settings to satisfy [predicate]. These tests exercise
    // the real (Robolectric) DataStore round-trip, whose IO runs off the test
    // scheduler; runTest's virtual clock cannot observe it and its end-of-test
    // completion check races the write to an UncompletedCoroutinesError. runBlocking
    // waits on real time instead, and this withTimeout bounds a write that never
    // lands so a regression fails fast rather than hanging.
    private suspend fun awaitSetting(predicate: (DisplaySettings) -> Boolean): DisplaySettings =
        withTimeout(WRITE_TIMEOUT_MS) {
            displayPreferences.settings.first(predicate)
        }

    private companion object {
        const val WRITE_TIMEOUT_MS = 5_000L
    }
}
