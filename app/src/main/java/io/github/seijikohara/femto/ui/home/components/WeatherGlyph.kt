package io.github.seijikohara.femto.ui.home.components

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.lucide.Cloud
import com.composables.icons.lucide.CloudSun
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Moon
import com.composables.icons.lucide.Sun
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.weather.WeatherCode
import io.github.seijikohara.femto.ui.theme.WeatherGlyphColors
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// Shared weather-glyph mapping used by both the compact WeatherCard and the
// WeatherPanel: WeatherCode (+ daylight) -> icon / accent tint / condition
// label, plus the daylight test and the forecast hour label. Promoted from
// WeatherCard's private helpers so the panel renders identical glyphs.

internal fun glyphIconFor(
    code: WeatherCode,
    isDay: Boolean,
): ImageVector =
    when (code) {
        WeatherCode.CLEAR -> if (isDay) Lucide.Sun else Lucide.Moon

        WeatherCode.PARTLY_CLOUDY -> Lucide.CloudSun

        WeatherCode.CLOUDY,
        WeatherCode.FOG,
        WeatherCode.DRIZZLE,
        WeatherCode.RAIN,
        WeatherCode.FREEZING_RAIN,
        WeatherCode.SNOW,
        WeatherCode.SNOW_GRAINS,
        WeatherCode.RAIN_SHOWERS,
        WeatherCode.SNOW_SHOWERS,
        WeatherCode.THUNDERSTORM,
        WeatherCode.UNKNOWN,
        -> Lucide.Cloud
    }

internal fun glyphTintFor(
    code: WeatherCode,
    isDay: Boolean,
    glyphs: WeatherGlyphColors,
): Color =
    when (code) {
        WeatherCode.CLEAR -> if (isDay) glyphs.sun else glyphs.moon
        WeatherCode.PARTLY_CLOUDY -> glyphs.cloudSun
        else -> glyphs.cloud
    }

@StringRes
internal fun labelResFor(code: WeatherCode): Int =
    when (code) {
        WeatherCode.CLEAR -> R.string.weather_cond_sunny
        WeatherCode.PARTLY_CLOUDY -> R.string.weather_cond_partly_cloudy
        WeatherCode.CLOUDY -> R.string.weather_cond_cloudy
        WeatherCode.FOG -> R.string.weather_cond_fog
        WeatherCode.DRIZZLE -> R.string.weather_cond_drizzle
        WeatherCode.RAIN -> R.string.weather_cond_rain
        WeatherCode.FREEZING_RAIN -> R.string.weather_cond_freezing_rain
        WeatherCode.SNOW -> R.string.weather_cond_snow
        WeatherCode.SNOW_GRAINS -> R.string.weather_cond_snow_grains
        WeatherCode.RAIN_SHOWERS -> R.string.weather_cond_rain_showers
        WeatherCode.SNOW_SHOWERS -> R.string.weather_cond_snow_showers
        WeatherCode.THUNDERSTORM -> R.string.weather_cond_thunderstorm
        WeatherCode.UNKNOWN -> R.string.weather_cond_unknown
    }

// Fixed daylight window used only when the snapshot carries no sunrise/sunset
// bounds, so a forecast hour past sunset still reads as night.
private val FallbackDaylightHours = 6..18

// Day/night drives the sun-vs-moon glyph for CLEAR. Use the snapshot's real
// sunrise/sunset when present; fall back to [FallbackDaylightHours] only when
// either bound is missing.
internal fun isDaylight(
    time: LocalTime,
    sunrise: LocalTime?,
    sunset: LocalTime?,
): Boolean =
    if (sunrise != null && sunset != null) {
        time >= sunrise && time < sunset
    } else {
        time.hour in FallbackDaylightHours
    }

// Forecast hour label in the same notation family as the clock and the
// calendar ("12:00" / "12 PM" via the locale's meridiem word), so the
// 12/24-hour setting is visibly honoured.
// internal (not file-private): WeatherCard.kt's asOfTimeLabel reuses this for
// its 24h "as of" caption instead of keeping a duplicate formatter.
internal val ForecastHourFormatter24: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val ForecastHourFormatter12: DateTimeFormatter = DateTimeFormatter.ofPattern("h a")

internal fun forecastHourLabel(
    time: LocalTime,
    is24Hour: Boolean,
): String = time.format(if (is24Hour) ForecastHourFormatter24 else ForecastHourFormatter12)
