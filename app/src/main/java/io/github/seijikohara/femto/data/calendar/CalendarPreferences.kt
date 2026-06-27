package io.github.seijikohara.femto.data.calendar

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.seijikohara.femto.data.common.catchIoAsDefaults
import io.github.seijikohara.femto.data.common.editOrLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val TAG = "CalendarPreferences"

private val Context.calendarDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "calendar_preferences")

/** Persists which calendars the user has hidden from the dashboard agenda. */
internal interface CalendarPreferencesStore {
    /** Calendar IDs the user has hidden; empty means every visible calendar shows. */
    val hiddenCalendarIds: Flow<Set<Long>>

    suspend fun setCalendarHidden(
        id: Long,
        hidden: Boolean,
    )
}

internal class CalendarPreferences(
    private val context: Context,
) : CalendarPreferencesStore {
    override val hiddenCalendarIds: Flow<Set<Long>> =
        context.calendarDataStore.data
            .catchIoAsDefaults(TAG)
            .map { prefs ->
                // Stored as strings; drop any malformed entry defensively.
                prefs[HIDDEN_IDS_KEY].orEmpty().mapNotNull { it.toLongOrNull() }.toSet()
            }

    override suspend fun setCalendarHidden(
        id: Long,
        hidden: Boolean,
    ) {
        context.calendarDataStore.editOrLog(TAG) { prefs ->
            val current = prefs[HIDDEN_IDS_KEY].orEmpty().toMutableSet()
            if (hidden) current.add(id.toString()) else current.remove(id.toString())
            prefs[HIDDEN_IDS_KEY] = current
        }
    }

    private companion object {
        val HIDDEN_IDS_KEY = stringSetPreferencesKey("hidden_calendar_ids")
    }
}
