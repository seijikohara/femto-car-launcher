package io.github.seijikohara.femto.ui.home.components

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pause
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.RotateCcw
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.location.TripStats
import io.github.seijikohara.femto.ui.locale.SpeedUnit
import io.github.seijikohara.femto.ui.locale.distanceLabel
import io.github.seijikohara.femto.ui.locale.fromMetersPerSecond
import io.github.seijikohara.femto.ui.locale.label
import io.github.seijikohara.femto.ui.locale.tripDistanceFromMeters
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoIcon
import io.github.seijikohara.femto.ui.theme.eyebrow
import io.github.seijikohara.femto.ui.theme.glanceCaption
import io.github.seijikohara.femto.ui.theme.panelMetric
import io.github.seijikohara.femto.ui.theme.sectionLabel
import kotlin.math.roundToInt

private const val PLAY_SECONDS = 22f

/**
 * The maximized trip view: a full-bleed sci-fi wireframe flyover of the selected
 * trip (native Vulkan where available, 2D Compose fallback otherwise) with a
 * glass HUD layered on top — collapse control, a horizontal trip selector, the
 * trip's headline stats, and play / replay / scrub. Unlike the calendar and
 * weather maximize panels this is not the shared glass [MaximizePanel]: the
 * renderer *is* the background, so the HUD floats over it instead of inside a
 * blurred sheet.
 *
 * The draw-on playhead is driven here by a frame clock and handed to whichever
 * renderer is active, so both paths animate identically; the camera orbit is
 * self-driven inside each renderer.
 */
@Composable
internal fun TripPanel(
    onClose: () -> Unit,
    speedUnit: SpeedUnit,
    modifier: Modifier = Modifier,
) {
    val viewModel: TripVizViewModel = viewModel(factory = TripVizViewModelFactory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(onBack = onClose)

    val selection = uiState.selection
    var playing by remember { mutableStateOf(true) }
    var progress by remember { mutableFloatStateOf(0f) }

    // Reset and auto-play whenever a new trip's geometry loads.
    LaunchedEffect(selection?.tripId) {
        progress = 0f
        playing = selection != null
    }
    // Frame-clock playhead; the renderers are a pure function of it.
    LaunchedEffect(playing, selection?.tripId) {
        if (!playing || selection == null) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (progress < 1f) {
            val now = withFrameNanos { it }
            progress = (progress + (now - last).coerceAtLeast(0L) / 1_000_000_000f / PLAY_SECONDS).coerceAtMost(1f)
            last = now
        }
        playing = false
    }

    Box(modifier = modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(RenderBackground)) {
        // Background render fills the panel; the HUD composes on top.
        when {
            selection != null -> {
                TripFlyoverHost(selection.wireframe, progress, Modifier.fillMaxSize())
            }

            !uiState.loading && uiState.trips.isEmpty() -> {
                CenteredMessage(stringResource(R.string.trip_viz_empty))
            }

            else -> {
                CenteredMessage(stringResource(R.string.trip_viz_preparing))
            }
        }

        HudScrim(Modifier.align(Alignment.BottomCenter).fillMaxWidth())

        GlassCircleButton(
            icon = Lucide.ChevronDown,
            description = stringResource(R.string.panel_collapse),
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopStart).padding(FemtoDimens.CardPadding),
        )

        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = FemtoDimens.CardPadding, vertical = FemtoDimens.CardPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (uiState.trips.isNotEmpty()) {
                TripSelectorRow(
                    trips = uiState.trips,
                    selectedTripId = uiState.selectedTripId,
                    onSelect = { viewModel.onAction(TripVizAction.Select(it)) },
                )
            }
            selection?.let { current ->
                TripStatsRow(stats = current.stats, speedUnit = speedUnit)
                PlaybackControls(
                    playing = playing,
                    progress = progress,
                    onTogglePlay = {
                        // Replay from the start when toggling play after it finished.
                        if (!playing && progress >= 1f) progress = 0f
                        playing = !playing
                    },
                    onReplay = {
                        progress = 0f
                        playing = true
                    },
                    onScrub = {
                        playing = false
                        progress = it
                    },
                )
            }
        }
    }
}

@Composable
private fun TripFlyoverHost(
    wireframe: FloatArray,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val controller = remember { TripFlyoverController() }
    var useVulkan by remember { mutableStateOf(controller.ensureCreated()) }
    if (useVulkan) {
        TripFlyoverSurface(
            controller = controller,
            wireframe = wireframe,
            progress = progress,
            onUnavailable = { useVulkan = false },
            modifier = modifier,
        )
    } else {
        TripFlyoverFallback(wireframe = wireframe, progress = progress, modifier = modifier)
    }
}

@Composable
private fun TripSelectorRow(
    trips: List<TripListItem>,
    selectedTripId: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) = LazyRow(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
) {
    items(trips, key = { it.tripId }) { trip ->
        TripChip(trip = trip, selected = trip.tripId == selectedTripId, onClick = { onSelect(trip.tripId) })
    }
}

@Composable
private fun TripChip(
    trip: TripListItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val label =
        remember(trip.startMs) {
            DateUtils.formatDateTime(
                context,
                trip.startMs,
                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_ALL,
            )
        }
    val fill =
        if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
        }
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(12.dp))
                .background(fill)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.sectionLabel(12),
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Text(
            text =
                if (trip.isCurrent) {
                    stringResource(R.string.trip_viz_current)
                } else {
                    pluralStringResource(R.plurals.trip_viz_point_count, trip.pointCount, trip.pointCount)
                },
            style = MaterialTheme.typography.glanceCaption(),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            maxLines = 1,
        )
    }
}

@Composable
private fun TripStatsRow(
    stats: TripStats,
    speedUnit: SpeedUnit,
    modifier: Modifier = Modifier,
) = Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(20.dp),
) {
    StatCell(
        label = stringResource(R.string.speed_metric_distance),
        value = "%.1f".format(speedUnit.tripDistanceFromMeters(stats.distanceMeters)),
        unit = speedUnit.distanceLabel(),
    )
    StatCell(
        label = stringResource(R.string.trip_viz_duration),
        value = formatDuration(stats.endMs - stats.startMs),
        unit = null,
    )
    StatCell(
        label = stringResource(R.string.trip_viz_max_speed),
        value = "${speedUnit.fromMetersPerSecond(stats.maxSpeedMps).roundToInt()}",
        unit = speedUnit.label(),
    )
    if (stats.hasAltitude) {
        StatCell(
            label = stringResource(R.string.trip_viz_climb),
            value = "${stats.altitudeGainMeters.roundToInt()}",
            unit = "m",
        )
    }
}

@Composable
private fun StatCell(
    label: String,
    value: String,
    unit: String?,
    modifier: Modifier = Modifier,
) = Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(
        text = label,
        style = MaterialTheme.typography.sectionLabel(12),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = value,
            style = MaterialTheme.typography.panelMetric(),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier.alignByBaseline(),
        )
        if (unit != null) UnitSuffix(unit, modifier = Modifier.alignByBaseline())
    }
}

@Composable
private fun PlaybackControls(
    playing: Boolean,
    progress: Float,
    onTogglePlay: () -> Unit,
    onReplay: () -> Unit,
    onScrub: (Float) -> Unit,
    modifier: Modifier = Modifier,
) = Row(
    modifier = modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
) {
    GlassCircleButton(
        icon = if (playing) Lucide.Pause else Lucide.Play,
        description = stringResource(if (playing) R.string.trip_viz_pause else R.string.trip_viz_play),
        onClick = onTogglePlay,
    )
    GlassCircleButton(
        icon = Lucide.RotateCcw,
        description = stringResource(R.string.trip_viz_replay),
        onClick = onReplay,
    )
    Slider(
        value = progress,
        onValueChange = onScrub,
        modifier = Modifier.weight(1f),
    )
}

@Composable
private fun GlassCircleButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = Box(
    modifier =
        modifier
            .size(FemtoDimens.MinTouchTarget)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
    contentAlignment = Alignment.Center,
) {
    FemtoIcon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(26.dp),
    )
}

@Composable
private fun CenteredMessage(
    text: String,
    modifier: Modifier = Modifier,
) = Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Text(
        text = text,
        style = MaterialTheme.typography.eyebrow(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// A bottom-anchored gradient scrim so the HUD text stays legible over the bright
// wireframe without hiding the render.
@Composable
private fun HudScrim(modifier: Modifier = Modifier) =
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to RenderBackground.copy(alpha = 0.85f),
                    ),
                ),
    )

private fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}

// The dark render backdrop matches the native renderer's clear colour so the
// panel frame, empty states, and HUD scrim sit on the same near-black the
// wireframe glows on. A single scene-defining constant, like the mockup-derived
// alpha tuning already sanctioned in this package's overlays.
private val RenderBackground = Color(0xFF050810)
