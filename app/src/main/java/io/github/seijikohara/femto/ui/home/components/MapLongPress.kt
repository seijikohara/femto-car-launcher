package io.github.seijikohara.femto.ui.home.components

import io.github.seijikohara.femto.data.routing.RoutePoint

/**
 * The point of the OSM page's `longpress` event (webmap `src/long-press.ts`): its detail is `<lat>,<lon>` in degrees.
 * Null for a detail that names no point on the earth, so a malformed event opens nothing.
 */
internal fun longPressPointOrNull(detail: String): RoutePoint? {
    val parts = detail.split(',')
    val latitude = parts.getOrNull(0)?.trim()?.toDoubleOrNull()
    val longitude = parts.getOrNull(1)?.trim()?.toDoubleOrNull()
    return RoutePoint(latitude ?: return null, longitude ?: return null).takeIf {
        parts.size == 2 && it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0
    }
}
