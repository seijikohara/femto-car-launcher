package io.github.seijikohara.femto.data

import app.cash.turbine.test
import io.github.seijikohara.femto.testfixtures.fakeLocation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WeatherRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        client = OkHttpClient()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `parses Open-Meteo forecast response with current, hourly, and daily blocks`() =
        runTest {
            server.enqueue(MockResponse().setBody(FORECAST_BODY))

            val repo =
                WeatherRepository(
                    api = OpenMeteoApi(client = client, baseUrl = server.url("/").toString()),
                    locationFlow = flowOf(fakeLocation()),
                    clock = Clock.fixed(Instant.parse("2026-05-01T05:32:00Z"), ZoneOffset.UTC),
                )

            repo.snapshotFlow().test {
                val snapshot = awaitItem()
                assertNotNull(snapshot)
                assertEquals(18.5, snapshot.tempC, 0.0)
                assertEquals(17.0, snapshot.apparentTempC, 0.0)
                assertEquals(WeatherCode.CLEAR, snapshot.code)
                assertEquals(12.6, snapshot.windKmh, 0.0)
                assertEquals(4.5, snapshot.uvIndex)
                assertTrue(snapshot.isDay)
                assertEquals(LocalTime.of(5, 42), snapshot.sunrise)
                assertEquals(LocalTime.of(19, 14), snapshot.sunset)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `slices the next five hourly entries starting at the current hour`() =
        runTest {
            server.enqueue(MockResponse().setBody(FORECAST_BODY))

            val repo =
                WeatherRepository(
                    api = OpenMeteoApi(client = client, baseUrl = server.url("/").toString()),
                    locationFlow = flowOf(fakeLocation()),
                    clock = Clock.fixed(Instant.parse("2026-05-01T05:32:00Z"), ZoneOffset.UTC),
                )

            repo.snapshotFlow().test {
                val snapshot = assertNotNull(awaitItem())
                assertEquals(5, snapshot.hourly.size)
                assertEquals(LocalTime.of(11, 0), snapshot.hourly[0].time)
                assertEquals(LocalTime.of(15, 0), snapshot.hourly[4].time)
                assertEquals(20.0, snapshot.hourly[1].tempC, 0.0)
                assertEquals(WeatherCode.PARTLY_CLOUDY, snapshot.hourly[2].code)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `parses daily max min and code into DailyForecast list`() =
        runTest {
            server.enqueue(MockResponse().setBody(FORECAST_BODY))

            val repo =
                WeatherRepository(
                    api = OpenMeteoApi(client = client, baseUrl = server.url("/").toString()),
                    locationFlow = flowOf(fakeLocation()),
                    clock = Clock.fixed(Instant.parse("2026-05-01T05:32:00Z"), ZoneOffset.UTC),
                )

            repo.snapshotFlow().test {
                val snapshot = assertNotNull(awaitItem())
                assertEquals(3, snapshot.daily.size)
                assertEquals(22.0, snapshot.daily[0].tempMaxC, 0.0)
                assertEquals(14.0, snapshot.daily[0].tempMinC, 0.0)
                assertEquals(WeatherCode.CLEAR, snapshot.daily[0].code)
                assertEquals(WeatherCode.PARTLY_CLOUDY, snapshot.daily[1].code)
                assertEquals(WeatherCode.RAIN, snapshot.daily[2].code)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `returns null when http call fails`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(500))

            val repo =
                WeatherRepository(
                    api = OpenMeteoApi(client = client, baseUrl = server.url("/").toString()),
                    locationFlow = flowOf(fakeLocation()),
                    clock = Clock.systemUTC(),
                )

            repo.snapshotFlow().test {
                assertNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `returns null when location is null`() =
        runTest {
            val repo =
                WeatherRepository(
                    api = OpenMeteoApi(client = client, baseUrl = server.url("/").toString()),
                    locationFlow = flowOf(null),
                    clock = Clock.systemUTC(),
                )

            repo.snapshotFlow().test {
                assertNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `throttles outage retries within the minimum retry interval to a single request`() =
        runTest {
            // Sustained outage: every forecast call fails.
            server.enqueue(MockResponse().setResponseCode(500))
            server.enqueue(MockResponse().setResponseCode(500))

            val clock = MutableClock(Instant.parse("2026-05-01T05:32:00Z"))
            val repo =
                WeatherRepository(
                    api = OpenMeteoApi(client = client, baseUrl = server.url("/").toString()),
                    // Second emit lands well inside MIN_RETRY_INTERVAL (one minute).
                    locationFlow = flow {
                        emit(fakeLocation())
                        clock.advanceBy(Duration.ofSeconds(10))
                        emit(fakeLocation())
                    },
                    clock = clock,
                )

            repo.snapshotFlow().test {
                assertNull(awaitItem())
                // The throttled second emit re-emits the still-null snapshot.
                assertNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

            // Only the first attempt hit the network; the second was throttled.
            assertEquals(1, server.requestCount)
        }

    @Test
    fun `retries after the minimum retry interval elapses during an outage`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(500))
            server.enqueue(MockResponse().setResponseCode(500))

            val clock = MutableClock(Instant.parse("2026-05-01T05:32:00Z"))
            val repo =
                WeatherRepository(
                    api = OpenMeteoApi(client = client, baseUrl = server.url("/").toString()),
                    // Second emit lands just past MIN_RETRY_INTERVAL, so the retry fires.
                    locationFlow = flow {
                        emit(fakeLocation())
                        clock.advanceBy(Duration.ofSeconds(61))
                        emit(fakeLocation())
                    },
                    clock = clock,
                )

            repo.snapshotFlow().test {
                assertNull(awaitItem())
                assertNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(2, server.requestCount)
        }

    // Mutable clock whose instant the test advances explicitly to exercise the
    // outage-retry throttle without real-time waits.
    private class MutableClock(
        private var now: Instant,
        private val zone: ZoneId = ZoneOffset.UTC,
    ) : Clock() {
        fun advanceBy(amount: Duration) {
            now = now.plus(amount)
        }

        override fun getZone(): ZoneId = zone

        override fun withZone(zone: ZoneId): Clock = MutableClock(now, zone)

        override fun instant(): Instant = now
    }

    private companion object {
        // current.time aligns with hourly.time[2] so the slice should start at index 2.
        const val FORECAST_BODY = """
            {
              "timezone": "Asia/Tokyo",
              "current": {
                "time": "2026-05-01T11:00",
                "temperature_2m": 18.5,
                "apparent_temperature": 17.0,
                "weathercode": 0,
                "windspeed_10m": 12.6,
                "uv_index": 4.5,
                "is_day": 1
              },
              "hourly": {
                "time": [
                  "2026-05-01T09:00",
                  "2026-05-01T10:00",
                  "2026-05-01T11:00",
                  "2026-05-01T12:00",
                  "2026-05-01T13:00",
                  "2026-05-01T14:00",
                  "2026-05-01T15:00",
                  "2026-05-01T16:00"
                ],
                "temperature_2m": [16.0, 17.5, 19.0, 20.0, 21.0, 21.5, 22.0, 22.5],
                "weathercode": [0, 0, 0, 0, 2, 2, 2, 2]
              },
              "daily": {
                "time": ["2026-05-01", "2026-05-02", "2026-05-03"],
                "sunrise": ["2026-05-01T05:42", "2026-05-02T05:41", "2026-05-03T05:40"],
                "sunset": ["2026-05-01T19:14", "2026-05-02T19:15", "2026-05-03T19:16"],
                "weathercode": [0, 2, 61],
                "temperature_2m_max": [22.0, 23.0, 21.0],
                "temperature_2m_min": [14.0, 15.0, 14.0]
              }
            }
        """
    }
}
