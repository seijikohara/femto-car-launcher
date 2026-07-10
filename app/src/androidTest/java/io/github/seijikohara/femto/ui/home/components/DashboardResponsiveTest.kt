package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import io.github.seijikohara.femto.data.display.DockPosition
import io.github.seijikohara.femto.data.music.MusicCardState
import io.github.seijikohara.femto.testfixtures.fakeAddress
import io.github.seijikohara.femto.testfixtures.fakeCalendarSnapshot
import io.github.seijikohara.femto.testfixtures.fakeNowPlaying
import io.github.seijikohara.femto.testfixtures.fakeSystemStatus
import io.github.seijikohara.femto.testfixtures.fakeTripState
import io.github.seijikohara.femto.testfixtures.fakeWeatherSnapshot
import io.github.seijikohara.femto.ui.home.HomeUiState
import io.github.seijikohara.femto.ui.locale.SpeedUnit
import io.github.seijikohara.femto.ui.locale.TemperatureUnit
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Render the main dashboard at the head-unit geometries it must support and assert
 * it composes, measures, and lays out without breaking. The size is driven by a
 * [Modifier.requiredSize] wrapper because the dashboard is purely
 * [androidx.compose.foundation.layout.BoxWithConstraints]-driven (no
 * LocalConfiguration / window-size coupling), so the wrapper fully controls the
 * responsive split.
 *
 * Reaching the assertions means the whole tree composed without throwing at that
 * size — the primary "no breakage" signal across geometries; a layout that faulted
 * (unbounded constraint, zero-weight arithmetic) would throw before them. The two
 * universal anchors confirm the core structure (map pane + dock) survived the size.
 * `location = null` keeps the map on its static fallback (no MapLibre GL surface),
 * and `spectrum = null` avoids the audio-visualizer path. The GEOMETRIES list
 * mirrors the DashboardScaffold geometry previews — keep them in step.
 *
 * This is a layout / content-presence smoke test; pixel-level clipping and visual
 * regression are covered by the Roborazzi screenshot tests, not here.
 */
@RunWith(Parameterized::class)
internal class DashboardResponsiveTest(
    private val geometry: Geometry,
) {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun renders_without_breaking() {
        val uiState =
            HomeUiState.Initial.copy(
                location = null,
                address = fakeAddress(),
                weather = fakeWeatherSnapshot(),
                calendar = fakeCalendarSnapshot(),
                musicState = MusicCardState.Playing(fakeNowPlaying()),
                systemStatus = fakeSystemStatus(),
                tripState = fakeTripState(),
            )
        rule.setContent {
            FemtoTheme {
                Box(modifier = Modifier.requiredSize(geometry.width.dp, geometry.height.dp)) {
                    DashboardScaffold(
                        uiState = uiState,
                        is24Hour = true,
                        showClockSeconds = true,
                        speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
                        temperatureUnit = TemperatureUnit.CELSIUS,
                        mapConfig = MapConfig(),
                        panels = geometry.panels,
                        glassConfig = GlassConfig(),
                        onAction = {},
                        dockPosition = geometry.dockPosition,
                    )
                }
            }
        }
        rule.waitForIdle()
        // assertExists, not assertIsDisplayed: requiredSize can exceed the test
        // device's physical window (e.g. the 640dp-tall portrait case on a 480dp
        // device pushes the bottom dock off-window), and on-window visibility is a
        // device-size artifact, not breakage. Presence in the semantics tree means
        // the node composed and laid out at the requested geometry — the
        // size-independent "rendered without breaking" signal.
        rule.onNodeWithText("Map unavailable").assertExists()
        rule.onNodeWithContentDescription("Apps").assertExists()
    }

    internal data class Geometry(
        val label: String,
        val width: Int,
        val height: Int,
        val dockPosition: DockPosition = DockPosition.BOTTOM,
        val panels: PanelVisibility = PanelVisibility(),
    ) {
        override fun toString() = label
    }

    companion object {
        // Mirrors the DashboardScaffold geometry previews (the geometry SSOT):
        // 16:9, 8:3 ultra-wide, 5:3 head unit, portrait phone, left-rail dock, and a
        // partial-visibility case. Annotations can't be parameterised, so the list is
        // duplicated here deliberately — keep it in step with the previews.
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun geometries(): List<Geometry> =
            listOf(
                Geometry("16:9 640x360", width = 640, height = 360),
                Geometry("8:3 ultrawide 640x240", width = 640, height = 240),
                Geometry("5:3 head unit 853x512", width = 853, height = 512),
                Geometry("portrait 360x640", width = 360, height = 640),
                Geometry("left-rail 853x512", width = 853, height = 512, dockPosition = DockPosition.LEFT),
                Geometry(
                    "calendar-hidden 853x512",
                    width = 853,
                    height = 512,
                    panels = PanelVisibility(calendar = false),
                ),
            )
    }
}
