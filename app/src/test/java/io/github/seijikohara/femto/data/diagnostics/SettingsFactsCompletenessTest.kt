package io.github.seijikohara.femto.data.diagnostics

import io.github.seijikohara.femto.data.display.DisplaySettings
import org.junit.Test
import java.lang.reflect.Modifier
import kotlin.test.assertEquals

/**
 * Completeness drift guard for the SETTINGS dump: every [DisplaySettings] field
 * must surface as a fact, so a field added without a matching fact is caught
 * here instead of silently vanishing from every diagnostics report — the same
 * guard [io.github.seijikohara.femto.data.display.SettingsSectionIdTest] applies
 * to section keys.
 *
 * [propertyToFactLabel] is the bridge: facts that fold several fields into one
 * row (e.g. the light/dark map schemes) repeat the label. Reflection over the
 * data class's declared properties (Java reflection — kotlin-reflect is not on
 * the classpath) is the authoritative set the bridge is checked against.
 */
class SettingsFactsCompletenessTest {
    private val propertyToFactLabel: Map<String, String> =
        mapOf(
            "themeMode" to "Theme mode",
            "accentColor" to "Accent",
            "uiScale" to "UI scale",
            "speedUnit" to "Speed unit",
            "temperatureUnit" to "Temperature unit",
            "clock" to "Clock",
            "showClockSeconds" to "Clock seconds",
            "fullscreen" to "Fullscreen",
            "dockPosition" to "Dock position",
            "driverSide" to "Driver side",
            "motionTier" to "Motion tier",
            "orientation" to "Orientation",
            "keepScreenOn" to "Keep screen on",
            "assistantLaunch" to "Assistant launch",
            "mapBackend" to "Map backend",
            "mapStyle" to "Map style",
            "mapSchemeLight" to "Map schemes",
            "mapSchemeDark" to "Map schemes",
            "mapTiltDeg" to "Map zoom / tilt",
            "mapZoom" to "Map zoom / tilt",
            "mapNorthUp" to "Map north-up",
            "mapMarkerPos" to "Map marker position",
            "map3dBuildings" to "3D buildings / terrain",
            "mapTerrain" to "3D buildings / terrain",
            "glassBlurRadius" to "Glass blur / tint",
            "glassTintScale" to "Glass blur / tint",
            "glassShowBorder" to "Glass border / shadow",
            "glassShadowEnabled" to "Glass border / shadow",
            "glassShadowIntensity" to "Glass border / shadow",
            "glassShadowSizeDp" to "Glass border / shadow",
            "fontBaseSizeSp" to "Font size / weight / spacing",
            "fontWeightStep" to "Font size / weight / spacing",
            "fontLetterSpacingCentiEm" to "Font size / weight / spacing",
            "showCalendar" to "Panels (calendar / weather / music)",
            "showWeather" to "Panels (calendar / weather / music)",
            "showMusic" to "Panels (calendar / weather / music)",
            "musicSpectrum" to "Music spectrum",
            "musicShowAlbum" to "Music album / art",
            "musicShowArt" to "Music album / art",
            "mapboxStyle" to "Mapbox style",
            "mapboxTraffic" to "Mapbox traffic",
            "mapboxAccessToken" to "Mapbox token",
            "googleMapsRendering" to "Google Maps rendering",
            "googleMapsMapType" to "Google Maps type",
            "googleMapsTraffic" to "Google Maps traffic",
            "googleMapsApiKey" to "Google Maps key",
            "googleMapsMapId" to "Google Maps map ID",
        )

    @Test
    fun `every DisplaySettings property is mapped to a settings fact`() {
        val declaredProperties =
            DisplaySettings::class.java.declaredFields
                .filterNot { it.isSynthetic || Modifier.isStatic(it.modifiers) }
                .map { it.name }
                .toSet()

        assertEquals(declaredProperties, propertyToFactLabel.keys)
    }

    @Test
    fun `the collector emits every mapped fact label`() {
        val emittedLabels = displaySettingsFacts(DisplaySettings.Default).map { it.label }.toSet()

        assertEquals(propertyToFactLabel.values.toSet(), emittedLabels)
    }
}
