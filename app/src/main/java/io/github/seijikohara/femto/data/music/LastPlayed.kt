package io.github.seijikohara.femto.data.music

import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadata
import android.media.session.PlaybackState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * The last track a media session played, kept so the music card can offer it again after the launcher restarts: the
 * app that played it, and the title and artist the session published. Personal data: it stays on the device
 * ([LastPlayedStore]), and nothing logs its title or artist.
 */
internal data class LastPlayedTrack(
    val packageName: String,
    val title: String,
    val artist: String?,
)

/**
 * Return the track a session plays, or null unless [playbackState] is PLAYING and [metadata] carries a title. The
 * title is the raw METADATA_KEY_TITLE, read before the card's fallbacks (the display title, then the source label):
 * a session that names no track has nothing to show again later. A blank artist reads as none.
 */
internal fun lastPlayedTrackOrNull(
    packageName: String,
    playbackState: PlaybackState?,
    metadata: MediaMetadata?,
): LastPlayedTrack? =
    metadata
        ?.takeIf { playbackState.isPlaying() }
        ?.getString(MediaMetadata.METADATA_KEY_TITLE)
        ?.takeIf { it.isNotBlank() }
        ?.let { title ->
            LastPlayedTrack(
                packageName = packageName,
                title = title,
                artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() },
            )
        }

/**
 * One pass of the session watch, before the remembered track joins it: whether notification access is granted, the
 * session the card shows, whether any session is playing or paused ([sessionLive], with metadata or without), and the
 * first session playing now as a track to remember. Without access no session was inspected, and the other fields
 * stay empty.
 */
internal data class SessionsReading(
    val hasPermission: Boolean,
    val nowPlaying: NowPlaying? = null,
    val sessionLive: Boolean = nowPlaying != null,
    val playing: LastPlayedTrack? = null,
)

/**
 * Add the remembered track to the card: [offer] shows only with notification access and only while no session is
 * playing or paused, with metadata or without. A live session therefore always takes the card from it, including the
 * one a resume attempt brings up; a session that is merely active (as binding an app's browser service creates) does
 * not.
 */
internal fun musicCardStateOf(
    reading: SessionsReading,
    offer: MusicCardState.LastPlayed?,
): MusicCardState =
    musicCardStateOf(reading.hasPermission) { reading.nowPlaying }.let { state ->
        offer?.takeIf { state == MusicCardState.NoActiveSession && !reading.sessionLive } ?: state
    }

/**
 * What the music card asks of the installed apps, behind a seam so the rules that use it run on the JVM.
 * [PackageManagerMediaApps] is the production implementation.
 */
internal interface MediaApps {
    /** Return whether [packageName] is installed and has a launch intent. */
    fun isLaunchable(packageName: String): Boolean

    /** Return the app's name as installed (its application label), or null when the package is gone. */
    fun labelOrNull(packageName: String): String?
}

/** [MediaApps] read from the [PackageManager]; a failed lookup reads as "not launchable" and "no name". */
internal class PackageManagerMediaApps(
    private val context: Context,
) : MediaApps {
    override fun isLaunchable(packageName: String): Boolean =
        runCatching { context.packageManager.getLaunchIntentForPackage(packageName) != null }.getOrDefault(false)

    override fun labelOrNull(packageName: String): String? =
        runCatching {
            context.packageManager.run {
                getApplicationLabel(
                    getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0)),
                ).toString()
            }
        }.getOrNull()
}

/**
 * Keeps the last played track: remembers each track a session plays, keeps it when the session goes away, and lets
 * it go once the launcher could no longer offer it. That is when notification access is revoked (the launcher can no
 * longer see or control the app), and when the app is uninstalled or loses its launch intent: found the next time the
 * dashboard is opened (see [remembered]), or when the play button is pressed (the resume attempt's APP_GONE). A track
 * of the launcher's own package ([ownPackage]) is never remembered.
 */
internal class LastPlayedKeeper(
    private val store: LastPlayedStore,
    private val apps: MediaApps,
    private val ownPackage: String,
) {
    /**
     * The remembered track while its app can still be opened, else null. The app is checked each time the store is
     * read, which is when the dashboard starts collecting the card and when the store changes; a track whose app is
     * gone or has no launch intent is then forgotten. An app uninstalled while the dashboard stays open is therefore
     * still offered until the dashboard is next opened or the play button is pressed.
     */
    val remembered: Flow<LastPlayedTrack?> =
        store.track
            .map { track -> track?.takeIf { stillLaunchable(it) } }
            .distinctUntilChanged()

    /** The [remembered] track as the card offers it, named after its app; null while there is none to offer. */
    val offer: Flow<MusicCardState.LastPlayed?> =
        remembered.map { track ->
            track?.let { kept -> apps.labelOrNull(kept.packageName)?.let { MusicCardState.LastPlayed(kept, it) } }
        }

    /** Act on one change of the session watch: forget the track without access, else remember the track playing. */
    suspend fun record(reading: SessionsReading) =
        when {
            !reading.hasPermission -> store.clear()
            reading.playing == null || reading.playing.packageName == ownPackage -> Unit
            apps.isLaunchable(reading.playing.packageName) -> store.save(reading.playing)
            else -> Unit
        }

    /** Forget the remembered track if the app [packageName] played it. */
    suspend fun forget(packageName: String) = store.forget(packageName)

    private suspend fun stillLaunchable(track: LastPlayedTrack): Boolean =
        apps.isLaunchable(track.packageName).also { launchable -> if (!launchable) store.forget(track.packageName) }

    companion object {
        /** Build the production keeper over the launcher's own store and package manager. */
        fun of(context: Context): LastPlayedKeeper =
            LastPlayedKeeper(
                store = LastPlayedPreferences(context),
                apps = PackageManagerMediaApps(context),
                ownPackage = context.packageName,
            )
    }
}

/**
 * Turn the session watch into the card's state with the last played track: [keeper] remembers what plays (see
 * [recordingLastPlayed]) and offers the remembered track while no session is live (see [musicCardStateOf]). The
 * combine waits for the store's first read before the first state, so a session that is live at the start never shows
 * the remembered track first.
 */
internal fun Flow<SessionsReading>.withLastPlayed(keeper: LastPlayedKeeper): Flow<MusicCardState> =
    recordingLastPlayed(keeper).combine(keeper.offer) { reading, offer -> musicCardStateOf(reading, offer) }

/**
 * Pass every reading through and hand [keeper] each change of access or of the track playing. A position tick
 * repeats both, so it reaches the keeper as nothing and writes nothing. The reading goes downstream first, so the card
 * never waits on the store's write.
 */
internal fun Flow<SessionsReading>.recordingLastPlayed(keeper: LastPlayedKeeper): Flow<SessionsReading> =
    flow {
        var heard: Pair<Boolean, LastPlayedTrack?>? = null
        collect { reading ->
            emit(reading)
            (reading.hasPermission to reading.playing)
                .takeIf { it != heard }
                ?.let { change ->
                    heard = change
                    keeper.record(reading)
                }
        }
    }
