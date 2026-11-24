package io.github.seijikohara.femto.ui.home.components

import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState
import io.github.seijikohara.femto.R
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
 * still choose. One row per route, the one being followed marked; a tap on a row makes that route the one followed
 * ([onSelect] with its index), and Done closes the card keeping it ([onClose]). While another route is being searched
 * for, a last line says so; once the search has ended with the first route alone, a line says no other was found.
 *
 * The caller bounds the height: past the header, which keeps Done in reach, the rows scroll when they do not fit.
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
) = Column(
    modifier =
        modifier
            .glassChrome(MaterialTheme.shapes.large, hazeState, glassConfig)
            .padding(CardInset),
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = FemtoDimens.MinTouchTarget),
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
                    .padding(horizontal = RowPadding)
                    .semantics { heading() },
        )
        TextButton(
            onClick = onClose,
            modifier = TextButtonTouchTarget,
        ) {
            Text(text = stringResource(R.string.route_choice_close), style = MaterialTheme.typography.labelLarge)
        }
    }
    Column(
        modifier =
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .selectableGroup(),
    ) {
        choice.candidates.forEachIndexed { index, candidate ->
            CandidateRow(
                number = index + 1,
                candidate = candidate,
                selected = index == choice.selected,
                speedUnit = speedUnit,
                onClick = { onSelect(index) },
            )
        }
        when {
            choice.finding -> NoteRow(text = stringResource(R.string.route_choice_finding))
            choice.noOtherRoute -> NoteRow(text = stringResource(R.string.route_choice_none))
        }
    }
}

// One route: its number, then its travel time and length. The travel time
// leads, as the figure a driver compares routes by, so a narrow card cuts the
// length's tail rather than the time's.
@Composable
private fun CandidateRow(
    number: Int,
    candidate: CandidateSummary,
    selected: Boolean,
    speedUnit: SpeedUnit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor =
        if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = FemtoDimens.MinTouchTarget)
                .clip(MaterialTheme.shapes.medium)
                .then(
                    if (selected) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier,
                ).selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .padding(horizontal = RowPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.route_choice_candidate, number),
                style = MaterialTheme.typography.titleSmall,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text =
                    buildAnnotatedString {
                        append(travelTimeText(candidate.durationSeconds))
                        append(" · ")
                        append(
                            valueWithUnit(
                                value = remainingDistanceText(candidate.distanceMeters.toDouble(), speedUnit),
                                unit = speedUnit.distanceLabel(),
                            ),
                        )
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            FemtoIcon(
                imageVector = Lucide.Check,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(FemtoDimens.InlineIconSize),
            )
        }
    }
}

// A line under the rows that is no route: the search under way, or its end
// with no other route. Nothing to tap, so it takes less than a row's height.
@Composable
private fun NoteRow(
    text: String,
    modifier: Modifier = Modifier,
) = Text(
    text = text,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    maxLines = 2,
    overflow = TextOverflow.Ellipsis,
    modifier =
        modifier
            .fillMaxWidth()
            .heightIn(min = NoteRowMinHeight)
            .padding(horizontal = RowPadding, vertical = 12.dp),
)

/**
 * Return [durationSeconds] as a travel time in [locale]'s short units, rounded to the minute and at least one minute:
 * "18 min", or "1 hr, 5 min" from an hour on, with no "0 min" on the hour.
 */
internal fun travelTimeText(
    durationSeconds: Int,
    locale: Locale = Locale.getDefault(),
): String =
    ((durationSeconds + HALF_MINUTE_SECONDS) / SECONDS_PER_MINUTE).coerceAtLeast(1).let { minutes ->
        listOfNotNull(
            Measure(minutes / MINUTES_PER_HOUR, MeasureUnit.HOUR).takeIf { minutes >= MINUTES_PER_HOUR },
            Measure(minutes % MINUTES_PER_HOUR, MeasureUnit.MINUTE).takeIf { minutes % MINUTES_PER_HOUR != 0 },
        ).let { measures ->
            MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.SHORT).formatMeasures(*measures.toTypedArray())
        }
    }

private const val HALF_MINUTE_SECONDS = 30
private const val SECONDS_PER_MINUTE = 60
private const val MINUTES_PER_HOUR = 60

// The card's own inset around its rows, so a selected row's tint stops short
// of the glass edge; each row pads its text by RowPadding inside it.
private val CardInset = 4.dp
private val RowPadding = 12.dp
private val NoteRowMinHeight = 48.dp

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
            modifier = Modifier.width(260.dp),
        )
    }
}
