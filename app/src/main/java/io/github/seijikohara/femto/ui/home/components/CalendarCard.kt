package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
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
 * Calendar card. Three vertical sections on the [FemtoDimens.CardSectionGap]
 * rhythm:
 *
 *  1. Head — big day number (primary tint) + weekday + month label. The head
 *     always shows **today**; selecting another day in the strip never moves it.
 *  2. Strip — 6 days starting today, each tappable. The selected cell carries
 *     the highlight; today keeps a thin ring while another day is previewed.
 *  3. Events — the selected day's events, bottom-anchored so the per-day-capped
 *     list grows into the flexible gap rather than pushing the card taller.
 *
 * Selection is card-local ([rememberSaveable]); the dashboard owns no calendar
 * selection state. Typography and spacing follow
 * `docs/design/dashboard-v2-mockup.html` (`.calendar-card` rules); the
 * dashboard's 18sp body-size floor is intentionally relaxed here so the strip
 * and event list match the design.
 */
@Composable
internal fun CalendarCard(
    snapshot: CalendarSnapshot?,
    modifier: Modifier = Modifier,
) = Surface(
    modifier = modifier,
    shape = RoundedCornerShape(FemtoDimens.CardCorner),
    color = MaterialTheme.colorScheme.surfaceContainer,
    tonalElevation = FemtoDimens.CardElevation,
) {
    when {
        // null is the loading frame: render nothing rather than a denial the
        // user has not earned yet.
        snapshot == null -> Unit

        // A non-null snapshot with access denied carries no real strip data, so
        // show the denial message instead of a hollow strip.
        !snapshot.hasCalendarAccess -> PermissionDenied()

        else -> CalendarContent(snapshot)
    }
}

@Composable
private fun CalendarContent(snapshot: CalendarSnapshot) {
    // Card-local selection, defaulting to today and surviving configuration
    // change via the epoch-day saver. The big-day head stays on today.
    var selectedDate by rememberSaveable(stateSaver = LocalDateSaver) {
        mutableStateOf(snapshot.today)
    }
    val days = snapshot.dayStrip
    // A selection outside the rolling window — after a midnight rollover, or a
    // stale restored value — clamps back to today, so the events area never
    // shows an out-of-window blank. This is the spec's "reset to today on
    // rollover" without clobbering a config-change-restored selection.
    val selected = days.firstOrNull { it.date == selectedDate } ?: days.first()
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(FemtoDimens.CardPadding),
        verticalArrangement = Arrangement.spacedBy(FemtoDimens.CardSectionGap),
    ) {
        Head(snapshot)
        Strip(
            days = days,
            today = snapshot.today,
            // Highlight the clamped selection (`selected.date`), not the raw
            // `selectedDate` state: after a rollover the stored value can point
            // outside the window, and the clamp keeps exactly one cell lit.
            selectedDate = selected.date,
            onSelect = { selectedDate = it },
        )
        // Bottom-anchor the events: the per-day cap bounds their count, so they
        // grow upward into this flexible gap instead of pushing the card taller
        // — selecting a busy day cannot worsen the clip.
        Spacer(Modifier.weight(1f))
        Events(selected.events)
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

@Composable
private fun Strip(
    days: List<DayCell>,
    today: LocalDate,
    selectedDate: LocalDate,
    onSelect: (LocalDate) -> Unit,
) = Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(3.dp),
) {
    days.forEach { day ->
        DayCellView(
            day = day,
            isToday = day.date == today,
            isSelected = day.date == selectedDate,
            onClick = { onSelect(day.date) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DayCellView(
    day: DayCell,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background =
        if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
    val onBackground =
        if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val numberColor =
        if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val shape = RoundedCornerShape(FemtoDimens.DayCellCorner)
    Column(
        modifier =
            modifier
                .clip(shape)
                // Today keeps a thin ring when it is not the selected cell, so it
                // stays identifiable while the user previews another day.
                .then(
                    if (isToday && !isSelected) {
                        Modifier.border(1.dp, MaterialTheme.colorScheme.primary, shape)
                    } else {
                        Modifier
                    },
                ).background(background)
                .clickable(onClick = onClick)
                // The day-of-month alone repeats across months; the ISO date is a
                // stable, unique label for selection and testing.
                .semantics { contentDescription = day.date.toString() }
                // Sub-64dp tap target: a deliberate exception for the in-card
                // mini-calendar grid (CLAUDE.md#automotive-overrides keeps 64dp the
                // default; the strip relaxes it like the footer status cluster).
                // Tight horizontal padding so a two-digit day fits the ~20 dp cell
                // a narrow info-pane card gives each of the six columns.
                .padding(vertical = 8.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            // Two letters (not three): on the head-unit binding width a 3-letter
            // Latin abbreviation overflows the cell, while a 2-letter one fits and
            // stays unambiguous; CJK weekday labels are a single glyph regardless.
            text = day.weekdayLetter.take(2).uppercase(),
            style = MaterialTheme.typography.sectionLabel(9, 0.08f),
            color = onBackground,
            maxLines = 1,
            softWrap = false,
        )
        Text(
            text = "${day.date.dayOfMonth}",
            style =
                MaterialTheme.typography.titleSmall.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 15.sp,
                    fontFeatureSettings = TabularFigures,
                ),
            color = numberColor,
            maxLines = 1,
            softWrap = false,
        )
        Box(
            modifier =
                Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(
                        if (day.hasEvent) onBackground else background,
                    ),
        )
    }
}

@Composable
private fun Events(events: List<EventItem>) =
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (events.isEmpty()) {
            Text(
                text = stringResource(R.string.calendar_no_events),
                style =
                    MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                    ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        } else {
            events.forEachIndexed { index, event ->
                EventRow(
                    // A null time marks an all-day event.
                    time = event.time?.format(EventTimeFormatter) ?: stringResource(R.string.calendar_all_day),
                    title = event.title,
                    isPrimary = index == 0,
                )
            }
        }
    }

private val EventTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

// Store the card-local selection as an epoch day so rememberSaveable can persist
// it across configuration change without a Parcelable wrapper.
private val LocalDateSaver: Saver<LocalDate, Long> =
    Saver(
        save = { it.toEpochDay() },
        restore = { LocalDate.ofEpochDay(it) },
    )

@Composable
private fun EventRow(
    time: String,
    title: String,
    isPrimary: Boolean,
) {
    val dotColor =
        if (isPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(dotColor),
        )
        Text(
            text = time,
            style =
                MaterialTheme.typography.labelLarge.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 17.sp,
                    fontFeatureSettings = TabularFigures,
                ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Text(
            text = title,
            style =
                MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PermissionDenied() =
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.calendar_permission_denied),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

// Sized to the head-unit binding: each top-row card is ~165 x 207 dp (half the
// info pane on the 853 x 512 dp / 5:3 projection), the geometry that exposed the
// two-digit-day clip. Wider panels only add slack.
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
                    dayStrip =
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
        )
    }
}
