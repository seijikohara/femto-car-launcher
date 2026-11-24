package io.github.seijikohara.femto.data.diagnostics

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.core.content.getSystemService
import io.github.seijikohara.femto.data.music.AudioSpectrumRepository
import io.github.seijikohara.femto.data.music.MusicCardState
import io.github.seijikohara.femto.data.music.NotStartedReason
import io.github.seijikohara.femto.data.music.ResumeAttempts
import io.github.seijikohara.femto.data.music.ResumeOutcome
import io.github.seijikohara.femto.data.music.ResumeStep
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
 * The last attempt to resume a remembered track, as a fact: the app and the step that started playback, or why none
 * did. Names no track, so the report never carries a title or an artist. Pure so the wording and health are pinned by
 * JVM tests.
 */
internal fun resumeFact(outcome: ResumeOutcome?): DiagnosticFact =
    DiagnosticFact(
        "Last resume",
        when (outcome) {
            null -> {
                FactValue.Text("none since the launcher started")
            }

            is ResumeOutcome.Started -> {
                FactValue.Text("${outcome.packageName}: started by ${outcome.step.label}")
            }

            is ResumeOutcome.NotStarted -> {
                "${outcome.packageName}: ${outcome.reason.label}".let { value ->
                    when (outcome.reason) {
                        // The steps ran and the app never played, another app played in its place (the media
                        // key went elsewhere), or its sessions became unreadable: the resume itself failed, the
                        // case the report exists to explain.
                        NotStartedReason.NO_PLAYBACK,
                        NotStartedReason.OTHER_APP_PLAYING,
                        NotStartedReason.SESSIONS_UNREADABLE,
                        -> {
                            FactValue.Status(value, FactHealth.WARNING)
                        }

                        NotStartedReason.APP_GONE, NotStartedReason.LEFT_DASHBOARD, NotStartedReason.INTERRUPTED -> {
                            FactValue.Text(value)
                        }
                    }
                }
            }
        },
    )

private val ResumeStep.label: String
    get() =
        when (this) {
            ResumeStep.BROWSER -> "the media browser"
            ResumeStep.MEDIA_KEY -> "the play media key"
            ResumeStep.OPEN_APP -> "opening the app"
        }

private val NotStartedReason.label: String
    get() =
        when (this) {
            NotStartedReason.NO_PLAYBACK -> "no step started playback"
            NotStartedReason.APP_GONE -> "app not installed or not launchable"
            NotStartedReason.OTHER_APP_PLAYING -> "another app started playing"
            NotStartedReason.LEFT_DASHBOARD -> "dashboard left before the app would have opened"
            NotStartedReason.SESSIONS_UNREADABLE -> "sessions unreadable (notification access lost)"
            NotStartedReason.INTERRUPTED -> "interrupted"
        }

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

            // The app only: the remembered title and artist stay out of the report.
            is MusicCardState.LastPlayed -> {
                FactValue.Text("no active session (resume offered for ${musicState.track.packageName})")
            }

            MusicCardState.NeedsPermission -> {
                FactValue.Status("notification listener not granted", FactHealth.WARNING)
            }

            null -> {
                FactValue.Text("unknown")
            }
        },
    )

/**
 * Collects the MUSIC diagnostics section's one-shot half: the spectrum-probe
 * verdict, the last resume attempt, and the audio-output state. The live
 * `Session` fact is injected by
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
                        resumeFact(ResumeAttempts.latestOrNull()),
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
                if (audioManager.isVolumeFixed) " (fixed)" else ""
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
