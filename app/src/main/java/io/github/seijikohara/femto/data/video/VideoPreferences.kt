package io.github.seijikohara.femto.data.video

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.seijikohara.femto.data.common.catchIoAsDefaults
import io.github.seijikohara.femto.data.common.editOrLog
import io.github.seijikohara.femto.data.common.setOrRemove
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val TAG = "VideoPreferences"

/**
 * The video window's settings (issue #390): whether the window shows on the
 * dashboard, whether its picture hides while the vehicle is not stopped, and
 * the file the user picked. The picture gate is on by default; turning it off
 * is the user's explicit choice, made after a warning.
 */
internal data class VideoSettings(
    val windowEnabled: Boolean,
    val hidePictureWhileDriving: Boolean,
    /** The picked document's content URI, or null before the first pick. */
    val sourceUri: String?,
) {
    companion object {
        val Default =
            VideoSettings(
                windowEnabled = false,
                hidePictureWhileDriving = true,
                sourceUri = null,
            )
    }
}

/**
 * Read/write surface for [VideoSettings]. [VideoPreferences] is the
 * DataStore-backed production implementation; tests substitute an in-memory
 * fake.
 */
internal interface VideoSettingsStore {
    val settings: Flow<VideoSettings>

    suspend fun setWindowEnabled(value: Boolean)

    suspend fun setHidePictureWhileDriving(value: Boolean)

    /** Record the picked document; null clears the record. */
    suspend fun setSourceUri(value: String?)

    /**
     * Restore both switches to their defaults. The picked file stays: it is a
     * choice of content rather than a setting, and its read grant is still held.
     */
    suspend fun resetToDefaults()
}

// Its own file, so the backup rules can exclude it alone: a content URI grant
// is tied to this install and does not survive a restore or a device move.
// Internal: the backup rules test checks the file this name gives.
internal const val VIDEO_STORE_NAME = "video_preferences"

// Internal (not private): VideoPreferencesTest clears the raw store between tests.
internal val Context.videoDataStore: DataStore<Preferences> by preferencesDataStore(name = VIDEO_STORE_NAME)

/** DataStore-backed accessor for [VideoSettings]. Modelled on `UpdatePreferences`. */
internal class VideoPreferences(
    private val context: Context,
) : VideoSettingsStore {
    override val settings: Flow<VideoSettings> =
        context.videoDataStore.data
            .catchIoAsDefaults(TAG)
            .map { prefs ->
                VideoSettings(
                    windowEnabled = prefs[WINDOW_ENABLED_KEY] ?: VideoSettings.Default.windowEnabled,
                    hidePictureWhileDriving =
                        prefs[HIDE_PICTURE_KEY] ?: VideoSettings.Default.hidePictureWhileDriving,
                    sourceUri = prefs[SOURCE_URI_KEY],
                )
            }

    override suspend fun setWindowEnabled(value: Boolean) {
        context.videoDataStore.editOrLog(TAG) { it[WINDOW_ENABLED_KEY] = value }
    }

    override suspend fun setHidePictureWhileDriving(value: Boolean) {
        context.videoDataStore.editOrLog(TAG) { it[HIDE_PICTURE_KEY] = value }
    }

    override suspend fun setSourceUri(value: String?) {
        context.videoDataStore.editOrLog(TAG) { it.setOrRemove(SOURCE_URI_KEY, value) }
    }

    override suspend fun resetToDefaults() {
        context.videoDataStore.editOrLog(TAG) { prefs ->
            prefs.remove(WINDOW_ENABLED_KEY)
            prefs.remove(HIDE_PICTURE_KEY)
        }
    }

    private companion object {
        val WINDOW_ENABLED_KEY = booleanPreferencesKey("video_window_enabled")
        val HIDE_PICTURE_KEY = booleanPreferencesKey("video_hide_picture_while_driving")
        val SOURCE_URI_KEY = stringPreferencesKey("video_source_uri")
    }
}
