@file:OptIn(ExperimentalCoroutinesApi::class) // flatMapLatest

package io.github.seijikohara.femto.ui.settings

import io.github.seijikohara.femto.data.routing.InstalledRoutingTile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * The Routing data row's summary: the [installed] tiles counted and summed while routing is [available], else null,
 * which hides the row. [installed] is reached only while routing is available, so a build without routing never
 * creates the tile store.
 */
internal fun routingDataSummaryFlow(
    available: Flow<Boolean>,
    installed: () -> Flow<List<InstalledRoutingTile>>,
): Flow<RoutingDataSummary?> =
    available.flatMapLatest { offered ->
        if (offered) {
            installed().map { tiles ->
                RoutingDataSummary(tileCount = tiles.size, totalBytes = tiles.sumOf { it.sizeBytes })
            }
        } else {
            flowOf(null)
        }
    }
