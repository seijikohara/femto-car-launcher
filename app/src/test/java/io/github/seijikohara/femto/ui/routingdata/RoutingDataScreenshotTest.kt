package io.github.seijikohara.femto.ui.routingdata

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.seijikohara.femto.data.routing.InstalledRoutingTile
import io.github.seijikohara.femto.testfixtures.DashboardGeometries
import io.github.seijikohara.femto.testfixtures.ScreenshotCompareOptions
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Routing data sheet's golden (#389 phase 3, nightly builds only): two areas on the device, one never checked.
 * The tests run the stable build, where routing is off and Settings shows no Routing data row, so the sheet's state
 * is set here. Recorded on CI like every golden (see SettingsScreenshotTest).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class RoutingDataScreenshotTest {
    @Test
    @Config(qualifiers = DashboardGeometries.HEAD_UNIT_853X512)
    fun routing_data_head_unit() {
        captureRoboImage(
            filePath = "src/test/screenshots/routing-data-head-unit-853x512.png",
            roborazziOptions = ScreenshotCompareOptions,
        ) {
            FemtoTheme {
                RoutingDataScreen(
                    uiState =
                        RoutingDataUiState(
                            loaded = true,
                            tiles =
                                listOf(
                                    InstalledRoutingTile("E135_N30", 48_211_968L, CHECKED_EPOCH_MS, CHECKED_EPOCH_MS),
                                    InstalledRoutingTile("E135_N35", 96_403_456L, null, null),
                                ),
                        ),
                    onAction = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    private companion object {
        // Midday UTC, so the date reads the same on a runner within twelve hours of UTC.
        const val CHECKED_EPOCH_MS = 1_795_348_800_000L
    }
}
