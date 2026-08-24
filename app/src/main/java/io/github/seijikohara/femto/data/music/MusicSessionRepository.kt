package io.github.seijikohara.femto.data.music

import android.content.ComponentName
import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.Player
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import androidx.media3.session.MediaController as Media3Controller

private const val TAG = "MusicSessionRepo"

/**
 * The Secure setting listing every enabled notification-listener component,
 * which is where [NotificationManagerCompat.getEnabledListenerPackages] reads
 * the grant from. `internal` because the session tests grant it by writing the
 * same setting, and the name has to be the one place it is spelled.
 */
internal const val ENABLED_NOTIFICATION_LISTENERS = "enabled_notification_listeners"

internal class MusicSessionRepository(
    private val context: Context,
) {
    private val sessionManager: MediaSessionManager = checkNotNull(context.getSystemService())
    private val audioManager: AudioManager = checkNotNull(context.getSystemService())
    private val componentName = ComponentName(context, MusicSessionListenerService::class.java)

    // Source-app icons keyed by package. Resolved once per package: the icon is
    // stable for the process lifetime, while the session flow re-emits on every
    // playback tick. The icon-resolution map runs on the Default pool, where a
    // collector restart can land on a different thread mid-resolution, so all
    // access is synchronized (see sourceIconOf). A plain map under a monitor is
    // used instead of ConcurrentHashMap because a failed resolution stores null
    // and ConcurrentHashMap forbids null values.
    private val sourceIconCache = mutableMapOf<String, ImageBitmap?>()

    // media3 controller over the watched primary session's platform token.
    // Shuffle / repeat have no platform-API surface (verified via javap against
    // the API 37 android.jar) and the androidx.media compat layer is deprecated,
    // so media3 is the sanctioned route for their state, capability, and
    // setters. Everything here is Main-thread confined (the session flow and
    // send() both run on Main); `media3Generation` discards async results that
    // land after the watched session changed.
    private var media3Controller: Media3Controller? = null
    private var media3Future: ListenableFuture<Media3Controller>? = null
    private var media3PackageName: String? = null

    // The session the media3 controller is connected to, or connecting for.
    // Set when the connect starts, unlike media3PackageName (set on success),
    // because this one answers "is that session already covered?" for the
    // watch, which must not restart a connect that is still in flight.
    private var media3Token: MediaSession.Token? = null

    // Set when a media3 connect fails. The failure leaves media3Token pointing
    // at the session it was for, so retargetMedia3's "already covered" guard
    // keeps holding and a session that refuses is not re-attempted on every
    // playback tick — each attempt is a fresh asynchronous connect that has to
    // fail or time out on its own. This mark is what lets SessionWatch.rewatch
    // release the stale state on a membership change instead, the cadence the
    // retry ran at before the watch kept one controller per session.
    private var media3ConnectFailed = false
    private var media3Generation = 0

    private fun releaseMedia3() {
        media3Generation++
        media3Controller?.release()
        media3Controller = null
        media3Future?.let { runCatching { Media3Controller.releaseFuture(it) } }
        media3Future = null
        media3PackageName = null
        media3Token = null
        media3ConnectFailed = false
    }

    /**
     * Asynchronously connect a media3 controller to [controller]'s session and
     * invoke [onChanged] once connected — and again on every shuffle / repeat /
     * capability change — so the session flow re-emits with the fresh state.
     */
    private fun connectMedia3(
        controller: MediaController,
        onChanged: () -> Unit,
    ) {
        releaseMedia3()
        media3Token = controller.sessionToken
        val generation = media3Generation
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val tokenFuture = SessionToken.createSessionToken(context, controller.sessionToken)
        tokenFuture.addListener({
            if (generation != media3Generation) return@addListener
            val token =
                runCatching { tokenFuture.get() }
                    .onFailure {
                        Log.w(TAG, "media3 token failed for ${controller.packageName}", it)
                        media3ConnectFailed = true
                    }.getOrNull() ?: return@addListener
            val controllerFuture = Media3Controller.Builder(context, token).buildAsync()
            media3Future = controllerFuture
            controllerFuture.addListener({
                if (generation != media3Generation) {
                    // A rewatch happened while connecting; this controller belongs
                    // to a session we no longer track — release, don't leak.
                    runCatching { Media3Controller.releaseFuture(controllerFuture) }
                    return@addListener
                }
                val connected =
                    runCatching { controllerFuture.get() }
                        .onFailure {
                            Log.w(TAG, "media3 connect failed for ${controller.packageName}", it)
                            media3ConnectFailed = true
                        }.getOrNull() ?: return@addListener
                media3Controller = connected
                media3Future = null
                media3PackageName = controller.packageName
                connected.addListener(
                    object : Player.Listener {
                        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = onChanged()

                        override fun onRepeatModeChanged(repeatMode: Int) = onChanged()

                        override fun onAvailableCommandsChanged(availableCommands: Player.Commands) = onChanged()
                    },
                )
                onChanged()
            }, mainExecutor)
        }, mainExecutor)
    }

    fun stateFlow(): Flow<MusicCardState> =
        combine(permissionFlow(), activeControllersFlow()) { hasPermission, controllers ->
            musicCardStateOf(hasPermission) { controllers.toNowPlaying() }
        }.flowOn(Dispatchers.Main.immediate)
            // Resolve the source-app icon off Main: the PackageManager icon decode
            // is too heavy for the frame thread. The session logic stays on Main
            // (where MediaController callbacks arrive); the icon resolves on
            // Default, cached per package so it runs at most once per source.
            .map { it.withSourceIcon() }
            .flowOn(Dispatchers.Default)

    /**
     * Forward a transport command to the active media session. The
     * call is a no-op when no session is reachable — typically because
     * the user has not granted the notification-listener permission, in
     * which case the dashboard surfaces a "Connect music player" CTA
     * via [io.github.seijikohara.femto.ui.home.HomeAction.ConnectMusicPlayer].
     */
    fun send(command: MusicCommand) {
        val controller =
            runCatching {
                selectPrimaryController(sessionManager.getActiveSessions(componentName))
            }.onFailure { Log.w(TAG, "dropping $command: active-session query failed", it) }
                .getOrNull() ?: return
        val transport = controller.transportControls
        when (command) {
            MusicCommand.PlayPause -> {
                if (controller.playbackState.isPlaying()) {
                    transport.pause()
                } else {
                    transport.play()
                }
            }

            MusicCommand.SkipNext -> {
                transport.skipToNext()
            }

            MusicCommand.SkipPrevious -> {
                transport.skipToPrevious()
            }

            is MusicCommand.SeekTo -> {
                transport.seekTo(command.positionMs)
            }

            is MusicCommand.SkipToQueueItem -> {
                transport.skipToQueueItem(command.queueItemId)
            }

            MusicCommand.ToggleShuffle -> {
                // Toggle computed from the live controller state, mirroring the
                // PlayPause idiom (read current state at dispatch time). Guard on
                // the package like toNowPlaying does: the media3 controller trails
                // the platform session set, so in the window around a session
                // switch it may still belong to the previous app — better to drop
                // the tap than toggle the wrong session.
                media3Controller
                    ?.takeIf { media3PackageName == controller.packageName }
                    ?.let { it.setShuffleModeEnabled(!it.shuffleModeEnabled) }
            }

            MusicCommand.CycleRepeat -> {
                media3Controller
                    ?.takeIf { media3PackageName == controller.packageName }
                    ?.let { it.setRepeatMode(nextRepeatMode(repeatModeOf(it.repeatMode)).toPlayerMode()) }
            }
        }
    }

    /**
     * Best-effort resume of whatever session last held playback: a synthetic
     * KEYCODE_MEDIA_PLAY press routed through [AudioManager.dispatchMediaKeyEvent],
     * the same path a physical or Bluetooth media button uses. Many media apps
     * register a `MEDIA_BUTTON` receiver precisely so a key press resumes them
     * even while stopped or backgrounded — without the launcher targeting any
     * package by name, unlike [send] (which requires an already
     * playing/paused [MediaController] via [selectPrimaryController] and is a
     * no-op with [MusicCardState.NoActiveSession]). There is no callback
     * confirming a session actually resumed, so the caller (the music card's
     * empty-state Play tap, `HomeAction.PlayDefaultMusic`) also launches the
     * default music app as a visible fallback regardless of this call's outcome.
     */
    fun dispatchPlayMediaKey() {
        val eventTime = SystemClock.uptimeMillis()
        listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP).forEach { action ->
            audioManager.dispatchMediaKeyEvent(KeyEvent(eventTime, eventTime, action, KeyEvent.KEYCODE_MEDIA_PLAY, 0))
        }
    }

    /**
     * Emit `true` while the user has enabled the launcher's notification-listener
     * service in Settings, `false` otherwise. We watch the Secure setting via a
     * ContentObserver so the dashboard reacts to a permission grant returning
     * from `Settings → Notification Access` without an app restart.
     */
    private fun permissionFlow(): Flow<Boolean> =
        callbackFlow {
            val observer =
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        trySend(hasPermission())
                    }
                }
            val uri = Settings.Secure.getUriFor(ENABLED_NOTIFICATION_LISTENERS)
            context.contentResolver.registerContentObserver(uri, false, observer)
            trySend(hasPermission())
            awaitClose { context.contentResolver.unregisterContentObserver(observer) }
        }

    private fun activeControllersFlow(): Flow<List<MediaController>> =
        callbackFlow {
            val watch = SessionWatch { controllers -> trySend(controllers) }
            val sessionsListener =
                MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
                    watch.rewatch(controllers.orEmpty())
                }

            // The enumeration and the listener registration are guarded
            // separately: a throwing enumeration must not skip the
            // registration, or the subscription sees no session-set change for
            // as long as it lives. Both calls are gated on the same
            // notification-listener grant and both throw without it, so keeping
            // them apart only rescues an enumeration that failed for some other
            // reason.
            watch.reenumerate()
            runCatching { sessionManager.addOnActiveSessionsChangedListener(sessionsListener, componentName) }
                .onFailure { Log.w(TAG, "active-session listener registration failed", it) }

            awaitClose {
                watch.stop()
                runCatching { sessionManager.removeOnActiveSessionsChangedListener(sessionsListener) }
            }
        }

    /**
     * Keeps a [MediaController.Callback] on every active session and re-emits
     * the watched controllers on each live change.
     *
     * [MediaSessionManager.OnActiveSessionsChangedListener] only fires when the
     * SET of sessions changes, so on its own the flow is a one-shot snapshot:
     * progress, play/pause, and in-session metadata never update. Watching only
     * the selected controller was not enough either: an already-active session
     * whose app already holds the media button starts playing without the
     * platform pushing any session-set change, and while that session is
     * neither playing nor paused [selectPrimaryController] picks nothing to
     * watch at all — so the card sat on stale state until the flow was
     * collected again.
     *
     * Main-thread confined, like the flow that owns it and the media3 state it
     * drives.
     */
    private inner class SessionWatch(
        private val onChanged: (List<MediaController>) -> Unit,
    ) : MediaController.Callback() {
        // Keyed by session token, never by controller identity: getActiveSessions
        // mints a fresh MediaController per session on every call, so identity
        // keys would diff each enumeration as "unregister all, register all".
        private var watched: Map<MediaSession.Token, MediaController> = emptyMap()

        private fun controllers(): List<MediaController> = watched.values.toList()

        /**
         * Re-read the priority-ordered session list and reconcile the watch set
         * against it. A failed enumeration — typically a SecurityException
         * before the notification-listener grant — is logged so a silent "no
         * music" card stays diagnosable, and re-emits the sessions already
         * watched, which on the first pass is the empty list the card reads as
         * "nothing playing".
         */
        fun reenumerate() {
            runCatching { rewatch(sessionManager.getActiveSessions(componentName)) }
                .onFailure {
                    Log.w(TAG, "active-session enumeration failed; keeping the sessions already watched", it)
                    onChanged(controllers())
                }
        }

        /** Reconcile the watch set against a fresh enumeration, then emit. */
        fun rewatch(sessions: List<MediaController>) {
            val update = reconcileWatchSet(watched, sessions) { it.sessionToken }
            update.removed.forEach { gone -> runCatching { gone.unregisterCallback(this) } }
            update.added.forEach { controller ->
                // A refused registration freezes the card on stale metadata
                // while it looks healthy; leave a trail.
                runCatching { controller.registerCallback(this) }
                    .onFailure { Log.w(TAG, "registerCallback failed for ${controller.packageName}", it) }
            }
            watched = update.watched
            // A membership change is the one cadence worth retrying a failed
            // media3 connect at: releasing here clears the token the retarget
            // guard matches on, so the call below attempts the session again.
            // A playback tick reaches this function too (see
            // onPlaybackStateChanged), and it is far too frequent to retry on.
            if (media3ConnectFailed && (update.added.isNotEmpty() || update.removed.isNotEmpty())) {
                releaseMedia3()
            }
            retargetMedia3()
            onChanged(controllers())
        }

        /** Drop every registration and the media3 controller with them. */
        fun stop() {
            watched.values.forEach { controller -> runCatching { controller.unregisterCallback(this) } }
            watched = emptyMap()
            releaseMedia3()
        }

        /**
         * Point the media3 controller at the current primary session. Guarded
         * on the token so a playback tick on the session it already serves does
         * not tear it down and rebuild it asynchronously, which would blink the
         * shuffle / repeat affordances off and back on.
         */
        private fun retargetMedia3() {
            runCatching {
                selectPrimaryController(controllers()).let { primary ->
                    when {
                        primary?.sessionToken == media3Token -> Unit

                        // Shuffle / repeat state only surfaces through the media3
                        // controller; without it the panel's toggles would stay
                        // hidden (capability false) for a capable session.
                        primary != null -> connectMedia3(primary) { onChanged(controllers()) }

                        else -> releaseMedia3()
                    }
                }
                // media3 only carries the shuffle / repeat extras, and this runs
                // on platform callback dispatch as well as from rewatch. A throw
                // here must cost the extras, not more: escaping from the
                // subscription-time path degrades the whole music card for the
                // rest of the subscription epoch (HomeViewModel's catchAsDefault
                // completes the failed source until the next WhileUiSubscribed
                // restart), and escaping from callback dispatch is an uncaught
                // main-thread exception — a crash of the HOME app.
            }.onFailure { Log.w(TAG, "media3 retarget failed", it) }
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            if (state.isPlaying()) {
                // Re-read the priority order whenever a session reports
                // playing. getActiveSessions hands that order out only at call
                // time, and the platform pushes no session-set change when
                // playback starts inside a session it already lists — so
                // without this the launcher keeps ranking by the order of the
                // last enumeration, and a paused session that was first there
                // keeps the card while the one the user just resumed plays on
                // (issue #358).
                reenumerate()
            } else {
                // Anything else re-emits from the controllers already held,
                // rather than paying an enumeration on a callback that fires
                // for every pause, stop, and buffering blip. Which of those
                // controllers is primary can still have moved, which
                // retargetMedia3 follows on its own.
                retargetMedia3()
                onChanged(controllers())
            }
        }

        // Metadata and queue changes move neither the set nor the primary
        // (selection reads playback state alone), so they only re-emit.
        override fun onMetadataChanged(metadata: MediaMetadata?) = onChanged(controllers())

        override fun onQueueChanged(queue: MutableList<MediaSession.QueueItem>?) = onChanged(controllers())

        override fun onSessionDestroyed() =
            // The callback names no session, so the only way to learn which one
            // died is to ask again. The platform normally follows with an
            // OnActiveSessionsChangedListener push for the same destroy, which
            // reconciles to the same set and emits once more; paying that twice
            // beats holding a callback registration on a session that is gone.
            reenumerate()
    }

    private fun hasPermission(): Boolean =
        context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

    private fun List<MediaController>.toNowPlaying(): NowPlaying? =
        selectPrimaryController(this)
            ?.let { controller ->
                // A granted session can briefly expose no metadata (just connected,
                // or a metadata-less stream). Degrade to NoActiveSession upstream
                // rather than render a blank, broken-looking card.
                controller.metadata?.let { metadata ->
                    // The media3 controller trails the platform one (async
                    // connect); only trust it for the package it was built for.
                    val media3 = media3Controller?.takeIf { media3PackageName == controller.packageName }
                    nowPlayingOf(
                        metadata = metadata,
                        playbackState = controller.playbackState,
                        packageName = controller.packageName,
                        fallbackTitle = { sourceLabel(controller.packageName) },
                        canShuffle = media3?.isCommandAvailable(Player.COMMAND_SET_SHUFFLE_MODE) ?: false,
                        canRepeat = media3?.isCommandAvailable(Player.COMMAND_SET_REPEAT_MODE) ?: false,
                        shuffleOn = media3?.shuffleModeEnabled ?: false,
                        repeatMode = media3?.let { repeatModeOf(it.repeatMode) } ?: RepeatMode.NONE,
                        queue =
                            upcomingQueue(
                                entries = controller.queueEntries(),
                                activeQueueItemId = controller.playbackState?.activeQueueItemId ?: -1L,
                            ),
                    )
                }
            }

    private fun MediaController.queueEntries(): List<QueueEntry> =
        runCatching { queue.orEmpty() }
            .getOrDefault(emptyList())
            .map { item ->
                QueueEntry(
                    id = item.queueId,
                    title = item.description.title
                        ?.toString()
                        .orEmpty(),
                    subtitle = item.description.subtitle?.toString(),
                )
            }

    // Attach the source-app icon to a Playing state, leaving the other variants
    // untouched. Runs on Default (see stateFlow) so the icon decode is off Main.
    private fun MusicCardState.withSourceIcon(): MusicCardState =
        if (this is MusicCardState.Playing) {
            MusicCardState.Playing(nowPlaying.copy(sourceIcon = sourceIconOf(nowPlaying.packageName)))
        } else {
            this
        }

    /**
     * Resolve a source package's launcher icon, cached because it is stable for
     * the process lifetime and the session flow re-emits on every playback tick.
     * Returns null when the package has no resolvable icon (uninstalled /
     * restricted), in which case the card shows a generic launch glyph.
     */
    private fun sourceIconOf(packageName: String): ImageBitmap? =
        // The monitor spans the decode so two concurrent first lookups of the
        // same package resolve once; the decode is small (96 px) and runs at
        // most once per package, so the hold time is negligible.
        synchronized(sourceIconCache) {
            sourceIconCache.getOrPut(packageName) {
                runCatching {
                    context.packageManager
                        .getApplicationIcon(packageName)
                        .toBitmap(width = SOURCE_ICON_PIXELS, height = SOURCE_ICON_PIXELS)
                        .asImageBitmap()
                }.getOrNull()
            }
        }

    private companion object {
        // The card draws the source icon small (~28 dp); 96 px keeps it crisp on
        // high-density head units without decoding a full-size adaptive icon.
        const val SOURCE_ICON_PIXELS = 96

        /**
         * Pick the highest-priority controller that is playing or paused.
         * [MediaSessionManager.getActiveSessions] is priority-ordered, so the
         * first match is the session the user is most likely interacting with.
         */
        private fun selectPrimaryController(controllers: List<MediaController>): MediaController? =
            selectPrimarySession(controllers) { it.playbackState }
    }
}

/**
 * Return `true` when the state is PLAYING or PAUSED. A paused session is
 * still resumable and must stay on the card, so the plain [PlaybackState.isActive]
 * check (which excludes STATE_PAUSED) is not enough here.
 */
internal fun PlaybackState?.isPlayingOrPaused(): Boolean =
    this != null && (isActive() || state == PlaybackState.STATE_PAUSED)

/**
 * Return `true` when the state is PLAYING. The other half of the pair the card
 * reasons in, beside [isPlayingOrPaused]: this one answers "is playback running
 * right now", which decides the transport toggle, the Play / Pause affordance,
 * and whether a session push is worth re-reading the session priority order for.
 */
internal fun PlaybackState?.isPlaying(): Boolean = this?.state == PlaybackState.STATE_PLAYING

/**
 * Pick the first session whose playback state is playing or paused; the caller
 * passes sessions in priority order. Generic over the session type so the
 * selection policy is unit-testable with plain value holders instead of
 * [MediaController] instances, which cannot be constructed in JVM tests.
 */
internal fun <T> selectPrimarySession(
    sessions: List<T>,
    playbackStateOf: (T) -> PlaybackState?,
): T? = sessions.firstOrNull { playbackStateOf(it).isPlayingOrPaused() }

/**
 * The outcome of [reconcileWatchSet]: the sessions to keep watching, in
 * enumeration order, plus the registrations to add and drop to get there.
 */
internal data class WatchSetUpdate<K, T>(
    val watched: Map<K, T>,
    val added: List<T>,
    val removed: List<T>,
)

/**
 * Reconcile the watched sessions against a fresh enumeration, keyed by [keyOf]
 * instead of by instance identity: the platform mints a new [MediaController]
 * per session on every enumeration, so an identity-keyed diff reports the whole
 * set as replaced each pass. A session that survives keeps the instance that
 * already carries its callback registration.
 *
 * Every enumerated session is watched, not only the one the display policy
 * selects: [selectPrimarySession] skips sessions that are neither playing nor
 * paused, and one of those starting playback is exactly the change the card has
 * to notice.
 *
 * Generic over the session type so the policy is unit-testable with plain value
 * holders instead of [MediaController] instances, which cannot be constructed
 * in JVM tests.
 */
internal fun <K, T> reconcileWatchSet(
    watched: Map<K, T>,
    sessions: List<T>,
    keyOf: (T) -> K,
): WatchSetUpdate<K, T> =
    sessions.associateBy(keyOf).let { enumerated ->
        WatchSetUpdate(
            watched = enumerated.mapValues { (key, session) -> watched[key] ?: session },
            added = enumerated.filterKeys { it !in watched }.values.toList(),
            removed = watched.filterKeys { it !in enumerated }.values.toList(),
        )
    }

/**
 * Collapse the permission gate and the resolved session into the card state.
 * [nowPlaying] is a lambda so session inspection is skipped while the
 * notification-listener permission is missing.
 */
internal fun musicCardStateOf(
    hasPermission: Boolean,
    nowPlaying: () -> NowPlaying?,
): MusicCardState =
    if (hasPermission) {
        nowPlaying()?.let(MusicCardState::Playing) ?: MusicCardState.NoActiveSession
    } else {
        MusicCardState.NeedsPermission
    }

/**
 * Map one session's extracted fields to the card's [NowPlaying]. Operates on
 * plain values so the fallback branches are unit-testable without a
 * [MediaController]; [fallbackTitle] is a lambda so the source-label lookup
 * runs only when both metadata titles are blank. [canShuffle], [canRepeat],
 * [shuffleOn], [repeatMode], and [queue] pass through: shuffle/repeat state and
 * capability arrive as plain values from the media3 controller, which has no
 * platform getter.
 */
internal fun nowPlayingOf(
    metadata: MediaMetadata,
    playbackState: PlaybackState?,
    packageName: String,
    fallbackTitle: () -> String,
    canShuffle: Boolean = false,
    canRepeat: Boolean = false,
    shuffleOn: Boolean = false,
    repeatMode: RepeatMode = RepeatMode.NONE,
    queue: List<QueueEntry> = emptyList(),
): NowPlaying =
    (playbackState?.actions ?: 0L).let { actions ->
        NowPlaying(
            // METADATA_KEY_TITLE is empty for many podcast / radio / stream
            // sessions; fall back to the display title and finally the source
            // label so the 23sp title line is never blank.
            title =
                metadata.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }
                    ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)?.takeIf { it.isNotBlank() }
                    ?: fallbackTitle(),
            artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST),
            album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM),
            // Prefer METADATA_KEY_ART — the full-size artwork some apps publish
            // beside the album thumb — so the expanded panel gets the sharpest
            // bitmap available; the small card scales it down regardless.
            albumArt =
                (
                    metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
                        ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                )?.asImageBitmap(),
            // sourceIcon is resolved downstream off Main (see stateFlow).
            // A paused controller renders with isPlaying=false (Play icon,
            // resumable), but stays on screen via selectPrimaryController.
            isPlaying = playbackState.isPlaying(),
            positionMs = playbackState?.position ?: 0L,
            durationMs = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION),
            packageName = packageName,
            playbackSpeed = playbackState?.playbackSpeed ?: 1f,
            positionUpdateTimeMs = playbackState?.lastPositionUpdateTime ?: 0L,
            canSeek = (actions and PlaybackState.ACTION_SEEK_TO) != 0L,
            // Shuffle / repeat capability is a media3-controller fact (command
            // availability), not an action bit — supplied by the repository once
            // the async controller connects; false until then.
            canShuffle = canShuffle,
            canRepeat = canRepeat,
            canSkipToQueueItem = (actions and PlaybackState.ACTION_SKIP_TO_QUEUE_ITEM) != 0L,
            shuffleOn = shuffleOn,
            repeatMode = repeatMode,
            queue = queue,
        )
    }
