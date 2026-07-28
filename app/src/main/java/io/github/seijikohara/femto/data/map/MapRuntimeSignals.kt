package io.github.seijikohara.femto.data.map

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Process-wide record of what the live map page actually did this session.
 *
 * The map renders in a WebView, so its failures never surface as an Android
 * exception: the page reports a `fatal` over the JS bridge, the host swaps in
 * the failure notice, and the reason exists only as a logcat line. That leaves
 * the in-app diagnostics unable to state whether the map rendered — the one
 * question a "my map is blank" report turns on. This holder keeps the last
 * reason (and how many arrived) so the MAP diagnostics section can report it as
 * a fact rather than leaving it to be grepped out of the log tail, which is a
 * bounded tail and drops the line once enough logging follows it.
 *
 * Written by the WebView host in `ui/`, read by the diagnostics collector in
 * `data/`; it lives here because `data/` never imports `ui/`. Session-scoped by
 * design: a failure the user has since navigated away from is still the fact
 * worth reporting, but it must not outlive the process and mislead the next
 * launch.
 */
internal object MapRuntimeSignals {
    private val lastFailure = AtomicReference<MapFailure?>(null)
    private val failureCount = AtomicInteger(0)

    /** A `fatal` the page reported: [detail] is its reason string. */
    data class MapFailure(
        val detail: String,
        val elapsedRealtimeMs: Long,
    )

    /** Record a page-reported fatal. Called from the JS bridge thread. */
    fun recordFailure(
        detail: String,
        elapsedRealtimeMs: Long,
    ) {
        lastFailure.set(MapFailure(detail, elapsedRealtimeMs))
        failureCount.incrementAndGet()
    }

    /**
     * Clear the *last failure* once the map renders, so a recovered map stops
     * reporting a failure it has moved past. [failureCount] is deliberately kept:
     * a map that fails and recovers repeatedly still reads as flapping, which a
     * cleared counter would hide.
     */
    fun recordRendered() {
        lastFailure.set(null)
    }

    fun lastFailureOrNull(): MapFailure? = lastFailure.get()

    /** Total fatals this session, including ones a later recovery cleared. */
    fun failureCount(): Int = failureCount.get()
}
