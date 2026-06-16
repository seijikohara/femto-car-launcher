package io.github.seijikohara.femto.data.weather

import app.cash.turbine.test
import io.github.seijikohara.femto.testfixtures.fakeLocation
import kotlinx.coroutines.flow.emptyFlow
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

    private fun newApi(): MetNorwayApi =
        MetNorwayApi(
            client = client,
            baseUrl = server.url("/").toString(),
            userAgent = "FemtoCarLauncher/test (+https://example.com)",
        )

    @Test
    fun `maps current conditions from the first timeseries entry`() =
        runTest {
            server.enqueue(MockResponse().setBody(FORECAST_BODY))

            val repo =
                WeatherRepository(
                    api = newApi(),
                    locationFlow = flowOf(fakeLocation()),
                    clockFlow = emptyFlow(),
                    clock = Clock.fixed(NOW, ZoneOffset.UTC),
                    zone = ZoneOffset.UTC,
                )

            repo.snapshotFlow().test {
                val snapshot = assertNotNull(awaitItem())
                assertEquals(16.0, snapshot.tempC, 0.0)
                // MET has no "feels like": apparent falls back to the air temperature.
                assertEquals(16.0, snapshot.apparentTempC, 0.0)
                assertEquals(WeatherCode.CLEAR, snapshot.code)
                // 3.5 m/s -> 12.6 km/h.
                assertEquals(12.6, snapshot.windKmh, 1e-9)
                assertEquals(4.5, snapshot.uvIndex)
                assertEquals(60, snapshot.humidityPercent)
                assertTrue(snapshot.isDay)
                // Sun times are computed on-device, not delivered by MET.
                assertNotNull(snapshot.sunrise)
                assertNotNull(snapshot.sunset)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `slices the next five hourly entries starting at the current hour`() =
        runTest {
            server.enqueue(MockResponse().setBody(FORECAST_BODY))

            val repo =
                WeatherRepository(
                    api = newApi(),
                    locationFlow = flowOf(fakeLocation()),
                    clockFlow = emptyFlow(),
                    clock = Clock.fixed(NOW, ZoneOffset.UTC),
                    zone = ZoneOffset.UTC,
                )

            repo.snapshotFlow().test {
                val snapshot = assertNotNull(awaitItem())
                assertEquals(5, snapshot.hourly.size)
                assertEquals(LocalTime.of(5, 0), snapshot.hourly[0].time)
                assertEquals(LocalTime.of(9, 0), snapshot.hourly[4].time)
                assertEquals(17.5, snapshot.hourly[1].tempC, 0.0)
                assertEquals(WeatherCode.PARTLY_CLOUDY, snapshot.hourly[2].code)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `aggregates the timeseries into daily max min and a midday symbol`() =
        runTest {
            server.enqueue(MockResponse().setBody(FORECAST_BODY))

            val repo =
                WeatherRepository(
                    api = newApi(),
                    locationFlow = flowOf(fakeLocation()),
                    clockFlow = emptyFlow(),
                    clock = Clock.fixed(NOW, ZoneOffset.UTC),
                    zone = ZoneOffset.UTC,
                )

            repo.snapshotFlow().test {
                val snapshot = assertNotNull(awaitItem())
                assertEquals(3, snapshot.daily.size)
                assertEquals(22.0, snapshot.daily[0].tempMaxC, 0.0)
                assertEquals(16.0, snapshot.daily[0].tempMinC, 0.0)
                assertEquals(WeatherCode.RAIN, snapshot.daily[0].code)
                assertEquals(WeatherCode.PARTLY_CLOUDY, snapshot.daily[1].code)
                assertEquals(WeatherCode.RAIN_SHOWERS, snapshot.daily[2].code)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `returns null when http call fails`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(500))

            val repo =
                WeatherRepository(
                    api = newApi(),
                    locationFlow = flowOf(fakeLocation()),
                    clockFlow = emptyFlow(),
                    clock = Clock.systemUTC(),
                    zone = ZoneOffset.UTC,
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
                    api = newApi(),
                    locationFlow = flowOf(null),
                    clockFlow = emptyFlow(),
                    clock = Clock.systemUTC(),
                    zone = ZoneOffset.UTC,
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

            // The clock advances off the server request count, not wall time, so the
            // second emit is seen as +10s (inside MIN_RETRY_INTERVAL) only once the
            // first attempt has actually hit the server — immune to the flowOn(IO) /
            // merge scheduling that made a manually-advanced clock flaky on CI.
            val clock =
                RequestCountClock(
                    NOW,
                    server,
                    listOf(Duration.ZERO, Duration.ofSeconds(10)),
                )
            val repo =
                WeatherRepository(
                    api = newApi(),
                    locationFlow =
                        flow {
                            emit(fakeLocation())
                            emit(fakeLocation())
                        },
                    clockFlow = emptyFlow(),
                    clock = clock,
                    zone = ZoneOffset.UTC,
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

            // +61s once the first attempt has landed (just past MIN_RETRY_INTERVAL), so
            // the second emit triggers a real retry. Driven off the request count, not
            // wall time, to stay deterministic under flowOn(IO) / merge.
            val clock =
                RequestCountClock(
                    NOW,
                    server,
                    listOf(Duration.ZERO, Duration.ofSeconds(61)),
                )
            val repo =
                WeatherRepository(
                    api = newApi(),
                    locationFlow =
                        flow {
                            emit(fakeLocation())
                            emit(fakeLocation())
                        },
                    clockFlow = emptyFlow(),
                    clock = clock,
                    zone = ZoneOffset.UTC,
                )

            repo.snapshotFlow().test {
                assertNull(awaitItem())
                assertNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(2, server.requestCount)
        }

    @Test
    fun `applies the retry floor after a failed refresh of a stale cache`() =
        runTest {
            // First call succeeds and seeds the cache; the outage starts after.
            server.enqueue(MockResponse().setBody(FORECAST_BODY))
            server.enqueue(MockResponse().setResponseCode(500))
            server.enqueue(MockResponse().setResponseCode(500))

            // Staleness is driven by DISTANCE (each fix ~10 km from the last, far
            // past REFRESH_DISTANCE_M) so the cache ages play no role. The clock
            // is keyed off the request count: the failed second attempt happens
            // at +2min, and the third emission arrives 10s after that failure —
            // inside MIN_RETRY_INTERVAL, so it must not reach the network even
            // though the fix has moved far enough to warrant a refresh.
            val clock =
                RequestCountClock(
                    NOW,
                    server,
                    listOf(Duration.ZERO, Duration.ofMinutes(2), Duration.ofMinutes(2).plusSeconds(10)),
                )
            val repo =
                WeatherRepository(
                    api = newApi(),
                    locationFlow =
                        flow {
                            emit(fakeLocation())
                            emit(fakeLocation(latitude = 35.7480))
                            emit(fakeLocation(latitude = 35.8380))
                        },
                    clockFlow = emptyFlow(),
                    clock = clock,
                    zone = ZoneOffset.UTC,
                )

            repo.snapshotFlow().test {
                assertNotNull(awaitItem()) // fetched and cached
                assertNotNull(awaitItem()) // failed refresh keeps the cache
                assertNotNull(awaitItem()) // floored: cache again, no request
                cancelAndIgnoreRemainingEvents()
            }

            // The success plus ONE failed stale retry; before the global floor the
            // third emission fired a request too (once per GPS tick in production).
            assertEquals(2, server.requestCount)
        }

    @Test
    fun `keeps the floor after a successful fetch even when the fix moves far`() =
        runTest {
            server.enqueue(MockResponse().setBody(FORECAST_BODY))
            server.enqueue(MockResponse().setResponseCode(500))

            // The second fix arrives 30s after the successful fetch, 10 km away —
            // beyond REFRESH_DISTANCE_M but inside MIN_RETRY_INTERVAL. The floor
            // wins: a real vehicle cannot cover 5 km inside the one-minute floor,
            // so the distance trigger never needs to bypass it.
            val clock =
                RequestCountClock(
                    NOW,
                    server,
                    listOf(Duration.ZERO, Duration.ofSeconds(30)),
                )
            val repo =
                WeatherRepository(
                    api = newApi(),
                    locationFlow =
                        flow {
                            emit(fakeLocation())
                            emit(fakeLocation(latitude = 35.7480))
                        },
                    clockFlow = emptyFlow(),
                    clock = clock,
                    zone = ZoneOffset.UTC,
                )

            repo.snapshotFlow().test {
                assertNotNull(awaitItem())
                assertNotNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(1, server.requestCount)
        }

    @Test
    fun `yields a snapshot from temperature and code when secondary instant fields are missing`() =
        runTest {
            // An instant block lacking wind_speed, relative_humidity, and UV must
            // still decode and fall back to sensible defaults instead of discarding
            // the reading.
            server.enqueue(MockResponse().setBody(CURRENT_MINIMAL_BODY))

            val repo =
                WeatherRepository(
                    api = newApi(),
                    locationFlow = flowOf(fakeLocation()),
                    clockFlow = emptyFlow(),
                    clock = Clock.fixed(NOW, ZoneOffset.UTC),
                    zone = ZoneOffset.UTC,
                )

            repo.snapshotFlow().test {
                val snapshot = assertNotNull(awaitItem())
                assertEquals(18.5, snapshot.tempC, 0.0)
                assertEquals(18.5, snapshot.apparentTempC, 0.0)
                assertEquals(WeatherCode.CLEAR, snapshot.code)
                assertEquals(0.0, snapshot.windKmh, 0.0)
                assertNull(snapshot.humidityPercent)
                assertNull(snapshot.uvIndex)
                assertTrue(snapshot.isDay)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // Clock that advances off the MockWebServer request count rather than wall time:
    // it returns base + offsets[requestCount] (the last entry holds beyond the list).
    // Driving the clock off observable progress (not emission timing) makes the
    // outage-retry throttle tests immune to the flowOn(IO) / merge scheduling race that
    // a manually-advanced clock suffered on slow CI runners.
    private class RequestCountClock(
        private val base: Instant,
        private val server: MockWebServer,
        private val offsetsByRequestCount: List<Duration>,
        private val zone: ZoneId = ZoneOffset.UTC,
    ) : Clock() {
        override fun getZone(): ZoneId = zone

        override fun withZone(zone: ZoneId): Clock = RequestCountClock(base, server, offsetsByRequestCount, zone)

        override fun instant(): Instant =
            base.plus(offsetsByRequestCount[server.requestCount.coerceAtMost(offsetsByRequestCount.size - 1)])
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-05-01T05:32:00Z")

        // Three local days (UTC in tests). Day 1 starts at the current hour (05:00)
        // with six hourly entries plus a midday entry; the hourly slice takes the
        // first five, and daily aggregation reduces each day to max/min temp and the
        // symbol of the entry nearest local noon.
        const val FORECAST_BODY = """
            {
              "properties": {
                "timeseries": [
                  { "time": "2026-05-01T05:00:00Z", "data": { "instant": { "details": { "air_temperature": 16.0, "wind_speed": 3.5, "relative_humidity": 60.0, "ultraviolet_index_clear_sky": 4.5 } }, "next_1_hours": { "summary": { "symbol_code": "clearsky_day" } } } },
                  { "time": "2026-05-01T06:00:00Z", "data": { "instant": { "details": { "air_temperature": 17.5 } }, "next_1_hours": { "summary": { "symbol_code": "clearsky_day" } } } },
                  { "time": "2026-05-01T07:00:00Z", "data": { "instant": { "details": { "air_temperature": 19.0 } }, "next_1_hours": { "summary": { "symbol_code": "partlycloudy_day" } } } },
                  { "time": "2026-05-01T08:00:00Z", "data": { "instant": { "details": { "air_temperature": 20.0 } }, "next_1_hours": { "summary": { "symbol_code": "partlycloudy_day" } } } },
                  { "time": "2026-05-01T09:00:00Z", "data": { "instant": { "details": { "air_temperature": 21.0 } }, "next_1_hours": { "summary": { "symbol_code": "cloudy" } } } },
                  { "time": "2026-05-01T10:00:00Z", "data": { "instant": { "details": { "air_temperature": 21.5 } }, "next_1_hours": { "summary": { "symbol_code": "lightrain" } } } },
                  { "time": "2026-05-01T12:00:00Z", "data": { "instant": { "details": { "air_temperature": 22.0 } }, "next_6_hours": { "summary": { "symbol_code": "rain" } } } },
                  { "time": "2026-05-02T06:00:00Z", "data": { "instant": { "details": { "air_temperature": 14.0 } }, "next_1_hours": { "summary": { "symbol_code": "partlycloudy_day" } } } },
                  { "time": "2026-05-02T12:00:00Z", "data": { "instant": { "details": { "air_temperature": 23.0 } }, "next_6_hours": { "summary": { "symbol_code": "partlycloudy_day" } } } },
                  { "time": "2026-05-02T18:00:00Z", "data": { "instant": { "details": { "air_temperature": 15.0 } }, "next_1_hours": { "summary": { "symbol_code": "cloudy" } } } },
                  { "time": "2026-05-03T06:00:00Z", "data": { "instant": { "details": { "air_temperature": 13.0 } }, "next_1_hours": { "summary": { "symbol_code": "cloudy" } } } },
                  { "time": "2026-05-03T12:00:00Z", "data": { "instant": { "details": { "air_temperature": 21.0 } }, "next_6_hours": { "summary": { "symbol_code": "lightrainshowers_day" } } } }
                ]
              }
            }
        """

        // A single entry carrying only air_temperature and a symbol, every other
        // instant field absent.
        const val CURRENT_MINIMAL_BODY = """
            {
              "properties": {
                "timeseries": [
                  { "time": "2026-05-01T05:00:00Z", "data": { "instant": { "details": { "air_temperature": 18.5 } }, "next_1_hours": { "summary": { "symbol_code": "clearsky_day" } } } }
                ]
              }
            }
        """
    }
}
