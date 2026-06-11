package io.github.seijikohara.femto.data.location

import android.location.Location

// How long after the last fix the position counts as lost (e.g. a tunnel). The
// location flow forwards fixes verbatim and never emits null once seeded, so a
// lost signal surfaces as a fix that stops ageing-out rather than a null — hence
// an age threshold rather than a presence check. Mirrored in webmap (main.ts,
// LOCATION_STALE_THRESHOLD_MS) for the LIVE chevron.
internal const val LOCATION_STALE_THRESHOLD_MS = 10_000L

/**
 * Return whether this fix is still fresh at [nowElapsedRealtimeNanos] (pass
 * [android.os.SystemClock.elapsedRealtimeNanos]). Uses the monotonic
 * elapsed-realtime clock, not wall time, so a system clock change never makes a
 * fix look stale.
 */
internal fun Location.isFresh(nowElapsedRealtimeNanos: Long): Boolean =
    nowElapsedRealtimeNanos - elapsedRealtimeNanos <= LOCATION_STALE_THRESHOLD_MS * 1_000_000L
