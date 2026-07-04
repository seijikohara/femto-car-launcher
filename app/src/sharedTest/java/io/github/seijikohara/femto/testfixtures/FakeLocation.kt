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
    // Defaults to a neutral test provider; pass LocationManager.NETWORK_PROVIDER
    // to exercise the GPS-only trip-accrual path.
    provider: String = "test",
    // Null leaves the fix bearing-less (Location.hasBearing() == false), mirroring
    // a fix with no heading; non-null exercises the driving-face heading badge.
    bearingDegrees: Float? = null,
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
        // setBearing flips hasBearing() to true; only set when the caller asks.
        if (bearingDegrees != null) this.bearing = bearingDegrees
    }
