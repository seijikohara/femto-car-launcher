package io.github.seijikohara.femto.ui.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.seijikohara.femto.data.clock.ClockTick
import io.github.seijikohara.femto.data.music.MusicCardState
import io.github.seijikohara.femto.testfixtures.fakeAddress
import io.github.seijikohara.femto.testfixtures.fakeCalendarSnapshot
import io.github.seijikohara.femto.testfixtures.fakeNowPlaying
import io.github.seijikohara.femto.testfixtures.fakeSystemStatus
import io.github.seijikohara.femto.testfixtures.fakeTripState
import io.github.seijikohara.femto.testfixtures.fakeWeatherSnapshot
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
import java.time.LocalDate
import java.time.LocalTime

/**
 * JVM/Robolectric screenshot regression for the main dashboard across the display
 * geometries the launcher realistically ships on. Each `@Config(qualifiers = …)`
 * lays the dashboard out in a real window of that size (the idiomatic Roborazzi way
 * to test multiple device sizes), so this catches visual breakage — clipping,
 * overlap, truncation — that the layout/content-presence instrumented test
 * (DashboardResponsiveTest) cannot. Runs on the JVM, so unlike androidTest it
 * executes in CI (verifyRoborazziDebug).
 *
 * Geometry selection (in dp): in-car displays run at low density (~mdpi, dp ≈ px),
 * so their dp footprints are large — a mainstream 16:9 head unit is ~1280×720 dp,
 * not the phone-sized figures a high-density assumption would give. The set spans
 * the real-world clusters surveyed across worldwide automakers, aftermarket Android
 * head units, CarPlay / Android Auto projection, and dashboard-mounted phones /
 * tablets:
 *  - 5:3 / 17:10 head units: the 800×480 floor (Android Auto minimum, cheapest
 *    units), the 853×512 reference binding, the 1024×600 budget cluster, and the
 *    2000×1200 premium panel.
 *  - 16:9 head units: 1280×720 mainstream and the 1920×1080 flagship / Android Auto
 *    ceiling.
 *  - 8:3 ultrawide bar: 1920×720 (Hyundai-group ccNC, Mazda, OEM-fit widescreens).
 *  - Dashboard phones: 915×412 landscape and 412×915 portrait.
 *  - Tablets / native car portrait: 800×1280, and the tall 1024×1365 / 1200×1920
 *    portrait panels (Polestar / Ram class).
 * All recorded at mdpi: density only scales the captured PNG, not the dp layout, so
 * one density keeps the goldens small and the geometries faithful.
 *
 * Pinned to `sdk = 33` like the other Robolectric tests (sidesteps the compileSdk
 * gap). `location = null` keeps the map on its static fallback (no MapLibre GL).
 * Caveats baked into the goldens: Robolectric's software rasterizer does not render
 * the Haze RenderEffect blur (the glass overlays show their tint, not the frost),
 * and downloadable Google Fonts fall back to the system face — both deterministic.
 * Goldens must be recorded on the CI OS (Linux) so verifyRoborazziDebug matches; a
 * small changeThreshold absorbs residual sub-pixel antialiasing differences.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class DashboardScreenshotTest {
    // --- Landscape head units (right-column layout) ---

    @Test
    @Config(qualifiers = "w800dp-h480dp-mdpi")
    fun dashboard_floor_800x480_5x3() = capture("floor-800x480")

    @Test
    @Config(qualifiers = "w853dp-h512dp-mdpi")
    fun dashboard_head_unit_853x512_5x3() = capture("head-unit-853x512")

    @Test
    @Config(qualifiers = "w1024dp-h600dp-mdpi")
    fun dashboard_budget_1024x600() = capture("budget-1024x600")

    @Test
    @Config(qualifiers = "w1280dp-h720dp-mdpi")
    fun dashboard_mainstream_1280x720_16x9() = capture("mainstream-1280x720")

    @Test
    @Config(qualifiers = "w1920dp-h720dp-mdpi")
    fun dashboard_ultrawide_1920x720_8x3() = capture("ultrawide-1920x720")

    @Test
    @Config(qualifiers = "w1920dp-h1080dp-mdpi")
    fun dashboard_flagship_1920x1080_16x9() = capture("flagship-1920x1080")

    @Test
    @Config(qualifiers = "w2000dp-h1200dp-mdpi")
    fun dashboard_premium_2000x1200_5x3() = capture("premium-2000x1200")

    // --- Dashboard phone (single bottom card row) ---

    @Test
    @Config(qualifiers = "w915dp-h412dp-mdpi")
    fun dashboard_phone_landscape_915x412() = capture("phone-landscape-915x412")

    // --- Portrait (bottom card band): phone, tablet, native car portrait ---

    @Test
    @Config(qualifiers = "w412dp-h915dp-mdpi")
    fun dashboard_phone_portrait_412x915() = capture("phone-portrait-412x915")

    @Test
    @Config(qualifiers = "w800dp-h1280dp-mdpi")
    fun dashboard_tablet_800x1280() = capture("tablet-800x1280")

    @Test
    @Config(qualifiers = "w1024dp-h1365dp-mdpi")
    fun dashboard_car_portrait_1024x1365() = capture("car-portrait-1024x1365")

    @Test
    @Config(qualifiers = "w1200dp-h1920dp-mdpi")
    fun dashboard_car_portrait_tall_1200x1920() = capture("car-portrait-tall-1200x1920")

    private fun capture(name: String) {
        captureRoboImage(filePath = "src/test/screenshots/dashboard-$name.png", roborazziOptions = OPTIONS) {
            FemtoTheme {
                DashboardScaffold(
                    uiState = STATE,
                    is24Hour = true,
                    showClockSeconds = true,
                    speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
                    temperatureUnit = TemperatureUnit.CELSIUS,
                    mapConfig = MapConfig(),
                    panels = PanelVisibility(),
                    glassConfig = GlassConfig(),
                    onAction = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    private companion object {
        // A small tolerance absorbs sub-pixel antialiasing differences between the
        // golden-record host and the CI runner while still catching real layout
        // regressions.
        val OPTIONS =
            RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f),
            )

        val STATE =
            HomeUiState.Initial.copy(
                clock = ClockTick(LocalTime.of(14, 32), LocalDate.of(2026, 5, 1)),
                location = null,
                address = fakeAddress(),
                weather = fakeWeatherSnapshot(),
                calendar = fakeCalendarSnapshot(),
                musicState = MusicCardState.Playing(fakeNowPlaying()),
                systemStatus = fakeSystemStatus(),
                tripState = fakeTripState(),
            )
    }
}
