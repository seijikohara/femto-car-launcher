package io.github.seijikohara.femto.data.location

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.seijikohara.femto.testfixtures.fakeLocation
import io.github.seijikohara.femto.testfixtures.fakeTrackPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executor
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// runCurrent (never advanceUntilIdle) throughout: the repository schedules a
// daily prune tick, and advancing until "idle" would chase that ticker forever.
// The same ticker forces [recorderTest]: the repository's scope must be
// cancelled before runTest's own cleanup drains the scheduler, or that drain
// chases the ticker forever too.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TrackLogRepositoryTest {
    // Direct executors + unconfined dispatchers make every insert land before
    // runCurrent() returns, so counts are asserted deterministically.
    private val directExecutor = Executor { it.run() }
    private val database =
        Room
            .inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                TrackLogDatabase::class.java,
            ).allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()
    private val dao = database.trackPointDao()
    private val settings = MutableStateFlow(LocationSettings.Default)
    private var repositoryScope: CoroutineScope? = null

    @After
    fun tearDown() {
        database.close()
    }

    private fun recorderTest(testBody: suspend TestScope.() -> Unit) =
        runTest {
            try {
                testBody()
            } finally {
                repositoryScope?.cancel()
            }
        }

    private fun TestScope.buildRepository(): TrackLogRepository =
        TrackLogRepository(
            dao = dao,
            settings = settings,
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)).also { repositoryScope = it },
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
            // Batch size 1: every accepted point inserts immediately, so tests
            // assert on the table without exercising batching timing.
            batchMaxPoints = 1,
            nowEpochMs = { NOW_EPOCH_MS },
        )

    @Test
    fun `records an offered fix with the chip's readings`() =
        recorderTest {
            val repository = buildRepository()
            repository.offer(
                fakeLocation(
                    latitude = 35.5,
                    longitude = 139.25,
                    speedMps = 12.5f,
                    altitudeM = 47.0,
                    timeMs = NOW_EPOCH_MS,
                    elapsedRealtimeNanos = 0L,
                    bearingDeg = 270f,
                    accuracyM = 5f,
                ),
                tripId = 7L,
            )
            runCurrent()

            val stored = dao.pageAfter(0L, 10).single()
            assertEquals(7L, stored.tripId)
            assertEquals(NOW_EPOCH_MS, stored.timeMs)
            assertEquals(35.5, stored.latitude, 0.0)
            assertEquals(139.25, stored.longitude, 0.0)
            assertEquals(12.5f, stored.speedMps)
            assertEquals(270f, stored.bearingDeg)
            assertEquals(47.0, stored.altitudeM)
            assertEquals(5f, stored.accuracyM)
        }

    @Test
    fun `stores absent readings as null`() =
        recorderTest {
            val repository = buildRepository()
            repository.offer(fakeLocation(hasSpeed = false, timeMs = NOW_EPOCH_MS), tripId = 0L)
            runCurrent()

            val stored = dao.pageAfter(0L, 10).single()
            assertNull(stored.speedMps)
            assertNull(stored.bearingDeg)
            assertNull(stored.accuracyM)
        }

    @Test
    fun `samples down to one point per second`() =
        recorderTest {
            val repository = buildRepository()
            listOf(0L, 250_000_000L, 500_000_000L, 1_000_000_000L).forEach { nanos ->
                repository.offer(
                    fakeLocation(
                        speedMps = 11f,
                        timeMs = NOW_EPOCH_MS + nanos / 1_000_000,
                        elapsedRealtimeNanos = nanos,
                    ),
                    tripId = 0L,
                )
            }
            runCurrent()

            // Only the 0 s and 1 s fixes clear the 1 Hz gate.
            assertEquals(2L, dao.count())
        }

    @Test
    fun `keeps only the first parked point`() =
        recorderTest {
            val repository = buildRepository()
            listOf(
                0L to 0.1f,
                1_000_000_000L to 0.1f,
                2_000_000_000L to 0.1f,
                3_000_000_000L to 11f,
            ).forEach { (nanos, speed) ->
                repository.offer(
                    fakeLocation(
                        speedMps = speed,
                        timeMs = NOW_EPOCH_MS + nanos / 1_000_000,
                        elapsedRealtimeNanos = nanos,
                    ),
                    tripId = 0L,
                )
            }
            runCurrent()

            // First parked point + the fix that starts moving again.
            assertEquals(2L, dao.count())
        }

    @Test
    fun `rejects fixes with accuracy worse than the floor`() =
        recorderTest {
            val repository = buildRepository()
            repository.offer(
                fakeLocation(speedMps = 11f, timeMs = NOW_EPOCH_MS, elapsedRealtimeNanos = 0L, accuracyM = 80f),
                tripId = 0L,
            )
            repository.offer(
                fakeLocation(
                    speedMps = 11f,
                    timeMs = NOW_EPOCH_MS + 1_000,
                    elapsedRealtimeNanos = 1_000_000_000L,
                    accuracyM = 10f,
                ),
                tripId = 0L,
            )
            runCurrent()

            assertEquals(10f, dao.pageAfter(0L, 10).single().accuracyM)
        }

    @Test
    fun `records nothing while recording is disabled`() =
        recorderTest {
            settings.value = LocationSettings.Default.copy(trackRecordingEnabled = false)
            val repository = buildRepository()
            runCurrent()

            repository.offer(fakeLocation(speedMps = 11f, timeMs = NOW_EPOCH_MS), tripId = 0L)
            runCurrent()

            assertEquals(0L, dao.count())
        }

    @Test
    fun `prunes points older than the retention window on start`() =
        recorderTest {
            dao.insertAll(
                listOf(
                    fakeTrackPoint(timeMs = daysAgo(100)),
                    fakeTrackPoint(timeMs = daysAgo(1)),
                ),
            )

            buildRepository() // default retention: 90 days
            runCurrent()

            assertEquals(daysAgo(1), dao.pageAfter(0L, 10).single().timeMs)
        }

    @Test
    fun `re-prunes immediately when the retention window tightens`() =
        recorderTest {
            dao.insertAll(listOf(fakeTrackPoint(timeMs = daysAgo(60))))
            buildRepository()
            runCurrent()
            assertEquals(1L, dao.count()) // inside the default 90-day window

            settings.value = LocationSettings.Default.copy(trackRetention = TrackRetentionSetting.DAYS_30)
            runCurrent()

            assertEquals(0L, dao.count())
        }

    @Test
    fun `skips pruning while the clock reads implausibly far ahead of the newest point`() =
        recorderTest {
            // A real point at "now", then the process restarts with the clock set
            // ~2 years ahead (dead RTC before NTP). A naive prune would compute a
            // cutoff far past every row and wipe the history; the guard skips it.
            dao.insertAll(listOf(fakeTrackPoint(timeMs = NOW_EPOCH_MS)))
            val futureNow = NOW_EPOCH_MS + 730L * 24 * 60 * 60 * 1_000
            TrackLogRepository(
                dao = dao,
                settings = settings,
                scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)).also { repositoryScope = it },
                ioDispatcher = UnconfinedTestDispatcher(testScheduler),
                batchMaxPoints = 1,
                nowEpochMs = { futureNow },
            )
            runCurrent()

            assertEquals(1L, dao.count())
        }

    @Test
    fun `ignores a duplicate point with the same trip and time`() =
        recorderTest {
            // The crash-restart path: a fresh process re-seeds the same
            // getLastKnownLocation and records it under the same trip id / time_ms
            // as the pre-crash flush. The unique index + IGNORE keeps one row.
            dao.insertAll(listOf(fakeTrackPoint(tripId = 3L, timeMs = NOW_EPOCH_MS)))
            dao.insertAll(listOf(fakeTrackPoint(tripId = 3L, timeMs = NOW_EPOCH_MS, latitude = 1.0, longitude = 2.0)))

            assertEquals(1L, dao.count())
        }

    @Test
    fun `unlimited retention never prunes`() =
        recorderTest {
            dao.insertAll(listOf(fakeTrackPoint(timeMs = daysAgo(1_000))))
            settings.value = LocationSettings.Default.copy(trackRetention = TrackRetentionSetting.UNLIMITED)

            buildRepository()
            runCurrent()

            assertEquals(1L, dao.count())
        }

    @Test
    fun `clearHistory deletes every recorded point`() =
        recorderTest {
            dao.insertAll(listOf(fakeTrackPoint(timeMs = NOW_EPOCH_MS)))
            val repository = buildRepository()

            assertTrue(repository.clearHistory())

            assertEquals(0L, dao.count())
        }

    @Test
    fun `exportGpx streams every point and reports the count`() =
        recorderTest {
            dao.insertAll(
                listOf(
                    fakeTrackPoint(tripId = 0L, timeMs = NOW_EPOCH_MS),
                    fakeTrackPoint(tripId = 1L, timeMs = NOW_EPOCH_MS + 1_000),
                ),
            )
            val repository = buildRepository()
            val output = ByteArrayOutputStream()

            assertEquals(2L, repository.exportGpx(output))

            val xml = output.toString("UTF-8")
            assertTrue(xml.startsWith("<?xml"))
            assertTrue(xml.contains("<name>Trip 0</name>"))
            assertTrue(xml.contains("<name>Trip 1</name>"))
        }

    private companion object {
        const val NOW_EPOCH_MS = 1_752_710_400_000L // 2025-07-17T00:00:00Z

        fun daysAgo(days: Long): Long = NOW_EPOCH_MS - days * 24L * 60 * 60 * 1_000
    }
}
