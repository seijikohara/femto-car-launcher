package io.github.seijikohara.femto.ui.home.components

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
 * The dashboard header's date line. Robolectric because the pattern-bearing
 * forms resolve their field order through the platform's DateFormat skeleton
 * API.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DateLineTest {
    private val friday = LocalDate.of(2026, 5, 1)

    @Test
    fun `full and short forms follow the locale field order`() {
        // Asserting against the same skeleton-derived expectation (rather than a
        // hardcoded string per locale) proves the formatter is locale-aware
        // without baking a brittle literal for every locale.
        listOf(DateLineForm.FULL, DateLineForm.SHORT).forEach { form ->
            listOf(Locale.ENGLISH, Locale.JAPANESE, Locale.GERMAN).forEach { locale ->
                val expected =
                    friday.format(
                        DateTimeFormatter.ofPattern(
                            DateFormat.getBestDateTimePattern(locale, form.skeleton!!),
                            locale,
                        ),
                    )
                assertEquals(expected, dateLineOf(friday, locale, form))
            }
            // A guard against a hand-joined "Weekday, Month day": ja leads with the
            // month and day and trails the weekday, en does the opposite.
            assertNotEquals(dateLineOf(friday, Locale.ENGLISH, form), dateLineOf(friday, Locale.JAPANESE, form))
        }
    }

    @Test
    fun `english forms read as the header shows them`() {
        // The literal the header tests and the goldens rely on.
        assertEquals("Friday, May 1", dateLineOf(friday, Locale.US, DateLineForm.FULL))
        assertEquals("Fri, May 1", dateLineOf(friday, Locale.US, DateLineForm.SHORT))
    }

    @Test
    fun `weekday form is the locale's full weekday name`() {
        assertEquals("Friday", dateLineOf(friday, Locale.ENGLISH, DateLineForm.WEEKDAY))
        assertEquals("金曜日", dateLineOf(friday, Locale.JAPANESE, DateLineForm.WEEKDAY))
    }
}
