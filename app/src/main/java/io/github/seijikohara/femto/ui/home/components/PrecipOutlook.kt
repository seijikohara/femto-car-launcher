package io.github.seijikohara.femto.ui.home.components

import io.github.seijikohara.femto.data.weather.HourlyForecast
import io.github.seijikohara.femto.data.weather.WeatherCode
import java.time.LocalTime

// Ignore trace amounts: MET reports 0.0 for dry hours and hundredths for
// negligible drizzle that would make the nowcast cry wolf.
private const val PRECIP_ONSET_MM = 0.1

// Only call out precipitation the driver could plan around; a shower half a
// day away belongs to the curve, not the hero line.
private const val NOWCAST_HORIZON_HOURS = 12

/**
 * The hero's precipitation callout, derived from the hourly forecast: either
 * precipitation in the FIRST hour ([PrecipOutlook.Now]) or the first wet hour
 * within the coming [NOWCAST_HORIZON_HOURS] ([PrecipOutlook.Upcoming]). Null
 * when the window is dry — the hero then shows no callout at all.
 */
internal sealed interface PrecipOutlook {
    val probabilityPercent: Int?
    val snow: Boolean

    data class Now(
        override val probabilityPercent: Int?,
        override val snow: Boolean,
    ) : PrecipOutlook

    data class Upcoming(
        val at: LocalTime,
        override val probabilityPercent: Int?,
        override val snow: Boolean,
    ) : PrecipOutlook
}

internal fun precipOutlookOrNull(hourly: List<HourlyForecast>): PrecipOutlook? {
    val first = hourly.firstOrNull() ?: return null
    if (first.isWet()) return PrecipOutlook.Now(first.precipitationProbabilityPercent, first.isSnow())
    return hourly
        .take(NOWCAST_HORIZON_HOURS)
        .firstOrNull { it.isWet() }
        ?.let { PrecipOutlook.Upcoming(it.time, it.precipitationProbabilityPercent, it.isSnow()) }
}

private fun HourlyForecast.isWet(): Boolean = (precipitationMm ?: 0.0) >= PRECIP_ONSET_MM

private fun HourlyForecast.isSnow(): Boolean =
    code == WeatherCode.SNOW || code == WeatherCode.SNOW_SHOWERS || code == WeatherCode.SNOW_GRAINS
