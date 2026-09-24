package io.github.seijikohara.femto.ui.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.seijikohara.femto.data.display.DriverSide
import io.github.seijikohara.femto.data.display.UiScale
import io.github.seijikohara.femto.testfixtures.DashboardFixtures
import io.github.seijikohara.femto.testfixtures.DashboardGeometries
import io.github.seijikohara.femto.testfixtures.MapBackdrop
import io.github.seijikohara.femto.testfixtures.ScreenshotCompareOptions
import io.github.seijikohara.femto.ui.home.components.DashboardScaffold
import io.github.seijikohara.femto.ui.home.components.GlassConfig
import io.github.seijikohara.femto.ui.home.components.MapConfig
import io.github.seijikohara.femto.ui.home.components.PanelVisibility
import io.github.seijikohara.femto.ui.locale.SpeedUnit
import io.github.seijikohara.femto.ui.locale.TemperatureUnit
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * JVM/Robolectric screenshot regression for the main dashboard across the display
 * geometries the launcher realistically ships on. Each `@Config(qualifiers = …)`
 * lays the dashboard out in a real window of that size (the idiomatic Roborazzi way
 * to test multiple device sizes), so this catches visual breakage — clipping,
 * overlap, truncation — that the layout/content-presence instrumented test
 * (DashboardResponsiveTest) cannot. Runs on the JVM, so unlike androidTest it
 * executes in CI (verifyRoborazziStableDebug).
 *
 * The geometries are [DashboardGeometries] — see its KDoc for the selection
 * rationale; the catalog generator renders the same list.
 *
 * Pinned to `sdk = 33` like the other Robolectric tests (sidesteps the compileSdk
 * gap). `location = null` keeps the map on its static fallback (no MapLibre GL).
 * Caveats baked into the goldens: Robolectric's software rasterizer does not render
 * the Haze RenderEffect blur (the glass overlays show their tint, not the frost),
 * and downloadable Google Fonts fall back to the system face — both deterministic.
 * Goldens must be recorded on the CI OS (Linux) so verifyRoborazziStableDebug matches; a
 * small changeThreshold absorbs residual sub-pixel antialiasing differences.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class DashboardScreenshotTest {
    // --- Landscape head units (right-column layout) ---

    @Test
    @Config(qualifiers = DashboardGeometries.FLOOR_800X480)
    fun dashboard_floor_800x480_5x3() = capture("floor-800x480")

    @Test
    @Config(qualifiers = DashboardGeometries.HEAD_UNIT_853X512)
    fun dashboard_head_unit_853x512_5x3() = capture("head-unit-853x512")

    @Test
    @Config(qualifiers = DashboardGeometries.BUDGET_1024X600)
    fun dashboard_budget_1024x600() = capture("budget-1024x600")

    @Test
    @Config(qualifiers = DashboardGeometries.MAINSTREAM_1280X720)
    fun dashboard_mainstream_1280x720_16x9() = capture("mainstream-1280x720")

    @Test
    @Config(qualifiers = DashboardGeometries.ULTRAWIDE_1920X720)
    fun dashboard_ultrawide_1920x720_8x3() = capture("ultrawide-1920x720")

    @Test
    @Config(qualifiers = DashboardGeometries.FLAGSHIP_1920X1080)
    fun dashboard_flagship_1920x1080_16x9() = capture("flagship-1920x1080")

    @Test
    @Config(qualifiers = DashboardGeometries.PREMIUM_2000X1200)
    fun dashboard_premium_2000x1200_5x3() = capture("premium-2000x1200")

    // --- Dashboard phone (single bottom card row) ---

    @Test
    @Config(qualifiers = DashboardGeometries.PHONE_LANDSCAPE_915X412)
    fun dashboard_phone_landscape_915x412() = capture("phone-landscape-915x412")

    // --- Portrait (bottom card band): phone, tablet, native car portrait ---

    @Test
    @Config(qualifiers = DashboardGeometries.PHONE_PORTRAIT_412X915)
    fun dashboard_phone_portrait_412x915() = capture("phone-portrait-412x915")

    @Test
    @Config(qualifiers = DashboardGeometries.TABLET_800X1280)
    fun dashboard_tablet_800x1280() = capture("tablet-800x1280")

    @Test
    @Config(qualifiers = DashboardGeometries.CAR_PORTRAIT_1024X1365)
    fun dashboard_car_portrait_1024x1365() = capture("car-portrait-1024x1365")

    @Test
    @Config(qualifiers = DashboardGeometries.CAR_PORTRAIT_TALL_1200X1920)
    fun dashboard_car_portrait_tall_1200x1920() = capture("car-portrait-tall-1200x1920")

    // --- UI-scale opt-ins: SMALL fits the tight phone-landscape, LARGE enlarges a
    // comfortable head unit. Default MEDIUM is the no-op baseline of every case above. ---

    @Test
    @Config(qualifiers = DashboardGeometries.PHONE_LANDSCAPE_915X412)
    fun dashboard_phone_landscape_small_scale() = capture("phone-landscape-915x412-small", UiScale.SMALL)

    @Test
    @Config(qualifiers = DashboardGeometries.HEAD_UNIT_853X512)
    fun dashboard_head_unit_large_scale() = capture("head-unit-853x512-large", UiScale.LARGE)

    // --- Driver-side opt-ins: LEFT mirrors the dashboard column to the driver's
    // left. Default RIGHT is the no-op baseline of every case above. ---

    @Test
    @Config(qualifiers = DashboardGeometries.HEAD_UNIT_853X512)
    fun dashboard_head_unit_driver_left() = capture("head-unit-853x512-driver-left", driverSide = DriverSide.LEFT)

    @Test
    @Config(qualifiers = DashboardGeometries.PHONE_PORTRAIT_412X915)
    fun dashboard_phone_portrait_driver_left() =
        capture("phone-portrait-412x915-driver-left", driverSide = DriverSide.LEFT)

    // Dark variant: the glass chrome, the card surfaces and the map style all
    // swap, and none of the light goldens above exercise that pairing.
    @Test
    @Config(qualifiers = DashboardGeometries.HEAD_UNIT_853X512)
    fun dashboard_head_unit_dark() = capture("head-unit-853x512-dark", darkTheme = true)

    private fun capture(
        name: String,
        uiScale: UiScale = UiScale.MEDIUM,
        driverSide: DriverSide = DriverSide.RIGHT,
        darkTheme: Boolean = false,
    ) {
        captureRoboImage(
            filePath = "src/test/screenshots/dashboard-$name.png",
            roborazziOptions = ScreenshotCompareOptions,
        ) {
            FemtoTheme(uiScale = uiScale, darkTheme = darkTheme) {
                DashboardScaffold(
                    uiState = DashboardFixtures.state,
                    is24Hour = true,
                    showClockSeconds = true,
                    speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
                    temperatureUnit = TemperatureUnit.CELSIUS,
                    mapConfig = MapConfig(),
                    panels = PanelVisibility(),
                    glassConfig = GlassConfig(),
                    onAction = {},
                    modifier = Modifier.fillMaxSize(),
                    driverSide = driverSide,
                    clock = DashboardFixtures.fixedClock,
                    mapSurface = { _, config -> MapBackdrop(darkTheme = darkTheme, mapConfig = config) },
                )
            }
        }
    }
}
