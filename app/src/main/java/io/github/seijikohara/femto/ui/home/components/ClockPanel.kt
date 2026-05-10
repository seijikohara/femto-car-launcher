package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.seijikohara.femto.data.ClockTick
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
internal fun ClockPanel(
    tick: ClockTick,
    is24Hour: Boolean,
    sunrise: LocalTime?,
    sunset: LocalTime?,
    modifier: Modifier = Modifier,
) = Surface(
    modifier = modifier,
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.surfaceContainer,
    tonalElevation = FemtoDimens.CardElevation,
) {
    Column(
        modifier = Modifier.padding(FemtoDimens.GridGutter),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // displayMedium gives the time enough visual weight to fill the card's
        // horizontal space — displaySmall left a noticeable right-side gap and
        // the time should be the dashboard's most glance-critical value.
        Text(
            text = tick.time.format(timeFormatter(is24Hour)),
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = tick.date.format(fullDateFormatter()),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // Daylight chips anchor the time context — "how long until dusk?" answers
        // the next driving-relevant question after "what time is it?". They live
        // here instead of the weather card so weather can focus on the hourly
        // outlook without crowding. SpaceBetween pushes sunrise to the left edge
        // and sunset to the right, eliminating the prior right-side empty space.
        if (sunrise != null || sunset != null) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                sunrise?.let {
                    AstroChip(
                        icon = Icons.Outlined.WbTwilight,
                        contentDescription = "Sunrise",
                        label = it.format(timeFormatter(is24Hour)),
                    )
                }
                sunset?.let {
                    AstroChip(
                        icon = Icons.Outlined.Bedtime,
                        contentDescription = "Sunset",
                        label = it.format(timeFormatter(is24Hour)),
                    )
                }
            }
        }
    }
}

@Composable
private fun AstroChip(
    icon: ImageVector,
    contentDescription: String,
    label: String,
) = Row(
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        modifier = Modifier.size(FemtoDimens.InlineIconSize),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

private fun timeFormatter(is24Hour: Boolean): DateTimeFormatter =
    DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a", Locale.getDefault())

private fun fullDateFormatter(): DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.getDefault())
