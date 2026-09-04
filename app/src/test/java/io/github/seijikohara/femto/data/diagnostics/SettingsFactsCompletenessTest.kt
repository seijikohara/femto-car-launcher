package io.github.seijikohara.femto.data.diagnostics

import io.github.seijikohara.femto.data.display.DisplaySettings
import io.github.seijikohara.femto.data.location.LocationSettings
import org.junit.Test
import java.lang.reflect.Modifier
import kotlin.test.assertEquals

/**
 * Completeness drift guard for the SETTINGS dump: every [DisplaySettings] and
 * [LocationSettings] field must surface as a fact, so a field added without a
 * matching fact is caught here instead of silently vanishing from every
 * diagnostics report — the same guard
 * [io.github.seijikohara.femto.data.display.SettingsSectionIdTest] applies to
 * section keys.
 *
 * [propertyToFactLabel] and [locationPropertyToFactLabel] are the bridges: facts
 * that fold several fields into one row (e.g. the light/dark map schemes) repeat
 * the label. Reflection over the data class's declared properties (Java
 * reflection — kotlin-reflect is not on the classpath) is the authoritative set
 * each bridge is checked against.
 */
class SettingsFactsCompletenessTest {
    private val locationPropertyToFactLabel: Map<String, String> =
        mapOf(
            "quality" to "Location quality",
            "intervalMillis" to "Location interval",
            "minUpdateDistanceMeters" to "Location min distance",
            "backgroundRangingEnabled" to "Background ranging",
            "tripAutoReset" to "Trip auto-reset",
            "trackRecordingEnabled" to "Track recording",
            "trackRetention" to "Track retention",
        )

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
            "dockWidth" to "Dock width",
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
        assertEquals(declaredProperties(DisplaySettings::class.java), propertyToFactLabel.keys)
    }

    @Test
    fun `the collector emits every mapped fact label`() {
        val emittedLabels = displaySettingsFacts(DisplaySettings.Default).map { it.label }.toSet()

        assertEquals(propertyToFactLabel.values.toSet(), emittedLabels)
    }

    @Test
    fun `every LocationSettings property is mapped to a settings fact`() {
        assertEquals(declaredProperties(LocationSettings::class.java), locationPropertyToFactLabel.keys)
    }

    @Test
    fun `the collector emits every mapped location fact label`() {
        val emittedLabels = locationSettingsFacts(LocationSettings.Default).map { it.label }.toSet()

        assertEquals(locationPropertyToFactLabel.values.toSet(), emittedLabels)
    }

    private fun declaredProperties(type: Class<*>): Set<String> =
        type.declaredFields
            .filterNot { it.isSynthetic || Modifier.isStatic(it.modifiers) }
            .map { it.name }
            .toSet()
}
