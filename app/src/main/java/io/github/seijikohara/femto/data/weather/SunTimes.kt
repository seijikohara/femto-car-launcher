package io.github.seijikohara.femto.data.weather

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.tan

internal data class SunTimes(
    val sunrise: LocalTime?,
    val sunset: LocalTime?,
)

// Locationforecast 2.0 omits sunrise/sunset (MET's symbol codes already encode
// day/night). Rather than add a second network call to the Sunrise API — extra
// traffic against MET's rate limit, and another ToS surface — sun times are
// computed on-device with the standard sunrise equation. No network, no ToS.
// Accuracy is ~1 minute, far below the card's display granularity.
//
// Returns null sunrise/sunset where the sun never crosses the horizon on [date]
// (polar day or night), which the card renders as a dash.
internal object SunCalculator {
    // Official zenith for sunrise/sunset: 90°50′, including atmospheric refraction
    // and the solar disc radius.
    private const val ZENITH_DEG = 90.833

    fun compute(
        latitude: Double,
        longitude: Double,
        date: LocalDate,
        zone: ZoneId,
    ): SunTimes =
        SunTimes(
            sunrise = solarEvent(latitude, longitude, date, zone, isSunrise = true),
            sunset = solarEvent(latitude, longitude, date, zone, isSunrise = false),
        )

    // Reference: the sunrise equation
    // (https://en.wikipedia.org/wiki/Sunrise_equation). Angles are in degrees
    // except where converted for the trig calls.
    private fun solarEvent(
        latitude: Double,
        longitude: Double,
        date: LocalDate,
        zone: ZoneId,
        isSunrise: Boolean,
    ): LocalTime? {
        val dayOfYear = date.dayOfYear
        val lngHour = longitude / 15.0
        val approxTime = dayOfYear + ((if (isSunrise) 6.0 else 18.0) - lngHour) / 24.0

        val meanAnomaly = (0.9856 * approxTime) - 3.289
        val trueLongitude =
            (
                meanAnomaly +
                    (1.916 * sinDeg(meanAnomaly)) +
                    (0.020 * sinDeg(2 * meanAnomaly)) +
                    282.634
            ).mod(360.0)

        var rightAscension = atanDeg(0.91764 * tanDeg(trueLongitude)).mod(360.0)
        // Right ascension must be in the same quadrant as the true longitude.
        rightAscension += (floor(trueLongitude / 90.0) * 90.0) - (floor(rightAscension / 90.0) * 90.0)
        rightAscension /= 15.0

        val sinDeclination = 0.39782 * sinDeg(trueLongitude)
        val cosDeclination = cosDeg(asinDeg(sinDeclination))
        val cosHourAngle =
            (cosDeg(ZENITH_DEG) - (sinDeclination * sinDeg(latitude))) /
                (cosDeclination * cosDeg(latitude))
        // > 1: sun never rises that day; < -1: sun never sets that day.
        if (cosHourAngle > 1.0 || cosHourAngle < -1.0) return null

        val hourAngle = (if (isSunrise) 360.0 - acosDeg(cosHourAngle) else acosDeg(cosHourAngle)) / 15.0
        val localMeanTime = hourAngle + rightAscension - (0.06571 * approxTime) - 6.622
        val utcHours = (localMeanTime - lngHour).mod(24.0)

        // utcHours is decimal UTC time on [date]; project it onto the wall clock of
        // [zone]. plusMinutes handles minute rounding overflow and any date rollover
        // (a UTC sunrise can land on the previous/next local day).
        return ZonedDateTime
            .of(date, LocalTime.MIDNIGHT, ZoneOffset.UTC)
            .plusMinutes((utcHours * 60.0).roundToLong())
            .withZoneSameInstant(zone)
            .toLocalTime()
    }

    private fun sinDeg(deg: Double): Double = sin(Math.toRadians(deg))

    private fun cosDeg(deg: Double): Double = cos(Math.toRadians(deg))

    private fun tanDeg(deg: Double): Double = tan(Math.toRadians(deg))

    private fun asinDeg(value: Double): Double = Math.toDegrees(asin(value))

    private fun acosDeg(value: Double): Double = Math.toDegrees(acos(value))

    private fun atanDeg(value: Double): Double = Math.toDegrees(atan(value))
}
