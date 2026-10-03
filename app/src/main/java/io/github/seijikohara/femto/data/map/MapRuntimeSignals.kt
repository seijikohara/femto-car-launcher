package io.github.seijikohara.femto.data.map

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
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
    private val webGlRenderer = AtomicReference<String?>(null)
    private val pageFrames = AtomicReference<PageFrames?>(null)

    // The current map page, and the last lens report with the page that sent
    // it: a report is only ever read back for the page it came from.
    private val currentPage = AtomicLong(0)
    private val googleLens = AtomicReference<Pair<Long, GoogleLens>?>(null)

    /**
     * Whether the Google Maps page has measured its tilted vector map's
     * perspective (webmap lens.ts), which keeps the road ahead vertical through
     * the chevron beside the cards. [source] is the probe that measured it:
     * "webgl" (the WebGL overlay's camera transformer, with a Map ID) or
     * "canvas" (the 2D projection, without one), null when the lens is unused;
     * [fovyDeg] is the field of view the measurement implies, null unless
     * measured.
     */
    data class GoogleLens(
        val status: LensStatus,
        val source: String?,
        val fovyDeg: Double?,
    )

    /** The page's lens state: see [GoogleLens]. */
    enum class LensStatus {
        MEASURED,
        UNMEASURED,

        /** A raster or flat (0°) map, which needs no lens. */
        UNUSED,
    }

    /**
     * One burst of the map page's own frame intervals (bridge.ts
     * startFrameSampler): the page's renderer cadence, which the launcher's
     * Compose frame statistics cannot see.
     */
    data class PageFrames(
        val medianMs: Int,
        val worstMs: Int,
        val sampledFrames: Int,
        val elapsedRealtimeMs: Long,
    )

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
    fun recordRendered(webGlRenderer: String) {
        lastFailure.set(null)
        this.webGlRenderer.set(webGlRenderer.takeIf { it.isNotBlank() })
    }

    /**
     * The GPU (or software rasteriser) behind the page's WebGL, as the page
     * unmasked it on its first render; null until a page has rendered.
     */
    fun webGlRendererOrNull(): String? = webGlRenderer.get()

    /**
     * Record a `frames` event. [detail] is the page's compact
     * "median=<ms>,worst=<ms>,samples=<n>"; anything else is ignored, since a
     * malformed sample must not replace a good one.
     */
    fun recordPageFrames(
        detail: String,
        elapsedRealtimeMs: Long,
    ) {
        pageFramesFrom(detail, elapsedRealtimeMs)?.let(pageFrames::set)
    }

    fun pageFramesOrNull(): PageFrames? = pageFrames.get()

    /**
     * Record a `lens` event from map page [page] (the token
     * [recordMapPageLoad] gave it): "measured,source=<s>,fovy=<deg>",
     * "unmeasured,source=<s>" or "unused"; anything else is ignored, and so
     * is a report from a page that is no longer the current one. The
     * current-page check and the write are one atomic update, so an old page
     * that passed the check cannot land its write over the newer page's.
     */
    fun recordGoogleLens(
        detail: String,
        page: Long,
    ) {
        val lens = googleLensFrom(detail) ?: return
        googleLens.updateAndGet { stored -> replacedGoogleLens(stored, page, currentPage.get(), lens) }
    }

    /**
     * The stored lens report after [page] reports [lens], with [currentPage]
     * the page current at the time: only the current page's report is
     * stored, and never over a report from a newer page — whatever the
     * current page looked like to the writer.
     */
    internal fun replacedGoogleLens(
        stored: Pair<Long, GoogleLens>?,
        page: Long,
        currentPage: Long,
        lens: GoogleLens,
    ): Pair<Long, GoogleLens>? =
        when {
            page != currentPage -> stored
            stored != null && stored.first > page -> stored
            else -> page to lens
        }

    /** The current page's lens state; null until that page has reported. */
    fun googleLensOrNull(): GoogleLens? = googleLens.get()?.takeIf { it.first == currentPage.get() }?.second

    /**
     * Start a new map page: returns its token for [recordGoogleLens]. The
     * lens state belongs to one page, so a rebuild to a raster map, or a
     * page that never measures, must not keep showing an earlier page's
     * "measured" — and the old WebView, destroyed only after the new page
     * has loaded, may still report; the token drops those reports even when
     * they land after the new page's own. The session-wide facts (failures,
     * renderer, frames) stay.
     */
    fun recordMapPageLoad(): Long = currentPage.incrementAndGet()

    internal fun googleLensFrom(detail: String): GoogleLens? {
        val parts = detail.split(',')
        val fields =
            parts
                .drop(1)
                .mapNotNull { field ->
                    field.substringBefore('=', "").takeIf { it.isNotEmpty() }?.let { it to field.substringAfter('=') }
                }.toMap()
        val source = fields["source"]?.takeIf { it.isNotEmpty() }
        return when (parts.first()) {
            "measured" -> {
                source?.let {
                    fields["fovy"]?.toDoubleOrNull()?.let { fovy ->
                        GoogleLens(LensStatus.MEASURED, it, fovy)
                    }
                }
            }

            "unmeasured" -> {
                source?.let { GoogleLens(LensStatus.UNMEASURED, it, fovyDeg = null) }
            }

            "unused" -> {
                GoogleLens(LensStatus.UNUSED, source = null, fovyDeg = null)
            }

            else -> {
                null
            }
        }
    }

    internal fun pageFramesFrom(
        detail: String,
        elapsedRealtimeMs: Long,
    ): PageFrames? {
        val fields =
            detail
                .split(',')
                .mapNotNull { field ->
                    field.substringBefore('=', "").takeIf { it.isNotEmpty() }?.let { key ->
                        field.substringAfter('=').toIntOrNull()?.let { key to it }
                    }
                }.toMap()
        val median = fields["median"] ?: return null
        val worst = fields["worst"] ?: return null
        val samples = fields["samples"]?.takeIf { it > 0 } ?: return null
        return PageFrames(median, worst, samples, elapsedRealtimeMs)
    }

    fun lastFailureOrNull(): MapFailure? = lastFailure.get()

    /** Total fatals this session, including ones a later recovery cleared. */
    fun failureCount(): Int = failureCount.get()
}
