package io.github.seijikohara.femto.ui.settings

import io.github.seijikohara.femto.data.routing.InstalledRoutingTile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RoutingDataSummariesTest {
    @Test
    fun `where routing is offered the tiles are counted and summed`() =
        runTest {
            val summary =
                routingDataSummaryFlow(flowOf(true)) { flowOf(listOf(tile(48_000_000L), tile(96_000_000L))) }.first()

            assertEquals(RoutingDataSummary(tileCount = 2, totalBytes = 144_000_000L), summary)
        }

    @Test
    fun `where routing is not offered there is no summary and the store is never reached`() =
        runTest {
            val summary = routingDataSummaryFlow(flowOf(false)) { error("the store must not be created") }.first()

            assertNull(summary)
        }

    @Test
    fun `a switch to the Google backend hides the row`() =
        runTest {
            val available = MutableStateFlow(true)
            val summaries = mutableListOf<RoutingDataSummary?>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                routingDataSummaryFlow(available) { flowOf(emptyList()) }.take(2).toList(summaries)
            }

            available.value = false

            assertEquals(listOf(RoutingDataSummary(0, 0L), null), summaries)
        }

    private fun tile(sizeBytes: Long) = InstalledRoutingTile("E130_N30", sizeBytes, null, null)
}
