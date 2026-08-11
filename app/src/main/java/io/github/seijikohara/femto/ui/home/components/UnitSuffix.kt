package io.github.seijikohara.femto.ui.home.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import io.github.seijikohara.femto.ui.theme.strongWeight
import io.github.seijikohara.femto.ui.theme.unitLabel

// How far the unit sits below its value. One constant behind both the standalone
// suffix and the inline span, so the two treatments cannot drift apart.
private const val UNIT_SUFFIX_ALPHA = 0.7f

/**
 * Dimmed, small, trailing unit glyph shared by the dashboard's measured values
 * (speed, distance, average, altitude, temperature scale, wind). Rendered in the
 * shared [unitLabel] style at [UNIT_SUFFIX_ALPHA] so the unit reads a step below
 * the value it annotates.
 *
 * Callers place it in a `Row` trailing the value, both the value and this suffix
 * carrying `Modifier.alignByBaseline()` on a 4dp gap, so the unit sits on the
 * value's baseline regardless of the value's much larger size.
 *
 * Use [valueWithUnit] instead wherever the pair has to survive a slot narrower
 * than it wants: a `Row` measures the value first and clips the unit out of the
 * leftover width.
 */
@Composable
internal fun UnitSuffix(
    unit: String,
    modifier: Modifier = Modifier,
) = Text(
    text = unit,
    style = MaterialTheme.typography.unitLabel(),
    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = UNIT_SUFFIX_ALPHA),
    maxLines = 1,
    modifier = modifier,
)

/**
 * The [UnitSuffix] treatment as one string: `value` followed by a space and the
 * dimmed [unit], or the bare value when [unit] is null (a percentage or a word
 * annotates itself). Rendered through
 * [io.github.seijikohara.femto.ui.theme.FitText], the pair shrinks together to
 * fit its slot rather than the unit being clipped away.
 *
 * The unit span sets weight and colour but deliberately no size: it inherits the
 * value's, which is what lets auto-size scale the whole reading. The standalone
 * [UnitSuffix] can hold its own size because it is measured on its own; here the
 * two are one layout, and the glance metrics that use this render value and unit
 * at the same size anyway.
 */
@Composable
internal fun valueWithUnit(
    value: String,
    unit: String?,
): AnnotatedString {
    val unitSpan =
        SpanStyle(
            fontWeight = MaterialTheme.typography.strongWeight,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = UNIT_SUFFIX_ALPHA),
        )
    return buildAnnotatedString {
        append(value)
        if (unit != null) {
            append(" ")
            withStyle(unitSpan) { append(unit) }
        }
    }
}
