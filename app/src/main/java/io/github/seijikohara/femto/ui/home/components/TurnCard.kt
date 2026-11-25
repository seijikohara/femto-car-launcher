package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.routing.TurnKind
import io.github.seijikohara.femto.ui.home.TurnCardState
import io.github.seijikohara.femto.ui.home.TurnManeuver
import io.github.seijikohara.femto.ui.locale.SpeedUnit
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.FitText
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import io.github.seijikohara.femto.ui.theme.glanceCaption
import io.github.seijikohara.femto.ui.theme.heroNumeral
import io.github.seijikohara.femto.ui.theme.singleLineBox

/**
 * The turn card (issue #389 phase 4): the next turn over the map while a route is followed. The turn's arrow, then its
 * distance over one line of text ("300 m", "Turn left"), and, when the turn after it follows closely
 * ([TurnCardState.then]), a small arrow for that one under "Then" at the end. No road names: the routing data has
 * none.
 *
 * The card carries no control. It reads as one sentence to a screen reader ("Turn left in 300 m. Then: Turn right."),
 * so its parts are merged into one node. Its height is fixed at [TurnCardHeight]; the caller gives the width, between
 * [TurnCardMinWidth] and [TurnCardMaxWidth]. The line of text shrinks to fit rather than wrap, at most to the 16 sp
 * floor, which the longest text still fits at the narrowest width (TurnCardTest).
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
    val contentColor = MaterialTheme.colorScheme.onSurface
    Row(
        modifier =
            modifier
                .height(TurnCardHeight)
                .glassChrome(MaterialTheme.shapes.large, hazeState, glassConfig)
                .semantics(mergeDescendants = true) { contentDescription = description }
                .padding(horizontal = CardPaddingHorizontal),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ContentGap),
    ) {
        TurnArrow(maneuver = state.maneuver, modifier = Modifier.size(TurnArrowSize), color = contentColor)
        Column(modifier = Modifier.weight(1f)) {
            val distanceStyle = MaterialTheme.typography.heroNumeral()
            // Pinned to the numeral's line, as the speed readout pins its own: a fallback face would otherwise grow
            // the line and push the text out of the fixed height.
            Row(modifier = Modifier.singleLineBox(distanceStyle), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = value,
                    style = distanceStyle,
                    color = contentColor,
                    maxLines = 1,
                    modifier = Modifier.alignByBaseline(),
                )
                UnitSuffix(unit, modifier = Modifier.alignByBaseline())
            }
            FitText(text = text, style = MaterialTheme.typography.titleMedium, color = contentColor)
        }
        if (state.then != null) {
            ThenColumn(then = state.then, color = contentColor)
        }
    }
}

// The turn after next: the "Then" caption, at the glance size, over its small arrow.
@Composable
private fun ThenColumn(
    then: TurnManeuver.Turn,
    color: Color,
    modifier: Modifier = Modifier,
) = Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
    Text(
        text = stringResource(R.string.turn_card_then),
        style = MaterialTheme.typography.glanceCaption(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
    TurnArrow(maneuver = then, modifier = Modifier.size(ThenArrowSize), color = color)
}

// The arrows: the turn's own at the size of a tap target, so it reads at a glance; the turn after next's under half
// that, a step down.
private val TurnArrowSize = 64.dp
private val ThenArrowSize = 28.dp

private val CardPaddingHorizontal = 16.dp
private val ContentGap = 12.dp

/**
 * The card's height: the 64 dp arrow, or the 40 sp distance over the text's 24 sp line at the default text size, with
 * 12 dp above and below.
 */
internal val TurnCardHeight = 88.dp

/**
 * The card's narrowest: the longest line of text ("Enter the roundabout") at the 16 sp floor beside the arrow
 * and the turn after next. Its widest keeps it from spreading over the map on a wide screen.
 */
internal val TurnCardMinWidth = 360.dp
internal val TurnCardMaxWidth = 420.dp

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
