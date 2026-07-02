package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.calendar.CalendarSnapshot
import io.github.seijikohara.femto.data.calendar.DayCell
import io.github.seijikohara.femto.data.calendar.EventItem
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.FitText
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import io.github.seijikohara.femto.ui.theme.PreviewTextStress
import io.github.seijikohara.femto.ui.theme.TabularFigures
import io.github.seijikohara.femto.ui.theme.bigNumber
import io.github.seijikohara.femto.ui.theme.calendarWeekday
import io.github.seijikohara.femto.ui.theme.eyebrow
import io.github.seijikohara.femto.ui.theme.glanceBody
import io.github.seijikohara.femto.ui.theme.glanceMetric
import io.github.seijikohara.femto.ui.theme.sectionLabel
import java.time.LocalDate
import java.time.LocalTime

/**
 * Calendar card:
 *
 *  1. Head — big day number (neutral onSurface) + weekday + month label, always today.
 *  2. Days — a scrollable vertical list of the coming days (today first), each row
 *     showing that day's full set of events. Days with no events are omitted so
 *     the short card spends every row on real entries; only today stays when
 *     free, carrying an explicit no-events line.
 *
 * Typography and spacing originated in the retired dashboard-v2 design mockup;
 * the dashboard's 18sp body-size floor is intentionally relaxed here so the
 * agenda fits the short head-unit info-pane card.
 */
@Composable
internal fun CalendarCard(
    snapshot: CalendarSnapshot?,
    is24Hour: Boolean,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState = rememberHazeState(),
    glassConfig: GlassConfig = GlassConfig(),
) = Surface(
    modifier = modifier.glassChrome(MaterialTheme.shapes.large, hazeState, glassConfig),
    shape = MaterialTheme.shapes.large,
    color = Color.Transparent,
    contentColor = MaterialTheme.colorScheme.onSurface,
) {
    Box(modifier = Modifier.fillMaxSize()) {
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

            else -> CalendarContent(snapshot, is24Hour, onExpand)
        }
    }
}

@Composable
private fun CalendarContent(
    snapshot: CalendarSnapshot,
    is24Hour: Boolean,
    onExpand: () -> Unit,
) = Column(
    modifier =
        Modifier
            .fillMaxSize()
            // Tighter than the shared card padding/gap: the head-unit info-pane
            // card is short, so pack the head and the list to avoid a clip.
            .padding(FemtoDimens.CardPaddingCompact),
    verticalArrangement = Arrangement.spacedBy(FemtoDimens.CardSectionGapCompact),
) {
    Head(snapshot, onExpand)
    // Free days are dropped rather than rendered as placeholder rows: the
    // glance question is "what is coming up", and on the short head-unit card
    // a six-row continuous agenda clipped before reaching the real entries.
    // Today is the one exception — it stays visible even when free.
    val visibleDays = remember(snapshot) { snapshot.visibleDays }
    LazyColumn(
        modifier = Modifier.fillMaxWidth().weight(1f),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items = visibleDays, key = { it.date.toString() }) { day ->
            DayRow(day = day, isToday = day.date == snapshot.today, is24Hour = is24Hour)
        }
    }
}

@Composable
private fun Head(
    snapshot: CalendarSnapshot,
    onExpand: () -> Unit,
) {
    // clickable + an explicit contentDescription (the AlbumArt idiom in
    // MusicCardMeta): onClickLabel alone sets only the OnClick action label, not
    // the node's content description, so the maximize entry stays discoverable.
    // Hoisted out of the semantics lambda, which is not @Composable.
    val calendarExpandLabel = stringResource(R.string.calendar_expand)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onExpand() }
                .semantics { contentDescription = calendarExpandLabel },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "${snapshot.today.dayOfMonth}",
            style = MaterialTheme.typography.bigNumber(),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FitText(
                text = snapshot.weekday,
                style = MaterialTheme.typography.calendarWeekday(),
                color = MaterialTheme.colorScheme.onSurface,
                // The weekday is glance metadata beside the big day number, so it may
                // relax below the 18sp body floor to GlanceTextSize to keep the full
                // localized name (e.g. "Wednesday") on the narrow head-unit card
                // (CLAUDE.md#automotive-overrides). It shrinks only as far as needed.
                minFontSize = FemtoDimens.GlanceTextSize,
            )
            Text(
                text = snapshot.monthLabel.uppercase(),
                style = MaterialTheme.typography.eyebrow(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
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
            style = MaterialTheme.typography.glanceMetric().copy(lineHeight = 18.sp),
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
            // Only today can reach here (free days are filtered out upstream);
            // an explicit line beats a bare dash for the one row that stays.
            Text(
                text = stringResource(R.string.calendar_no_events),
                style = MaterialTheme.typography.glanceBody(),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                maxLines = 1,
            )
        } else {
            day.events.forEach { event ->
                EventRow(
                    // Event times honour the user's 12/24-hour clock setting, matching
                    // the dashboard clock rather than always printing 24-hour; "All
                    // day" in the same slot marks the untimed events.
                    time =
                        event.time?.format(clockTimeFormatter(is24Hour))
                            ?: stringResource(R.string.calendar_all_day),
                    title = event.title,
                )
            }
        }
    }
}

// No leading glyph: the time slot ("14:00" / "All day") already states the
// event kind, and on the ~165 dp head-unit card every glyph-width goes to the
// title instead.
@Composable
private fun EventRow(
    time: String,
    title: String,
) = Column(
    // Time above, title below: the side-by-side row made a wrapping title
    // hang after the time at an unnatural break, while the two-line stack
    // wraps from the card's left edge.
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(1.dp),
) {
    Text(
        text = time,
        style =
            MaterialTheme.typography.labelLarge.copy(
                fontSize = FemtoDimens.GlanceTextSize,
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
        style = MaterialTheme.typography.glanceBody(),
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

// Sized to the head-unit binding: each top-row card is ~165 x 207 dp (half the
// info pane on the 853 x 512 dp / 5:3 projection). Wider panels only add slack.
@PreviewLightDark
@PreviewTextStress
@Preview(name = "Calendar card", widthDp = 165, heightDp = 207)
@Composable
private fun CalendarCardPreview() {
    FemtoTheme {
        CalendarCard(
            snapshot =
                CalendarSnapshot(
                    today = LocalDate.of(2026, 3, 30),
                    // The longest common English weekday / month exercise the head's
                    // FitText fit-to-width on the narrow card.
                    weekday = "Wednesday",
                    monthLabel = "September 2026",
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
            onExpand = {},
        )
    }
}
