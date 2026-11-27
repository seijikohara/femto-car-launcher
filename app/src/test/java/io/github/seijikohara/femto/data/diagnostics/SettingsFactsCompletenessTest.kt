package io.github.seijikohara.femto.data.diagnostics

import io.github.seijikohara.femto.data.display.DisplaySettings
import io.github.seijikohara.femto.data.location.LocationSettings
import io.github.seijikohara.femto.data.routing.NavigationSettings
import io.github.seijikohara.femto.data.routing.RouteRowTime
import io.github.seijikohara.femto.data.update.UpdateSettings
import io.github.seijikohara.femto.data.video.VideoSettings
import org.junit.Test
import java.lang.reflect.Modifier
import kotlin.test.assertEquals

/**
 * Completeness drift guard for the SETTINGS dump: every [DisplaySettings],
 * [LocationSettings], [UpdateSettings], [VideoSettings] and
 * [NavigationSettings] field must surface as a fact, so a
 * field added without a matching fact is caught here instead of silently
 * vanishing from every diagnostics report — the same guard
 * [io.github.seijikohara.femto.data.display.SettingsSectionIdTest] applies to
 * section keys.
 *
 * [propertyToFactLabel], [locationPropertyToFactLabel],
 * [updatePropertyToFactLabel], [videoPropertyToFactLabel] and
 * [navigationPropertyToFactLabel] are the bridges:
 * facts that fold several fields
 * into one row (e.g. the light/dark map schemes) repeat the label. Reflection
 * over the data class's declared properties (Java reflection — kotlin-reflect
 * is not on the classpath) is the authoritative set each bridge is checked
 * against.
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

    private val updatePropertyToFactLabel: Map<String, String> =
        mapOf(
            "autoCheck" to "Update auto-check",
            "lastCheckAttemptAt" to "Update last check attempt",
            "pendingInstallVersionCode" to "Update pending install",
            "offer" to "Update offer",
            "promptedVersionCode" to "Update prompted",
            "skippedVersionCode" to "Update skipped",
            "refusedVersionCode" to "Update refused",
        )

    private val videoPropertyToFactLabel: Map<String, String> =
        mapOf(
            "windowEnabled" to "Video window",
            "hidePictureWhileDriving" to "Video hide picture while driving",
            "sourceUri" to "Video file",
        )

    private val navigationPropertyToFactLabel: Map<String, String> =
        mapOf(
            "rerouteAutomatically" to "Reroute automatically",
            "suggestOtherRoutes" to "Suggest other routes",
            "restoreRouteAfterRestart" to "Restore route after restart",
            "routeRowTime" to "Route row time",
            "vehicleOnRouteLine" to "Vehicle on the route line",
            "turnCard" to "Turn card",
            "turnCardNextAfter" to "Turn card next after",
            "turnCardStraight" to "Turn card straight",
            "spokenPrompts" to "Spoken prompts",
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
            "mapTileHost" to "Map tile host",
            "offlineStorageLimit" to "Offline storage limit",
            "mapCustomStyleUrl" to "Map custom style URL",
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
            "calendarDateOnly" to "Calendar date only",
            "musicSpectrum" to "Music spectrum",
            "musicShowAlbum" to "Music album / art",
            "musicShowArt" to "Music album / art",
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

    @Test
    fun `every UpdateSettings property is mapped to a settings fact`() {
        assertEquals(declaredProperties(UpdateSettings::class.java), updatePropertyToFactLabel.keys)
    }

    @Test
    fun `the collector emits every mapped update fact label`() {
        val emittedLabels = updateSettingsFacts(UpdateSettings.Default).map { it.label }.toSet()

        assertEquals(updatePropertyToFactLabel.values.toSet(), emittedLabels)
    }

    @Test
    fun `every VideoSettings property is mapped to a settings fact`() {
        assertEquals(declaredProperties(VideoSettings::class.java), videoPropertyToFactLabel.keys)
    }

    @Test
    fun `the collector emits every mapped video fact label`() {
        val emittedLabels = videoSettingsFacts(VideoSettings.Default).map { it.label }.toSet()

        assertEquals(videoPropertyToFactLabel.values.toSet(), emittedLabels)
    }

    @Test
    fun `every NavigationSettings property is mapped to a settings fact`() {
        assertEquals(declaredProperties(NavigationSettings::class.java), navigationPropertyToFactLabel.keys)
    }

    @Test
    fun `the collector emits every mapped navigation fact label`() {
        val emittedLabels = navigationSettingsFacts(NavigationSettings.Default).map { it.label }.toSet()

        assertEquals(navigationPropertyToFactLabel.values.toSet(), emittedLabels)
    }

    @Test
    fun `the navigation facts read true or false like their neighbours and name the route row's time`() {
        val defaults = navigationSettingsFacts(NavigationSettings.Default).map { it.value }
        val changed =
            navigationSettingsFacts(
                NavigationSettings(false, false, false, RouteRowTime.REMAINING, false, false, false, true, false),
            ).map { it.value }

        assertEquals(
            listOf("true", "true", "true", "arrival time", "true", "true", "true", "false", "true").map {
                FactValue.Text(it)
            },
            defaults,
        )
        assertEquals(
            listOf("false", "false", "false", "time remaining", "false", "false", "false", "true", "false").map {
                FactValue.Text(it)
            },
            changed,
        )
    }

    @Test
    fun `the video file renders as set or not set, never its URI`() {
        val uri = "content://com.example.documents/document/video%3A42"
        val file = videoSettingsFacts(VideoSettings.Default.copy(sourceUri = uri)).single { it.label == "Video file" }

        assertEquals(FactValue.Text("set"), file.value)
    }

    private fun declaredProperties(type: Class<*>): Set<String> =
        type.declaredFields
            .filterNot { it.isSynthetic || Modifier.isStatic(it.modifiers) }
            .map { it.name }
            .toSet()
}
