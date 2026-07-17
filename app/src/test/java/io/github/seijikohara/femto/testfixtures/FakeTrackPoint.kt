package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.location.TrackPointEntity

internal fun fakeTrackPoint(
    tripId: Long = 0L,
    timeMs: Long = 0L,
    latitude: Double = 35.6580,
    longitude: Double = 139.7016,
    speedMps: Float? = 10f,
    bearingDeg: Float? = null,
    altitudeM: Double? = 47.0,
    accuracyM: Float? = 5f,
): TrackPointEntity =
    TrackPointEntity(
        tripId = tripId,
        timeMs = timeMs,
        latitude = latitude,
        longitude = longitude,
        speedMps = speedMps,
        bearingDeg = bearingDeg,
        altitudeM = altitudeM,
        accuracyM = accuracyM,
    )
