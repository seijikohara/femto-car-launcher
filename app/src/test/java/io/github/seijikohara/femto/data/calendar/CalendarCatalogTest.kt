package io.github.seijikohara.femto.data.calendar

import android.Manifest
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.CalendarContract
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CalendarCatalogTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `lists visible calendars when permission granted`() =
        runTest {
            shadowOf(app).grantPermissions(Manifest.permission.READ_CALENDAR)
            Robolectric
                .buildContentProvider(VisibleCalendarsProvider::class.java)
                .create(CalendarContract.AUTHORITY)

            val catalog = CalendarCatalog(app)
            val calendars = catalog.availableCalendarsFlow().first()

            assertEquals(listOf(1L, 2L), calendars.map { it.id })
            assertEquals("Personal", calendars.first().displayName)
        }

    @Test
    fun `emits empty when permission denied`() =
        runTest {
            // READ_CALENDAR intentionally not granted: the list must be empty.
            val catalog = CalendarCatalog(app)
            assertEquals(emptyList(), catalog.availableCalendarsFlow().first())
        }

    /**
     * Fake Calendars provider returning two visible rows (id 1 "Personal", id 2 "Work")
     * ordered by CALENDAR_DISPLAY_NAME ASC, matching the query in [CalendarCatalog].
     */
    class VisibleCalendarsProvider : ContentProvider() {
        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor {
            val columns =
                projection ?: arrayOf(
                    CalendarContract.Calendars._ID,
                    CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                    CalendarContract.Calendars.ACCOUNT_NAME,
                    CalendarContract.Calendars.CALENDAR_COLOR,
                )
            val cursor = MatrixCursor(columns)
            ROWS.forEach { (id, displayName, accountName, color) ->
                cursor.addRow(
                    columns.map { column ->
                        when (column) {
                            CalendarContract.Calendars._ID -> id
                            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME -> displayName
                            CalendarContract.Calendars.ACCOUNT_NAME -> accountName
                            CalendarContract.Calendars.CALENDAR_COLOR -> color
                            else -> null
                        }
                    },
                )
            }
            return cursor
        }

        override fun getType(uri: Uri): String? = null

        override fun insert(
            uri: Uri,
            values: ContentValues?,
        ): Uri? = null

        override fun delete(
            uri: Uri,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int = 0

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int = 0

        companion object {
            data class Row(
                val id: Long,
                val displayName: String,
                val accountName: String,
                val color: Int,
            )

            // Ordered by displayName ASC to mirror the sort the catalog applies, so the
            // fake provider's insertion order matches what the catalog would return.
            val ROWS =
                listOf(
                    Row(1L, "Personal", "user@example.com", 0xFF0000.toInt()),
                    Row(2L, "Work", "work@example.com", 0x0000FF.toInt()),
                )
        }
    }
}
