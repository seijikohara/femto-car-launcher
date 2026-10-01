@file:OptIn(ExperimentalCoroutinesApi::class) // flatMapLatest in autoCheckEvaluations.

package io.github.seijikohara.femto.data.update

import android.content.Context
import android.util.Log
import io.github.seijikohara.femto.BuildConfig
import io.github.seijikohara.femto.data.clock.ClockRepository
import io.github.seijikohara.femto.data.common.femtoUserAgent
import io.github.seijikohara.femto.data.location.LocationGraph
import io.github.seijikohara.femto.data.location.VehicleMotion
import io.github.seijikohara.femto.data.location.currentOrUnknown
import io.github.seijikohara.femto.data.system.SystemStatusRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "UpdateRepository"

// The automatic check runs at most once a day: the manifest read is cheap, but
// a launcher that phones home every tick would be one nobody asked for.
private const val AUTO_CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000

// The staged download: the APK and the manifest it was verified against, side
// by side so the system reclaims them together. Fixed names — the manifest's
// asset name is remote input and never becomes a path.
private const val STAGED_APK = "update.apk"
private const val STAGED_MANIFEST = "update.json"

// Two confirmations for one session must never stack. A tap on "show again"
// while the dialog is still coming up, a double tap on a head unit's touch
// panel, or a re-show after Home while the first dialog is still alive would
// stack them, and on Android 13 accepting both, or cancelling the stale one,
// destroys the session under the first. A re-show this soon after the same
// confirmation last went on screen is ignored; one that never went up (held
// while the vehicle moved) shows at once. Internal so tests probe both sides
// of it.
internal const val CONFIRMATION_RESHOW_GUARD_MS = 10_000L

/** Why the last update action failed, for the UI to phrase. */
internal enum class UpdateFailure {
    /** The feed or the download could not be reached. */
    NETWORK,

    /** The feed asked this client to wait (HTTP 403 / 429). */
    RATE_LIMITED,

    /**
     * The device could not store the update: a full disk or an unwritable
     * directory for the download, or too little space for the platform to
     * install it.
     */
    STORAGE,

    /** The downloaded file does not match the manifest's size or SHA-256. */
    VERIFY,

    /** The platform refused the APK as signed with a different key. */
    INSTALL_CONFLICT,

    /**
     * The device blocks the install: a policy, a verifier, or a system that
     * cannot show the install confirmation (its package installer disabled).
     */
    INSTALL_BLOCKED,

    /**
     * Android's developer verification blocked the install: the developer
     * could not be verified, or the verifier could not be reached.
     */
    DEVELOPER_VERIFICATION,

    /** Anything else, e.g. the platform could not take the file at all. */
    OTHER,
}

/** The updater's lifecycle, as the Settings section and the dock badge render it. */
internal sealed interface UpdateState {
    /** This build never checks: a local build, or a flavor without a feed. */
    data object Disabled : UpdateState

    /**
     * No result in this process yet. [lastAttemptAt] is the last recorded
     * check attempt — failed and uninformative ones included — or null if none.
     */
    data class Idle(
        val lastAttemptAt: Instant?,
    ) : UpdateState

    data object Checking : UpdateState

    data object UpToDate : UpdateState

    data class Available(
        val manifest: UpdateManifest,
    ) : UpdateState

    /** [fraction] runs 0..1 over the manifest's APK size. */
    data class Downloading(
        val manifest: UpdateManifest,
        val fraction: Float,
    ) : UpdateState

    /**
     * [file] matched the manifest's size and SHA-256 when it was downloaded.
     * The hash is checked again right before the hand-off, so only a matching
     * file is ever installed.
     */
    data class Ready(
        val manifest: UpdateManifest,
        val file: File,
    ) : UpdateState

    /**
     * Handed to the platform, which may be waiting for the user to confirm.
     * [sessionId] is the platform's install session once the file is staged
     * in one. [confirmation] is the platform's request for the user's
     * confirmation once it has arrived; [UpdateRepository.install] shows it
     * again, because a confirmation dismissed with Home sends no verdict, or
     * for the first time, when it arrived while the vehicle moved.
     */
    data class Installing(
        val manifest: UpdateManifest,
        val sessionId: Int? = null,
        val confirmation: InstallConfirmation? = null,
    ) : UpdateState

    /**
     * [manifest] is set when the failure concerns a known offer, which
     * [UpdateRepository.download] can retry; null means only a new check helps.
     * [staged] says the offer's verified download is still staged (an install
     * the platform refused keeps it), so a retry needs no transfer and
     * [UpdateRepository.discard] can delete it.
     */
    data class Failed(
        val reason: UpdateFailure,
        val manifest: UpdateManifest?,
        val staged: Boolean = false,
    ) : UpdateState
}

/** A claim that won: the state it was made from and the successor it published. */
private data class Claim<out T : UpdateState>(
    val from: UpdateState,
    val to: T,
)

/** The confirmation that last went on screen, and when (clock millis). */
private class ShownConfirmation(
    val confirmation: InstallConfirmation,
    val atMs: Long,
)

/**
 * App-scoped updater: checks this channel's release feed, downloads and
 * verifies the offered APK, and hands it to the platform installer.
 *
 * A singleton so the Settings section, the dock badge and the installer's
 * status callbacks share one state and one in-flight check or download
 * (mirrors `FontRepository`). Construction counts as the process start: it
 * reconciles a pending install and restores a staged download, or else the
 * offer the last check found, before any check may claim the state. Every
 * action claims its starting state through one compare-and-set, so
 * concurrent callers never double-launch. The constructor stays injectable
 * for JVM tests; production wiring goes through [get].
 */
internal class UpdateRepository internal constructor(
    private val feed: UpdateFeed,
    private val store: UpdateSettingsStore,
    private val installer: ApkInstaller,
    // The vehicle's motion, from the source the Settings install step reads
    // (LocationGraph.vehicleMotion); gates the install confirmation.
    private val motion: Flow<VehicleMotion>,
    private val channel: UpdateChannel,
    // The feed's releases page, the one [feed] reads; a manifest's APK must be
    // served from the same server (isUsableFor).
    private val feedBase: String,
    private val currentVersionCode: Int,
    private val currentVersionName: String,
    private val enabled: Boolean,
    private val clock: Clock,
    private val scope: CoroutineScope,
    // The updater's own directory, deleted wholesale whenever the staged
    // download goes: never one that other files share.
    private val stagingDir: File,
    // Hashing and file IO run here; tests inject their scheduler's dispatcher.
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : InstallVerdicts {
    private val stagedApk = File(stagingDir, STAGED_APK)
    private val stagedManifest = File(stagingDir, STAGED_MANIFEST)

    private val _state =
        MutableStateFlow<UpdateState>(if (enabled) UpdateState.Idle(lastAttemptAt = null) else UpdateState.Disabled)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val _updatedTo = MutableStateFlow<String?>(null)

    /**
     * The running version when this process succeeds an install the updater
     * handed to the platform, until [acknowledgeUpdatedTo]; null otherwise. Set
     * at most once: the reconciliation that sets it also clears the record.
     */
    val updatedTo: StateFlow<String?> = _updatedTo.asStateFlow()

    // The automatic check's due-read, claim and attempt record form one step,
    // so a concurrent evaluation reads the attempt instead of racing it into a
    // second request.
    private val autoCheckGate = Mutex()

    // The last attempt this process made. The gate needs it due as well as the
    // persisted one: a write the store lost (a full disk, a corrupted file)
    // would otherwise leave every tick due — a request a minute for the life of
    // the process.
    @Volatile
    private var lastAttemptAtMs: Long? = null

    // Set at start when an install of a newer build was pending but nothing of
    // its offer survived, neither the staged download nor the persisted offer:
    // the next automatic check runs at once instead of waiting out the day
    // since the check that found it.
    @Volatile
    private var recheckAtOnce = false

    // The persisted refusal (UpdateSettings.refusedVersionCode), read at
    // start and set on a conflict, so the checks that decide what is newer
    // read it without a store read each.
    @Volatile
    private var refusedVersionCode: Int? = null

    // The confirmation that last went on screen; see CONFIRMATION_RESHOW_GUARD_MS.
    // Compared and set as one step (see present), so two callers racing to
    // show one confirmation put up one dialog.
    private val lastShown = AtomicReference<ShownConfirmation?>(null)

    // Serialises the pending-install record's writes; see syncPendingRecord.
    private val pendingRecordLock = Mutex()

    // Held while a download is staged or a discarded one deleted, so a
    // download tapped right after a discard never finds the file the
    // discard is about to delete (see launchDroppingStaged).
    private val stagingLock = Mutex()

    // Declared last: the reconciliation may start on another thread before the
    // constructor returns, and it reads every field above. Checks join it, so
    // none can claim a state it is about to replace.
    private val started: Job = scope.launch { if (enabled) reconcile() }

    /** Check the feed at the user's request; the outcome, failures included, replaces [state]. */
    fun checkNow() {
        if (!enabled) return
        scope.launch {
            started.join()
            val from = claimCheck() ?: return@launch
            val attemptAt = recordAttempt()
            publishCheck(from, attemptAt, feed.latest(channel), quiet = false)
        }
    }

    /**
     * Run the daily check when it is due: automatic checks on, validated
     * internet, and 24 h since the last attempt. A clock behind that attempt
     * counts as due too — an AI box can boot on a wrong clock until NTP sets it,
     * and the check must not wait out the skew. A failed automatic check stays
     * quiet: whatever the user last saw stands until the next one.
     */
    fun maybeAutoCheck(online: Boolean) {
        if (!enabled || !online) return
        scope.launch {
            started.join()
            val (from, attemptAt) = autoCheckGate.withLock { claimAutoCheck() } ?: return@launch
            val result = feed.latest(channel)
            when {
                from is UpdateState.Ready -> offerBeyond(from, result)
                from.offerOrNull() != null -> publishBehindOffer(from, attemptAt, result)
                else -> publishCheck(from, attemptAt, result, quiet = true)
            }
        }
    }

    /**
     * Download and verify the offered APK, or retry after a failure that still
     * names its offer; a build the channel published since the check is
     * fetched instead (see fetchCurrent). Only ever at the user's request: the
     * ~45 MB may ride a phone hotspot.
     */
    fun download() {
        val downloading =
            claim { current -> current.offerOrNull()?.let { UpdateState.Downloading(it, fraction = 0f) } }?.to
                ?: return
        // No other action leaves Downloading, so this claim's outcome is written plainly.
        scope.launch { publish(fetchCurrent(downloading.manifest)) }
    }

    /**
     * Hand the verified APK to the platform installer, which asks the user to
     * confirm. While an install waits for that confirmation, this shows the
     * confirmation (again, or for the first time when it arrived while the
     * vehicle moved), though not within [CONFIRMATION_RESHOW_GUARD_MS] of the
     * last time it went up; once the platform no longer holds the session, it
     * offers the verified file again ([UpdateState.Ready]).
     */
    fun install() {
        val claimed = claim { current -> (current as? UpdateState.Ready)?.let { UpdateState.Installing(it.manifest) } }
        if (claimed != null) {
            scope.launch { handOff(claimed.to) }
        } else {
            (_state.value as? UpdateState.Installing)?.let { installing -> scope.launch { resume(installing) } }
        }
    }

    /**
     * The platform asks the user to confirm session [sessionId]. The
     * confirmation is kept for [install] to show again, and shown now unless a
     * fix shows the vehicle moving: a tap while stopped at a light stages the
     * update, and the car can pull away in the seconds before the platform
     * asks. A request for another session, one an earlier attempt left behind,
     * is ignored.
     */
    override fun onConfirmationRequested(
        sessionId: Int,
        confirmation: InstallConfirmation,
    ): Job? {
        val kept = claim { current -> current.installingOrNull(sessionId)?.copy(confirmation = confirmation) }
        return if (kept != null) {
            scope.launch { presentUnlessMoving(sessionId, confirmation) }
        } else {
            Log.w(TAG, "ignored a confirmation for session $sessionId")
            null
        }
    }

    /** The user declined session [sessionId]'s install, or it was abandoned: offer the same verified file again. */
    override fun onInstallCancelled(sessionId: Int): Job? =
        settleInstall(sessionId) { UpdateState.Ready(it, stagedApk) }

    /**
     * The platform refused session [sessionId]'s install for [reason]. The
     * staged file stays, so a retry once the cause is gone needs no second
     * download. [UpdateFailure.INSTALL_CONFLICT] is the exception: an APK
     * signed with another key never installs over this one, so the file and
     * its offer are deleted, the persisted one included, and its versionCode
     * is recorded as refused. Neither a retry, the next start nor a later
     * check offers that build, or an older one, again; only a newer build is
     * offered.
     */
    override fun onInstallFailed(
        sessionId: Int,
        reason: UpdateFailure,
    ): Job? =
        when (reason) {
            UpdateFailure.INSTALL_CONFLICT -> {
                settleInstall(sessionId, cleanUp = ::refuse) { UpdateState.Failed(reason, manifest = null) }
            }

            else -> {
                settleInstall(sessionId) { UpdateState.Failed(reason, it, staged = true) }
            }
        }

    /**
     * Delete the verified download that waits for the user (see
     * [discardableOrNull]) and return to the plain offer, which stays. Nothing
     * of the download is restored at the next start, and the pending-install
     * record goes with it. A download under way cannot be discarded.
     */
    fun discard() {
        val discarded = claim { current -> current.discardableOrNull()?.let { UpdateState.Available(it) } } ?: return
        launchDroppingStaged(discarded.to)
    }

    /**
     * Skip the offered build (see [skippableOrNull]): record its versionCode
     * as skipped, so it raises no dock dot and no dashboard prompt, and
     * discard a verified download of it. The offer stays, so Settings can
     * still install it on request.
     */
    fun skip() {
        val offer = _state.value.skippableOrNull() ?: return
        val discarded = claim { current ->
            current.discardableOrNull()?.takeIf { it == offer }?.let(UpdateState::Available)
        }
        // In the updater's scope, not the screen's: a DataStore write the
        // section cancels when it leaves records nothing.
        scope.launch { store.setSkippedVersionCode(offer.versionCode) }
        discarded?.let { launchDroppingStaged(it.to) }
    }

    /** The UI has shown [updatedTo]; stop reporting it. */
    fun acknowledgeUpdatedTo() {
        _updatedTo.value = null
    }

    // Once per process, before any check may claim (they join [started]). A
    // successful install kills the process that started it, so its outcome is
    // only visible from here: the running versionCode reached the pending one.
    private suspend fun reconcile() {
        val settings = store.settings.first()
        val pending = settings.pendingInstallVersionCode
        val installed = pending != null && currentVersionCode >= pending
        // A skip or a refusal of a build this one has reached has nothing left
        // to hold back.
        if (settings.skippedVersionCode?.let { it <= currentVersionCode } == true) store.setSkippedVersionCode(null)
        refusedVersionCode = settings.refusedVersionCode?.takeIf { it > currentVersionCode }
        if (settings.refusedVersionCode != null && refusedVersionCode == null) store.setRefusedVersionCode(null)
        if (installed) {
            // The state is not Installing, so the sync clears the record.
            syncPendingRecord()
            _updatedTo.value = currentVersionName
        }
        val staged = withContext(ioDispatcher) { restoreStaged() }
        // A verified download wins over a plain offer. The offer the last check
        // found wins over a staged manifest whose APK the system trimmed: it is
        // the later finding.
        val restored = staged as? UpdateState.Ready ?: restoreOffer(settings.offer) ?: staged
        if (pending != null && !installed && restored == null) recheckAtOnce = true
        _state.value = restored ?: UpdateState.Idle(settings.lastCheckAttemptAt?.let(Instant::ofEpochMilli))
    }

    // A staged download outlives the process — an AI box cold-boots every
    // drive. While its manifest still describes a newer build of this channel
    // the offer stands: Ready when the APK has the manifest's size (its hash is
    // checked at the hand-off, so a cold start never reads ~45 MB), Available
    // when the system trimmed the APK. Anything else staged is deleted, the APK
    // of an install that has since landed included.
    private fun restoreStaged(): UpdateState? {
        val manifest = stagedManifestOrNull()?.takeIf { it.isOffer() }
        return when {
            manifest == null -> {
                clearStaged()
                null
            }

            stagedApk.hasSizeOf(manifest) -> {
                UpdateState.Ready(manifest, stagedApk)
            }

            else -> {
                clearStagedApk()
                UpdateState.Available(manifest)
            }
        }
    }

    // The persisted offer (recordOffer) outlives the process the same way,
    // through the same filter. A record that fails it (the running build has
    // caught up, or this build cannot use it) is deleted.
    private suspend fun restoreOffer(offer: UpdateManifest?): UpdateState.Available? =
        when {
            offer == null -> {
                null
            }

            offer.isOffer() -> {
                UpdateState.Available(offer)
            }

            else -> {
                store.setOffer(null)
                null
            }
        }

    // Whether this build may offer [this]: usable for its channel, and newer
    // (isNewer). A staged manifest and a persisted offer both pass
    // through it at start.
    private fun UpdateManifest.isOffer(): Boolean = isUsableFor(this@UpdateRepository.channel, feedBase) && isNewer()

    // Newer than the running build, and than a build refused as signed with
    // another key: that build, or an older one, would only be refused again.
    private fun UpdateManifest.isNewer(): Boolean = versionCode > maxOf(currentVersionCode, refusedVersionCode ?: 0)

    // The one place a state is claimed. [next] maps the current state to the
    // claim's successor, or to null where the action does not apply; the
    // compare-and-set makes concurrent callers race for one claim, so at most
    // one of them proceeds.
    private inline fun <T : UpdateState> claim(next: (UpdateState) -> T?): Claim<T>? {
        val from = _state.value
        val to = next(from) ?: return null
        return Claim(from, to).takeIf { _state.compareAndSet(from, to) }
    }

    // A check starts only from a resting state.
    private fun claimCheck(): UpdateState? =
        claim { current ->
            UpdateState.Checking.takeIf { current.isResting() }
        }?.from

    // Runs under [autoCheckGate]. The in-memory attempt is checked first: it
    // costs nothing, spares the store a read on every tick that is not due, and
    // still holds when the persisted attempt was lost. Keep this order: the
    // test that pins the gate relies on it, and with the store read first the
    // in-memory check alone would mask a missing gate.
    private suspend fun claimAutoCheck(): Pair<UpdateState, Instant>? {
        if (!isDue(lastAttemptAtMs)) return null
        val settings = store.settings.first()
        if (!settings.autoCheck || !(recheckAtOnce || isDue(settings.lastCheckAttemptAt))) return null
        // Behind an offer (a verified download, an available build, or a
        // failure that still names one) the check runs without claiming the
        // state. The offer stays on screen, so the dock's dot and the step row
        // stay up while the request runs, which on a weak hotspot can take the
        // client's timeouts, and the user can still act on it meanwhile (see
        // offerBeyond and publishBehindOffer).
        val from =
            _state.value.takeIf { it is UpdateState.Ready || it.offerOrNull() != null } ?: claimCheck() ?: return null
        return from to recordAttempt()
    }

    // Recorded before the request leaves, so a check that fails still counts
    // against the daily gate — in memory as well as on disk (see lastAttemptAtMs).
    private suspend fun recordAttempt(): Instant =
        clock.instant().also { now ->
            lastAttemptAtMs = now.toEpochMilli()
            recheckAtOnce = false
            store.setLastCheckAttemptAt(now.toEpochMilli())
        }

    private fun isDue(lastAttemptAt: Long?): Boolean =
        lastAttemptAt == null ||
            (clock.millis() - lastAttemptAt).let { elapsed -> elapsed < 0 || elapsed >= AUTO_CHECK_INTERVAL_MS }

    // No other action leaves Checking, so the claimed check publishes plainly.
    private suspend fun publishCheck(
        from: UpdateState,
        attemptAt: Instant,
        result: FeedResult,
        quiet: Boolean,
    ) = publish(checkOutcome(from, attemptAt, result, quiet))

    // Writes [outcome] as the state and records its offer, for an action that
    // owns the state it replaces: a claimed check or a download, since no
    // other action leaves Checking or Downloading.
    private suspend fun publish(outcome: UpdateState) {
        _state.value = outcome
        recordOffer(outcome)
    }

    // A quiet check behind an offer claimed nothing, so its outcome replaces
    // the offer only while the user has left it as it was: a Download tap
    // meanwhile moved it on, and wins.
    private suspend fun publishBehindOffer(
        from: UpdateState,
        attemptAt: Instant,
        result: FeedResult,
    ) {
        val outcome = checkOutcome(from, attemptAt, result, quiet = true)
        claim { current -> outcome.takeIf { current == from } } ?: return
        recordOffer(outcome)
    }

    // What a check that read [result] leaves on screen, starting from [from];
    // [quiet] for an automatic check.
    private fun checkOutcome(
        from: UpdateState,
        attemptAt: Instant,
        result: FeedResult,
        quiet: Boolean,
    ): UpdateState =
        usableManifestOrNull(result).let { manifest ->
            when {
                manifest != null && manifest.isNewer() -> UpdateState.Available(manifest)

                manifest != null -> UpdateState.UpToDate

                // A background check that learnt nothing keeps what the user last
                // saw: an outage or the nightly republish window must not retract
                // an offer until the next day's check.
                quiet -> if (from is UpdateState.Idle) UpdateState.Idle(attemptAt) else from

                // A check that failed says nothing about an offer already known.
                result is FeedResult.Unavailable -> from.failedCheck(result.reason)

                // No information is not a failure: nothing to offer, nothing broke.
                else -> UpdateState.Idle(attemptAt)
            }
        }

    // The persisted offer (UpdateSettings.offer) mirrors the offer each settled
    // outcome leaves on screen, so a restart offers exactly what the user last
    // saw. It is written only here, from the outcome of a check, a download or
    // an install verdict. A newer build, or a failure that still names one,
    // sets it. An outcome that names none clears it: nothing newer, a check
    // that found no manifest, a download that failed verification, a build
    // refused as signed with another key. A state still on its way
    // (Downloading, Ready, Installing) leaves the record on the offer it acts
    // on. The one deletion elsewhere is at start: a record the running build
    // has caught up with, or that this build cannot use (restoreOffer).
    //
    // A newer offer also ends the skip of an older build: it may carry the
    // fix the user waited for, so it is offered as any other.
    private suspend fun recordOffer(outcome: UpdateState) {
        if (!outcome.isResting()) return
        val offer = outcome.offerOrNull()
        store.setOffer(offer)
        offer?.let { store.clearSkipBelow(it.versionCode) }
    }

    // A quiet check behind a verified download replaces it only with a strictly
    // newer build: the same build is already staged, and an outage or no
    // information says nothing new. The claim fails once the user has started
    // installing, which is left alone. The superseded file stays staged until
    // the newer download replaces it.
    private suspend fun offerBeyond(
        ready: UpdateState.Ready,
        result: FeedResult,
    ) {
        val newer = usableManifestOrNull(result)?.takeIf { it.versionCode > ready.manifest.versionCode } ?: return
        val offered = claim { current -> UpdateState.Available(newer).takeIf { current == ready } } ?: return
        recordOffer(offered.to)
    }

    // A manifest this build cannot act on — another channel, another schema,
    // no verifiable hash, an APK served from elsewhere — is no information,
    // the same as none at all.
    private fun usableManifestOrNull(result: FeedResult): UpdateManifest? =
        (result as? FeedResult.Found)?.manifest?.takeIf { manifest ->
            manifest.isUsableFor(channel, feedBase).also { usable ->
                if (!usable) Log.w(TAG, "ignored an unusable manifest: $manifest")
            }
        }

    // The channel's manifest is read once more right before the transfer. The
    // nightly republishes under one asset URL, so a build pushed since the
    // check would otherwise cost a full transfer that then fails verification.
    // The read is no check: the daily gate stays as it is. A build still newer
    // than the running one is the one fetched, and becomes the offer; one that
    // is not ends the offer, as a check would; no answer fetches the offered
    // build.
    private suspend fun fetchCurrent(offered: UpdateManifest): UpdateState {
        val found = usableManifestOrNull(feed.latest(channel))
        return when {
            found == null -> {
                fetchVerified(offered)
            }

            !found.isNewer() -> {
                UpdateState.UpToDate
            }

            else -> {
                if (found != offered) {
                    // Still Downloading, which no other action leaves.
                    _state.value = UpdateState.Downloading(found, fraction = 0f)
                    recordOffer(UpdateState.Available(found))
                }
                fetchVerified(found)
            }
        }
    }

    private suspend fun fetchVerified(manifest: UpdateManifest): UpdateState =
        stagingLock.withLock {
            // A copy already staged for this very manifest — kept after a refused
            // install — needs no second transfer; the hand-off checks its hash.
            if (withContext(ioDispatcher) { isStaged(manifest) }) return@withLock UpdateState.Ready(manifest, stagedApk)
            withContext(ioDispatcher) { clearStaged() }
            val result =
                feed.download(manifest.apk.url, stagedApk, manifest.apk.size) { fraction ->
                    reportProgress(manifest, fraction)
                }
            when (result) {
                DownloadResult.Saved -> withContext(ioDispatcher) { verifyStaged(manifest) }
                is DownloadResult.Failed -> UpdateState.Failed(result.reason, manifest)
            }
        }

    // The integrity gate, failing closed: only a file whose size and SHA-256
    // both equal the manifest's becomes Ready. A mismatch drops the offer too,
    // the persisted one included (recordOffer), so no restart brings it back —
    // the file was corrupted in transit, or the nightly release moved on in
    // the moments since the manifest was read (its APK URL never changes), and
    // only a new check can tell which.
    private fun verifyStaged(manifest: UpdateManifest): UpdateState =
        if (stagedApk.matches(manifest)) {
            writeStagedManifest(manifest)
            UpdateState.Ready(manifest, stagedApk)
        } else {
            Log.w(TAG, "download of ${manifest.versionName} does not match its manifest; deleted")
            clearStaged()
            UpdateState.Failed(UpdateFailure.VERIFY, manifest = null)
        }

    private fun reportProgress(
        manifest: UpdateManifest,
        fraction: Float,
    ) = _state.update { current ->
        if (current is UpdateState.Downloading && current.manifest == manifest) {
            current.copy(fraction = fraction)
        } else {
            current
        }
    }

    // The hash is checked here rather than at every cold start: hashing ~45 MB
    // belongs to this user-initiated step, and the file sat in a cache the
    // system may trim or damage since it was verified. A mismatch keeps the
    // offer — the manifest still describes the release; only the staged copy
    // went bad — so a retry downloads it afresh.
    private suspend fun handOff(installing: UpdateState.Installing) {
        val manifest = installing.manifest
        if (!withContext(ioDispatcher) { stagedApk.matches(manifest) }) {
            Log.w(TAG, "staged ${manifest.versionName} no longer matches its manifest; deleted")
            withContext(ioDispatcher) { clearStagedApk() }
            claim { current -> UpdateState.Failed(UpdateFailure.VERIFY, manifest).takeIf { current == installing } }
            return
        }
        // Recorded before the platform takes the file: a successful install
        // kills this process, so the next start can only recognise it by
        // comparing its own versionCode with this record.
        syncPendingRecord()
        val attempt = stageSession(installing)
        val committed =
            attempt?.sessionId?.let { id -> platformCall("committing session $id") { installer.commit(id) } } == true
        if (!committed) {
            // The verified file stays staged, so a retry needs no transfer.
            claim { current ->
                UpdateState.Failed(UpdateFailure.OTHER, manifest, staged = true).takeIf {
                    current ==
                        (attempt ?: installing)
                }
            }
            syncPendingRecord()
        }
    }

    // Stages the verified file in a new session and attaches the session to
    // the attempt before the commit, so every status the platform sends for it
    // finds this attempt. Nothing else moves an Installing that has no session
    // yet (a verdict needs the session, and a repeated install() leaves it
    // alone), so the attach is written plainly. Null when the platform took no
    // session.
    private suspend fun stageSession(installing: UpdateState.Installing): UpdateState.Installing? =
        platformCall("staging the update") { installer.stage(stagedApk) }
            ?.let { sessionId -> installing.copy(sessionId = sessionId).also { _state.value = it } }

    // A confirmation left unanswered (dismissed with Home, or held while the
    // vehicle moved) sends no verdict, and would hold Installing for the rest
    // of the process. The platform's session decides the way out: while the
    // platform still holds it, its confirmation is shown; once it is gone, the
    // verified file is offered again.
    private suspend fun resume(installing: UpdateState.Installing) {
        // No session yet: the hand-off is under way, and its own outcome follows.
        val sessionId = installing.sessionId ?: return
        if (platformCall("looking up session $sessionId") { installer.isPending(sessionId) } == true) {
            installing.confirmation?.let { present(sessionId, it) }
            return
        }
        claim { current -> UpdateState.Ready(installing.manifest, stagedApk).takeIf { current == installing } }
            ?: return
        syncPendingRecord()
    }

    // The installer fronts another process's API. A failure there fails this
    // step and is logged; it never escapes into the scope, where it would crash
    // the app.
    private suspend fun <T> platformCall(
        step: String,
        call: suspend () -> T,
    ): T? =
        runCatching { call() }
            .onFailure {
                if (it is CancellationException) throw it
                Log.w(TAG, "$step failed", it)
            }.getOrNull()

    // Only a fix that shows the vehicle moving holds the arriving confirmation
    // back: the Settings install step's own rule, read through the same
    // source and function (currentOrUnknown). A held confirmation is never put
    // on screen later on its own. "Parked" also means stopped at the next
    // light, where a dialog popping up could meet the car pulling away again;
    // the install step, which waits while the vehicle moves, shows it when the
    // user asks. The state may have moved on during the motion read, so only
    // the confirmation this session still holds is shown.
    private suspend fun presentUnlessMoving(
        sessionId: Int,
        confirmation: InstallConfirmation,
    ) {
        when {
            motion.currentOrUnknown() == VehicleMotion.MOVING -> {
                Log.i(TAG, "session $sessionId: confirmation held while the vehicle moves")
            }

            _state.value.installingOrNull(sessionId)?.confirmation === confirmation -> {
                present(sessionId, confirmation)
            }
        }
    }

    // Shows [confirmation] unless that same confirmation went on screen less
    // than CONFIRMATION_RESHOW_GUARD_MS ago; a clock set back since counts as
    // long ago, so a re-show is never held off for longer than the guard. The
    // mark is taken by one compare-and-set before the show, so of an arriving
    // confirmation's first show and a racing tap to show it again, one wins.
    private suspend fun present(
        sessionId: Int,
        confirmation: InstallConfirmation,
    ) {
        val now = clock.millis()
        val last = lastShown.get()
        val shownRecently =
            last != null &&
                last.confirmation === confirmation &&
                now - last.atMs in 0 until CONFIRMATION_RESHOW_GUARD_MS
        if (!shownRecently && lastShown.compareAndSet(last, ShownConfirmation(confirmation, now))) {
            show(sessionId, confirmation)
        }
    }

    // Puts session [sessionId]'s [confirmation] on screen. A confirmation the
    // platform cannot start (a locked-down ROM with its package installer
    // disabled, for one) would leave the session waiting until the platform
    // expires it days later, and the attempt waiting with it. So the attempt
    // fails as blocked, and the session is abandoned.
    // The failure's records are written before this returns, so a receiver
    // that waits for the confirmation waits for them too.
    private suspend fun show(
        sessionId: Int,
        confirmation: InstallConfirmation,
    ) {
        if (confirmation.show()) return
        settleInstall(sessionId, cleanUp = { abandon(sessionId) }) {
            UpdateState.Failed(UpdateFailure.INSTALL_BLOCKED, it, staged = true)
        }?.join()
    }

    private suspend fun abandon(sessionId: Int) {
        platformCall("abandoning session $sessionId") { installer.abandon(sessionId) }
    }

    // A verdict on this attempt's session (the platform's, or a confirmation
    // that could not be shown) ends the attempt. A verdict on any other session
    // settles nothing: the platform names the session in every status, and an
    // older session (one an earlier attempt left behind, abandoned when the next
    // one is staged) still reports. The offer and pending records follow after
    // [cleanUp], which does IO and so runs off the caller's thread (the status
    // receiver calls on the main thread).
    private fun settleInstall(
        sessionId: Int,
        cleanUp: suspend (UpdateManifest) -> Unit = {},
        next: (UpdateManifest) -> UpdateState,
    ): Job? {
        val settled = claim { current -> current.installingOrNull(sessionId)?.let { next(it.manifest) } } ?: return null
        val manifest = (settled.from as UpdateState.Installing).manifest
        return scope.launch {
            withContext(ioDispatcher) { cleanUp(manifest) }
            recordOffer(settled.to)
            syncPendingRecord()
        }
    }

    // Deletes the staged download after a discard published [offer], then
    // brings the offer and pending records in line with it. Started
    // undispatched, so the staging lock is taken before the caller returns: a
    // download tapped on the offer right after waits for the deletion instead
    // of finding the file about to go.
    private fun launchDroppingStaged(offer: UpdateState.Available) =
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            stagingLock.withLock { withContext(ioDispatcher) { clearStaged() } }
            recordOffer(offer)
            syncPendingRecord()
        }

    // A build signed with another key: its file goes, and its versionCode is
    // recorded, in memory first, so a check that starts meanwhile already
    // passes it over.
    private suspend fun refuse(manifest: UpdateManifest) {
        refusedVersionCode = maxOf(manifest.versionCode, refusedVersionCode ?: 0)
        clearStaged()
        store.setRefusedVersionCode(refusedVersionCode)
    }

    // The pending-install record follows the state: set while an install is in
    // the platform's hands, cleared once the attempt is over. Each write reads
    // the current state under one lock, so writes that run in another order
    // than their transitions still leave the record matching the latest state.
    private suspend fun syncPendingRecord() =
        pendingRecordLock.withLock {
            store.setPendingInstallVersionCode((_state.value as? UpdateState.Installing)?.manifest?.versionCode)
        }

    private fun isStaged(manifest: UpdateManifest): Boolean =
        stagedManifestOrNull() == manifest && stagedApk.hasSizeOf(manifest)

    private fun stagedManifestOrNull(): UpdateManifest? =
        stagedManifest.takeIf { it.isFile }?.let { file ->
            runCatching { parseUpdateManifest(file.readText()) }
                .onFailure { Log.w(TAG, "staged manifest unreadable", it) }
                .getOrNull()
        }

    // Written only once the APK verified: while the APK stays it vouches for
    // it, and if the system trims the APK it still carries the offer across a
    // restart. A failed write costs only that restore.
    private fun writeStagedManifest(manifest: UpdateManifest) {
        runCatching { stagedManifest.writeText(manifest.toJson()) }
            .onFailure { Log.w(TAG, "staging the manifest failed", it) }
    }

    // Everything under [stagingDir] belongs to the staged download, a
    // transfer's leftover part file included.
    private fun clearStaged() {
        stagingDir.deleteRecursively()
    }

    // The staged APK and any transfer leftover go; the manifest stays, so the
    // offer outlives the file.
    private fun clearStagedApk() {
        stagingDir.listFiles()?.filterNot { it == stagedManifest }?.forEach { it.deleteRecursively() }
    }

    companion object {
        @Volatile
        private var instance: UpdateRepository? = null

        fun get(context: Context): UpdateRepository =
            instance ?: synchronized(this) { instance ?: create(context).also { instance = it } }

        /**
         * [select] applied to the app's updater, which this resolves on first
         * collection, on a background thread. The first [get] starts the
         * daily check, and on the way it builds an HTTP client and opens the
         * store: work that has no place in `MainActivity#onCreate`, where cold
         * start is a key product metric. The dashboard's subscription is what
         * normally collects this first, once `onCreate` has returned.
         */
        fun <T> observe(
            context: Context,
            select: (UpdateRepository) -> Flow<T>,
        ): Flow<T> = flow { emitAll(select(get(context))) }.flowOn(Dispatchers.Default)

        private fun create(context: Context): UpdateRepository {
            val app = context.applicationContext
            val channel = UpdateChannel.fromFlavorOrNull(BuildConfig.FLAVOR)
            // A flavor outside the known channels has no feed of its own: it
            // never checks rather than read another channel's.
            val enabled = BuildConfig.UPDATE_CHECK_ENABLED && channel != null
            val store = UpdatePreferences(app)
            val feedBase = BuildConfig.UPDATE_FEED_BASE_URL
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val repository =
                UpdateRepository(
                    // Its own client, without the shared 5 MiB HTTP disk cache: a
                    // ~45 MB APK would only churn it.
                    feed =
                        UpdateFeedApi(
                            client = OkHttpClient(),
                            feedBase = feedBase,
                            userAgent = femtoUserAgent,
                        ),
                    store = store,
                    installer = PackageInstallerApkInstaller(PlatformInstallSessions(app)),
                    // Resolved on first read: building the location graph starts
                    // its track-log upkeep, which a process started by an install
                    // status broadcast has no use for until a confirmation arrives.
                    motion = flow { emitAll(LocationGraph.get(app).vehicleMotion()) },
                    // Never read while disabled; any channel keeps the type non-null.
                    channel = channel ?: UpdateChannel.STABLE,
                    feedBase = feedBase,
                    currentVersionCode = BuildConfig.VERSION_CODE,
                    currentVersionName = BuildConfig.VERSION_NAME,
                    enabled = enabled,
                    clock = Clock.systemUTC(),
                    scope = scope,
                    // The cache domain is excluded from backups, and a download
                    // the system reclaims is simply fetched again.
                    stagingDir = File(app.cacheDir, "update"),
                )
            if (enabled) {
                // onlineFlow seeds its current value, so the first evaluation
                // runs right after start.
                autoCheckEvaluations(
                    autoCheck = store.settings.map { it.autoCheck },
                    online = SystemStatusRepository(app).onlineFlow(),
                    ticks = ClockRepository(app).tickFlow(),
                ).onEach { online -> repository.maybeAutoCheck(online) }
                    .launchIn(scope)
            }
            return repository
        }
    }
}

/**
 * The evaluations behind [UpdateRepository.maybeAutoCheck]: the [online] state
 * on every clock tick and every online edge. They are collected only while
 * automatic checks are on, so turning the checks off also releases the clock
 * receiver and the network callback behind [ticks] and [online].
 *
 * A source that fails (the platform refusing the network callback, for one)
 * ends the evaluations for the life of the process, logged. The driver runs
 * in the updater's own scope, which has no exception handler, so an escape
 * would take down the HOME process; manual checks keep working without it.
 */
internal fun autoCheckEvaluations(
    autoCheck: Flow<Boolean>,
    online: Flow<Boolean>,
    ticks: Flow<*>,
): Flow<Boolean> =
    autoCheck
        .distinctUntilChanged()
        .flatMapLatest { on ->
            if (on) combine(online, ticks) { isOnline, _ -> isOnline } else emptyFlow()
        }.catch { e ->
            if (e is CancellationException) throw e
            Log.w(TAG, "automatic checks stopped", e)
        }

/**
 * Whether a check may start from this state ([UpdateRepository.checkNow]
 * ignores every other one). Internal so the Settings check row is tappable
 * exactly when a tap would do something.
 */
internal fun UpdateState.isResting(): Boolean =
    this is UpdateState.Idle ||
        this == UpdateState.UpToDate ||
        this is UpdateState.Available ||
        this is UpdateState.Failed

/**
 * The newer build this state names that is not installed yet: offered,
 * downloading, verified, handed to the installer, or failed while the offer is
 * still known; null otherwise. The dock's badge, the Updates entry's dot in
 * the Settings category list and the section's "Available version" row all
 * read this one rule, so they cannot disagree, and none blinks off between
 * steps.
 */
internal fun UpdateState.offeredManifestOrNull(): UpdateManifest? =
    when (this) {
        is UpdateState.Available -> manifest
        is UpdateState.Downloading -> manifest
        is UpdateState.Ready -> manifest
        is UpdateState.Installing -> manifest
        is UpdateState.Failed -> manifest
        UpdateState.Disabled, is UpdateState.Idle, UpdateState.Checking, UpdateState.UpToDate -> null
    }

/**
 * The build whose verified download is staged and waits for the user: one
 * ready to install, or one an install failure kept. Null otherwise, a
 * download under way and one in the installer's hands included. What
 * [UpdateRepository.discard] deletes, and when Settings offers to.
 */
internal fun UpdateState.discardableOrNull(): UpdateManifest? =
    when (this) {
        is UpdateState.Ready -> manifest
        is UpdateState.Failed -> manifest?.takeIf { staged }
        else -> null
    }

/**
 * The offered build waiting for the user's next step: available, verified, or
 * failed while still known. Null otherwise: a download under way, or an
 * install in the installer's hands, has a step of its own to finish first.
 * What [UpdateRepository.skip] skips, and when Settings offers to.
 */
internal fun UpdateState.skippableOrNull(): UpdateManifest? =
    when (this) {
        is UpdateState.Available -> manifest
        is UpdateState.Ready -> manifest
        is UpdateState.Failed -> manifest
        else -> null
    }

/**
 * Whether this state names a newer build that is not installed yet
 * ([offeredManifestOrNull]) and that the user has not skipped
 * ([UpdateSettings.skipped]): the dock's dot and the Updates entry's dot in the
 * Settings category list.
 */
internal fun UpdateState.offersUpdate(settings: UpdateSettings): Boolean =
    offeredManifestOrNull()?.let { !settings.skipped(it.versionCode) } == true

// The install attempt this state is, if it is the one on [sessionId].
private fun UpdateState.installingOrNull(sessionId: Int): UpdateState.Installing? =
    (this as? UpdateState.Installing)?.takeIf { it.sessionId == sessionId }

// A failed check from this state: the offer it knew stays, and so does the
// download a refused install kept for it.
private fun UpdateState.failedCheck(reason: UpdateFailure): UpdateState.Failed =
    UpdateState.Failed(reason, offerOrNull(), staged = (this as? UpdateState.Failed)?.staged == true)

// The offer a state carries: an available build, or one a failure still names.
private fun UpdateState.offerOrNull(): UpdateManifest? =
    when (this) {
        is UpdateState.Available -> manifest
        is UpdateState.Failed -> manifest
        else -> null
    }

private fun File.hasSizeOf(manifest: UpdateManifest): Boolean = isFile && length() == manifest.apk.size

// Size first: a mismatch there settles it without reading ~45 MB. A file that
// cannot be read counts as a mismatch, so the gate fails closed.
private fun File.matches(manifest: UpdateManifest): Boolean =
    hasSizeOf(manifest) && runCatching { sha256Hex() }.getOrNull() == manifest.apk.sha256

private fun File.sha256Hex(): String =
    MessageDigest
        .getInstance("SHA-256")
        .apply {
            inputStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                generateSequence { input.read(buffer).takeIf { it != -1 } }.forEach { update(buffer, 0, it) }
            }
        }.digest()
        .toHexString()
