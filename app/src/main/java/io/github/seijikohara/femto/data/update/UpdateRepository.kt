@file:OptIn(ExperimentalCoroutinesApi::class) // flatMapLatest in autoCheckEvaluations.

package io.github.seijikohara.femto.data.update

import android.content.Context
import android.util.Log
import io.github.seijikohara.femto.BuildConfig
import io.github.seijikohara.femto.data.clock.ClockRepository
import io.github.seijikohara.femto.data.common.femtoUserAgent
import io.github.seijikohara.femto.data.system.SystemStatusRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
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

    /** The device (a policy or a verifier) blocks the install. */
    INSTALL_BLOCKED,

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
     * again, because a confirmation dismissed with Home sends no verdict.
     */
    data class Installing(
        val manifest: UpdateManifest,
        val sessionId: Int? = null,
        val confirmation: InstallConfirmation? = null,
    ) : UpdateState

    /**
     * [manifest] is set when the failure concerns a known offer, which
     * [UpdateRepository.download] can retry; null means only a new check helps.
     */
    data class Failed(
        val reason: UpdateFailure,
        val manifest: UpdateManifest?,
    ) : UpdateState
}

/** A claim that won: the state it was made from and the successor it published. */
private data class Claim<out T : UpdateState>(
    val from: UpdateState,
    val to: T,
)

/**
 * App-scoped updater: checks this channel's release feed, downloads and
 * verifies the offered APK, and hands it to the platform installer.
 *
 * A singleton so the Settings section, the dock badge and the installer's
 * status callbacks share one state and one in-flight check or download
 * (mirrors `FontRepository`). Construction counts as the process start: it
 * reconciles a pending install and restores a staged download before any
 * check may claim the state. Every action claims its starting state through
 * one compare-and-set, so concurrent callers never double-launch. The
 * constructor stays injectable for JVM tests; production wiring goes through
 * [get].
 */
internal class UpdateRepository internal constructor(
    private val feed: UpdateFeed,
    private val store: UpdateSettingsStore,
    private val installer: ApkInstaller,
    private val channel: UpdateChannel,
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
    // its offer survived the cache: the next automatic check runs at once
    // instead of waiting out the day since the check that found it.
    @Volatile
    private var recheckAtOnce = false

    // Serialises the pending-install record's writes; see syncPendingRecord.
    private val pendingRecordLock = Mutex()

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
            when (from) {
                is UpdateState.Ready -> offerBeyond(from, result)
                else -> publishCheck(from, attemptAt, result, quiet = true)
            }
        }
    }

    /**
     * Download and verify the offered APK, or retry after a failure that still
     * names its offer. Only ever at the user's request: the ~45 MB may ride a
     * phone hotspot.
     */
    fun download() {
        val downloading =
            claim { current -> current.offerOrNull()?.let { UpdateState.Downloading(it, fraction = 0f) } }?.to
                ?: return
        // No other action leaves Downloading, so this claim's outcome is written plainly.
        scope.launch { _state.value = fetchVerified(downloading.manifest) }
    }

    /**
     * Hand the verified APK to the platform installer, which asks the user to
     * confirm. While an install waits for that confirmation, this shows the
     * confirmation again; once the platform no longer holds the session, it
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
     * Whether the user lets this app request installs ("Install unknown
     * apps"). Without that grant the platform stops [install] at a dialog of
     * its own, so the UI routes the user to the setting first.
     */
    fun canRequestInstalls(): Boolean = installer.canRequestInstalls()

    /**
     * The platform asks the user to confirm session [sessionId]. The
     * confirmation is shown and kept for [install] to show again. A request
     * for another session, one an earlier attempt left behind, is ignored.
     */
    override fun onConfirmationRequested(
        sessionId: Int,
        confirmation: InstallConfirmation,
    ) {
        val kept = claim { current -> current.installingOrNull(sessionId)?.copy(confirmation = confirmation) }
        if (kept != null) confirmation.show() else Log.w(TAG, "ignored a confirmation for session $sessionId")
    }

    /** The user declined session [sessionId]'s install, or it was abandoned: offer the same verified file again. */
    override fun onInstallCancelled(sessionId: Int) = settleInstall(sessionId) { UpdateState.Ready(it, stagedApk) }

    /**
     * The platform refused session [sessionId]'s install for [reason]. The
     * staged file stays, so a retry once the cause is gone needs no second
     * download. [UpdateFailure.INSTALL_CONFLICT] is the exception: an APK
     * signed with another key never installs over this one, so the file and
     * its offer are deleted. Neither a retry nor the next start offers that
     * APK again; only a new check can offer a newer build.
     */
    override fun onInstallFailed(
        sessionId: Int,
        reason: UpdateFailure,
    ) = when (reason) {
        UpdateFailure.INSTALL_CONFLICT -> {
            settleInstall(sessionId, cleanUp = ::clearStaged) { UpdateState.Failed(reason, manifest = null) }
        }

        else -> {
            settleInstall(sessionId) { UpdateState.Failed(reason, it) }
        }
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
        if (installed) {
            // The state is not Installing, so the sync clears the record.
            syncPendingRecord()
            _updatedTo.value = currentVersionName
        }
        val restored = withContext(ioDispatcher) { restoreStaged() }
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
        val manifest = stagedManifestOrNull()?.takeIf { it.isUsableFor(channel) && it.versionCode > currentVersionCode }
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
        // Behind a verified download the check runs without claiming the state:
        // the user can still install meanwhile, and only a strictly newer build
        // replaces the offer (see offerBeyond).
        val from = _state.value.takeIf { it is UpdateState.Ready } ?: claimCheck() ?: return null
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

    private fun publishCheck(
        from: UpdateState,
        attemptAt: Instant,
        result: FeedResult,
        quiet: Boolean,
    ) {
        val manifest = usableManifestOrNull(result)
        // No other action leaves Checking, so the claimed check publishes plainly.
        _state.value =
            when {
                manifest != null && manifest.versionCode > currentVersionCode -> UpdateState.Available(manifest)

                manifest != null -> UpdateState.UpToDate

                // A background check that learnt nothing keeps what the user last
                // saw: an outage or the nightly republish window must not retract
                // an offer until the next day's check.
                quiet -> if (from is UpdateState.Idle) UpdateState.Idle(attemptAt) else from

                // A check that failed says nothing about an offer already known.
                result is FeedResult.Unavailable -> UpdateState.Failed(result.reason, from.offerOrNull())

                // No information is not a failure: nothing to offer, nothing broke.
                else -> UpdateState.Idle(attemptAt)
            }
    }

    // A quiet check behind a verified download replaces it only with a strictly
    // newer build: the same build is already staged, and an outage or no
    // information says nothing new. The claim fails once the user has started
    // installing, which is left alone. The superseded file stays staged until
    // the newer download replaces it.
    private fun offerBeyond(
        ready: UpdateState.Ready,
        result: FeedResult,
    ) {
        val newer = usableManifestOrNull(result)?.takeIf { it.versionCode > ready.manifest.versionCode } ?: return
        claim { current -> UpdateState.Available(newer).takeIf { current == ready } }
    }

    // A manifest this build cannot act on — another channel, another schema,
    // no verifiable hash — is no information, the same as none at all.
    private fun usableManifestOrNull(result: FeedResult): UpdateManifest? =
        (result as? FeedResult.Found)?.manifest?.takeIf { manifest ->
            manifest.isUsableFor(channel).also { usable ->
                if (!usable) Log.w(TAG, "ignored an unusable manifest: $manifest")
            }
        }

    private suspend fun fetchVerified(manifest: UpdateManifest): UpdateState {
        // A copy already staged for this very manifest — kept after a refused
        // install — needs no second transfer; the hand-off checks its hash.
        if (withContext(ioDispatcher) { isStaged(manifest) }) return UpdateState.Ready(manifest, stagedApk)
        withContext(ioDispatcher) { clearStaged() }
        val result =
            feed.download(manifest.apk.url, stagedApk, manifest.apk.size) { fraction ->
                reportProgress(manifest, fraction)
            }
        return when (result) {
            DownloadResult.Saved -> withContext(ioDispatcher) { verifyStaged(manifest) }
            is DownloadResult.Failed -> UpdateState.Failed(result.reason, manifest)
        }
    }

    // The integrity gate, failing closed: only a file whose size and SHA-256
    // both equal the manifest's becomes Ready. A mismatch drops the manifest
    // too — the file was corrupted in transit, or the nightly release moved on
    // since the check (its APK URL never changes), and only a new check can
    // tell which.
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
            claim { current ->
                UpdateState.Failed(UpdateFailure.OTHER, manifest).takeIf { current == (attempt ?: installing) }
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

    // A confirmation left unanswered (dismissed with Home, for one) sends no
    // verdict, and would hold Installing for the rest of the process. The
    // platform's session decides the way out: while the platform still holds
    // it, its confirmation is shown again; once it is gone, the verified file
    // is offered again.
    private suspend fun resume(installing: UpdateState.Installing) {
        // No session yet: the hand-off is under way, and its own outcome follows.
        val sessionId = installing.sessionId ?: return
        if (platformCall("looking up session $sessionId") { installer.isPending(sessionId) } == true) {
            installing.confirmation?.show()
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

    // The platform's verdict on this attempt's session ends the attempt. A
    // verdict on any other session settles nothing: the platform names the
    // session in every status, and an older session (one an earlier attempt
    // left behind, abandoned when the next one is staged) still reports. The
    // pending record follows after [cleanUp], which is file IO and so runs off
    // the caller's thread (the status receiver calls on the main thread).
    private fun settleInstall(
        sessionId: Int,
        cleanUp: () -> Unit = {},
        next: (UpdateManifest) -> UpdateState,
    ) {
        claim { current -> current.installingOrNull(sessionId)?.let { next(it.manifest) } } ?: return
        scope.launch {
            withContext(ioDispatcher) { cleanUp() }
            syncPendingRecord()
        }
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

        private fun create(context: Context): UpdateRepository {
            val app = context.applicationContext
            val channel = UpdateChannel.fromFlavorOrNull(BuildConfig.FLAVOR)
            // A flavor outside the known channels has no feed of its own: it
            // never checks rather than read another channel's.
            val enabled = BuildConfig.UPDATE_CHECK_ENABLED && channel != null
            val store = UpdatePreferences(app)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val repository =
                UpdateRepository(
                    // Its own client, without the shared 5 MiB HTTP disk cache: a
                    // ~45 MB APK would only churn it.
                    feed =
                        UpdateFeedApi(
                            client = OkHttpClient(),
                            feedBase = BuildConfig.UPDATE_FEED_BASE_URL,
                            userAgent = femtoUserAgent,
                        ),
                    store = store,
                    installer = PackageInstallerApkInstaller(PlatformInstallSessions(app)),
                    // Never read while disabled; any channel keeps the type non-null.
                    channel = channel ?: UpdateChannel.STABLE,
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
 */
internal fun autoCheckEvaluations(
    autoCheck: Flow<Boolean>,
    online: Flow<Boolean>,
    ticks: Flow<*>,
): Flow<Boolean> =
    autoCheck.distinctUntilChanged().flatMapLatest { on ->
        if (on) combine(online, ticks) { isOnline, _ -> isOnline } else emptyFlow()
    }

private fun UpdateState.isResting(): Boolean =
    this is UpdateState.Idle ||
        this == UpdateState.UpToDate ||
        this is UpdateState.Available ||
        this is UpdateState.Failed

// The install attempt this state is, if it is the one on [sessionId].
private fun UpdateState.installingOrNull(sessionId: Int): UpdateState.Installing? =
    (this as? UpdateState.Installing)?.takeIf { it.sessionId == sessionId }

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
