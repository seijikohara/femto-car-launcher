package io.github.seijikohara.femto.ui.home.components.driving

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.calendar.EventItem
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

/**
 * How an event's [eventDate] relates to [today], for the driving-face
 * briefing label: today's event needs no day prefix, tomorrow reads as a
 * friendly "Tomorrow", and anything further out — or, defensively, a past
 * date that should never reach here from `nextUpcomingEventOrNull` — falls
 * back to its weekday name.
 */
internal enum class RelativeDay { TODAY, TOMORROW, OTHER }

/** Pure so the day classification is JVM-testable without Compose. */
internal fun relativeDayOf(
    eventDate: LocalDate,
    today: LocalDate,
): RelativeDay =
    when (eventDate) {
        today -> RelativeDay.TODAY
        today.plusDays(1) -> RelativeDay.TOMORROW
        else -> RelativeDay.OTHER
    }

/**
 * The driving-face briefing label for [event] occurring on [eventDate]: bare
 * "HH:mm title" for today (unchanged from the pre-scope behavior; all-day →
 * title alone), day-prefixed otherwise so a non-today event never reads as
 * an ambiguous bare time ("Tomorrow 09:00 …", "Sun 09:00 …"). 24-hour
 * notation for v1 — the driving face carries no 12/24h setting yet.
 */
@Composable
internal fun briefingLabel(
    event: EventItem,
    eventDate: LocalDate,
    today: LocalDate,
): String {
    // Read the platform Locale through LocalLocale rather than Locale.getDefault():
    // the latter does not read observable Compose state, so the weekday prefix would
    // not recompose if the user changes the system locale mid-session.
    val locale = LocalLocale.current.platformLocale
    return listOfNotNull(
        when (relativeDayOf(eventDate, today)) {
            RelativeDay.TODAY -> null
            RelativeDay.TOMORROW -> stringResource(R.string.briefing_tomorrow)
            RelativeDay.OTHER -> eventDate.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
        },
        event.time?.format(BriefingTimeFormatter),
        event.title,
    ).joinToString(" ")
}

private val BriefingTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
