package io.github.seijikohara.femto.data.calendar

import android.text.format.DateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// The dashboard's two date labels, shared by the calendar snapshot and the
// dashboard header so the header can show today's date from the clock alone
// (no calendar permission involved) with exactly the calendar's wording.

/** Return the locale's full weekday name for [date] ("Friday", "金曜日"). */
internal fun weekdayLabelOf(
    date: LocalDate,
    locale: Locale,
): String = date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)

/**
 * Format the "month year" label using the locale's preferred field order.
 * `getBestDateTimePattern` resolves the skeleton "yMMMM" to e.g. "MMMM y" for
 * en (March 2026) but a year-first pattern for ja / ko (2026年3月). A
 * hand-joined "Month Year" string would force English ordering on every locale.
 */
internal fun monthLabelOf(
    date: LocalDate,
    locale: Locale,
): String =
    date.format(
        DateTimeFormatter.ofPattern(
            DateFormat.getBestDateTimePattern(locale, "yMMMM"),
            locale,
        ),
    )
