package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.LocateFixed
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Minus
import com.composables.icons.lucide.Plus
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoIcon
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark

/**
 * The map pane's one control rail, a glass capsule in the top corner opposite
 * the cards: the zoom +/- pair, the optional locate (return-to-position)
 * segment, then the compass, separated by hairline dividers inside one
 * continuous frosted pill.
 *
 * One rail, not a compass disc in the corner and a control pill at the mid
 * edge: the two islands sat 85–160 dp apart on the wider geometries, and a
 * mid-edge pill collided with the speed overlay and the portrait card band —
 * on a portrait phone the whole pill sat under the cards, unreachable.
 * Anchored to the top corner the rail clears the bottom-anchored speed overlay
 * on every recorded geometry but the shortest, and there it yields instead of
 * colliding: the host bounds the rail's height to the room above the overlay
 * (see DashboardScaffold's rail reserve), and the segments are placed whole
 * from the top until that room runs out ([FitWholeRows]) — the compass first
 * to go, then locate, while the zoom pair always stays. That order is the
 * driving priority: the head unit has no multitouch, so the zoom buttons are
 * its only zoom affordance; locate is the way back once a drag has detached
 * the camera; the compass toggles a persisted orientation that Settings also
 * carries.
 *
 * The compass needle tracks the live camera bearing (north on screen sits at
 * `-bearing`), so it spins with a heading-up camera and rests upright under
 * north-up; tapping it flips the persisted north-up ⇄ heading-up orientation —
 * the rotation itself is the mode feedback, so the segment carries no separate
 * state badge. Zoom steps write through the host into the persisted setting, so
 * they work on both render backends; locate only exists where a camera can
 * detach ([showLocate] = LIVE).
 *
 * Deliberately compact: the segments sit below the FemtoDimens.MinTouchTarget
 * automotive floor as an explicit owner decision (the full-size discs crowded
 * the map pane).
 */
@Composable
internal fun MapControlRail(
    // Deferred read: the bearing updates at up to ~6.7 Hz while turning. Taking it
    // as a lambda and reading it inside the graphicsLayer block below keeps those
    // updates in the layer phase, so only the layer re-records — the rail never
    // recomposes and the dashboard above it never re-lays-out per event.
    bearingDeg: () -> Float,
    onCompassTap: () -> Unit,
    showLocate: Boolean,
    following: Boolean,
    onLocate: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    hazeState: HazeState,
    glassConfig: GlassConfig,
    modifier: Modifier = Modifier,
) = FitWholeRows(
    modifier =
        modifier
            .width(MapControlsStripWidth)
            .glassChrome(MaterialTheme.shapes.large, hazeState, glassConfig),
    // The zoom pair is never dropped; every segment after it may be (see the
    // KDoc). Each later segment carries its own leading divider so a dropped
    // segment takes its divider with it and the capsule never ends on a line.
    mandatoryCount = ZOOM_SEGMENTS,
) {
    GroupSegment(
        onClick = onZoomIn,
        contentDescription = stringResource(R.string.map_zoom_in_desc),
    ) {
        ControlIcon(imageVector = Lucide.Plus)
    }
    DividedSegment(
        onClick = onZoomOut,
        contentDescription = stringResource(R.string.map_zoom_out_desc),
    ) {
        ControlIcon(imageVector = Lucide.Minus)
    }
    if (showLocate) {
        DividedSegment(
            onClick = onLocate,
            contentDescription = stringResource(R.string.map_recenter_desc),
        ) {
            // Accent while detached — the tap has an effect (the camera is off
            // wandering); muted while already following.
            ControlIcon(
                imageVector = Lucide.LocateFixed,
                tint =
                    if (following) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
            )
        }
    }
    DividedSegment(
        onClick = onCompassTap,
        contentDescription = stringResource(R.string.map_compass_desc),
    ) {
        CompassNeedle(bearingDeg = bearingDeg)
    }
}

// A segment led by its divider, as one child of the rail's FitWholeRows.
@Composable
private fun DividedSegment(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) = Column(modifier = modifier) {
    GroupDivider()
    GroupSegment(onClick = onClick, contentDescription = contentDescription, content = content)
}

// The compass glyph: a two-tone diamond needle — the accent half points at
// geographic north, the muted half at south — the universal compass glyph, no
// lettering to localise.
@Composable
private fun CompassNeedle(
    bearingDeg: () -> Float,
    modifier: Modifier = Modifier,
) {
    val north = MaterialTheme.colorScheme.primary
    val south = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = SOUTH_NEEDLE_ALPHA)
    Canvas(
        modifier =
            modifier
                .size(CONTROL_ICON_SIZE)
                .graphicsLayer { rotationZ = -bearingDeg() },
    ) {
        val w = size.width
        val h = size.height
        val waist = w * NEEDLE_WAIST_FRACTION
        drawPath(
            Path().apply {
                moveTo(w / 2f, 0f)
                lineTo(w / 2f + waist, h / 2f)
                lineTo(w / 2f - waist, h / 2f)
                close()
            },
            color = north,
        )
        drawPath(
            Path().apply {
                moveTo(w / 2f, h)
                lineTo(w / 2f + waist, h / 2f)
                lineTo(w / 2f - waist, h / 2f)
                close()
            },
            color = south,
        )
    }
}

// One tappable row of the grouped pill; the pill owns the glass chrome, the
// segment only sizes and centres its glyph.
@Composable
private fun GroupSegment(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) = Box(
    modifier =
        modifier
            .fillMaxWidth()
            .height(SEGMENT_HEIGHT)
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
    contentAlignment = Alignment.Center,
) {
    content()
}

// Hairline separator between segments — inset from the pill edges (not edge-to-
// edge) so it floats inside the capsule rather than cutting across it. Shares the
// dashboard divider opacity with the speed overlay and dock.
@Composable
private fun GroupDivider(modifier: Modifier = Modifier) =
    HorizontalDivider(
        modifier = modifier.padding(horizontal = GROUP_DIVIDER_INSET),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = FemtoDimens.DividerAlpha),
    )

@Composable
private fun ControlIcon(
    imageVector: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) = FemtoIcon(
    imageVector = imageVector,
    // The tappable wrapper carries the description.
    contentDescription = null,
    tint = tint,
    modifier = modifier.size(CONTROL_ICON_SIZE),
)

// Compact control geometry (an explicit owner decision below the automotive
// touch floor — see the MapControlRail KDoc): the rail's width / per-segment
// height, the shared glyph size, and the horizontal inset that keeps the segment
// dividers off the pill edges. The rail's corner is MaterialTheme.shapes.large,
// shared with the other glass panels.
//
// Width of the rail — shared with ExposedMapRegion (MapPanel), whose centred
// content insets past this strip so text never slides under the controls
// riding the exposed region's driver-side edge, and with the dashboard, which
// keeps the portrait header band clear of it.
internal val MapControlsStripWidth = 48.dp
private val SEGMENT_HEIGHT = 48.dp
private val CONTROL_ICON_SIZE = 22.dp
private val GROUP_DIVIDER_INSET = 12.dp

// The leading segments the rail always keeps: zoom in and zoom out.
private const val ZOOM_SEGMENTS = 2

// Compass needle: half-width of the waist as a fraction of the glyph width,
// and the muted alpha of the south half.
private const val NEEDLE_WAIST_FRACTION = 0.22f
private const val SOUTH_NEEDLE_ALPHA = 0.45f

@PreviewLightDark
@Preview(name = "Map control rail", widthDp = 100, heightDp = 240)
@Composable
private fun MapControlRailPreview() {
    FemtoTheme {
        MapControlRail(
            bearingDeg = { 35f },
            onCompassTap = {},
            showLocate = true,
            following = false,
            onLocate = {},
            onZoomIn = {},
            onZoomOut = {},
            hazeState = rememberHazeState(),
            glassConfig = GlassConfig(),
        )
    }
}
