package io.github.seijikohara.femto.data.calendar

import android.text.format.DateFormat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * The dashboard's two date labels, shared by the calendar agenda and the
 * dashboard header. Robolectric because the month label resolves its field
 * order through the platform's DateFormat skeleton API.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DateLabelsTest {
    @Test
    fun `month label follows the locale field order`() {
        // The label is produced from getBestDateTimePattern(locale, "yMMMM").
        // en places the month first; ja / ko place the year first. Asserting
        // against the same pattern-derived expectation (rather than a hardcoded
        // English string) proves the formatter is locale-aware without baking a
        // brittle literal per locale.
        val today = LocalDate.of(2026, 3, 30)
        listOf(Locale.ENGLISH, Locale.JAPANESE, Locale.KOREAN).forEach { locale ->
            val expected =
                today.format(
                    DateTimeFormatter.ofPattern(
                        DateFormat.getBestDateTimePattern(locale, "yMMMM"),
                        locale,
                    ),
                )
            assertEquals(expected, monthLabelOf(today, locale))
        }
        // The English and Japanese labels must differ: ja leads with the year, en
        // with the month name — a guard against the old hand-joined "Month Year".
        assertNotEquals(monthLabelOf(today, Locale.ENGLISH), monthLabelOf(today, Locale.JAPANESE))
    }

    @Test
    fun `weekday label is the locale's full weekday name`() {
        val friday = LocalDate.of(2026, 5, 1)
        assertEquals("Friday", weekdayLabelOf(friday, Locale.ENGLISH))
        assertEquals("金曜日", weekdayLabelOf(friday, Locale.JAPANESE))
    }
}
