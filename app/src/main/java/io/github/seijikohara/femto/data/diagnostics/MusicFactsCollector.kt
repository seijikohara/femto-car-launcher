package io.github.seijikohara.femto.data.diagnostics

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.session.PlaybackState
import android.os.SystemClock
import androidx.core.content.getSystemService
import io.github.seijikohara.femto.data.music.AudioSpectrumRepository
import io.github.seijikohara.femto.data.music.CardEntry
import io.github.seijikohara.femto.data.music.CardKind
import io.github.seijikohara.femto.data.music.MusicCardState
import io.github.seijikohara.femto.data.music.NotStartedReason
import io.github.seijikohara.femto.data.music.ResumeAttempts
import io.github.seijikohara.femto.data.music.ResumeOutcome
import io.github.seijikohara.femto.data.music.ResumeStep
import io.github.seijikohara.femto.data.music.SessionSnapshot
import io.github.seijikohara.femto.data.music.SessionTrace
import io.github.seijikohara.femto.data.music.SpectrumDiagnosis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

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

// The trace is written by the dashboard's session watch, so an empty record
// can mean the dashboard has not been in front yet.
private const val NO_SNAPSHOT_YET =
    "none recorded since the launcher started (the trace records while the dashboard is in front)"

private const val MILLIS_PER_SECOND = 1_000.0

/**
 * The latest session-trace snapshot as the `Sessions` fact: every active session in the watch's order, its playback
 * state, its track number, and which one the watch selected. The card shows the selected session only when it
 * publishes a track and notification access holds. Names no track, only in-process track numbers. Pure so the wording
 * is pinned by JVM tests.
 */
internal fun sessionsFact(latest: SessionSnapshot?): DiagnosticFact =
    DiagnosticFact("Sessions", FactValue.Text(latest?.let(::snapshotText) ?: NO_SNAPSHOT_YET))

/**
 * The recorded session-trace snapshots as `Session history` facts, oldest first, each labelled with its age at
 * [nowElapsedRealtimeMs]. One fact per snapshot, the form the crash history uses for its list, keeps each snapshot on
 * one report bullet and one screen row. Pure so the wording is pinned by JVM tests.
 */
internal fun sessionHistoryFacts(
    snapshots: List<SessionSnapshot>,
    nowElapsedRealtimeMs: Long,
): List<DiagnosticFact> =
    snapshots
        .map { snapshot ->
            DiagnosticFact(
                "Session history ${ageLabel(nowElapsedRealtimeMs - snapshot.elapsedRealtimeMs)}",
                FactValue.Text(snapshotText(snapshot)),
            )
        }.ifEmpty { listOf(DiagnosticFact("Session history", FactValue.Text(NO_SNAPSHOT_YET))) }

/**
 * The recorded card entries as `Card history` facts, oldest first, each labelled with its age at [nowElapsedRealtimeMs]:
 * what the card finally showed, which the session snapshots cannot say (the card can show the remembered track's resume
 * offer, nothing, or the permission prompt instead of the selected session). Names no track, only the track numbers the
 * session history uses. Pure so the wording is pinned by JVM tests.
 */
internal fun cardHistoryFacts(
    entries: List<CardEntry>,
    nowElapsedRealtimeMs: Long,
): List<DiagnosticFact> =
    entries
        .map { entry ->
            DiagnosticFact(
                "Card history ${ageLabel(nowElapsedRealtimeMs - entry.elapsedRealtimeMs)}",
                FactValue.Text(cardEntryText(entry)),
            )
        }.ifEmpty { listOf(DiagnosticFact("Card history", FactValue.Text(NO_SNAPSHOT_YET))) }

// One entry on one line, e.g. "resume offered for com.example.music, track 3".
private fun cardEntryText(entry: CardEntry): String =
    listOfNotNull(
        when (entry.kind) {
            CardKind.PLAYING -> "playing ${entry.packageName}"
            CardKind.RESUME_OFFERED -> "resume offered for ${entry.packageName}"
            CardKind.NOTHING_PLAYING -> "nothing playing"
            CardKind.NO_ACCESS -> "no notification access"
        },
        entry.track?.let { "track $it" },
    ).joinToString(", ")

/** Return the platform name of a [PlaybackState] code, `no state` for none, and `state <n>` for an unknown code. */
internal fun playbackStateName(state: Int?): String =
    when (state) {
        null -> "no state"
        PlaybackState.STATE_NONE -> "NONE"
        PlaybackState.STATE_STOPPED -> "STOPPED"
        PlaybackState.STATE_PAUSED -> "PAUSED"
        PlaybackState.STATE_PLAYING -> "PLAYING"
        PlaybackState.STATE_FAST_FORWARDING -> "FAST_FORWARDING"
        PlaybackState.STATE_REWINDING -> "REWINDING"
        PlaybackState.STATE_BUFFERING -> "BUFFERING"
        PlaybackState.STATE_ERROR -> "ERROR"
        PlaybackState.STATE_CONNECTING -> "CONNECTING"
        PlaybackState.STATE_SKIPPING_TO_PREVIOUS -> "SKIPPING_TO_PREVIOUS"
        PlaybackState.STATE_SKIPPING_TO_NEXT -> "SKIPPING_TO_NEXT"
        PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM -> "SKIPPING_TO_QUEUE_ITEM"
        else -> "state $state"
    }

// One snapshot on one line, e.g. "#1 com.example.music PLAYING, track 7, on
// the card; #2 com.example.radio PAUSED, track 2".
private fun snapshotText(snapshot: SessionSnapshot): String =
    snapshot.sessions
        .joinToString("; ") { session ->
            listOfNotNull(
                "#${session.session} ${session.packageName} ${playbackStateName(session.state)}",
                session.track?.let { "track $it" } ?: "no track",
                "on the card".takeIf { session.session == snapshot.cardSession },
            ).joinToString(", ")
        }.ifEmpty { "none" }

// Locale.ROOT keeps the decimal point a point on every device locale, as the
// report's stable wording requires.
private fun ageLabel(ageMs: Long): String =
    String.format(Locale.ROOT, "-%.3f s", ageMs.coerceAtLeast(0L) / MILLIS_PER_SECOND)

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
 * Collects the MUSIC diagnostics section's one-shot half: the spectrum probe, the last resume attempt, the session
 * trace, and the audio output state. The ViewModel adds the live `Session` fact through [musicSectionWithSession].
 * [trace] is the one the dashboard's session watch writes; a test passes its own.
 */
internal class MusicFactsCollector(
    private val context: Context,
    private val spectrumRepository: AudioSpectrumRepository,
    private val trace: SessionTrace = SessionTrace.processWide,
) {
    suspend fun musicFacts(): SectionPayload.Facts {
        // The probe drives its own short-lived Visualizer with an internal
        // window timeout; run it before the plain AudioManager reads so its
        // errors precede the log tail (the registry awaits this ordering).
        val diagnosis = spectrumRepository.diagnose()
        return withContext(Dispatchers.IO) {
            val audioManager = context.getSystemService<AudioManager>()!!
            // One read, so the Sessions fact and the history's last entry agree.
            val snapshots = trace.snapshots()
            val now = SystemClock.elapsedRealtime()
            SectionPayload.Facts(
                spectrumFacts(diagnosis) +
                    listOf(
                        resumeFact(ResumeAttempts.latestOrNull()),
                        sessionsFact(snapshots.lastOrNull()),
                        outputsFact(audioManager),
                        volumeFact(audioManager),
                        propertiesFact(audioManager),
                    ) +
                    // Last: up to SESSION_TRACE_SNAPSHOTS rows each, which
                    // would push the audio facts far down the section.
                    sessionHistoryFacts(snapshots, now) +
                    cardHistoryFacts(trace.cardEntries(), now),
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
