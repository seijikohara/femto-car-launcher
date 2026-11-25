package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.display.AccentColor
import io.github.seijikohara.femto.data.display.AssistantLaunchSetting
import io.github.seijikohara.femto.data.display.ClockSetting
import io.github.seijikohara.femto.data.display.DisplayPreferences
import io.github.seijikohara.femto.data.display.DisplaySettings
import io.github.seijikohara.femto.data.display.DisplaySettingsStore
import io.github.seijikohara.femto.data.display.DockPosition
import io.github.seijikohara.femto.data.display.DockWidth
import io.github.seijikohara.femto.data.display.DriverSide
import io.github.seijikohara.femto.data.display.FullscreenSetting
import io.github.seijikohara.femto.data.display.GoogleMapType
import io.github.seijikohara.femto.data.display.GoogleMapsRendering
import io.github.seijikohara.femto.data.display.MAX_MAP_ZOOM
import io.github.seijikohara.femto.data.display.MapBackend
import io.github.seijikohara.femto.data.display.MapColorScheme
import io.github.seijikohara.femto.data.display.MapStyleSetting
import io.github.seijikohara.femto.data.display.MotionTier
import io.github.seijikohara.femto.data.display.OrientationSetting
import io.github.seijikohara.femto.data.display.SpeedUnitSetting
import io.github.seijikohara.femto.data.display.TemperatureUnitSetting
import io.github.seijikohara.femto.data.display.ThemeMode
import io.github.seijikohara.femto.data.display.UiScale
import io.github.seijikohara.femto.data.map.offline.OfflineStorageLimit
import kotlinx.coroutines.flow.first

/** Remove every persisted display key, so every field reads its default. */
internal suspend fun DisplaySettingsStore.resetAllKeys() = resetKeys(DisplayPreferences.ALL_KEYS)

/**
 * Set every persisted display field to a value other than [DisplaySettings.Default] and return the result: the real
 * store's reset tests and the view model's reset tests both start here, so each states only what its reset restores.
 */
internal suspend fun DisplaySettingsStore.mutateAllAwayFromDefault(): DisplaySettings {
    resetAllKeys()
    setThemeMode(ThemeMode.DARK)
    setAccentColor(AccentColor.TEAL)
    setUiScale(UiScale.LARGE)
    setSpeedUnit(SpeedUnitSetting.MILES)
    setTemperatureUnit(TemperatureUnitSetting.FAHRENHEIT)
    setClock(ClockSetting.TWENTY_FOUR_HOUR)
    setShowClockSeconds(true)
    setFullscreen(FullscreenSetting.OFF)
    setDockPosition(DockPosition.LEFT)
    setDockWidth(DockWidth.EXTENDED)
    setDriverSide(DriverSide.LEFT)
    setMotionTier(MotionTier.OFF)
    setOrientation(OrientationSetting.PORTRAIT)
    setKeepScreenOn(false)
    setAssistantLaunch(AssistantLaunchSetting.IN_APP)
    setMapStyle(MapStyleSetting.DARK)
    setMapSchemeLight(MapColorScheme.BRIGHT)
    setMapSchemeDark(MapColorScheme.FIORD)
    setMapCustomStyleUrl("https://example.test/style.json")
    setMapTilt(40)
    setMapZoom(MAX_MAP_ZOOM)
    setMapNorthUp(true)
    setMapMarkerPos(10)
    setMap3dBuildings(false)
    setMapTerrain(true)
    setGlassBlurRadius(5)
    setGlassTintScale(90)
    setGlassShowBorder(true)
    setGlassShadowEnabled(false)
    setGlassShadowIntensity(70)
    setGlassShadowSizeDp(16)
    setFontBaseSizeSp(20)
    setFontWeightStep(2)
    setFontLetterSpacingCentiEm(8)
    setShowCalendar(false)
    setShowWeather(false)
    setShowMusic(false)
    setCalendarDateOnly(true)
    setMusicSpectrum(true)
    setMusicShowAlbum(false)
    setMusicShowArt(false)
    setMapBackend(MapBackend.GOOGLEMAPS)
    setGoogleMapsApiKey("mutated-key")
    setGoogleMapsMapId("mutated-id")
    setGoogleMapsRendering(GoogleMapsRendering.VECTOR)
    setGoogleMapsMapType(GoogleMapType.HYBRID)
    setGoogleMapsTraffic(true)
    setMapTileHost("https://tiles.example.test")
    setOfflineStorageLimit(OfflineStorageLimit.GB_10)
    return settings.first()
}
