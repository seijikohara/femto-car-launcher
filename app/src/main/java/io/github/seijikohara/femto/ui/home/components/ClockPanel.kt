package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.seijikohara.femto.data.ClockTick
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
internal fun ClockPanel(
    tick: ClockTick,
    is24Hour: Boolean,
    modifier: Modifier = Modifier,
) = Surface(
    modifier = modifier,
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.surfaceContainer,
    tonalElevation = FemtoDimens.CardElevation,
) {
    Column(
        modifier = Modifier.padding(FemtoDimens.GridGutter),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Time is the most glance-critical value on the dashboard, so it carries the
        // primary accent color. The role itself is communicated by the digits and
        // colon — no need for a "TIME" header label.
        Text(
            text = tick.time.format(timeFormatter(is24Hour)),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        // FormatStyle.FULL combines weekday and date in one locale-aware line —
        // "Saturday, May 9, 2026" / "2026年5月9日土曜日" — to keep the card to three
        // lines and avoid bottom-edge clipping in the right-column layout.
        Text(
            text = tick.date.format(fullDateFormatter()),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun timeFormatter(is24Hour: Boolean): DateTimeFormatter =
    DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a", Locale.getDefault())

private fun fullDateFormatter(): DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.getDefault())
