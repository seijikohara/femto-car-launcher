package io.github.seijikohara.femto.data.weather

import io.github.seijikohara.femto.testfixtures.fakeWeatherSnapshot
import org.junit.Test
import java.time.Duration
import java.time.Instant
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WeatherSnapshotTest {
    private val fetchedAt = Instant.parse("2026-05-01T05:32:00Z")
    private val snapshot = fakeWeatherSnapshot(fetchedAt = fetchedAt)

    @Test
    fun `a just-fetched snapshot is fresh`() {
        assertFalse(snapshot.isStale(fetchedAt))
    }

    @Test
    fun `a snapshot just under the threshold is fresh`() {
        val now = fetchedAt + WEATHER_STALE_THRESHOLD - Duration.ofSeconds(1)
        assertFalse(snapshot.isStale(now))
    }

    @Test
    fun `a snapshot exactly at the threshold is stale`() {
        // Threshold is inclusive: two full missed refresh windows is an outage.
        assertTrue(snapshot.isStale(fetchedAt + WEATHER_STALE_THRESHOLD))
    }

    @Test
    fun `a snapshot well past the threshold is stale`() {
        assertTrue(snapshot.isStale(fetchedAt + Duration.ofHours(3)))
    }

    @Test
    fun `a snapshot fetched in the future is stale once the skew exceeds the threshold`() {
        // A clock moved backwards (NTP correction) must not read a future fetch as
        // fresh forever; abs() treats the skew the same as an equal age.
        val now = fetchedAt - Duration.ofHours(2)
        assertTrue(snapshot.isStale(now))
    }
}
