package io.github.seijikohara.femto.data.music

import android.content.Context
import android.media.MediaMetadata
import android.media.session.PlaybackState
import kotlinx.coroutines.flow.Flow
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
 * session the card shows, and the first session playing now as a track to remember. Without access no session was
 * inspected, and the other fields stay empty.
 */
internal data class SessionsReading(
    val hasPermission: Boolean,
    val nowPlaying: NowPlaying? = null,
    val playing: LastPlayedTrack? = null,
)

/**
 * What the music card asks of the installed apps, behind a seam so the rules that use it run on the JVM.
 * [PackageManagerMediaApps] is the production implementation.
 */
internal interface MediaApps {
    /** Return whether [packageName] is installed and has a launch intent. */
    fun isLaunchable(packageName: String): Boolean
}

/** [MediaApps] read from the [android.content.pm.PackageManager]; a failed lookup reads as "not launchable". */
internal class PackageManagerMediaApps(
    private val context: Context,
) : MediaApps {
    override fun isLaunchable(packageName: String): Boolean =
        runCatching { context.packageManager.getLaunchIntentForPackage(packageName) != null }.getOrDefault(false)
}

/**
 * Keeps the last played track: remembers each track a session plays, keeps it when the session goes away, and lets
 * it go once the launcher could no longer offer it. That is when notification access is revoked (the launcher can no
 * longer see or control the app), and when the app is uninstalled or loses its launch intent. A track of the
 * launcher's own package ([ownPackage]) is never remembered.
 */
internal class LastPlayedKeeper(
    private val store: LastPlayedStore,
    private val apps: MediaApps,
    private val ownPackage: String,
) {
    /**
     * The remembered track while its app can still be opened, else null. A track whose app is gone or has no launch
     * intent is forgotten on the way.
     */
    val remembered: Flow<LastPlayedTrack?> =
        store.track
            .map { track -> track?.takeIf { stillLaunchable(it) } }
            .distinctUntilChanged()

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
