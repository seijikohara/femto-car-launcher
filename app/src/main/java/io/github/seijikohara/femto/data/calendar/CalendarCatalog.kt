package io.github.seijikohara.femto.data.calendar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/** Reads the set of device calendars the user could choose to show or hide. */
internal class CalendarCatalog(
    private val context: Context,
) {
    fun availableCalendarsFlow(): Flow<List<CalendarInfo>> =
        calendarChangeFlow(context)
            .onStart { emit(Unit) }
            .map { readCalendars() }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    private fun readCalendars(): List<CalendarInfo> {
        if (!hasPermission()) return emptyList()
        val projection =
            arrayOf(
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.CALENDAR_COLOR,
            )
        return context.contentResolver
            .query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                "${CalendarContract.Calendars.VISIBLE} = 1",
                null,
                "${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} ASC",
            )?.use { c ->
                buildList {
                    while (c.moveToNext()) {
                        add(
                            CalendarInfo(
                                id = c.getLong(0),
                                displayName = c.getString(1).orEmpty(),
                                accountName = c.getString(2).orEmpty(),
                                color = c.getInt(3),
                            ),
                        )
                    }
                }
            }.orEmpty()
    }
}
