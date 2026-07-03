package io.github.seijikohara.femto.data.diagnostics

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.core.content.getSystemService
import io.github.seijikohara.femto.data.music.AudioSpectrumRepository
import io.github.seijikohara.femto.data.music.MusicCardState
import io.github.seijikohara.femto.data.music.SpectrumDiagnosis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The spectrum-probe verdict as facts. Pure so the health mapping is pinned
 * by JVM tests; the value tokens match the v1 report wording.
 */
internal fun spectrumFacts(diagnosis: SpectrumDiagnosis?): List<DiagnosticFact> =
    listOf(
        DiagnosticFact(
            "Spectrum capture",
            when (diagnosis) {
                SpectrumDiagnosis.ACTIVE -> {
                    FactValue.Status(diagnosis.name, FactHealth.OK)
                }

                SpectrumDiagnosis.ENGINE_UNAVAILABLE, SpectrumDiagnosis.NO_PERMISSION -> {
                    FactValue.Status(diagnosis.name, FactHealth.WARNING)
                }

                // Silence is expected whenever nothing is playing — a plain
                // value, not a verdict.
                SpectrumDiagnosis.SILENT -> {
                    FactValue.Text(diagnosis.name)
                }

                null -> {
                    FactValue.Text("not probed")
                }
            },
        ),
    )

/**
 * Prepend the live `Session` fact onto the collected MUSIC payload. Pure:
 * the session state streams from the ViewModel's music flow rather than the
 * one-shot collector, so the two halves stay independently testable. A
 * still-collecting (null) or failed (Unavailable) payload passes through
 * unchanged.
 */
internal fun musicSectionWithSession(
    collected: SectionPayload?,
    musicState: MusicCardState?,
): SectionPayload? =
    when (collected) {
        is SectionPayload.Facts -> SectionPayload.Facts(listOf(sessionFact(musicState)) + collected.facts)
        else -> collected
    }

private fun sessionFact(musicState: MusicCardState?): DiagnosticFact =
    DiagnosticFact(
        "Session",
        when (musicState) {
            is MusicCardState.Playing -> {
                FactValue.Text(
                    "${musicState.nowPlaying.packageName} " +
                        "(${if (musicState.nowPlaying.isPlaying) "playing" else "paused"})",
                )
            }

            MusicCardState.NoActiveSession -> {
                FactValue.Text("no active session")
            }

            MusicCardState.NeedsPermission -> {
                FactValue.Status("notification listener NOT granted", FactHealth.WARNING)
            }

            null -> {
                FactValue.Text("unknown")
            }
        },
    )

/**
 * Collects the MUSIC diagnostics section's one-shot half: the spectrum-probe
 * verdict plus the audio-output state. The live `Session` fact is injected by
 * the ViewModel via [musicSectionWithSession].
 */
internal class MusicFactsCollector(
    private val context: Context,
    private val spectrumRepository: AudioSpectrumRepository,
) {
    suspend fun musicFacts(): SectionPayload.Facts {
        // The probe drives its own short-lived Visualizer with an internal
        // window timeout; run it before the plain AudioManager reads so its
        // errors precede the log tail (the registry awaits this ordering).
        val diagnosis = spectrumRepository.diagnose()
        return withContext(Dispatchers.IO) {
            val audioManager = context.getSystemService<AudioManager>()!!
            SectionPayload.Facts(
                spectrumFacts(diagnosis) +
                    listOf(
                        outputsFact(audioManager),
                        volumeFact(audioManager),
                        propertiesFact(audioManager),
                    ),
            )
        }
    }

    private fun outputsFact(audioManager: AudioManager): DiagnosticFact =
        DiagnosticFact(
            "Outputs",
            FactValue.Text(
                audioManager
                    .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    .joinToString { it.type.audioDeviceTypeName() }
                    .ifEmpty { "none" },
            ),
        )

    // A fixed volume is a WARNING because it explains "the volume buttons do
    // nothing" — common on head units that route volume to the amplifier.
    private fun volumeFact(audioManager: AudioManager): DiagnosticFact {
        val value =
            "${audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)}/" +
                "${audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)}" +
                if (audioManager.isVolumeFixed) " (FIXED)" else ""
        return DiagnosticFact(
            "Volume",
            if (audioManager.isVolumeFixed) FactValue.Status(value, FactHealth.WARNING) else FactValue.Text(value),
        )
    }

    private fun propertiesFact(audioManager: AudioManager): DiagnosticFact {
        val sampleRate = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE) ?: "unknown"
        val framesPerBuffer = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER) ?: "unknown"
        return DiagnosticFact("Audio properties", FactValue.Text("$sampleRate Hz, $framesPerBuffer frames"))
    }

    private fun Int.audioDeviceTypeName(): String =
        when (this) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "builtin speaker"
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "builtin earpiece"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "wired headset"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "wired headphones"
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "bluetooth a2dp"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "bluetooth sco"
            AudioDeviceInfo.TYPE_HDMI -> "hdmi"
            AudioDeviceInfo.TYPE_USB_DEVICE -> "usb device"
            AudioDeviceInfo.TYPE_USB_HEADSET -> "usb headset"
            AudioDeviceInfo.TYPE_USB_ACCESSORY -> "usb accessory"
            AudioDeviceInfo.TYPE_AUX_LINE -> "aux line"
            AudioDeviceInfo.TYPE_LINE_ANALOG -> "line analog"
            AudioDeviceInfo.TYPE_LINE_DIGITAL -> "line digital"
            AudioDeviceInfo.TYPE_BUS -> "bus"
            AudioDeviceInfo.TYPE_REMOTE_SUBMIX -> "remote submix"
            AudioDeviceInfo.TYPE_TELEPHONY -> "telephony"
            else -> "type $this"
        }
}
