package io.github.seijikohara.femto.data.common

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import java.io.IOException
import kotlin.enums.enumEntries

// Decode a stored enum name to [T], falling back to [fallback] for a missing or
// unrecognised value so the read never throws on a downgrade / renamed entry.
// Shared by every preferences store under data/.
internal inline fun <reified T : Enum<T>> String?.toEnumOr(fallback: T): T =
    this?.let { name -> enumEntries<T>().firstOrNull { it.name == name } } ?: fallback

// DataStore surfaces an unreadable prefs file as an IOException on the read
// flow. The launcher is the HOME app, so a corrupted file (e.g. after a power
// loss mid-write) must degrade to defaults instead of crash-looping every cold
// start. Shared by every preferences store under data/.
internal fun Flow<Preferences>.catchIoAsDefaults(tag: String): Flow<Preferences> =
    catch { e ->
        if (e is IOException) {
            Log.e(tag, "preferences read failed; falling back to defaults", e)
            emit(emptyPreferences())
        } else {
            throw e
        }
    }

// Preference writes are launched fire-and-forget, so an IOException thrown by
// edit() would otherwise escape the launching coroutine and kill the HOME
// process. Losing one write is acceptable; crashing the launcher is not.
// Cancellation is rethrown to keep structured concurrency intact.
internal suspend fun DataStore<Preferences>.editOrLog(
    tag: String,
    transform: suspend (MutablePreferences) -> Unit,
) {
    runCatching { edit(transform) }
        .onFailure { e ->
            if (e is CancellationException) throw e
            Log.e(tag, "preferences write failed", e)
        }
}
