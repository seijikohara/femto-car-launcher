package io.github.seijikohara.femto.data.music

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.seijikohara.femto.data.common.catchIoAsDefaults
import io.github.seijikohara.femto.data.common.editOrLog
import io.github.seijikohara.femto.data.common.setOrRemove
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private const val TAG = "LastPlayedPreferences"

/**
 * Where the music card's last played track is kept between runs. [LastPlayedPreferences] is the DataStore-backed
 * production implementation; tests substitute an in-memory fake.
 */
internal interface LastPlayedStore {
    /** The remembered track, or null when none is remembered. */
    val track: Flow<LastPlayedTrack?>

    /** Remember [track] in place of the one before; a track equal to the stored one changes nothing. */
    suspend fun save(track: LastPlayedTrack)

    /** Forget the remembered track if the app [packageName] played it; a track of another app stays. */
    suspend fun forget(packageName: String)

    /** Forget the remembered track, whichever app played it. */
    suspend fun clear()
}

/**
 * The DataStore file name. The backup and data-extraction rules exclude
 * `datastore/<name>.preferences_pb`, so a rename must move those rules too.
 */
internal const val LAST_PLAYED_STORE_NAME = "music_resume"

// Internal (not private): LastPlayedPreferencesTest clears the raw store between tests.
internal val Context.lastPlayedDataStore: DataStore<Preferences> by preferencesDataStore(name = LAST_PLAYED_STORE_NAME)

/**
 * DataStore-backed [LastPlayedStore]. A title and an artist are personal data: the file stays on the device, out of
 * backup and device transfer, and nothing here logs either.
 */
internal class LastPlayedPreferences(
    private val context: Context,
) : LastPlayedStore {
    override val track: Flow<LastPlayedTrack?> =
        context.lastPlayedDataStore.data
            .catchIoAsDefaults(TAG)
            .map { prefs -> prefs.trackOrNull() }
            .distinctUntilChanged()

    // The comparison keeps an unchanged track from rewriting the file: the
    // recorder hears the same track again after every pause and resume.
    override suspend fun save(track: LastPlayedTrack) {
        context.lastPlayedDataStore.editOrLog(TAG) { prefs ->
            prefs.takeIf { it.trackOrNull() != track }?.write(track)
        }
    }

    override suspend fun forget(packageName: String) {
        context.lastPlayedDataStore.editOrLog(TAG) { prefs ->
            prefs.takeIf { it[PACKAGE_KEY] == packageName }?.removeTrack()
        }
    }

    override suspend fun clear() {
        context.lastPlayedDataStore.editOrLog(TAG) { it.removeTrack() }
    }

    private companion object {
        val PACKAGE_KEY = stringPreferencesKey("last_played_package")
        val TITLE_KEY = stringPreferencesKey("last_played_title")
        val ARTIST_KEY = stringPreferencesKey("last_played_artist")

        // A track needs its app and its title; a file holding only one of them reads as none.
        fun Preferences.trackOrNull(): LastPlayedTrack? =
            this[PACKAGE_KEY]?.let { packageName ->
                this[TITLE_KEY]?.let { title -> LastPlayedTrack(packageName, title, this[ARTIST_KEY]) }
            }

        fun MutablePreferences.write(track: LastPlayedTrack) {
            this[PACKAGE_KEY] = track.packageName
            this[TITLE_KEY] = track.title
            setOrRemove(ARTIST_KEY, track.artist)
        }

        fun MutablePreferences.removeTrack() {
            remove(PACKAGE_KEY)
            remove(TITLE_KEY)
            remove(ARTIST_KEY)
        }
    }
}
