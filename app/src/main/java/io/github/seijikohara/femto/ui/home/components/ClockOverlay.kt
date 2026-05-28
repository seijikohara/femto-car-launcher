package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.github.seijikohara.femto.data.ClockTick
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Glass overlay anchored to the map pane's top-right.
 *
 * Shows the wall-clock time only — the calendar card to the right already
 * carries today / weekday / month, so duplicating them in the overlay
 * would just add visual noise.
 */
@Composable
internal fun ClockOverlay(
    clock: ClockTick,
    modifier: Modifier = Modifier,
    is24Hour: Boolean = true,
) {
    val pattern = if (is24Hour) "HH:mm" else "h:mm"
    val formatted = clock.time.format(DateTimeFormatter.ofPattern(pattern))
    Text(
        text = formatted,
        style =
            MaterialTheme.typography.displayMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.04f).em,
            ),
        color = MaterialTheme.colorScheme.onSurface,
        modifier =
            modifier
                .clip(RoundedCornerShape(FemtoDimens.OverlayCorner))
                .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.78f))
                .padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

@PreviewLightDark
@Preview(name = "Clock overlay", widthDp = 200, heightDp = 80)
@Composable
private fun ClockOverlayPreview() {
    FemtoTheme {
        ClockOverlay(
            clock = ClockTick(time = LocalTime.of(8, 24), date = LocalDate.of(2026, 3, 30)),
        )
    }
}
