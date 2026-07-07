package io.github.seijikohara.femto.data.apps

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.seijikohara.femto.data.common.catchIoAsDefaults
import io.github.seijikohara.femto.data.common.editOrLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Top-N cap for the drawer's "Recent" row (a short, glanceable strip, not a
 * second full app list). Also caps the persisted history itself: nothing past
 * the display cap is ever read back out, so keeping more on disk would only
 * be wasted writes.
 */
internal const val RECENT_APPS_MAX_COUNT = 8

/**
 * One tracked app launch. [component] is a flattened [android.content.ComponentName]
 * (matches the encoding [DrawerPreferences] already uses for pins).
 * [launchCount] costs nothing extra to carry alongside the timestamp and is
 * the natural input for a future "most used" view; today only
 * [lastLaunchAtMillis] drives the Recent row's ordering.
 */
internal data class RecentLaunch(
    val component: String,
    val lastLaunchAtMillis: Long,
    val launchCount: Int,
)

// A flattened ComponentName can never contain a newline or this field
// separator, so both are safe delimiters (mirrors DrawerPreferences.PIN_SEPARATOR).
private const val RECORD_SEPARATOR = "\n"
private const val FIELD_SEPARATOR = ""

/** Decode the persisted history string, dropping any record that fails to parse. */
internal fun parseRecentLaunches(raw: String?): List<RecentLaunch> =
    raw
        ?.split(RECORD_SEPARATOR)
        ?.filter { it.isNotEmpty() }
        ?.mapNotNull { it.toRecentLaunchOrNull() }
        .orEmpty()

private fun String.toRecentLaunchOrNull(): RecentLaunch? {
    val fields = split(FIELD_SEPARATOR)
    if (fields.size != 3) return null
    val timestamp = fields[1].toLongOrNull() ?: return null
    val count = fields[2].toIntOrNull() ?: return null
    return RecentLaunch(component = fields[0], lastLaunchAtMillis = timestamp, launchCount = count)
}

/** Encode the history back to the persisted string form [parseRecentLaunches] reads. */
internal fun List<RecentLaunch>.encode(): String =
    joinToString(RECORD_SEPARATOR) {
        "${it.component}$FIELD_SEPARATOR${it.lastLaunchAtMillis}$FIELD_SEPARATOR${it.launchCount}"
    }

/**
 * Return the history with a launch of [component] at [atMillis] applied: bump
 * the existing entry's timestamp and count, or append a new entry. The result
 * is sorted most-recent-first and trimmed to [RECENT_APPS_MAX_COUNT], so it is
 * always ready to persist or read back as-is.
 */
internal fun List<RecentLaunch>.withRecordedLaunch(
    component: String,
    atMillis: Long,
): List<RecentLaunch> {
    val previousCount = find { it.component == component }?.launchCount ?: 0
    val updated = RecentLaunch(component = component, lastLaunchAtMillis = atMillis, launchCount = previousCount + 1)
    return (filterNot { it.component == component } + updated)
        .sortedByDescending { it.lastLaunchAtMillis }
        .take(RECENT_APPS_MAX_COUNT)
}

/**
 * Persisted launch-history store backing the drawer's "Recent" row: which
 * component was launched, and when.
 */
internal interface RecentAppsStore {
    /** Most-recently-launched components first, capped to [RECENT_APPS_MAX_COUNT]. */
    val recentComponents: Flow<List<String>>

    /** Record a launch of [component], bumping it to the front of [recentComponents]. */
    suspend fun recordLaunch(
        component: String,
        atMillis: Long = System.currentTimeMillis(),
    )
}

private val Context.recentAppsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "recent_apps_preferences",
)

private const val TAG = "RecentAppsPreferences"

internal class RecentAppsPreferences(
    private val context: Context,
) : RecentAppsStore {
    override val recentComponents: Flow<List<String>> =
        context.recentAppsDataStore.data
            .catchIoAsDefaults(TAG)
            .map { prefs -> parseRecentLaunches(prefs[HISTORY_KEY]).map { it.component } }

    override suspend fun recordLaunch(
        component: String,
        atMillis: Long,
    ) {
        context.recentAppsDataStore.editOrLog(TAG) { prefs ->
            prefs[HISTORY_KEY] =
                parseRecentLaunches(prefs[HISTORY_KEY]).withRecordedLaunch(component, atMillis).encode()
        }
    }

    private companion object {
        val HISTORY_KEY = stringPreferencesKey("recent_app_launches")
    }
}
