package io.github.seijikohara.femto.testfixtures

import android.location.Location

internal fun fakeLocation(
    latitude: Double = 35.6580,
    longitude: Double = 139.7016,
    speedMps: Float = 0f,
    altitudeM: Double = 47.0,
    timeMs: Long = 0L,
    elapsedRealtimeNanos: Long = 0L,
    // When false, the fix carries no speed (Location.hasSpeed() == false),
    // mirroring cheap GPS chips and raw GPS_PROVIDER HALs.
    hasSpeed: Boolean = true,
    // Null leaves hasBearing()/hasAccuracy() false — the chip-didn't-say path.
    bearingDeg: Float? = null,
    accuracyM: Float? = null,
    // Defaults to a neutral test provider; pass LocationManager.NETWORK_PROVIDER
    // to exercise the GPS-only trip-accrual path.
    provider: String = "test",
): Location =
    Location(provider).apply {
        this.latitude = latitude
        this.longitude = longitude
        this.altitude = altitudeM
        this.time = timeMs
        this.elapsedRealtimeNanos = elapsedRealtimeNanos
        // setSpeed flips hasSpeed() to true; removeSpeed() clears it. Only
        // set a speed when the caller asks for one so the speed-less path
        // stays exercisable.
        if (hasSpeed) this.speed = speedMps else removeSpeed()
        bearingDeg?.let { this.bearing = it }
        accuracyM?.let { this.accuracy = it }
    }
