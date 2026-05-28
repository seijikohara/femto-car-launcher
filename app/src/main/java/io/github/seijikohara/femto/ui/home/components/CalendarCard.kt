package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.seijikohara.femto.data.CalendarSnapshot
import io.github.seijikohara.femto.data.DayCell
import io.github.seijikohara.femto.data.EventItem
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Calendar card. Three vertical sections distributed by [Arrangement.SpaceBetween]:
 *
 *  1. Head — 56sp day number (primary tint) + weekday + month label.
 *  2. Strip — 6 future days starting today, each cell carrying a "has event" dot.
 *  3. Events — up to two upcoming events, separated from the strip by a 1dp divider.
 *
 * Typography and spacing follow `docs/design/dashboard-v2-mockup.html`
 * (`.calendar-card` rules) verbatim; the dashboard's wider 18sp body-size
 * floor is intentionally relaxed here so the strip and event list match
 * the design.
 */
@Composable
internal fun CalendarCard(
    snapshot: CalendarSnapshot?,
    modifier: Modifier = Modifier,
) = Surface(
    modifier = modifier,
    shape = RoundedCornerShape(FemtoDimens.OverlayCorner),
    color = MaterialTheme.colorScheme.surfaceContainer,
    tonalElevation = FemtoDimens.CardElevation,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        if (snapshot != null) {
            Head(snapshot)
            Strip(snapshot.dayStrip)
            Events(snapshot.events)
        } else {
            EmptyState()
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
            style =
                MaterialTheme.typography.displayLarge.copy(
                    fontSize = FemtoDimens.BigNumberFontSize,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.045f).em,
                    lineHeight = (FemtoDimens.BigNumberFontSize.value * 0.92f).sp,
                ),
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = snapshot.weekday,
                style =
                    MaterialTheme.typography.titleLarge.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.01f).em,
                        lineHeight = 20.sp,
                    ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = snapshot.monthLabel.uppercase(),
                style =
                    MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.14f.em,
                    ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }

@Composable
private fun Strip(days: List<DayCell>) =
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        days.forEachIndexed { index, day ->
            DayCellView(
                day = day,
                isToday = index == 0,
                modifier = Modifier.weight(1f),
            )
        }
    }

@Composable
private fun DayCellView(
    day: DayCell,
    isToday: Boolean,
    modifier: Modifier = Modifier,
) {
    val background =
        if (isToday) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
    val onBackground =
        if (isToday) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val numberColor =
        if (isToday) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(10.dp))
                .background(background)
                .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = day.weekdayLetter.take(3).uppercase(),
            style =
                MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.08f.em,
                    lineHeight = 10.sp,
                ),
            color = onBackground,
            maxLines = 1,
            softWrap = false,
        )
        Text(
            text = "${day.date.dayOfMonth}",
            style =
                MaterialTheme.typography.titleSmall.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 16.sp,
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
private fun Events(events: List<EventItem>) {
    if (events.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        events.forEachIndexed { index, event ->
            EventRow(
                time = event.time.format(EventTimeFormatter),
                title = event.title,
                isPrimary = index == 0,
            )
        }
    }
}

private val EventTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

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
                    fontWeight = FontWeight.Bold,
                    lineHeight = 17.sp,
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
private fun EmptyState() =
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Calendar access not granted",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

@PreviewLightDark
@Preview(name = "Calendar card", widthDp = 240, heightDp = 224)
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
                            DayCell(LocalDate.of(2026, 3, 30), "Mon", true),
                            DayCell(LocalDate.of(2026, 3, 31), "Tue", false),
                            DayCell(LocalDate.of(2026, 4, 1), "Wed", true),
                            DayCell(LocalDate.of(2026, 4, 2), "Thu", false),
                            DayCell(LocalDate.of(2026, 4, 3), "Fri", true),
                            DayCell(LocalDate.of(2026, 4, 4), "Sat", false),
                        ),
                    events =
                        listOf(
                            EventItem(LocalTime.of(10, 30), "Team standup"),
                            EventItem(LocalTime.of(14, 0), "Pick up kids"),
                        ),
                ),
        )
    }
}
