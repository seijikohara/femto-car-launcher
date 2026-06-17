package io.github.seijikohara.femto.ui.theme

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.takeOrElse

/**
 * Fit-to-width glance text — the SSOT for a single-line label that must stay
 * readable as its content length changes across locales and screen widths (the
 * weekday name, a track title, a metric value). The font shrinks within
 * `[minFontSize] .. style.fontSize` to fit [maxLines] in the available width,
 * then ellipsizes if even [minFontSize] overflows — so the automotive minimum is
 * never breached silently by auto-shrinking past it.
 *
 * Wraps the stable foundation auto-size path (`BasicText` +
 * `TextAutoSize.StepBased`, Stable in the pinned Compose foundation — verified
 * via javap; the material3 `Text(autoSize = …)` overload ships from an alpha
 * artifact). `BasicText` is theme-unaware, so this is the one place that injects
 * the Material content colour and a [Type.kt] style; callers pass a named
 * `Typography` extension / M3 role and never construct a `TextStyle` or touch
 * `BasicText` directly (see `.claude/rules/design-system.md`).
 *
 * The font only ever shrinks from the style's design size — `maxFontSize` is
 * pinned to `style.fontSize`, so auto-size is a fit-to-width safety valve, not a
 * free resizer that would break the Bold-Minimal scale.
 */
@Composable
internal fun FitText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    minFontSize: TextUnit = FemtoDimens.MinBodyTextSize,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    textAlign: TextAlign? = null,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
) {
    val resolved = color.takeOrElse { LocalContentColor.current }
    // The style's own size is the ceiling; fall back to the floor when a caller
    // passes a size-less style so StepBased always gets max >= min.
    val ceiling = style.fontSize.takeOrElse { minFontSize }
    BasicText(
        text = text,
        modifier = modifier,
        style = if (textAlign != null) style.copy(textAlign = textAlign) else style,
        color = { resolved },
        onTextLayout = onTextLayout,
        overflow = overflow,
        maxLines = maxLines,
        autoSize = TextAutoSize.StepBased(minFontSize = minFontSize, maxFontSize = ceiling),
    )
}
