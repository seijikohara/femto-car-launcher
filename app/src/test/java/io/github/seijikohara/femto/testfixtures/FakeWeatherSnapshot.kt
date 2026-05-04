package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.WeatherCode
import io.github.seijikohara.femto.data.WeatherSnapshot
import java.time.Instant

internal fun fakeWeatherSnapshot(
    tempC: Double = 18.0,
    code: WeatherCode = WeatherCode.CLEAR,
    fetchedAt: Instant = Instant.parse("2026-05-01T05:32:00Z"),
): WeatherSnapshot = WeatherSnapshot(tempC, code, fetchedAt)
