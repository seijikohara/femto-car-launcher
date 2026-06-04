package io.github.seijikohara.femto.ui.settings

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
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
    fun `SetFullscreen ON persists via DisplayPreferences and uiState reflects it`() =
        runTest {
            val viewModel = SettingsViewModel(displayPreferences, FontPreferences(application))
            viewModel.uiState.test {
                assertEquals(FullscreenSetting.OFF, awaitItem().fullscreen)
                viewModel.onAction(SettingsAction.SetFullscreen(FullscreenSetting.ON))
                assertEquals(FullscreenSetting.ON, awaitItem().fullscreen)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(FullscreenSetting.ON, displayPreferences.settings.first().fullscreen)
        }
}
