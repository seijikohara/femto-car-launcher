package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
import io.github.seijikohara.femto.ui.theme.bigNumber
import io.github.seijikohara.femto.ui.theme.cardMeta
import io.github.seijikohara.femto.ui.theme.glanceBody
import io.github.seijikohara.femto.ui.theme.glanceCaption
import io.github.seijikohara.femto.ui.theme.glanceMetric
import io.github.seijikohara.femto.ui.theme.normalWeight
import io.github.seijikohara.femto.ui.theme.sectionLabel
import io.github.seijikohara.femto.ui.theme.singleLineBox
import java.time.LocalDate
import java.time.LocalTime

/**
 * Calendar card: the agenda — today as the card's head, then the coming days,
 * each row showing that day's full set of events, in a vertically scrollable
 * region. Every visible day renders; overflow scrolls rather than being
 * dropped, so the agenda never hides an entry behind the card's capped height.
 * Days with no events are omitted so the agenda spends every row on real
 * entries; only today stays when free, carrying an explicit no-events line.
 *
 * Today opens the card as a head rather than as a gutter row ([TodayHead]): a
 * "Today" eyebrow, the first event's time as the hero numeral, its title
 * beneath. That head is the same shape as the weather card's beside it —
 * eyebrow, hero numeral on one digit band, detail below — so the two cards of
 * the row read as one line. Today's date (day numeral, weekday, month) is not
 * the card's: it lives in the [DashboardHeader] above the cluster, beside the
 * clock, so it shows without calendar permission and stays when this card is
 * hidden — hiding the card is the "date and day only" dashboard. The coming
 * days keep their date gutter ([DayRow]) below the head.
 *
 * Typography and spacing originated in the retired dashboard-v2 design mockup;
 * the dashboard's body-size floor ([FemtoDimens.MinBodyTextSize]) is intentionally
 * relaxed here so the agenda fits the short head-unit info-pane card.
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
) {
    // clickable + an explicit contentDescription (the AlbumArt idiom in
    // MusicCardMeta): onClickLabel alone sets only the OnClick action label, not
    // the node's content description, so the maximize entry stays discoverable.
    // Hoisted out of the semantics lambda, which is not @Composable. Applied to
    // the whole card so tapping anywhere opens the full-screen panel; the
    // scrollable agenda has no other clickable children, so Compose routes a tap
    // to this maximize click and a vertical drag to the agenda's scroll without a
    // nested-gesture conflict.
    val calendarExpandLabel = stringResource(R.string.calendar_expand)
    // Remembered at the content level (not keyed on the snapshot) so a data refresh
    // re-emitting the agenda keeps the user's scroll position rather than snapping
    // back to today.
    val agendaScroll = rememberScrollState()
    // Free days are dropped rather than rendered as placeholder rows: the glance
    // question is "what is coming up". Today is the one exception — it stays
    // visible even when free.
    val visibleDays = remember(snapshot) { snapshot.visibleDays }
    // The whole card is the agenda's viewport: every visible day renders — overflow
    // scrolls instead of being dropped whole (contrast FitWholeRows, still used by
    // the maximize panels). A plain scrollable Column, not a LazyColumn: the day
    // list is short, and a plain Column coexists cleanly with the parent maximize
    // click. Deliberately not wrapped in a Crossfade — one would reset the user's
    // scroll on every refresh.
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .clickable { onExpand() }
                .semantics { contentDescription = calendarExpandLabel }
                // Tighter than the shared card padding/gap: the head-unit info-pane
                // card is short, so pack the list to avoid a clip.
                .padding(FemtoDimens.CardPaddingCompact)
                .verticalScroll(agendaScroll),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        visibleDays.forEach { day ->
            if (day.date == snapshot.today) {
                TodayHead(day = day, is24Hour = is24Hour, showColorBars = snapshot.multipleCalendarsVisible)
            } else {
                DayRow(day = day, is24Hour = is24Hour, showColorBars = snapshot.multipleCalendarsVisible)
            }
        }
    }
}

// Today as the card's head: the "Today" eyebrow (the date itself is in the
// header above the cluster — a "FRI 1" gutter here would say it a third time),
// the first event's time as the hero numeral on the same digit band as the
// weather card's temperature beside it, and its title beneath; today's further
// events follow flush-left in the agenda's own row form. Free today keeps the
// eyebrow over the explicit no-events line, so the head never renders empty.
@Composable
private fun TodayHead(
    day: DayCell,
    is24Hour: Boolean,
    showColorBars: Boolean,
) = Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(3.dp),
) {
    val first = day.events.firstOrNull()
    // The eyebrow sits directly on the hero (no gap), exactly as the weather
    // card's head stacks its eyebrow on the temperature, so the two heroes land
    // on the same digit band across the row.
    Column(modifier = Modifier.fillMaxWidth()) {
        CardEyebrow(text = stringResource(R.string.calendar_today))
        if (first == null) {
            // Only today can be free here (free days are filtered out upstream); an
            // explicit line beats a bare dash for the one row that stays.
            Text(
                text = stringResource(R.string.calendar_no_events),
                style = MaterialTheme.typography.glanceBody(),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                maxLines = 1,
            )
        } else {
            HeroEvent(event = first, is24Hour = is24Hour, showColorBar = showColorBars)
        }
    }
    if (first != null) {
        day.events.drop(1).forEach { event ->
            EventRow(event = event, is24Hour = is24Hour, showColorBar = showColorBars)
        }
    }
}

// The first event of today: its time in the cards' hero treatment (bigNumber at
// Text4Xl, Normal — the weather temperature's style, on the same clamped line
// box), its title on one line under it.
@Composable
private fun HeroEvent(
    event: EventItem,
    is24Hour: Boolean,
    showColorBar: Boolean,
) {
    val heroStyle =
        MaterialTheme.typography.bigNumber(
            size = FemtoDimens.Text4Xl,
            weight = MaterialTheme.typography.normalWeight,
        )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        FitText(
            text = eventTimeLabel(event, is24Hour),
            style = heroStyle,
            color = MaterialTheme.colorScheme.onSurface,
            // A 12-hour "10:30 AM" or an "All day" label outruns the numeral's slot
            // on the head-unit card; it shrinks a step rather than ellipsizes.
            minFontSize = FemtoDimens.Text2Xl,
            modifier = Modifier.singleLineBox(heroStyle),
        )
        EventTitle(
            title = event.title,
            color = event.color,
            showColorBar = showColorBar,
            style = MaterialTheme.typography.cardMeta(),
            textColor = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

// One agenda row for a coming day: a fixed-width date gutter on the left and the
// day's events on the right — every event for the day. Today never reaches here
// (it is the card's head), so the row is never free: free days are filtered out
// upstream.
@Composable
private fun DayRow(
    day: DayCell,
    is24Hour: Boolean,
    showColorBars: Boolean,
) = Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    verticalAlignment = Alignment.Top,
) {
    Column(
        modifier = Modifier.width(28.dp).padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        FitText(
            // The full locale short form ("MON", "月") — never a hand-truncated
            // two letters; FitText shrinks the rare longer form (e.g. "LUN.")
            // into the narrow gutter instead.
            text = day.weekdayLetter.uppercase(),
            style = MaterialTheme.typography.sectionLabel(12),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            minFontSize = FemtoDimens.TextXs,
        )
        Text(
            text = "${day.date.dayOfMonth}",
            style = MaterialTheme.typography.glanceMetric().copy(lineHeight = 18.sp),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
        )
    }
    Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        day.events.forEach { event ->
            EventRow(event = event, is24Hour = is24Hour, showColorBar = showColorBars)
        }
    }
}

// Event times honour the user's 12/24-hour clock setting, matching the dashboard
// clock rather than always printing 24-hour; "All day" in the same slot marks the
// untimed events.
@Composable
private fun eventTimeLabel(
    event: EventItem,
    is24Hour: Boolean,
): String = event.time?.format(clockTimeFormatter(is24Hour)) ?: stringResource(R.string.calendar_all_day)

// The time slot ("14:00" / "All day") states the event kind, so no kind glyph
// leads the row. A calendar color bar may still lead it, but only when the
// window spans more than one calendar (showColorBar) — there it tells the
// calendars apart; otherwise every glyph-width goes to the title on the ~165 dp
// head-unit card.
@Composable
private fun EventRow(
    event: EventItem,
    is24Hour: Boolean,
    showColorBar: Boolean,
) = Column(
    // Time above, title below: the side-by-side row made a wrapping title
    // hang after the time at an unnatural break, while the two-line stack
    // wraps from the text column's left edge.
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(1.dp),
) {
    Text(
        text = eventTimeLabel(event, is24Hour),
        // Indent past the bar gutter so the time shares the title's left edge;
        // the bar leads the title row below, spanning its rendered lines.
        modifier =
            if (showColorBar) {
                Modifier.padding(start = FemtoDimens.CalendarBarGutter)
            } else {
                Modifier
            },
        style = MaterialTheme.typography.glanceCaption(lineHeight = 18.sp),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        softWrap = false,
    )
    EventTitle(
        title = event.title,
        color = event.color,
        showColorBar = showColorBar,
        style = MaterialTheme.typography.glanceBody(),
        textColor = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
    )
}

// An event's title line(s), led by the calendar color bar when the window spans
// more than one calendar. Shared by the head's hero event and the agenda rows,
// which differ only in the title's style, colour, and line budget.
@Composable
private fun EventTitle(
    title: String,
    color: Int,
    showColorBar: Boolean,
    style: TextStyle,
    textColor: Color,
    maxLines: Int,
) = Row(
    // IntrinsicSize.Min sizes this row to the title text, so the bar's
    // fillMaxHeight spans exactly the rendered line(s) — one line for a
    // short title, both when it wraps — instead of floating as a
    // fixed-height stub beside wrapped text.
    modifier = Modifier.height(IntrinsicSize.Min),
    horizontalArrangement = Arrangement.spacedBy(FemtoDimens.CalendarBarGap),
) {
    if (showColorBar) {
        CalendarColorBar(color = color, modifier = Modifier.fillMaxHeight())
    }
    Text(
        text = title,
        modifier = Modifier.weight(1f),
        style = style,
        color = textColor,
        maxLines = maxLines,
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

// Sized to the head-unit binding: each top-row card is ~159 x 207 dp (half the
// info pane on the 853 x 512 dp / 5:3 projection, under the header). Wider panels
// only add slack.
@PreviewLightDark
@PreviewTextStress
@Preview(name = "Calendar card", widthDp = 159, heightDp = 207)
@Composable
private fun CalendarCardPreview() {
    FemtoTheme {
        CalendarCard(
            snapshot =
                CalendarSnapshot(
                    today = LocalDate.of(2026, 3, 30),
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
