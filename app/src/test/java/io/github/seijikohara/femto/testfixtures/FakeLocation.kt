package io.github.seijikohara.femto.testfixtures

import android.location.Location

internal fun fakeLocation(
    latitude: Double = 35.6580,
    longitude: Double = 139.7016,
    speedMps: Float = 0f,
    altitudeM: Double = 47.0,
): Location =
    Location("test").apply {
        this.latitude = latitude
        this.longitude = longitude
        this.speed = speedMps
        this.altitude = altitudeM
        this.time = 0L
        this.elapsedRealtimeNanos = 0L
    }
