package io.github.seijikohara.femto.data.music

import android.content.ComponentName
import android.content.Context
import android.database.ContentObserver
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
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

internal class MusicSessionRepository(
    private val context: Context,
) {
    private val sessionManager: MediaSessionManager = checkNotNull(context.getSystemService())
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
    private var media3Generation = 0

    private fun releaseMedia3() {
        media3Generation++
        media3Controller?.release()
        media3Controller = null
        media3Future?.let { runCatching { Media3Controller.releaseFuture(it) } }
        media3Future = null
        media3PackageName = null
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
        val generation = media3Generation
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val tokenFuture = SessionToken.createSessionToken(context, controller.sessionToken)
        tokenFuture.addListener({
            if (generation != media3Generation) return@addListener
            val token =
                runCatching { tokenFuture.get() }
                    .onFailure { Log.w(TAG, "media3 token failed for ${controller.packageName}", it) }
                    .getOrNull() ?: return@addListener
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
                        .onFailure { Log.w(TAG, "media3 connect failed for ${controller.packageName}", it) }
                        .getOrNull() ?: return@addListener
                media3Controller = connected
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
                if (controller.playbackState?.state == PlaybackState.STATE_PLAYING) {
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
            // The OnActiveSessionsChangedListener only fires when the SET of
            // sessions changes, so on its own the flow is a one-shot snapshot:
            // progress, play/pause, and in-session metadata never update. We
            // additionally register a MediaController.Callback on the primary
            // controller and re-send the latest controller list on every live
            // change, so the downstream combine recomputes toNowPlaying.
            var current: List<MediaController> = emptyList()
            var watched: MediaController? = null
            val watcher =
                object : MediaController.Callback() {
                    override fun onPlaybackStateChanged(state: PlaybackState?) {
                        trySend(current)
                    }

                    override fun onMetadataChanged(metadata: MediaMetadata?) {
                        trySend(current)
                    }

                    override fun onSessionDestroyed() {
                        trySend(current)
                    }

                    override fun onQueueChanged(queue: MutableList<MediaSession.QueueItem>?) {
                        trySend(current)
                    }
                }

            fun rewatch(controllers: List<MediaController>) {
                current = controllers
                val next = selectPrimaryController(controllers)
                // Re-register only when the active controller changes; unregister
                // the previous one first so we never leak a stale callback.
                if (next !== watched) {
                    runCatching { watched?.unregisterCallback(watcher) }
                    watched = next
                    // A refused registration freezes the card on stale metadata
                    // while it looks healthy; leave a trail.
                    runCatching { watched?.registerCallback(watcher) }
                        .onFailure { Log.w(TAG, "registerCallback failed for ${next?.packageName}", it) }
                    // Shuffle / repeat state only surfaces through the media3
                    // controller; without it the panel's toggles would stay
                    // hidden (capability false) for a capable session.
                    val nextController = next
                    if (nextController != null) {
                        connectMedia3(nextController) { trySend(current) }
                    } else {
                        releaseMedia3()
                    }
                }
            }

            val sessionsListener =
                MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
                    rewatch(controllers.orEmpty())
                    trySend(current)
                }

            runCatching {
                rewatch(sessionManager.getActiveSessions(componentName))
                trySend(current)
                sessionManager.addOnActiveSessionsChangedListener(sessionsListener, componentName)
            }.onFailure {
                // Typically a SecurityException before the notification-listener
                // grant; surface it so a silent "no music" card is diagnosable.
                Log.w(TAG, "active-session enumeration failed; emitting empty list", it)
                trySend(emptyList())
            }

            awaitClose {
                releaseMedia3()
                runCatching { watched?.unregisterCallback(watcher) }
                runCatching { sessionManager.removeOnActiveSessionsChangedListener(sessionsListener) }
            }
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
        const val ENABLED_NOTIFICATION_LISTENERS = "enabled_notification_listeners"

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
 * [shuffleOn], [repeatMode], and [queue] pass through session state and
 * capability that have no platform getter and must be read via the media3
 * controller upstream.
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
            isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING,
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
