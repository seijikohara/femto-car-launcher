package io.github.seijikohara.femto.data.routing

import io.github.seijikohara.femto.data.display.DisplaySettings
import io.github.seijikohara.femto.data.display.MapBackend
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Whether the launcher offers routing: only in a build that enables it ([enabled], the `routing_enabled` resource,
 * on in nightly builds until routing has been checked on a head unit) and only while the map runs on the OpenStreetMap
 * backend, since Google's terms forbid drawing a route over its map. Off, nothing about routing shows anywhere.
 */
internal fun routingAvailableFlow(
    enabled: Boolean,
    settings: Flow<DisplaySettings>,
): Flow<Boolean> =
    if (enabled) settings.map { it.mapBackend == MapBackend.OSM }.distinctUntilChanged() else flowOf(false)
