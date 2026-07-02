package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.calendar.CalendarSnapshot
import io.github.seijikohara.femto.data.calendar.DayCell
import io.github.seijikohara.femto.data.calendar.EventItem
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import io.github.seijikohara.femto.ui.theme.PreviewTextStress
import io.github.seijikohara.femto.ui.theme.calendarWeekday
import io.github.seijikohara.femto.ui.theme.cardMeta
import io.github.seijikohara.femto.ui.theme.eyebrow
import io.github.seijikohara.femto.ui.theme.sectionLabel
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Full-screen calendar panel: a multi-day agenda over the live map. Shows the
 * same day filter as the compact card (days with events + today) but with room
 * for every event, untruncated titles, and the panel-only end time + location.
 */
@Composable
internal fun CalendarPanel(
    snapshot: CalendarSnapshot,
    is24Hour: Boolean,
    onOpenExternal: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState = rememberHazeState(),
    glassConfig: GlassConfig = GlassConfig(),
) = MaximizePanel(
    title = stringResource(R.string.calendar_title),
    onClose = onClose,
    onOpenExternal = onOpenExternal,
    openExternalLabel = stringResource(R.string.calendar_open_app),
    modifier = modifier,
    hazeState = hazeState,
    glassConfig = glassConfig,
) {
    val visibleDays =
        remember(snapshot) {
            snapshot.days.filter { it.hasEvent || it.date == snapshot.today }
        }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(FemtoDimens.CardSectionGap),
    ) {
        visibleDays.forEach { day ->
            AgendaDay(day = day, isToday = day.date == snapshot.today, is24Hour = is24Hour)
        }
    }
}

// One day of the agenda: a date gutter + the day's events (untruncated title,
// time range, optional location). Today's gutter is tinted primary.
@Composable
private fun AgendaDay(
    day: DayCell,
    isToday: Boolean,
    is24Hour: Boolean,
    modifier: Modifier = Modifier,
) = Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(14.dp),
    verticalAlignment = Alignment.Top,
) {
    val accent = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Column(
        modifier = Modifier.width(44.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = day.weekdayLetter.take(3).uppercase(),
            style = MaterialTheme.typography.sectionLabel(11, 0.08f),
            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
        )
        Text(
            text = "${day.date.dayOfMonth}",
            style = MaterialTheme.typography.calendarWeekday(),
            color = accent,
            maxLines = 1,
        )
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (day.events.isEmpty()) {
            Text(
                text = stringResource(R.string.calendar_no_events),
                style = MaterialTheme.typography.cardMeta(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        } else {
            day.events.forEach { event -> AgendaEvent(event = event, is24Hour = is24Hour) }
        }
    }
}

@Composable
private fun AgendaEvent(
    event: EventItem,
    is24Hour: Boolean,
    modifier: Modifier = Modifier,
) = Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(2.dp),
) {
    Text(
        text = eventTimeRange(event, is24Hour),
        style = MaterialTheme.typography.eyebrow(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
    Text(
        text = event.title,
        // titleLarge (24sp) is above the 18sp floor; the panel has room to wrap
        // the whole title rather than ellipsize like the compact card.
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
    event.location?.takeUnless { it.isBlank() }?.let { location ->
        Text(
            text = location,
            style = MaterialTheme.typography.cardMeta(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val PanelTimeFormatter24: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val PanelTimeFormatter12: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

// "10:30 – 11:00", "10:30" (no end), or "All day".
@Composable
private fun eventTimeRange(
    event: EventItem,
    is24Hour: Boolean,
): String {
    val formatter = if (is24Hour) PanelTimeFormatter24 else PanelTimeFormatter12
    val start = event.time ?: return stringResource(R.string.calendar_all_day)
    return event.endTime?.let { "${start.format(formatter)} – ${it.format(formatter)}" }
        ?: start.format(formatter)
}

@PreviewLightDark
@PreviewTextStress
@androidx.compose.ui.tooling.preview.Preview(name = "Calendar panel · head unit", widthDp = 805, heightDp = 400)
@Composable
private fun CalendarPanelPreview() {
    FemtoTheme {
        CalendarPanel(
            snapshot =
                CalendarSnapshot(
                    today = LocalDate.of(2026, 5, 1),
                    weekday = "Friday",
                    monthLabel = "May 2026",
                    days =
                        listOf(
                            DayCell(
                                LocalDate.of(2026, 5, 1),
                                "Fri",
                                listOf(
                                    EventItem(
                                        LocalTime.of(10, 30),
                                        "Team standup",
                                        endTime = LocalTime.of(11, 0),
                                        location = "Room 4",
                                    ),
                                    EventItem(null, "Company holiday"),
                                ),
                            ),
                            DayCell(
                                LocalDate.of(2026, 5, 3),
                                "Sun",
                                listOf(
                                    EventItem(
                                        LocalTime.of(9, 0),
                                        "Brunch with the extended family",
                                        endTime = LocalTime.of(11, 0),
                                    ),
                                ),
                            ),
                        ),
                    hasCalendarAccess = true,
                ),
            is24Hour = true,
            onOpenExternal = {},
            onClose = {},
        )
    }
}
