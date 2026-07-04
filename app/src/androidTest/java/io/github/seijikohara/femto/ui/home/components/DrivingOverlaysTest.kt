package io.github.seijikohara.femto.ui.home.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import dev.chrisbanes.haze.rememberHazeState
import io.github.seijikohara.femto.data.music.MusicCardState
import io.github.seijikohara.femto.testfixtures.fakeAddress
import io.github.seijikohara.femto.testfixtures.fakeCalendarSnapshot
import io.github.seijikohara.femto.testfixtures.fakeLocation
import io.github.seijikohara.femto.testfixtures.fakeNowPlaying
import io.github.seijikohara.femto.testfixtures.fakeTripState
import io.github.seijikohara.femto.testfixtures.fakeWeatherSnapshot
import io.github.seijikohara.femto.ui.home.HomeUiState
import io.github.seijikohara.femto.ui.home.components.driving.DrivingOverlays
import io.github.seijikohara.femto.ui.locale.SpeedUnit
import io.github.seijikohara.femto.ui.locale.TemperatureUnit
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test

class DrivingOverlaysTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun renders_big_speed_and_transport_when_playing() {
        rule.setContent {
            FemtoTheme {
                DrivingOverlays(
                    uiState =
                        HomeUiState.Initial.copy(
                            // A non-null fix keeps the hero numeral on the live-speed
                            // path (not the no-fix em-dash); the trip supplies the value.
                            // A 90 degree bearing also exercises the heading badge (-> "E").
                            location = fakeLocation(bearingDegrees = 90f),
                            address = fakeAddress(road = "Oak St"),
                            tripState = fakeTripState(currentSpeedMs = 18.0),
                            musicState = MusicCardState.Playing(fakeNowPlaying()),
                            weather = fakeWeatherSnapshot(),
                            calendar = fakeCalendarSnapshot(),
                        ),
                    speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
                    temperatureUnit = TemperatureUnit.CELSIUS,
                    glassConfig = GlassConfig(),
                    hazeState = rememberHazeState(),
                    outerPad = FemtoDimens.ScreenPadding,
                    onBarHeightChange = {},
                    onAction = {},
                )
            }
        }
        // 18 m/s -> 64.8 km/h -> 65: the big-speed numeral renders the rounded value.
        rule.onNodeWithText("65").assertExists()
        // Transport controls render (reusing TransportRow) so play/pause is reachable.
        rule.onNodeWithContentDescription("Play / pause").assertIsDisplayed()
        // The location strip shows the road name...
        rule.onNodeWithText("Oak St", substring = true).assertIsDisplayed()
        // ...and a 90 degree GPS-fix bearing renders as the "E" heading badge.
        rule.onNodeWithText("E").assertIsDisplayed()
    }
}
