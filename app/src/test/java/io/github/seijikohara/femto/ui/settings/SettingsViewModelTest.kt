package io.github.seijikohara.femto.ui.settings

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.github.seijikohara.femto.data.DisplayPreferences
import io.github.seijikohara.femto.data.FontPreferences
import io.github.seijikohara.femto.data.FullscreenSetting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsViewModelTest {
    private val application: Application = ApplicationProvider.getApplicationContext()
    private val displayPreferences = DisplayPreferences(application)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `SetFullscreen persists via DisplayPreferences`() =
        runTest {
            val viewModel = SettingsViewModel(displayPreferences, FontPreferences(application))
            viewModel.onAction(SettingsAction.SetFullscreen(FullscreenSetting.ON))
            // Assert the persisted value via first { predicate }: it waits for the
            // write to land and is robust both to the DataStore being shared across
            // the tests in this class and to DataStore IO completing off the test
            // clock. Collecting the ViewModel StateFlow here instead hangs runTest,
            // because its WhileSubscribed upstream reads the real DataStore on IO.
            assertEquals(
                FullscreenSetting.ON,
                displayPreferences.settings.first { it.fullscreen == FullscreenSetting.ON }.fullscreen,
            )
        }

    @Test
    fun `SetMapFps persists via DisplayPreferences`() =
        runTest {
            val viewModel = SettingsViewModel(displayPreferences, FontPreferences(application))
            viewModel.onAction(SettingsAction.SetMapFps(30))
            assertEquals(30, displayPreferences.settings.first { it.mapFps == 30 }.mapFps)
        }
}
