package io.github.seijikohara.femto.data

import app.cash.turbine.test
import io.github.seijikohara.femto.testfixtures.fakeLocation
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
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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
    fun `parses Open-Meteo current_weather response`() =
        runTest {
            server.enqueue(
                MockResponse().setBody(
                    """{"current_weather":{"temperature":18.5,"weathercode":0,"time":"2026-05-01T05:32"}}""",
                ),
            )

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
                assertEquals(WeatherCode.CLEAR, snapshot.code)
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
}
