package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Sun
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.CalendarSnapshot
import io.github.seijikohara.femto.data.DayCell
import io.github.seijikohara.femto.data.EventItem
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import io.github.seijikohara.femto.ui.theme.TabularFigures
import io.github.seijikohara.femto.ui.theme.bigNumber
import io.github.seijikohara.femto.ui.theme.sectionLabel
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Calendar card:
 *
 *  1. Head — big day number (primary tint) + weekday + month label, always today.
 *  2. Days — a scrollable vertical list of the coming days (today first), each row
 *     showing that day's full set of events. Days with no events are shown too,
 *     with a muted placeholder, so the list reads as a continuous agenda.
 *
 * Typography and spacing originated in the retired dashboard-v2 design mockup;
 * the dashboard's 18sp body-size floor is intentionally relaxed here so the
 * agenda fits the short head-unit info-pane card.
 */
@Composable
internal fun CalendarCard(
    snapshot: CalendarSnapshot?,
    is24Hour: Boolean,
    modifier: Modifier = Modifier,
) = Surface(
    modifier = modifier,
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.surfaceContainer,
) {
    when {
        // null is the loading frame: render nothing rather than a denial the
        // user has not earned yet.
        snapshot == null -> Unit

        // A non-null snapshot with access denied carries no real data, so show the
        // denial message instead of a hollow agenda.
        !snapshot.hasCalendarAccess -> CenteredHint(stringResource(R.string.calendar_permission_denied))

        // Access is granted but the provider query faulted: the empty agenda is
        // a read failure, not a free month, so say so rather than fake it.
        snapshot.queryFailed -> CenteredHint(stringResource(R.string.calendar_query_failed))

        else -> CalendarContent(snapshot, is24Hour)
    }
}

@Composable
private fun CalendarContent(
    snapshot: CalendarSnapshot,
    is24Hour: Boolean,
) = Column(
    modifier =
        Modifier
            .fillMaxSize()
            // Tighter than the shared card padding/gap: the head-unit info-pane
            // card is short, so pack the head and the list to avoid a clip.
            .padding(FemtoDimens.CardPaddingCompact),
    verticalArrangement = Arrangement.spacedBy(FemtoDimens.CardSectionGapCompact),
) {
    Head(snapshot)
    LazyColumn(
        modifier = Modifier.fillMaxWidth().weight(1f),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items = snapshot.days, key = { it.date.toString() }) { day ->
            DayRow(day = day, isToday = day.date == snapshot.today, is24Hour = is24Hour)
        }
    }
}

@Composable
private fun Head(snapshot: CalendarSnapshot) =
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "${snapshot.today.dayOfMonth}",
            style = MaterialTheme.typography.bigNumber(),
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = snapshot.weekday,
                style =
                    MaterialTheme.typography.titleLarge.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.01f).em,
                        lineHeight = 20.sp,
                    ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = snapshot.monthLabel.uppercase(),
                style = MaterialTheme.typography.sectionLabel(11, 0.14f),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }

// One agenda row: a fixed-width date gutter on the left (today tinted primary) and
// the day's events on the right — every event for the day, or a muted dash when the
// day is free.
@Composable
private fun DayRow(
    day: DayCell,
    isToday: Boolean,
    is24Hour: Boolean,
) = Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    verticalAlignment = Alignment.Top,
) {
    val accent = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Column(
        modifier =
            Modifier
                .width(28.dp)
                .then(
                    // A faint pill behind today's gutter lifts it out of the agenda
                    // at a glance, beyond the primary text tint alone.
                    if (isToday) {
                        Modifier
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    } else {
                        Modifier
                    },
                ).padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(
            // Two letters keep a Latin abbreviation inside the narrow gutter; a CJK
            // weekday label is a single glyph regardless.
            text = day.weekdayLetter.take(2).uppercase(),
            style = MaterialTheme.typography.sectionLabel(9, 0.08f),
            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
        )
        Text(
            text = "${day.date.dayOfMonth}",
            style =
                MaterialTheme.typography.titleSmall.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 18.sp,
                    fontFeatureSettings = TabularFigures,
                ),
            color = accent,
            maxLines = 1,
            softWrap = false,
        )
    }
    Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (day.events.isEmpty()) {
            Text(
                text = NO_EVENTS_PLACEHOLDER,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                maxLines = 1,
            )
        } else {
            day.events.forEach { event ->
                EventRow(
                    // A timed event leads with a clock; an all-day event (null time)
                    // leads with a sun so it reads as "all day" at a glance.
                    icon = if (event.time != null) Lucide.Clock else Lucide.Sun,
                    // Event times honour the user's 12/24-hour clock setting, matching
                    // the dashboard clock rather than always printing 24-hour.
                    time =
                        event.time?.format(eventTimeFormatter(is24Hour))
                            ?: stringResource(R.string.calendar_all_day),
                    title = event.title,
                )
            }
        }
    }
}

@Composable
private fun EventRow(
    icon: ImageVector,
    time: String,
    title: String,
) = Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.Top,
) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 3.dp).size(12.dp),
    )
    Text(
        text = time,
        style =
            MaterialTheme.typography.labelLarge.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 18.sp,
                fontFeatureSettings = TabularFigures,
            ),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        softWrap = false,
    )
    Text(
        text = title,
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

// Shared centred hint for the no-data states (permission denied / provider
// fault): one muted line in place of the agenda.
@Composable
private fun CenteredHint(text: String) =
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

private const val NO_EVENTS_PLACEHOLDER = "—"

// 24-hour "14:30" or compact 12-hour "2:30 PM"; the latter stays short enough to
// keep the narrow agenda row from clipping the event title.
private val EventTimeFormatter24: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val EventTimeFormatter12: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

private fun eventTimeFormatter(is24Hour: Boolean): DateTimeFormatter =
    if (is24Hour) EventTimeFormatter24 else EventTimeFormatter12

// Sized to the head-unit binding: each top-row card is ~165 x 207 dp (half the
// info pane on the 853 x 512 dp / 5:3 projection). Wider panels only add slack.
@PreviewLightDark
@Preview(name = "Calendar card", widthDp = 165, heightDp = 207)
@Composable
private fun CalendarCardPreview() {
    FemtoTheme {
        CalendarCard(
            snapshot =
                CalendarSnapshot(
                    today = LocalDate.of(2026, 3, 30),
                    weekday = "Monday",
                    monthLabel = "March 2026",
                    days =
                        listOf(
                            DayCell(
                                LocalDate.of(2026, 3, 30),
                                "Mon",
                                listOf(
                                    EventItem(LocalTime.of(10, 30), "Team standup"),
                                    EventItem(LocalTime.of(14, 0), "Pick up kids"),
                                ),
                            ),
                            DayCell(LocalDate.of(2026, 3, 31), "Tue", emptyList()),
                            DayCell(
                                LocalDate.of(2026, 4, 1),
                                "Wed",
                                listOf(EventItem(null, "Quarter close")),
                            ),
                            DayCell(LocalDate.of(2026, 4, 2), "Thu", emptyList()),
                            DayCell(
                                LocalDate.of(2026, 4, 3),
                                "Fri",
                                listOf(EventItem(LocalTime.of(9, 0), "Dentist")),
                            ),
                            DayCell(LocalDate.of(2026, 4, 4), "Sat", emptyList()),
                        ),
                    hasCalendarAccess = true,
                ),
            is24Hour = true,
        )
    }
}
