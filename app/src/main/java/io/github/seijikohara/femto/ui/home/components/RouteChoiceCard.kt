package io.github.seijikohara.femto.ui.home.components

import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.routing.CANDIDATE_COUNT
import io.github.seijikohara.femto.ui.home.CandidateSummary
import io.github.seijikohara.femto.ui.home.RouteChoiceState
import io.github.seijikohara.femto.ui.locale.SpeedUnit
import io.github.seijikohara.femto.ui.locale.distanceLabel
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoIcon
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import java.util.Locale

/**
 * The route choice card (issue #389 phase 3): the routes found to the destination, over the map while the driver may
 * still choose. A header with Done, then one row of up to [CANDIDATE_COUNT] equal tiles, one per route: its number,
 * travel time and length, the one being followed marked. A tap on a tile makes that route the one followed ([onSelect]
 * with its index), and Done closes the card keeping it ([onClose]). While another route is being searched for, the
 * next slot holds a placeholder saying so; once the search has ended with the first route alone, the space beside it
 * says no other was found ([RouteChoiceState.noOtherRoute]).
 *
 * Side by side rather than one row each, the routes keep the card short enough to sit between the clock and the
 * vehicle's chevron on a head unit. The caller bounds the height; past the header, which keeps Done in reach, the
 * tiles scroll only where the dashboard leaves less room than they need.
 */
@Composable
internal fun RouteChoiceCard(
    choice: RouteChoiceState,
    speedUnit: SpeedUnit,
    onSelect: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState = rememberHazeState(),
    glassConfig: GlassConfig = GlassConfig(),
) = BoxWithConstraints(
    modifier =
        modifier
            .glassChrome(MaterialTheme.shapes.large, hazeState, glassConfig)
            .padding(CardInset),
) {
    // The width a tile leaves its text, which picks the travel time's units. Worked out here, above the tiles: the
    // tile row asks its tiles for their intrinsic height, which a layout reading its own constraints cannot give. In
    // whole px rounded down, as the row's weight split can leave one tile a pixel short of an even share.
    val density = LocalDensity.current
    val tileTextWidthPx =
        with(density) {
            (constraints.maxWidth - TileGap.roundToPx() * (CANDIDATE_COUNT - 1)) / CANDIDATE_COUNT -
                TilePaddingHorizontal.roundToPx() * 2
        }
    val timeStyle = MaterialTheme.typography.bodyMedium
    val measurer = rememberTextMeasurer()
    Column {
        RouteChoiceHeader(onClose = onClose)
        Row(
            modifier =
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth()
                    // Every tile as tall as the tallest, should a line wrap.
                    .height(IntrinsicSize.Min)
                    .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(TileGap),
        ) {
            choice.candidates.take(CANDIDATE_COUNT).forEachIndexed { index, candidate ->
                // The locale's short units ("1 hr, 5 min") where they fit the tile on one line, its narrow ones
                // ("1h 5m") where they do not. Never cut short: the line wraps if even those do not fit. Measured at
                // the current density and keyed on it, as the user's text size reaches the card through its font
                // scale, even while the card is open under Settings.
                val travelTime =
                    remember(candidate.durationSeconds, timeStyle, tileTextWidthPx, density) {
                        travelTimeText(candidate.durationSeconds).takeIf {
                            measurer.measure(it, timeStyle, maxLines = 1, density = density).size.width <=
                                tileTextWidthPx
                        } ?: travelTimeText(candidate.durationSeconds, width = MeasureFormat.FormatWidth.NARROW)
                    }
                CandidateTile(
                    number = index + 1,
                    travelTime = travelTime,
                    candidate = candidate,
                    selected = index == choice.selected,
                    speedUnit = speedUnit,
                    onClick = { onSelect(index) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
            // With every slot taken, the last branch adds no spacer.
            val free = CANDIDATE_COUNT - choice.candidates.size.coerceAtMost(CANDIDATE_COUNT)
            when {
                choice.finding && free > 0 -> {
                    FindingTile(modifier = Modifier.weight(1f).fillMaxHeight())
                    EmptySlots(count = free - 1)
                }

                // Only the first route, so the other slots are free.
                choice.noOtherRoute -> {
                    NoOtherRoute(modifier = Modifier.weight(free.toFloat()).fillMaxHeight())
                }

                else -> {
                    EmptySlots(count = free)
                }
            }
        }
    }
}

@Composable
private fun RouteChoiceHeader(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) = Row(
    modifier = modifier.fillMaxWidth().heightIn(min = FemtoDimens.MinTouchTarget),
    verticalAlignment = Alignment.CenterVertically,
) {
    Text(
        text = stringResource(R.string.route_choice_title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier =
            Modifier
                .weight(1f)
                .padding(horizontal = TilePaddingHorizontal)
                .semantics { heading() },
    )
    TextButton(
        onClick = onClose,
        modifier = TextButtonTouchTarget,
    ) {
        Text(text = stringResource(R.string.route_choice_close), style = MaterialTheme.typography.labelLarge)
    }
}

// Slots no tile fills keep their share of the width, so a tile keeps its width as routes arrive.
@Composable
private fun RowScope.EmptySlots(count: Int) =
    repeat(count) {
        Spacer(modifier = Modifier.weight(1f))
    }

// One route: its number, then its travel time and length, each on its own line. The selected tile takes the
// container colour and a check in its corner, over the tile's padding, so a narrow tile keeps the number's room.
@Composable
private fun CandidateTile(
    number: Int,
    travelTime: String,
    candidate: CandidateSummary,
    selected: Boolean,
    speedUnit: SpeedUnit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor =
        if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
    Box(
        modifier =
            modifier
                .heightIn(min = FemtoDimens.MinTouchTarget)
                .clip(MaterialTheme.shapes.medium)
                .background(
                    if (selected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                ).selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = TilePaddingHorizontal, vertical = TilePaddingVertical),
        ) {
            Text(
                text = stringResource(R.string.route_choice_candidate, number),
                style = MaterialTheme.typography.titleSmall,
                color = contentColor,
                modifier = Modifier.padding(end = SelectedMarkClearance),
            )
            Text(text = travelTime, style = MaterialTheme.typography.bodyMedium, color = contentColor)
            Text(
                text =
                    valueWithUnit(
                        value = remainingDistanceText(candidate.distanceMeters.toDouble(), speedUnit),
                        unit = speedUnit.distanceLabel(),
                    ),
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
            )
        }
        if (selected) {
            FemtoIcon(
                imageVector = Lucide.Check,
                contentDescription = null,
                tint = contentColor,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = TilePaddingVertical + SelectedMarkLineInset, end = SelectedMarkEndInset)
                        .size(SelectedMarkSize),
            )
        }
    }
}

// The next slot while another route is being searched for: the size of a tile, but outlined rather than filled, and
// nothing to tap.
@Composable
private fun FindingTile(modifier: Modifier = Modifier) =
    Box(
        modifier =
            modifier
                .heightIn(min = FemtoDimens.MinTouchTarget)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                .padding(horizontal = TilePaddingHorizontal, vertical = TilePaddingVertical),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = stringResource(R.string.route_choice_finding),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

// The space beside the first route once the search found no other.
@Composable
private fun NoOtherRoute(modifier: Modifier = Modifier) =
    Box(
        modifier = modifier.padding(horizontal = TilePaddingHorizontal, vertical = TilePaddingVertical),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = stringResource(R.string.route_choice_none),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

/**
 * Return [durationSeconds] as a travel time in [locale]'s units of [width], rounded to the minute and at least one
 * minute: "18 min", or "1 hr, 5 min" from an hour on, with no "0 min" on the hour; "1h 5m" in the narrow width.
 */
internal fun travelTimeText(
    durationSeconds: Int,
    locale: Locale = Locale.getDefault(),
    width: MeasureFormat.FormatWidth = MeasureFormat.FormatWidth.SHORT,
): String =
    ((durationSeconds + HALF_MINUTE_SECONDS) / SECONDS_PER_MINUTE).coerceAtLeast(1).let { minutes ->
        listOfNotNull(
            Measure(minutes / MINUTES_PER_HOUR, MeasureUnit.HOUR).takeIf { minutes >= MINUTES_PER_HOUR },
            Measure(minutes % MINUTES_PER_HOUR, MeasureUnit.MINUTE).takeIf { minutes % MINUTES_PER_HOUR != 0 },
        ).let { measures -> MeasureFormat.getInstance(locale, width).formatMeasures(*measures.toTypedArray()) }
    }

private const val HALF_MINUTE_SECONDS = 30
private const val SECONDS_PER_MINUTE = 60
private const val MINUTES_PER_HOUR = 60

// The card's own inset around its tiles, so a tile's fill stops short of the glass edge, and the gap between tiles.
private val CardInset = 4.dp
private val TileGap = 4.dp

// A tile's text padding. The vertical one is what leaves three 16 sp lines (20 dp each) inside the 64 dp floor.
private val TilePaddingHorizontal = 8.dp
private val TilePaddingVertical = 2.dp

// The selected tile's check: smaller than an inline icon, so it fits in the tile's corner beside "Route N", centred on
// that 20 dp line and [SelectedMarkEndInset] in from the tile's edge. The number keeps clear of it by
// [SelectedMarkClearance]: the check's width, its inset and a 2 dp gap, less the tile's own padding.
private val SelectedMarkSize = 16.dp
private val SelectedMarkLineInset = 2.dp
private val SelectedMarkEndInset = 4.dp
private val SelectedMarkClearance = SelectedMarkSize + SelectedMarkEndInset + 2.dp - TilePaddingHorizontal

// The narrowest tile that keeps "Route N" beside its check, and a short travel time, each on one line at the
// default font size. Declared after the insets it adds up with: top-level values initialise in file order.
private val RouteChoiceTileMinWidth = 84.dp

/** The card's widest, and its narrowest: room for [CANDIDATE_COUNT] tiles of [RouteChoiceTileMinWidth]. */
internal val RouteChoiceCardMaxWidth = 360.dp
internal val RouteChoiceCardMinWidth =
    RouteChoiceTileMinWidth * CANDIDATE_COUNT + TileGap * (CANDIDATE_COUNT - 1) + CardInset * 2

@PreviewLightDark
@Composable
private fun RouteChoiceCardPreview() {
    FemtoTheme {
        RouteChoiceCard(
            choice =
                RouteChoiceState(
                    session = 1L,
                    candidates =
                        listOf(
                            CandidateSummary(distanceMeters = 12_400, durationSeconds = 1_080),
                            CandidateSummary(distanceMeters = 13_900, durationSeconds = 1_260),
                        ),
                    selected = 0,
                    finding = true,
                    searchCompleted = false,
                ),
            speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
            onSelect = {},
            onClose = {},
            modifier = Modifier.width(360.dp),
        )
    }
}
