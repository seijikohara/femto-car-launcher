package io.github.seijikohara.femto.ui.home.components

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.github.seijikohara.femto.data.music.MusicCardState
import io.github.seijikohara.femto.testfixtures.fakeAddress
import io.github.seijikohara.femto.testfixtures.fakeCalendarSnapshot
import io.github.seijikohara.femto.testfixtures.fakeLocation
import io.github.seijikohara.femto.testfixtures.fakeNowPlaying
import io.github.seijikohara.femto.testfixtures.fakeTripState
import io.github.seijikohara.femto.testfixtures.fakeWeatherSnapshot
import io.github.seijikohara.femto.ui.home.HomeUiState
import io.github.seijikohara.femto.ui.home.PresetId
import io.github.seijikohara.femto.ui.locale.SpeedUnit
import io.github.seijikohara.femto.ui.locale.TemperatureUnit
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test

/**
 * The preset switch selects which overlay face the scaffold cross-fades in. These
 * tests drive [DashboardScaffold.activePreset] directly and assert on content that
 * is unique to one face:
 *  - Driving-only: the briefing renders the next event as a single "HH:mm title"
 *    string ("10:30 Team standup"). The cockpit calendar card renders the time and
 *    the title as separate nodes, so that combined string never appears there.
 *  - Cockpit-only: the calendar agenda lists every event of the day ("Pick up
 *    kids"), while the driving briefing shows only the first, so the later event is
 *    a cockpit-exclusive marker.
 *
 * `location` is non-null so the driving face takes its live-speed path and the
 * cockpit face renders its map controls, matching a real fix.
 */
class DashboardPresetTest {
    @get:Rule
    val rule = createComposeRule()

    private val uiState =
        HomeUiState.Initial.copy(
            location = fakeLocation(),
            address = fakeAddress(),
            tripState = fakeTripState(currentSpeedMs = 18.0),
            weather = fakeWeatherSnapshot(),
            calendar = fakeCalendarSnapshot(),
            musicState = MusicCardState.Playing(fakeNowPlaying()),
        )

    @Test
    fun driving_preset_shows_driving_face_and_hides_cockpit_calendar() {
        rule.setContent {
            FemtoTheme {
                DashboardScaffold(
                    uiState = uiState,
                    is24Hour = true,
                    showClockSeconds = true,
                    speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
                    temperatureUnit = TemperatureUnit.CELSIUS,
                    mapConfig = MapConfig(),
                    panels = PanelVisibility(),
                    glassConfig = GlassConfig(),
                    onAction = {},
                    activePreset = PresetId.DRIVING,
                )
            }
        }
        // Driving-only: the briefing's combined next-event line.
        rule.onNodeWithText("10:30 Team standup").assertExists()
        // Cockpit-only: a later agenda event never surfaces on the driving face.
        rule.onNodeWithText("Pick up kids").assertDoesNotExist()
    }

    @Test
    fun cockpit_preset_shows_cockpit_calendar_and_hides_driving_briefing() {
        rule.setContent {
            FemtoTheme {
                DashboardScaffold(
                    uiState = uiState,
                    is24Hour = true,
                    showClockSeconds = true,
                    speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
                    temperatureUnit = TemperatureUnit.CELSIUS,
                    mapConfig = MapConfig(),
                    panels = PanelVisibility(),
                    glassConfig = GlassConfig(),
                    onAction = {},
                    activePreset = PresetId.COCKPIT,
                )
            }
        }
        // Cockpit-only: the calendar card lists the full day's agenda.
        rule.onNodeWithText("Pick up kids").assertExists()
        // Driving-only: the briefing's combined line never appears on the cockpit face.
        rule.onNodeWithText("10:30 Team standup").assertDoesNotExist()
    }
}
