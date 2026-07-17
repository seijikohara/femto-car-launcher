package io.github.seijikohara.femto.data.location

import java.io.Writer
import java.time.Instant

// The exporter reuses the trip math's gap window: a drought longer than this
// splits a <trkseg> (the track verifiably has a hole there).
private val SegmentGapMs = (MAX_GAP_SECONDS * 1_000).toLong()

/**
 * Streaming GPX 1.0 serializer for recorded track points.
 *
 * GPX 1.0 rather than 1.1 on purpose: 1.0's trkpt schema carries `<course>`
 * and `<speed>` as standard elements, so bearing and speed export without a
 * vendor extension namespace; GPX 1.1 dropped both. Each reset-to-reset trip
 * becomes one `<trk>`, and a fix drought longer than the trip math's gap
 * window opens a new `<trkseg>` inside it. Points must be fed in (trip, time)
 * order — the caller pages the table in insert order, which the single-writer
 * recorder guarantees is exactly that.
 *
 * Everything written is machine-formatted numbers and constants, so no XML
 * escaping is needed.
 */
internal class GpxWriter(
    private val out: Writer,
) {
    private var openTripId: Long? = null
    private var lastTimeMs: Long? = null

    fun begin() {
        out.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        out.write("<gpx version=\"1.0\" creator=\"$CREATOR\" xmlns=\"http://www.topografix.com/GPX/1/0\">\n")
    }

    fun point(point: TrackPointEntity) {
        when {
            openTripId != point.tripId -> startTrack(point.tripId)
            exceedsSegmentGap(point.timeMs) -> restartSegment()
        }
        lastTimeMs = point.timeMs
        out.write("      <trkpt lat=\"${point.latitude}\" lon=\"${point.longitude}\">\n")
        point.altitudeM?.let { out.write("        <ele>$it</ele>\n") }
        out.write("        <time>${Instant.ofEpochMilli(point.timeMs)}</time>\n")
        point.bearingDeg?.let { out.write("        <course>$it</course>\n") }
        point.speedMps?.let { out.write("        <speed>$it</speed>\n") }
        out.write("      </trkpt>\n")
    }

    fun end() {
        closeTrackIfOpen()
        out.write("</gpx>\n")
    }

    private fun startTrack(tripId: Long) {
        closeTrackIfOpen()
        openTripId = tripId
        lastTimeMs = null
        out.write("  <trk>\n    <name>Trip $tripId</name>\n    <trkseg>\n")
    }

    private fun restartSegment() {
        out.write("    </trkseg>\n    <trkseg>\n")
    }

    private fun exceedsSegmentGap(timeMs: Long): Boolean = lastTimeMs?.let { timeMs - it > SegmentGapMs } ?: false

    private fun closeTrackIfOpen() {
        if (openTripId == null) return
        out.write("    </trkseg>\n  </trk>\n")
        openTripId = null
    }

    private companion object {
        const val CREATOR = "Femto Car Launcher"
    }
}
