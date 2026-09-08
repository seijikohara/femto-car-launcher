package io.github.seijikohara.femto.ui.home.components

import android.text.format.DateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// Today's date as the dashboard header (DashboardHeader) words it, produced from
// the clock alone (no calendar permission involved) in the locale's own field
// order. It lives beside the header, its only consumer: it left data/calendar
// when the calendar snapshot stopped carrying the date labels.

/**
 * The forms the header's date line can take, longest first. The header walks
 * them in this order and renders the first that fits its slot (see
 * `DashboardHeader`), so a narrow band degrades from "Friday, May 1" through
 * "Fri, May 1" to the bare weekday before the date folds away.
 */
internal enum class DateLineForm(
    // ICU skeleton for getBestDateTimePattern; null for the weekday-only form,
    // a plain display name rather than a pattern.
    internal val skeleton: String?,
) {
    // Weekday, month name, day of month: "Friday, May 1" / "5月1日金曜日".
    FULL("EEEEMMMMd"),

    // Abbreviated weekday and month: "Fri, May 1" / "5月1日(金)".
    SHORT("EEEMMMd"),

    // The full weekday name alone: "Friday" / "金曜日".
    WEEKDAY(null),
}

/**
 * Format [date] in [form] for [locale]. The pattern-bearing forms go through
 * `getBestDateTimePattern`, which resolves the skeleton to the locale's field
 * order — "EEEE, MMMM d" for en (Friday, May 1) but a day-first, suffixed
 * pattern for ja (5月1日金曜日). A hand-joined "Weekday, Month day" string
 * would force English ordering on every locale.
 */
internal fun dateLineOf(
    date: LocalDate,
    locale: Locale,
    form: DateLineForm,
): String =
    form.skeleton
        ?.let { skeleton ->
            date.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale))
        }
        ?: date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
