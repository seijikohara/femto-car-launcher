package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
 * Calendar card. Three vertical sections:
 *
 *  1. Head — large day number (primary tint) + weekday + month label
 *  2. Strip — 6 future days starting today, each cell carrying a "has event" dot
 *  3. Events — up to two upcoming events (border-separated)
 *
 * The card renders in the same dimensions whether or not [snapshot] is
 * loaded. A null snapshot ("permission denied / loading") shows the
 * calendar icon as a placeholder so the layout doesn't shift.
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
                ),
            color = MaterialTheme.colorScheme.primary,
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = snapshot.weekday,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = snapshot.monthLabel.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = onBackground,
        )
        Text(
            text = "${day.date.dayOfMonth}",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = if (isToday) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        )
        Box(
            modifier =
                Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(
                        if (day.hasEvent) onBackground else MaterialTheme.colorScheme.surfaceContainer,
                    ),
        )
    }
}

@Composable
private fun Events(events: List<EventItem>) {
    if (events.isEmpty()) return
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Box(modifier = Modifier.height(8.dp))
        events.forEachIndexed { index, event ->
            EventRow(
                time = event.time.format(EventTimeFormatter),
                title = event.title,
                isPrimary = index == 0,
            )
            if (index < events.lastIndex) Box(modifier = Modifier.height(6.dp))
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
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Box(modifier = Modifier.width(2.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
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
