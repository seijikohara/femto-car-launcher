package io.github.seijikohara.femto.ui.home.components

import io.github.seijikohara.femto.data.location.TrackPointEntity
import io.github.seijikohara.femto.data.location.TripSummaryRow
import io.github.seijikohara.femto.testfixtures.fakeTrackPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TripVizViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // The flyover refreshes on every open, and the current trip keeps growing
    // while it is closed. A refresh must reload the selected trip's now-larger
    // point set instead of showing the first-open snapshot.
    @Test
    fun `refresh reloads the selected trip's new points`() =
        runTest {
            var points = straightTrack(count = 10)
            val viewModel =
                TripVizViewModel(
                    tripSummaries = { listOf(summaryFor(TRIP_ID, points.size)) },
                    pointsForTrip = { points },
                    currentTripId = { TRIP_ID },
                    computeDispatcher = StandardTestDispatcher(testScheduler),
                )
            advanceUntilIdle()

            val firstOpen = viewModel.uiState.value.selection
            assertNotNull(firstOpen)
            val firstVertexCount = firstOpen.geometry.vertexCount

            // Drive more while the panel is "closed", then reopen (Refresh).
            points = straightTrack(count = 40)
            viewModel.onAction(TripVizAction.Refresh)
            advanceUntilIdle()

            val reopened = viewModel.uiState.value.selection
            assertNotNull(reopened)
            assertTrue(
                reopened.geometry.vertexCount > firstVertexCount,
                "reopening should reload the trip's new points " +
                    "(was $firstVertexCount, now ${reopened.geometry.vertexCount})",
            )
        }

    private fun summaryFor(
        tripId: Long,
        pointCount: Int,
    ) = TripSummaryRow(
        tripId = tripId,
        startMs = 0L,
        endMs = pointCount * 1_000L,
        pointCount = pointCount,
        minLat = 35.0,
        maxLat = 36.0,
        minLon = 139.0,
        maxLon = 140.0,
        minAltitude = 20.0,
        maxAltitude = 60.0,
    )

    private fun straightTrack(count: Int): List<TrackPointEntity> =
        (0 until count).map { i ->
            fakeTrackPoint(
                tripId = TRIP_ID,
                timeMs = i * 1_000L,
                latitude = 35.6580 + i * 0.0005,
                longitude = 139.7016 + i * 0.0003,
                speedMps = 8f + (i % 5),
                altitudeM = 20.0 + i,
            )
        }

    private companion object {
        const val TRIP_ID = 1L
    }
}
