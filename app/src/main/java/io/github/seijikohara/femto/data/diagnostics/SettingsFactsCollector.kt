package io.github.seijikohara.femto.data.diagnostics

import android.content.Context
import io.github.seijikohara.femto.data.display.DisplayPreferences
import io.github.seijikohara.femto.data.display.DisplaySettings
import io.github.seijikohara.femto.data.location.LocationPreferences
import io.github.seijikohara.femto.data.location.LocationSettings
import kotlinx.coroutines.flow.first

/**
 * Dumps every [DisplaySettings] and [LocationSettings] field as SETTINGS
 * facts. Labels stay English on the screen as well as in the report: the
 * dump is a debug artifact shared verbatim with the unlocalized Markdown
 * report, where stable machine-greppable wording is the contract. Secrets
 * (tokens, API keys) render only as `set` / `not set` — never their value.
 */
internal class SettingsFactsCollector(
    private val context: Context,
) {
    suspend fun settingsFacts(): SectionPayload.Facts {
        val display = DisplayPreferences(context).settings.first()
        val location = LocationPreferences(context).settings.first()
        return SectionPayload.Facts(displaySettingsFacts(display) + locationSettingsFacts(location))
    }
}

// Pure and free of any Context so SettingsFactsCompletenessTest can assert on the
// JVM that every DisplaySettings field is dumped (mirrors displayGeometryFacts).
internal fun displaySettingsFacts(display: DisplaySettings): List<DiagnosticFact> =
    listOf(
        entry("Theme mode", display.themeMode.name),
        entry("Accent", display.accentColor.name),
        entry("UI scale", display.uiScale.name),
        entry("Speed unit", display.speedUnit.name),
        entry("Temperature unit", display.temperatureUnit.name),
        entry("Clock", display.clock.name),
        entry("Clock seconds", "${display.showClockSeconds}"),
        entry("Fullscreen", display.fullscreen.name),
        entry("Dock position", display.dockPosition.name),
        entry("Dock width", display.dockWidth.name),
        entry("Driver side", display.driverSide.name),
        entry("Motion tier", display.motionTier.name),
        entry("Orientation", display.orientation.name),
        entry("Keep screen on", "${display.keepScreenOn}"),
        entry("Assistant launch", display.assistantLaunch.name),
        entry("Map backend", display.mapBackend.name),
        entry("Map style", display.mapStyle.name),
        entry("Map schemes", "${display.mapSchemeLight.name} / ${display.mapSchemeDark.name}"),
        entry("Map zoom / tilt", "z${display.mapZoom} / ${display.mapTiltDeg}°"),
        entry("Map north-up", "${display.mapNorthUp}"),
        entry("Map marker position", "${display.mapMarkerPos}"),
        entry("3D buildings / terrain", "${display.map3dBuildings} / ${display.mapTerrain}"),
        entry("Glass blur / tint", "${display.glassBlurRadius} dp / ${display.glassTintScale}%"),
        entry(
            "Glass border / shadow",
            "border ${display.glassShowBorder} / shadow ${display.glassShadowEnabled} " +
                "(${display.glassShadowIntensity}% / ${display.glassShadowSizeDp} dp)",
        ),
        entry(
            "Font size / weight / spacing",
            "${display.fontBaseSizeSp} sp / ${display.fontWeightStep} / ${display.fontLetterSpacingCentiEm}",
        ),
        entry(
            "Panels (calendar / weather / music)",
            "${display.showCalendar} / ${display.showWeather} / ${display.showMusic}",
        ),
        entry("Music spectrum", "${display.musicSpectrum}"),
        entry("Music album / art", "${display.musicShowAlbum} / ${display.musicShowArt}"),
        entry("Mapbox style", display.mapboxStyle.name),
        entry("Mapbox traffic", "${display.mapboxTraffic}"),
        entry("Mapbox token", display.mapboxAccessToken.secretLabel()),
        entry("Google Maps rendering", display.googleMapsRendering.name),
        entry("Google Maps type", display.googleMapsMapType.name),
        entry("Google Maps traffic", "${display.googleMapsTraffic}"),
        entry("Google Maps key", display.googleMapsApiKey.secretLabel()),
        entry("Google Maps map ID", display.googleMapsMapId.secretLabel()),
    )

internal fun locationSettingsFacts(location: LocationSettings): List<DiagnosticFact> =
    listOf(
        entry("Location quality", location.quality.name),
        entry("Location interval", "${location.intervalMillis} ms"),
        entry("Location min distance", "${location.minUpdateDistanceMeters} m"),
        entry("Background ranging", "${location.backgroundRangingEnabled}"),
        entry("Track recording", "${location.trackRecordingEnabled}"),
        entry("Track retention", location.trackRetention.name),
    )

private fun entry(
    label: String,
    value: String,
): DiagnosticFact = DiagnosticFact(label, FactValue.Text(value))

private fun String.secretLabel(): String = if (isBlank()) "not set" else "set"
