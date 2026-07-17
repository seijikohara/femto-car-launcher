package io.github.seijikohara.femto.ui.home.components

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
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
import io.github.seijikohara.femto.ui.theme.TripSceneBackground
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

    // The VM is activity-scoped (obtained without a key), so refresh the trip
    // list every time the panel re-enters composition — otherwise reopening the
    // flyover after more driving would show the list from the first open.
    LaunchedEffect(Unit) { viewModel.onAction(TripVizAction.Refresh) }

    val selection = uiState.selection
    var playing by remember { mutableStateOf(true) }
    var progress by remember { mutableFloatStateOf(0f) }

    // Reset and auto-play whenever a new trip's geometry loads.
    LaunchedEffect(selection?.tripId) {
        progress = 0f
        playing = selection != null
    }
    // Frame-clock playhead; the renderers are a pure function of it. (The panel
    // recomposing each frame is a non-issue — strong skipping keeps the selector
    // and stats, whose inputs don't change, off the recomposition.)
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

    // No opaque background on the panel itself: the native path is a media-overlay
    // SurfaceView composited *behind* the window, so the render only shows where
    // the Compose surface is transparent. Each state paints its own dark backdrop
    // (the SurfaceView clears itself; the fallback and messages fill it).
    Box(modifier = modifier.fillMaxSize().clip(RoundedCornerShape(24.dp))) {
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
                        progress = it.coerceIn(0f, 1f)
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
    // Probe Vulkan off the composition path (VkInstance + device creation is heavy
    // work): null = still probing, true = native, false = 2D fallback. The probe
    // resolves within the same window the trip geometry is loading in, so the dark
    // scene shows first either way.
    var useVulkan by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(controller) { useVulkan = controller.ensureCreated() }
    when (useVulkan) {
        true -> {
            // Transparent: the native SurfaceView (media overlay) shows through
            // and clears itself to the render backdrop.
            TripFlyoverSurface(
                controller = controller,
                wireframe = wireframe,
                progress = progress,
                onUnavailable = { useVulkan = false },
                modifier = modifier,
            )
        }

        false -> {
            // The Compose fallback draws in the window, so it paints its own dark
            // backdrop (its additive lines must blend over black, not the dashboard).
            TripFlyoverFallback(
                wireframe = wireframe,
                progress = progress,
                modifier = modifier.background(TripSceneBackground),
            )
        }

        null -> {
            Box(modifier.fillMaxSize().background(TripSceneBackground))
        }
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
                // Hold the automotive tap floor even though the chip's content is
                // shorter (CLAUDE.md#automotive-overrides).
                .heightIn(min = FemtoDimens.MinTouchTarget)
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

// Locale-aware duration through string resources rather than baked-in "h/m/s".
@Composable
private fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val hours = (totalSeconds / 3600).toInt()
    val minutes = ((totalSeconds % 3600) / 60).toInt()
    val seconds = (totalSeconds % 60).toInt()
    return when {
        hours > 0 -> stringResource(R.string.trip_viz_duration_hm, hours, minutes)
        minutes > 0 -> stringResource(R.string.trip_viz_duration_ms, minutes, seconds)
        else -> stringResource(R.string.trip_viz_duration_s, seconds)
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
    FlyoverScrubBar(
        progress = progress,
        onScrub = onScrub,
        modifier = Modifier.weight(1f),
    )
}

/**
 * Draw-on scrubber: a thin track centred in a full [FemtoDimens.MinTouchTarget]
 * gesture surface so the automotive tap floor holds — the project's deliberate
 * choice over the shorter M3 Slider (see PlaybackSeekBar). A tap or drag reports
 * the fraction; while dragging, the local value leads so the bar tracks the
 * finger even though the panel's frame clock keeps advancing.
 */
@Composable
private fun FlyoverScrubBar(
    progress: Float,
    onScrub: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val shown = (drag ?: progress).coerceIn(0f, 1f)
    val scrubLabel = stringResource(R.string.trip_viz_scrub)
    Box(
        modifier =
            modifier
                .height(FemtoDimens.MinTouchTarget)
                .progressSemantics(shown)
                .semantics {
                    contentDescription = scrubLabel
                    setProgress(label = scrubLabel) { target ->
                        onScrub(target.coerceIn(0f, 1f))
                        true
                    }
                }.pointerInput(Unit) {
                    detectTapGestures { offset -> onScrub((offset.x / size.width).coerceIn(0f, 1f)) }
                }.pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset -> drag = (offset.x / size.width).coerceIn(0f, 1f) },
                        onHorizontalDrag = { change, _ ->
                            val f = (change.position.x / size.width).coerceIn(0f, 1f)
                            drag = f
                            onScrub(f)
                            change.consume()
                        },
                        onDragEnd = { drag = null },
                        onDragCancel = { drag = null },
                    )
                },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(shown)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
        )
    }
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
) = Box(modifier = modifier.fillMaxSize().background(TripSceneBackground), contentAlignment = Alignment.Center) {
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
                        1f to TripSceneBackground.copy(alpha = 0.85f),
                    ),
                ),
    )
