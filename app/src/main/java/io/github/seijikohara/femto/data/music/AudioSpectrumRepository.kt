@file:OptIn(ExperimentalCoroutinesApi::class) // flatMapLatest below.

package io.github.seijikohara.femto.data.music

import android.content.Context
import android.media.audiofx.Visualizer
import android.util.Log
import io.github.seijikohara.femto.data.location.hasRecordAudioPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn

private const val TAG = "AudioSpectrumRepo"

/**
 * Streams per-band spectrum levels of whatever audio the device is playing,
 * for the music card's spectrum background.
 *
 * The source is an [android.media.audiofx.Visualizer] attached to audio
 * session 0 — the global output mix — so it visualizes other apps' playback
 * without touching the microphone. The capture is MONO by API contract (the
 * UI mirrors it symmetrically) and gated on the RECORD_AUDIO runtime grant.
 * Sources that opt out of capture (DRM-protected streams) deliver silent
 * FFTs, which degrade to all-zero bands and a flat render — by design.
 */
internal class AudioSpectrumRepository(
    private val context: Context,
) {
    /**
     * Emit `SPECTRUM_BAND_COUNT` levels in `0..1` while [activeFlow] is true,
     * `null` while inactive or when capture is unavailable (permission
     * withheld, Visualizer construction failure). The Visualizer exists only
     * while the latest [activeFlow] value is true, so the capture engine is
     * released the moment playback stops or the setting turns off.
     */
    fun bandsFlow(activeFlow: Flow<Boolean>): Flow<FloatArray?> =
        activeFlow
            .distinctUntilChanged()
            .flatMapLatest { active -> if (active) captureFlow() else flowOf(null) }

    private fun captureFlow(): Flow<FloatArray?> =
        callbackFlow {
            val visualizer = visualizerOrNull()
            if (visualizer == null) {
                // Degrade to the flat render, never crash; one log line above
                // keeps the silent card diagnosable (same stance as
                // MusicSessionRepository's session-enumeration failure path).
                trySend(null)
                awaitClose {}
                return@callbackFlow
            }
            val edges = spectrumBandEdgesHz()
            runCatching {
                visualizer.captureSize = Visualizer.getCaptureSizeRange()[1]
                visualizer.setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            visualizer: Visualizer,
                            waveform: ByteArray,
                            samplingRate: Int,
                        ) = Unit

                        override fun onFftDataCapture(
                            visualizer: Visualizer,
                            fft: ByteArray,
                            samplingRate: Int,
                        ) {
                            // The Visualizer contract reports the rate in mHz.
                            trySend(spectrumBandLevels(fft, samplingRate / 1000, edges))
                        }
                    },
                    Visualizer.getMaxCaptureRate(),
                    false,
                    true,
                )
                visualizer.enabled = true
            }.onFailure {
                Log.w(TAG, "Visualizer capture setup failed; spectrum renders flat", it)
                trySend(null)
            }
            awaitClose {
                runCatching { visualizer.enabled = false }
                visualizer.release()
            }
        }.conflate()
            // Band math off Main; capture callbacks arrive on the
            // Visualizer's own thread either way.
            .flowOn(Dispatchers.Default)

    /**
     * Construct the output-mix Visualizer, or null (with one WARN) when the
     * RECORD_AUDIO grant is missing or the audio HAL refuses the session —
     * both are expected states, not errors, and there is no retry (the next
     * activation re-attempts naturally).
     */
    private fun visualizerOrNull(): Visualizer? =
        if (!context.hasRecordAudioPermission()) {
            Log.w(TAG, "RECORD_AUDIO not granted; spectrum renders flat")
            null
        } else {
            runCatching { Visualizer(0) }
                .onFailure { Log.w(TAG, "output-mix Visualizer unavailable; spectrum renders flat", it) }
                .getOrNull()
        }
}
