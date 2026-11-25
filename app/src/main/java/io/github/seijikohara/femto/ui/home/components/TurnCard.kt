package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.routing.TurnKind
import io.github.seijikohara.femto.ui.home.TurnCardState
import io.github.seijikohara.femto.ui.home.TurnManeuver
import io.github.seijikohara.femto.ui.locale.SpeedUnit
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.FitText
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import io.github.seijikohara.femto.ui.theme.glanceCaption
import io.github.seijikohara.femto.ui.theme.heroNumeral
import io.github.seijikohara.femto.ui.theme.lineBoxHeight
import io.github.seijikohara.femto.ui.theme.singleLineBox
import io.github.seijikohara.femto.ui.theme.unitLabel

/**
 * The turn card (issue #389 phase 4): the next turn over the map while a route is followed. The turn's arrow, then its
 * distance over one line of text ("300 m", "Turn left"), and, when the turn after it follows closely
 * ([TurnCardState.then]), a small arrow for that one under "Then" at the end. No road names: the routing data has
 * none.
 *
 * The card shows in the first of its forms ([TurnCardForm]) that fits the room the caller gives it: the width at least
 * the form's [TurnCardForm.minWidth], and the form's measured height within the caller's height cap. Measured, so a
 * larger Display size and a larger font both count. Where no form fits, the card shows nothing. The caller gives the
 * width, up to [TurnCardMaxWidth].
 *
 * The card carries no control. In every form it reads as one sentence to a screen reader ("Turn left in 300 m. Then:
 * Turn right."), so its parts are merged into one node. The full form is at least [TurnCardHeight] tall, more where a
 * larger font needs it; its line of text shrinks to fit rather than wrap, at most to the 16 sp floor, which the longest
 * text still fits at [TurnCardMinWidth] (TurnCardTest).
 */
@Composable
internal fun TurnCard(
    state: TurnCardState,
    speedUnit: SpeedUnit,
    modifier: Modifier = Modifier,
    hazeState: HazeState = rememberHazeState(),
    glassConfig: GlassConfig = GlassConfig(),
) {
    val (value, unit) = turnDistance(state.distanceMeters, speedUnit)
    val text = turnText(state.maneuver)
    val thenText = state.then?.let { turnText(it) }
    val description =
        listOfNotNull(
            stringResource(R.string.turn_card_description, text, "$value $unit"),
            thenText?.let { stringResource(R.string.turn_card_description_then, it) },
        ).joinToString(" ")
    val content = TurnCardContent(state = state, value = value, unit = unit, text = text)
    val sizes = turnCardSizes(content)
    // Only the form that shows is composed: a form tried and left out would stay in the semantics tree beside it.
    BoxWithConstraints(modifier = modifier) {
        val width = if (constraints.hasBoundedWidth) maxWidth else TurnCardMaxWidth
        TurnCardForm.entries
            .filter { it != TurnCardForm.COMPACT_THEN || state.then != null }
            .firstOrNull { form -> width >= form.minWidth && sizes.fits(form, width, maxHeight) }
            ?.let { form ->
                Box(
                    modifier =
                        Modifier
                            .glassChrome(MaterialTheme.shapes.large, hazeState, glassConfig)
                            .semantics(mergeDescendants = true) { contentDescription = description },
                ) {
                    TurnCardBody(content, form)
                }
            }
    }
}

/** The turn card's forms, in the order the card tries them. */
internal enum class TurnCardForm(
    /** Whether the form shows the turn after next, where there is one. */
    val withThen: Boolean,
) {
    /** The arrow, the distance over the line of text, and the turn after next. */
    FULL(withThen = true),

    /** The arrow and the distance on one line, and the turn after next. */
    COMPACT_THEN(withThen = true),

    /** The arrow and the distance on one line. */
    COMPACT(withThen = false),
    ;

    /** The narrowest width the form fits. */
    val minWidth: Dp
        get() = if (this == FULL) TurnCardMinWidth else TurnCardCompactMinWidth
}

// What every form of the card draws from.
private data class TurnCardContent(
    val state: TurnCardState,
    val value: String,
    val unit: String,
    val text: String,
)

// What each form needs, worked out from the card's own sizes and its text as measured in the current font and
// Display size, so a larger font asks for more room just as a larger Display size does. The full form's line of text
// shrinks to fit any width from TurnCardMinWidth; the compact form keeps its text's size and needs its measured width.
private data class TurnCardSizes(
    val fullHeight: Dp,
    val compactHeight: Dp,
    val compactThenHeight: Dp,
    val compactWidth: Dp,
    val compactThenWidth: Dp,
) {
    fun fits(
        form: TurnCardForm,
        width: Dp,
        height: Dp,
    ): Boolean =
        when (form) {
            TurnCardForm.FULL -> fullHeight <= height
            TurnCardForm.COMPACT_THEN -> compactThenWidth <= width && compactThenHeight <= height
            TurnCardForm.COMPACT -> compactWidth <= width && compactHeight <= height
        }
}

// Every line on the card is pinned to its style's line box (singleLineBox), so the line boxes give the heights; the
// texts the compact form keeps at their size are measured for the widths.
@Composable
private fun turnCardSizes(content: TurnCardContent): TurnCardSizes {
    val typography = MaterialTheme.typography
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val distanceStyle = typography.heroNumeral()
    val captionStyle = typography.glanceCaption()
    val distanceLine = lineBoxHeight(distanceStyle)
    val textLine = lineBoxHeight(typography.titleMedium)
    val thenHeight = lineBoxHeight(captionStyle) + ThenArrowSize
    val fullThenHeight = if (content.state.then != null) thenHeight else 0.dp
    val caption = stringResource(R.string.turn_card_then)
    val unitStyle = typography.unitLabel()

    fun widthOf(
        text: String,
        style: TextStyle,
    ): Dp =
        with(density) {
            measurer
                .measure(text, style, maxLines = 1)
                .size.width
                .toDp()
        }

    val distanceWidth = widthOf(content.value, distanceStyle) + DistanceUnitGap + widthOf(content.unit, unitStyle)
    val thenWidth = maxOf(widthOf(caption, captionStyle), ThenArrowSize)
    val compactWidth = CardPaddingHorizontal * 2 + CompactArrowSize + ContentGap + distanceWidth
    val compactHeight = maxOf(CompactArrowSize, distanceLine) + CompactPaddingVertical * 2
    return TurnCardSizes(
        fullHeight =
            (maxOf(TurnArrowSize, distanceLine + textLine, fullThenHeight) + CardPaddingVertical * 2)
                .coerceAtLeast(TurnCardHeight),
        compactHeight = compactHeight,
        compactThenHeight = maxOf(compactHeight, thenHeight + CompactPaddingVertical * 2),
        compactWidth = compactWidth,
        // The spacer that carries the turn after next to the end sits between two gaps.
        compactThenWidth = compactWidth + ContentGap * 2 + thenWidth,
    )
}

// A form's content inside the chrome, filling the width the card is given.
@Composable
private fun TurnCardBody(
    content: TurnCardContent,
    form: TurnCardForm,
    modifier: Modifier = Modifier,
) {
    val contentColor = MaterialTheme.colorScheme.onSurface
    val full = form == TurnCardForm.FULL
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .then(if (full) Modifier.heightIn(min = TurnCardHeight) else Modifier)
                .padding(
                    horizontal = CardPaddingHorizontal,
                    vertical = if (full) CardPaddingVertical else CompactPaddingVertical,
                ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ContentGap),
    ) {
        TurnArrow(
            maneuver = content.state.maneuver,
            modifier = Modifier.size(if (full) TurnArrowSize else CompactArrowSize),
            color = contentColor,
        )
        if (full) {
            Column(modifier = Modifier.weight(1f)) {
                TurnDistance(value = content.value, unit = content.unit, color = contentColor)
                val textStyle = MaterialTheme.typography.titleMedium
                FitText(
                    text = content.text,
                    style = textStyle,
                    color = contentColor,
                    modifier = Modifier.singleLineBox(textStyle),
                )
            }
        } else {
            TurnDistance(value = content.value, unit = content.unit, color = contentColor)
        }
        if (form.withThen && content.state.then != null) {
            if (!full) {
                // Carries the turn after next to the end of the compact card.
                Spacer(modifier = Modifier.weight(1f))
            }
            ThenColumn(then = content.state.then, color = contentColor)
        }
    }
}

// The distance in the hero numeral, its unit beside it as UnitSuffix pairs them.
@Composable
private fun TurnDistance(
    value: String,
    unit: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val distanceStyle = MaterialTheme.typography.heroNumeral()
    // Pinned to the numeral's line, as the speed readout pins its own: a fallback face would otherwise grow the line
    // and push the text out of the card's height.
    Row(
        modifier = modifier.singleLineBox(distanceStyle),
        horizontalArrangement = Arrangement.spacedBy(DistanceUnitGap),
    ) {
        Text(
            text = value,
            style = distanceStyle,
            color = color,
            maxLines = 1,
            modifier = Modifier.alignByBaseline(),
        )
        UnitSuffix(unit, modifier = Modifier.alignByBaseline())
    }
}

// The turn after next: the "Then" caption, at the glance size, over its small arrow.
@Composable
private fun ThenColumn(
    then: TurnManeuver.Turn,
    color: Color,
    modifier: Modifier = Modifier,
) = Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
    val captionStyle = MaterialTheme.typography.glanceCaption()
    Text(
        text = stringResource(R.string.turn_card_then),
        style = captionStyle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier.singleLineBox(captionStyle),
    )
    TurnArrow(maneuver = then, modifier = Modifier.size(ThenArrowSize), color = color, showExitNumber = false)
}

// The arrows: the turn's own at the size of a tap target, so it reads at a glance; the turn after next's under half
// that, a step down. The compact form's own arrow sits between the two.
private val TurnArrowSize = 64.dp
private val ThenArrowSize = 28.dp
private val CompactArrowSize = 40.dp

private val CardPaddingHorizontal = 16.dp
private val CardPaddingVertical = 12.dp
private val CompactPaddingVertical = FemtoDimens.CardSectionGapCompact
private val ContentGap = 12.dp
private val DistanceUnitGap = 4.dp

/**
 * The full form's least height: the 64 dp arrow, or the 40 sp distance over the text's 24 sp line at the default text
 * size, with 12 dp above and below. A larger system font grows the text past the arrow, and the card with it.
 */
internal val TurnCardHeight = 88.dp

/**
 * The full form's narrowest: the longest line of text ("Enter the roundabout") at the 16 sp floor beside the arrow and
 * the turn after next. The test's face fits it from 300 dp; the rest is room for a wider face the driver may pick. A
 * phone in portrait leaves the card 328 dp. Its widest keeps it from spreading over the map on a wide screen.
 */
internal val TurnCardMinWidth = 320.dp
internal val TurnCardMaxWidth = 420.dp

/**
 * The compact form's narrowest: the 40 dp arrow beside the longest distance text up to three digits ("999 km"), with
 * the card's padding on either side. The test's face needs 173 dp; the rest, as for [TurnCardMinWidth], is room for a
 * wider face. The form is measured besides, so a longer text that would not fit leaves the card out rather than cut.
 */
internal val TurnCardCompactMinWidth = 200.dp

@PreviewLightDark
@Composable
private fun TurnCardPreview() {
    FemtoTheme {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            listOf(
                TurnCardState(maneuver = TurnManeuver.Turn(TurnKind.LEFT), distanceMeters = 300.0),
                TurnCardState(
                    maneuver = TurnManeuver.Turn(TurnKind.ROUNDABOUT_COUNTERCLOCKWISE, exitNumber = 2),
                    distanceMeters = 180.0,
                    then = TurnManeuver.Turn(TurnKind.KEEP_RIGHT),
                ),
                TurnCardState(maneuver = TurnManeuver.Arrive, distanceMeters = 1_240.0),
            ).forEach { state ->
                TurnCard(
                    state = state,
                    speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
                    modifier = Modifier.width(TurnCardMinWidth),
                )
            }
        }
    }
}
