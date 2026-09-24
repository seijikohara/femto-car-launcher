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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
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

    /** No result in this process yet; [lastCheckedAt] is the last recorded attempt, null if none. */
    data class Idle(
        val lastCheckedAt: Instant?,
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

    /** [file] matched the manifest's size and SHA-256; only such a file is ever installed. */
    data class Ready(
        val manifest: UpdateManifest,
        val file: File,
    ) : UpdateState

    /** Handed to the platform, which may be waiting for the user to confirm. */
    data class Installing(
        val manifest: UpdateManifest,
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

/**
 * App-scoped updater: checks this channel's release feed, downloads and
 * verifies the offered APK, and hands it to the platform installer.
 *
 * A singleton so the Settings section, the dock badge and the installer's
 * status callbacks share one state and one in-flight check or download
 * (mirrors `FontRepository`). Construction counts as the process start: it
 * reconciles a pending install and restores a verified download before any
 * check may claim the state. Each action claims its starting state by
 * compare-and-set, so concurrent callers never double-launch. The constructor
 * stays injectable for JVM tests; production wiring goes through [get].
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
    // The updater's own directory: the staged download and nothing else.
    private val cacheDir: File,
    // Hashing and file IO run here; tests inject their scheduler's dispatcher.
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val stagedApk = File(cacheDir, STAGED_APK)
    private val stagedManifest = File(cacheDir, STAGED_MANIFEST)

    private val _state =
        MutableStateFlow<UpdateState>(if (enabled) UpdateState.Idle(lastCheckedAt = null) else UpdateState.Disabled)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val _updatedTo = MutableStateFlow<String?>(null)

    /**
     * The running version when this process succeeds an install the updater
     * handed to the platform, until [acknowledgeUpdatedTo]; null otherwise. Set
     * at most once: the reconciliation that sets it also clears the record.
     */
    val updatedTo: StateFlow<String?> = _updatedTo.asStateFlow()

    // The automatic check's due-read, claim and attempt record form one step,
    // so the next tick or an online edge sees the attempt instead of racing it
    // into a second request.
    private val autoCheckGate = Mutex()

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
            val (from, attemptAt) =
                autoCheckGate.withLock {
                    val settings = store.settings.first()
                    if (settings.autoCheck && isDue(settings.lastCheckAttemptAt)) {
                        claimCheck()?.let { claimed -> claimed to recordAttempt() }
                    } else {
                        null
                    }
                } ?: return@launch
            publishCheck(from, attemptAt, feed.latest(channel), quiet = true)
        }
    }

    /**
     * Download and verify the offered APK, or retry after a failure that still
     * names its offer. Only ever at the user's request: the ~45 MB may ride a
     * phone hotspot.
     */
    fun download() {
        val from = _state.value
        val manifest =
            when (from) {
                is UpdateState.Available -> from.manifest
                is UpdateState.Failed -> from.manifest
                else -> null
            } ?: return
        if (!_state.compareAndSet(from, UpdateState.Downloading(manifest, fraction = 0f))) return
        // No other action leaves Downloading, so this claim's outcome is written plainly.
        scope.launch { _state.value = fetchVerified(manifest) }
    }

    /** Hand the verified APK to the platform installer, which asks the user to confirm. */
    fun install() {
        val ready = _state.value as? UpdateState.Ready ?: return
        val installing = UpdateState.Installing(ready.manifest)
        if (!_state.compareAndSet(ready, installing)) return
        scope.launch {
            // Recorded before the platform takes the file: a successful install
            // kills this process, so the next start can only recognise it by
            // comparing its own versionCode with this record.
            store.setPendingInstallVersionCode(ready.manifest.versionCode)
            if (!handOff(ready)) {
                store.setPendingInstallVersionCode(null)
                _state.compareAndSet(installing, UpdateState.Failed(UpdateFailure.OTHER, ready.manifest))
            }
        }
    }

    /** The user dismissed the platform's confirmation: offer the same verified file again. */
    fun onInstallCancelled() = settleInstall { UpdateState.Ready(it, stagedApk) }

    /**
     * The platform refused the install for [reason]. The staged file stays, so
     * a retry once the cause is gone needs no second download.
     */
    fun onInstallFailed(reason: UpdateFailure) = settleInstall { UpdateState.Failed(reason, it) }

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
        if (pending != null && currentVersionCode >= pending) {
            store.setPendingInstallVersionCode(null)
            _updatedTo.value = currentVersionName
        }
        _state.value = withContext(ioDispatcher) { restoreStaged() }
            ?: UpdateState.Idle(settings.lastCheckAttemptAt?.let(Instant::ofEpochMilli))
    }

    // A verified download outlives the process — an AI box cold-boots every
    // drive — so it is offered again rather than fetched anew. Whatever no
    // longer describes a newer, intact build of this channel is deleted: that
    // includes the APK of an install that has since landed.
    private fun restoreStaged(): UpdateState.Ready? {
        val manifest =
            stagedManifestOrNull()?.takeIf {
                it.isUsableFor(channel) && it.versionCode > currentVersionCode && stagedApk.matches(it)
            }
        if (manifest == null) clearStaged()
        return manifest?.let { UpdateState.Ready(it, stagedApk) }
    }

    // A check starts only from a resting state. The compare-and-set makes
    // concurrent callers race for one claim, so a double tap or a tick landing
    // mid-check never sends a second request.
    private fun claimCheck(): UpdateState? {
        val from = _state.value
        val resting =
            from is UpdateState.Idle ||
                from == UpdateState.UpToDate ||
                from is UpdateState.Available ||
                from is UpdateState.Failed
        return from.takeIf { resting && _state.compareAndSet(from, UpdateState.Checking) }
    }

    // Recorded before the request leaves, so a check that fails still counts
    // against the daily gate.
    private suspend fun recordAttempt(): Instant =
        clock.instant().also { store.setLastCheckAttemptAt(it.toEpochMilli()) }

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

                result is FeedResult.Unavailable -> UpdateState.Failed(result.reason, manifest = null)

                // No information is not a failure: nothing to offer, nothing broke.
                else -> UpdateState.Idle(attemptAt)
            }
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
        // install — needs no second transfer.
        if (withContext(ioDispatcher) { isStaged(manifest) }) return UpdateState.Ready(manifest, stagedApk)
        withContext(ioDispatcher) { clearStaged() }
        val fetched =
            feed.download(manifest.apk.url, stagedApk, manifest.apk.size) { fraction ->
                reportProgress(manifest, fraction)
            }
        return if (fetched) {
            withContext(ioDispatcher) { verifyStaged(manifest) }
        } else {
            UpdateState.Failed(UpdateFailure.NETWORK, manifest)
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

    private suspend fun handOff(ready: UpdateState.Ready): Boolean =
        runCatching { installer.install(ready.file, ready.manifest) }
            .onFailure {
                if (it is CancellationException) throw it
                Log.w(TAG, "install hand-off failed", it)
            }.getOrDefault(false)

    // The platform's verdict ends the attempt, so the pending record — kept only
    // to recognise the process that succeeds a successful install — goes too.
    private fun settleInstall(next: (UpdateManifest) -> UpdateState) {
        val installing = _state.value as? UpdateState.Installing ?: return
        if (_state.compareAndSet(installing, next(installing.manifest))) {
            scope.launch { store.setPendingInstallVersionCode(null) }
        }
    }

    private fun isStaged(manifest: UpdateManifest): Boolean =
        stagedManifestOrNull() == manifest && stagedApk.matches(manifest)

    private fun stagedManifestOrNull(): UpdateManifest? =
        stagedManifest.takeIf { it.isFile }?.let { file ->
            runCatching { parseUpdateManifest(file.readText()) }
                .onFailure { Log.w(TAG, "staged manifest unreadable", it) }
                .getOrNull()
        }

    // Written only once the APK verified, so its presence vouches for the file
    // beside it. A failed write costs nothing now: the download is just not
    // restored after a restart.
    private fun writeStagedManifest(manifest: UpdateManifest) {
        runCatching { stagedManifest.writeText(manifest.toJson()) }
            .onFailure { Log.w(TAG, "staging the manifest failed", it) }
    }

    // Everything under [cacheDir] belongs to the staged download, a transfer's
    // leftover part file included.
    private fun clearStaged() {
        cacheDir.deleteRecursively()
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
                    store = UpdatePreferences(app),
                    // No platform installer is wired yet; declining makes Install
                    // fail visibly instead of hanging.
                    installer = ApkInstaller { _, _ -> false },
                    // Never read while disabled; any channel keeps the type non-null.
                    channel = channel ?: UpdateChannel.STABLE,
                    currentVersionCode = BuildConfig.VERSION_CODE,
                    currentVersionName = BuildConfig.VERSION_NAME,
                    enabled = enabled,
                    clock = Clock.systemUTC(),
                    scope = scope,
                    // The cache domain is excluded from backups, and a download
                    // the system reclaims is simply fetched again.
                    cacheDir = File(app.cacheDir, "update"),
                )
            if (enabled) {
                // "Due?" is re-evaluated on every clock tick and on the
                // offline -> online edge; onlineFlow seeds its current value, so
                // the first evaluation runs right after start.
                combine(SystemStatusRepository(app).onlineFlow(), ClockRepository(app).tickFlow()) { online, _ ->
                    online
                }.onEach { online -> repository.maybeAutoCheck(online) }
                    .launchIn(scope)
            }
            return repository
        }
    }
}

// Size first: a mismatch there settles it without reading ~45 MB. A file that
// cannot be read counts as a mismatch, so the gate fails closed.
private fun File.matches(manifest: UpdateManifest): Boolean =
    isFile && length() == manifest.apk.size && runCatching { sha256Hex() }.getOrNull() == manifest.apk.sha256

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
