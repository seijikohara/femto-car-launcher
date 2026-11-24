package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.music.LastPlayedKeeper
import io.github.seijikohara.femto.data.music.LastPlayedStore
import io.github.seijikohara.femto.data.music.LastPlayedTrack
import io.github.seijikohara.femto.data.music.MediaApps
import io.github.seijikohara.femto.data.music.MusicCardState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory [LastPlayedStore]: every write lands in a [MutableStateFlow] at once, and [saves] counts the save calls,
 * so a test sees how often the recorder asked to write.
 */
internal class FakeLastPlayedStore(
    initial: LastPlayedTrack? = null,
) : LastPlayedStore {
    private val state = MutableStateFlow(initial)
    override val track: Flow<LastPlayedTrack?> = state

    /** The remembered track as of now, for direct assertions. */
    val current: LastPlayedTrack? get() = state.value

    var saves = 0
        private set

    override suspend fun save(track: LastPlayedTrack) {
        saves++
        state.value = track
    }

    override suspend fun forget(packageName: String) {
        if (state.value?.packageName == packageName) state.value = null
    }

    override suspend fun clear() {
        state.value = null
    }
}

/**
 * [MediaApps] over a fixed set of installed apps: [launchable] maps each package with a launch intent to its name. A
 * package outside it reads as uninstalled.
 */
internal class FakeMediaApps(
    var launchable: Map<String, String> = mapOf(FAKE_MUSIC_PACKAGE to FAKE_MUSIC_APP_NAME),
) : MediaApps {
    override fun isLaunchable(packageName: String): Boolean = packageName in launchable

    override fun labelOrNull(packageName: String): String? = launchable[packageName]
}

/** The package and the app name of the fictional music app the music fixtures play from (see fakeNowPlaying). */
internal const val FAKE_MUSIC_PACKAGE = "com.example.music"
internal const val FAKE_MUSIC_APP_NAME = "Example Music"

/** The launcher's own package as the keeper tests name it. */
internal const val FAKE_OWN_PACKAGE = "com.example.launcher"

/** A remembered track with test defaults: the fixture track of fakeNowPlaying. */
internal fun fakeLastPlayedTrack(
    packageName: String = FAKE_MUSIC_PACKAGE,
    title: String = "Headlights",
    artist: String? = "The Static Drive",
): LastPlayedTrack = LastPlayedTrack(packageName = packageName, title = title, artist = artist)

/** The music card offering [track] from [FAKE_MUSIC_APP_NAME], idle or [resuming]. */
internal fun fakeLastPlayedState(
    track: LastPlayedTrack = fakeLastPlayedTrack(),
    resuming: Boolean = false,
): MusicCardState.LastPlayed =
    MusicCardState.LastPlayed(track = track, appName = FAKE_MUSIC_APP_NAME, resuming = resuming)

/** A [LastPlayedKeeper] over a fake store and fake apps, with the launcher's package [FAKE_OWN_PACKAGE]. */
internal fun fakeLastPlayedKeeper(
    store: FakeLastPlayedStore = FakeLastPlayedStore(),
    apps: FakeMediaApps = FakeMediaApps(),
): LastPlayedKeeper = LastPlayedKeeper(store = store, apps = apps, ownPackage = FAKE_OWN_PACKAGE)
