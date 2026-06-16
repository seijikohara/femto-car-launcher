package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.location.TripState

internal fun fakeTripState(
    distanceMeters: Double = 24_400.0,
    avgSpeedMs: Double = 11.7,
    currentSpeedMs: Double = 0.0,
): TripState =
    TripState(
        distanceMeters = distanceMeters,
        avgSpeedMs = avgSpeedMs,
        currentSpeedMs = currentSpeedMs,
    )
