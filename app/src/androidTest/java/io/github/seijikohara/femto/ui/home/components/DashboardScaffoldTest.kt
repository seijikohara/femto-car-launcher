package io.github.seijikohara.femto.ui.home.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import io.github.seijikohara.femto.data.ClockTick
import io.github.seijikohara.femto.testfixtures.fakeAddress
import io.github.seijikohara.femto.testfixtures.fakeNowPlaying
import io.github.seijikohara.femto.testfixtures.fakeWeatherSnapshot
import io.github.seijikohara.femto.ui.locale.DistanceUnit
import io.github.seijikohara.femto.ui.locale.SpeedUnit
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class DashboardScaffoldTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun renders_all_panels_when_data_is_present() {
        rule.setContent {
            FemtoTheme {
                DashboardScaffold(
                    clock = ClockTick(LocalTime.of(14, 32), LocalDate.of(2026, 5, 1)),
                    is24Hour = true,
                    weather = fakeWeatherSnapshot(),
                    temperatureUnit = "°C",
                    address = fakeAddress(),
                    location = null,
                    speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
                    distanceUnit = DistanceUnit.METERS,
                    mapAvailable = false,
                    nowPlaying = fakeNowPlaying(),
                    onMapTap = {},
                    onMusicCommand = {},
                    onConnectMusic = {},
                    onOpenDrawer = {},
                    onShortcut = {},
                )
            }
        }
        rule.onNodeWithText("14:32").assertIsDisplayed()
        rule.onNodeWithText("Strobe").assertIsDisplayed()
        rule.onNodeWithContentDescription("Open all apps").assertIsDisplayed()
    }
}
