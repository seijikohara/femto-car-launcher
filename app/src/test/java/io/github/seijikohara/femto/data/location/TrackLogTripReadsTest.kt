package io.github.seijikohara.femto.data.location

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.seijikohara.femto.testfixtures.fakeTrackPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executor
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Read-side coverage for the trip selector queries and their repository wrappers. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TrackLogTripReadsTest {
    // Direct executors make every insert land before the read that follows it,
    // so aggregates are asserted deterministically.
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
    private var repositoryScope: CoroutineScope? = null

    @After
    fun tearDown() {
        database.close()
    }

    // The repository's prune ticker never idles; cancel its scope before
    // runTest's cleanup drains the scheduler (same pattern as
    // TrackLogRepositoryTest).
    private fun readerTest(testBody: suspend TestScope.() -> Unit) =
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
            settings = MutableStateFlow(LocationSettings.Default),
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)).also { repositoryScope = it },
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
            nowEpochMs = { NOW_EPOCH_MS },
        )

    @Test
    fun `tripSummaries returns trips newest first`() =
        runTest {
            dao.insertAll(
                listOf(
                    fakeTrackPoint(tripId = 0L, timeMs = NOW_EPOCH_MS),
                    fakeTrackPoint(tripId = 0L, timeMs = NOW_EPOCH_MS + 1_000),
                    fakeTrackPoint(tripId = 1L, timeMs = NOW_EPOCH_MS + 10_000),
                    fakeTrackPoint(tripId = 1L, timeMs = NOW_EPOCH_MS + 11_000),
                ),
            )

            assertEquals(listOf(1L, 0L), dao.tripSummaries().map { it.tripId })
        }

    @Test
    fun `tripSummaries aggregates one trip's span count and bounds`() =
        runTest {
            dao.insertAll(
                listOf(
                    fakeTrackPoint(timeMs = NOW_EPOCH_MS, latitude = 35.0, longitude = 139.0),
                    fakeTrackPoint(timeMs = NOW_EPOCH_MS + 1_000, latitude = 35.2, longitude = 138.9),
                    fakeTrackPoint(timeMs = NOW_EPOCH_MS + 2_000, latitude = 34.9, longitude = 139.3),
                ),
            )

            val row = dao.tripSummaries().single()
            assertEquals(NOW_EPOCH_MS, row.startMs)
            assertEquals(NOW_EPOCH_MS + 2_000, row.endMs)
            assertEquals(3, row.pointCount)
            assertEquals(34.9, row.minLat, 0.0)
            assertEquals(35.2, row.maxLat, 0.0)
            assertEquals(138.9, row.minLon, 0.0)
            assertEquals(139.3, row.maxLon, 0.0)
        }

    @Test
    fun `tripSummaries altitude bounds are null when the trip carries no altitude`() =
        runTest {
            dao.insertAll(
                listOf(
                    fakeTrackPoint(timeMs = NOW_EPOCH_MS, altitudeM = null),
                    fakeTrackPoint(timeMs = NOW_EPOCH_MS + 1_000, altitudeM = null),
                ),
            )

            val row = dao.tripSummaries().single()
            assertNull(row.minAltitude)
            assertNull(row.maxAltitude)
        }

    @Test
    fun `tripSummaries altitude bounds cover the recorded readings`() =
        runTest {
            dao.insertAll(
                listOf(
                    fakeTrackPoint(timeMs = NOW_EPOCH_MS, altitudeM = 30.0),
                    fakeTrackPoint(timeMs = NOW_EPOCH_MS + 1_000, altitudeM = 10.0),
                    fakeTrackPoint(timeMs = NOW_EPOCH_MS + 2_000, altitudeM = 20.0),
                ),
            )

            val row = dao.tripSummaries().single()
            assertEquals(10.0, row.minAltitude!!, 0.0)
            assertEquals(30.0, row.maxAltitude!!, 0.0)
        }

    @Test
    fun `tripSummaries is empty on an empty database`() =
        runTest {
            assertTrue(dao.tripSummaries().isEmpty())
        }

    @Test
    fun `pointsForTrip returns only the requested trip in time order`() =
        runTest {
            // Trip 5 is inserted out of chronological order; trip 6 must not leak in.
            dao.insertAll(
                listOf(
                    fakeTrackPoint(tripId = 5L, timeMs = NOW_EPOCH_MS + 2_000),
                    fakeTrackPoint(tripId = 5L, timeMs = NOW_EPOCH_MS),
                    fakeTrackPoint(tripId = 5L, timeMs = NOW_EPOCH_MS + 1_000),
                    fakeTrackPoint(tripId = 6L, timeMs = NOW_EPOCH_MS + 500),
                ),
            )

            val points = dao.pointsForTrip(5L)
            assertTrue(points.all { it.tripId == 5L })
            assertEquals(
                listOf(NOW_EPOCH_MS, NOW_EPOCH_MS + 1_000, NOW_EPOCH_MS + 2_000),
                points.map { it.timeMs },
            )
        }

    @Test
    fun `pointsForTrip is empty on an empty database`() =
        runTest {
            assertTrue(dao.pointsForTrip(0L).isEmpty())
        }

    @Test
    fun `repository tripSummaries surfaces the recorded trips`() =
        readerTest {
            dao.insertAll(
                listOf(
                    fakeTrackPoint(tripId = 0L, timeMs = NOW_EPOCH_MS),
                    fakeTrackPoint(tripId = 1L, timeMs = NOW_EPOCH_MS + 10_000),
                ),
            )
            val repository = buildRepository()

            assertEquals(listOf(1L, 0L), repository.tripSummaries().map { it.tripId })
        }

    @Test
    fun `repository pointsForTrip surfaces the trip's points chronologically`() =
        readerTest {
            dao.insertAll(
                listOf(
                    fakeTrackPoint(tripId = 2L, timeMs = NOW_EPOCH_MS + 1_000),
                    fakeTrackPoint(tripId = 2L, timeMs = NOW_EPOCH_MS),
                    fakeTrackPoint(tripId = 3L, timeMs = NOW_EPOCH_MS + 500),
                ),
            )
            val repository = buildRepository()

            assertEquals(
                listOf(NOW_EPOCH_MS, NOW_EPOCH_MS + 1_000),
                repository.pointsForTrip(2L).map { it.timeMs },
            )
        }

    private companion object {
        // Recent wall-clock base so the repository's startup prune (90-day
        // default window) retains every inserted point.
        const val NOW_EPOCH_MS = 1_752_710_400_000L // 2025-07-17T00:00:00Z
    }
}
