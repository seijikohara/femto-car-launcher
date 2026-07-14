package io.github.seijikohara.femto.ui.home.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.seijikohara.femto.ui.theme.unitLabel

/**
 * Dimmed, small, trailing unit glyph shared by the dashboard's measured values
 * (speed, distance, average, altitude, temperature scale, wind). Rendered in the
 * shared [unitLabel] style at 0.7 alpha so the unit reads a step below the value
 * it annotates.
 *
 * Callers place it in a `Row` trailing the value, both the value and this suffix
 * carrying `Modifier.alignByBaseline()` on a 4dp gap, so the unit sits on the
 * value's baseline regardless of the value's much larger size.
 */
@Composable
internal fun UnitSuffix(
    unit: String,
    modifier: Modifier = Modifier,
) = Text(
    text = unit,
    style = MaterialTheme.typography.unitLabel(),
    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
    maxLines = 1,
    modifier = modifier,
)
