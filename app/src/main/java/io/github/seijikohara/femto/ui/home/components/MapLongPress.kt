package io.github.seijikohara.femto.ui.home.components

import io.github.seijikohara.femto.data.routing.RoutePoint

/**
 * Decides whether a touch on the map ends as a tap or as a long press. The page's long press reaches the main thread
 * through a post from the bridge thread, while the tap fires on the main thread as the finger lifts; around the hold
 * time either can come first. Fed on the main thread only, this lets the main thread's order alone decide: a long
 * press counts only while the finger that made it is still down, and it then takes the tap that would end the touch.
 */
internal class MapPressArbiter {
    private var fingerDown = false
    private var longPressed = false

    fun onFingerDown() {
        fingerDown = true
        longPressed = false
    }

    fun onFingersUp() {
        fingerDown = false
    }

    /** Whether the page's long press counts: only while a finger is down, and the touch is then the long press's. */
    fun claimLongPress(): Boolean = fingerDown.also { down -> if (down) longPressed = true }

    /** Whether the release that ends the touch is a tap: not when a long press has claimed the touch. */
    fun allowsTap(): Boolean = !longPressed
}

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
