package io.github.seijikohara.femto.data.location

import android.location.Location
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter

private const val TAG = "TrackLogRepository"

/**
 * Records the trip chain's GPS fixes into `track_points` for later
 * visualization.
 *
 * [offer] is the [TripFixTap] target: it runs on TripRepository's single
 * accrual sequence, applies the recording gates (toggle, 1 Hz sample gate,
 * parked thinning, accuracy floor) against plain fields that only that
 * sequence touches, and hands accepted points to a bounded queue — the
 * accrual sequence never waits on the database. A writer coroutine drains the
 * queue in small batches so the on-disk state trails the road by a few
 * seconds at most (car power dies without warning). Because the recorder only
 * ever sees fixes the trip chain forwards, recording automatically runs
 * exactly while trip math runs — dashboard visible or the opt-in
 * background-ranging service alive — and can never keep GPS registered on its
 * own.
 *
 * Retention pruning restarts whenever the user changes the window (an
 * immediate prune, then daily); recording only happens while the process
 * lives, so an in-process loop covers everything a scheduled job would.
 *
 * Every database touch is wrapped: the launcher is the HOME app, and a
 * corrupt or full disk must degrade to a logged gap in the track, never a
 * crash loop.
 */
internal class TrackLogRepository(
    private val dao: TrackPointDao,
    settings: Flow<LocationSettings>,
    scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val batchMaxPoints: Int = BATCH_MAX_POINTS,
    private val batchFlushWindowMs: Long = BATCH_FLUSH_WINDOW_MS,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) {
    // Gate state; written only from the single accrual sequence behind [offer].
    private var lastRecordedElapsedNanos: Long? = null
    private var lastRecordedStationary = false

    // Read on the accrual sequence, written by the settings collector below.
    @Volatile
    private var recordingEnabled = DEFAULT_TRACK_RECORDING_ENABLED

    // Bounded + drop-oldest: if the writer stalls (disk hiccup) the queue
    // sheds the oldest points instead of suspending the accrual sequence.
    private val queue =
        Channel<TrackPointEntity>(capacity = QUEUE_CAPACITY, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    init {
        scope.launch {
            settings
                .map { it.trackRecordingEnabled }
                .distinctUntilChanged()
                .collect { recordingEnabled = it }
        }
        scope.launch(ioDispatcher) { writeLoop() }
        scope.launch(ioDispatcher) {
            settings
                .map { it.trackRetention }
                .distinctUntilChanged()
                .collectLatest { retention ->
                    // Tightening the window prunes immediately; then daily.
                    while (true) {
                        prune(retention)
                        delay(PRUNE_INTERVAL_MS)
                    }
                }
        }
    }

    /**
     * Offer one accepted GPS fix from the trip chain, tagged with its
     * reset-to-reset trip id. Never blocks; see the class KDoc for the gates.
     */
    fun offer(
        location: Location,
        tripId: Long,
    ) {
        if (!recordingEnabled) return
        // A fix without accuracy is kept: rejecting it would blank recording
        // on HALs that never report accuracy at all.
        if (location.hasAccuracy() && location.accuracy > MAX_ACCURACY_M) return
        val elapsed = location.elapsedRealtimeNanos
        val last = lastRecordedElapsedNanos
        if (last != null && elapsed - last < SAMPLE_INTERVAL_NANOS) return
        // Parked thinning: keep the first stationary point (the stop is part of
        // the track), skip the identical ones after it. Speed-less fixes never
        // count as stationary — better a fat parked cluster than a hole in the
        // drive on chips that report no speed.
        val stationary = location.hasSpeed() && location.speed < MIN_MOVING_SPEED_MS
        if (stationary && lastRecordedStationary) return
        lastRecordedElapsedNanos = elapsed
        lastRecordedStationary = stationary
        queue.trySend(location.toTrackPoint(tripId))
    }

    /** Delete every recorded point. Returns whether the delete succeeded. */
    suspend fun clearHistory(): Boolean =
        withContext(ioDispatcher) {
            runCatching { dao.deleteAll() }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    Log.e(TAG, "track-history delete failed", e)
                }.isSuccess
        }

    /**
     * Stream every recorded point into [output] as GPX 1.0 (see [GpxWriter]).
     * Returns the number of exported points, or null when the export failed —
     * the caller owns closing [output] regardless.
     */
    suspend fun exportGpx(output: OutputStream): Long? =
        withContext(ioDispatcher) {
            runCatching {
                var exported = 0L
                // Flush without closing: the SAF stream is the caller's.
                val writer = BufferedWriter(OutputStreamWriter(output, Charsets.UTF_8))
                val gpx = GpxWriter(writer)
                gpx.begin()
                var afterId = 0L
                while (true) {
                    val page = dao.pageAfter(afterId, EXPORT_PAGE_SIZE)
                    if (page.isEmpty()) break
                    page.forEach(gpx::point)
                    exported += page.size
                    afterId = page.last().id
                }
                gpx.end()
                writer.flush()
                exported
            }.onFailure { e ->
                if (e is CancellationException) throw e
                Log.e(TAG, "gpx export failed", e)
            }.getOrNull()
        }

    private suspend fun writeLoop() {
        val batch = mutableListOf<TrackPointEntity>()
        while (true) {
            batch += queue.receive()
            // Fill the batch from points already queued (or arriving within the
            // flush window) so steady 1 Hz driving inserts every ~5 s, while the
            // last points before a stop still land within one window. select's
            // onReceive/onTimeout is atomic — unlike withTimeoutOrNull(receive()),
            // it cannot dequeue a point and then discard it on the timeout race.
            while (batch.size < batchMaxPoints) {
                val next =
                    select<TrackPointEntity?> {
                        queue.onReceive { it }
                        onTimeout(batchFlushWindowMs) { null }
                    } ?: break
                batch += next
            }
            runCatching { dao.insertAll(batch.toList()) }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    Log.e(TAG, "track-point batch insert failed", e)
                }
            batch.clear()
        }
    }

    private suspend fun prune(retention: TrackRetentionSetting) {
        val maxAgeMs = retention.maxAgeMs ?: return
        runCatching {
            val now = nowEpochMs()
            // Row timestamps are Location.time (GNSS-derived, correct even when
            // the system clock is wrong). A dead-RTC head unit that boots to a
            // far-future clock before NTP corrects it would otherwise delete every
            // correctly-timestamped point on the first prune. Skip while the clock
            // reads implausibly far ahead of the newest recorded fix; normal
            // pruning resumes once the clock is sane.
            val newest = dao.newestTimeMs()
            if (newest != null && now - newest > CLOCK_IMPLAUSIBLY_AHEAD_MS) {
                Log.w(TAG, "skipping prune: clock reads far ahead of the newest track point")
                return@runCatching
            }
            dao.deleteOlderThan(now - maxAgeMs)
        }.onFailure { e ->
            if (e is CancellationException) throw e
            Log.e(TAG, "track prune failed", e)
        }
    }

    private companion object {
        // 1 Hz: the practical GNSS ceiling on phone/head-unit chips and the
        // drive-logger norm; the location request may run faster for the live
        // speed readout, so the recorder gates back down here.
        const val SAMPLE_INTERVAL_NANOS = 1_000_000_000L

        // Fixes worse than this are position guesses, not track points.
        const val MAX_ACCURACY_M = 50f

        const val BATCH_MAX_POINTS = 5
        const val BATCH_FLUSH_WINDOW_MS = 2_000L
        const val QUEUE_CAPACITY = 64
        const val PRUNE_INTERVAL_MS = 24L * 60 * 60 * 1_000

        // A real retention window is at most a year; a clock this far ahead of the
        // newest recorded fix is a mis-set RTC, not elapsed time.
        const val CLOCK_IMPLAUSIBLY_AHEAD_MS = 400L * 24 * 60 * 60 * 1_000

        const val EXPORT_PAGE_SIZE = 500
    }
}

private fun Location.toTrackPoint(tripId: Long): TrackPointEntity =
    TrackPointEntity(
        tripId = tripId,
        timeMs = time,
        latitude = latitude,
        longitude = longitude,
        speedMps = if (hasSpeed()) speed else null,
        bearingDeg = if (hasBearing()) bearing else null,
        altitudeM = if (hasAltitude()) altitude else null,
        accuracyM = if (hasAccuracy()) accuracy else null,
    )
