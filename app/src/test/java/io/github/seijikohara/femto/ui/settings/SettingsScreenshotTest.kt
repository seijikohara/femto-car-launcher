package io.github.seijikohara.femto.ui.settings

import com.github.takahirom.roborazzi.captureRoboImage
import io.github.seijikohara.femto.testfixtures.DashboardGeometries
import io.github.seijikohara.femto.testfixtures.ScreenshotCompareOptions
import io.github.seijikohara.femto.testfixtures.SettingsForCapture
import io.github.seijikohara.femto.testfixtures.routingOfferedSettingsState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings-screen goldens: every category's detail pane in the wide
 * master-detail layout at the reference head-unit geometry, one dark variant,
 * and the narrow list-detail layout's list and one detail page. Settings had no
 * screenshot goldens, so these pin the current rows as the baseline the #427
 * restyle (rows as Expressive list items in segmented groups) diffs against —
 * without them, that restyle could move or drop a row unnoticed.
 *
 * Each pane renders from [SettingsUiState.Initial] (Navigation's with routing
 * offered, the only state that lists it) and is captured unscrolled,
 * so a golden shows only the rows above the fold. The Updates pane's version
 * row reads `BuildConfig.VERSION_NAME`, which falls back to "1.0" whenever the
 * `VERSION_NAME` environment variable is unset — as it is for the CI unit-test
 * leg that records and verifies these goldens. Same recording flow as the
 * dashboard panel goldens: recorded on CI and committed from the artifact
 * (macOS and Linux anti-alias differently, so a local record would not match
 * the CI runner).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class SettingsScreenshotTest {
    // --- Wide master-detail layout, one golden per category ---

    @Test
    @Config(qualifiers = DashboardGeometries.HEAD_UNIT_853X512)
    fun settings_appearance_head_unit() = captureHeadUnit(SettingsCategoryId.APPEARANCE)

    @Test
    @Config(qualifiers = DashboardGeometries.HEAD_UNIT_853X512)
    fun settings_screen_head_unit() = captureHeadUnit(SettingsCategoryId.SCREEN)

    @Test
    @Config(qualifiers = DashboardGeometries.HEAD_UNIT_853X512)
    fun settings_units_head_unit() = captureHeadUnit(SettingsCategoryId.UNITS)

    @Test
    @Config(qualifiers = DashboardGeometries.HEAD_UNIT_853X512)
    fun settings_map_head_unit() = captureHeadUnit(SettingsCategoryId.MAP)

    // Navigation shows only where routing is offered, so its state offers it;
    // every other golden keeps a state without routing, and its eight categories.
    @Test
    @Config(qualifiers = DashboardGeometries.HEAD_UNIT_853X512)
    fun settings_navigation_head_unit() =
        captureHeadUnit(
            SettingsCategoryId.NAVIGATION,
            uiState = routingOfferedSettingsState(),
        )

    @Test
    @Config(qualifiers = DashboardGeometries.HEAD_UNIT_853X512)
    fun settings_location_head_unit() = captureHeadUnit(SettingsCategoryId.LOCATION)

    @Test
    @Config(qualifiers = DashboardGeometries.HEAD_UNIT_853X512)
    fun settings_panels_head_unit() = captureHeadUnit(SettingsCategoryId.PANELS)

    @Test
    @Config(qualifiers = DashboardGeometries.HEAD_UNIT_853X512)
    fun settings_updates_head_unit() = captureHeadUnit(SettingsCategoryId.UPDATES)

    @Test
    @Config(qualifiers = DashboardGeometries.HEAD_UNIT_853X512)
    fun settings_system_head_unit() = captureHeadUnit(SettingsCategoryId.SYSTEM)

    // Dark variant: the rows sit on surfaceContainerLow and the selected rail
    // entry on a tonal container, both of which the light goldens never show
    // in the dark scheme.
    @Test
    @Config(qualifiers = DashboardGeometries.HEAD_UNIT_853X512)
    fun settings_appearance_head_unit_dark() = captureHeadUnit(SettingsCategoryId.APPEARANCE, darkTheme = true)

    // --- Narrow list-detail layout ---

    // A null initial category opens on the category list rather than a detail page.
    @Test
    @Config(qualifiers = DashboardGeometries.PHONE_PORTRAIT_412X915)
    fun settings_list_portrait() = capture("settings-list-portrait-412x915", initialCategory = null)

    @Test
    @Config(qualifiers = DashboardGeometries.PHONE_PORTRAIT_412X915)
    fun settings_appearance_portrait() =
        capture("settings-appearance-portrait-412x915", initialCategory = SettingsCategoryId.APPEARANCE)

    private fun captureHeadUnit(
        category: SettingsCategoryId,
        darkTheme: Boolean = false,
        uiState: SettingsUiState = SettingsUiState.Initial,
    ) = capture(
        name = "settings-${category.name.lowercase()}-head-unit-853x512" + if (darkTheme) "-dark" else "",
        initialCategory = category,
        darkTheme = darkTheme,
        uiState = uiState,
    )

    private fun capture(
        name: String,
        initialCategory: SettingsCategoryId?,
        darkTheme: Boolean = false,
        uiState: SettingsUiState = SettingsUiState.Initial,
    ) {
        captureRoboImage(filePath = "src/test/screenshots/$name.png", roborazziOptions = ScreenshotCompareOptions) {
            SettingsForCapture(initialCategory = initialCategory, darkTheme = darkTheme, uiState = uiState)
        }
    }
}
