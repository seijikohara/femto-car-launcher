package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
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
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark

/**
 * Glass compass for the map pane's top-left corner: the needle tracks the live
 * camera bearing (north on screen sits at `-bearing`), so it spins with a
 * heading-up camera and rests upright under north-up. Tapping flips the
 * persisted north-up ⇄ heading-up orientation — the rotation itself is the mode
 * feedback, so the button carries no separate state badge.
 */
@Composable
internal fun MapCompass(
    bearingDeg: Float,
    onTap: () -> Unit,
    hazeState: HazeState,
    glassConfig: GlassConfig,
    modifier: Modifier = Modifier,
) {
    val north = MaterialTheme.colorScheme.primary
    val south = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = SOUTH_NEEDLE_ALPHA)
    GlassControl(
        onClick = onTap,
        contentDescription = stringResource(R.string.map_compass_desc),
        hazeState = hazeState,
        glassConfig = glassConfig,
        modifier = modifier,
    ) {
        Canvas(
            modifier =
                Modifier
                    .size(CONTROL_ICON_SIZE)
                    .graphicsLayer { rotationZ = -bearingDeg },
        ) {
            // A two-tone diamond needle: the accent half points at geographic
            // north, the muted half at south — the universal compass glyph, no
            // lettering to localise.
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
}

/**
 * Vertical glass control column for the map pane's left-centre edge: an
 * optional locate (return-to-position) button above the zoom +/- pair. Zoom
 * steps write through the host into the persisted setting, so they work on
 * both render backends; locate only exists where a camera can detach
 * ([showLocate] = LIVE). The head unit has no multitouch, so the buttons are
 * the only zoom affordance there — never gate them on gesture support.
 */
@Composable
internal fun MapControlColumn(
    showLocate: Boolean,
    following: Boolean,
    onLocate: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    hazeState: HazeState,
    glassConfig: GlassConfig,
    modifier: Modifier = Modifier,
) = Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(CONTROL_GAP),
) {
    if (showLocate) {
        GlassControl(
            onClick = onLocate,
            contentDescription = stringResource(R.string.map_recenter_desc),
            hazeState = hazeState,
            glassConfig = glassConfig,
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
    GlassControl(
        onClick = onZoomIn,
        contentDescription = stringResource(R.string.map_zoom_in_desc),
        hazeState = hazeState,
        glassConfig = glassConfig,
    ) {
        ControlIcon(imageVector = Lucide.Plus)
    }
    GlassControl(
        onClick = onZoomOut,
        contentDescription = stringResource(R.string.map_zoom_out_desc),
        hazeState = hazeState,
        glassConfig = glassConfig,
    ) {
        ControlIcon(imageVector = Lucide.Minus)
    }
}

// One circular frosted-glass button, sized to the automotive tap floor and
// styled like the clock / speed overlays (glassEffect + hairline border).
@Composable
private fun GlassControl(
    onClick: () -> Unit,
    contentDescription: String,
    hazeState: HazeState,
    glassConfig: GlassConfig,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) = Box(
    modifier =
        modifier
            .size(FemtoDimens.MinTouchTarget)
            .glassChrome(CircleShape, hazeState, glassConfig)
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
    contentAlignment = Alignment.Center,
) {
    content()
}

@Composable
private fun ControlIcon(
    imageVector: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) = Icon(
    imageVector = imageVector,
    // The tappable GlassControl wrapper carries the description.
    contentDescription = null,
    tint = tint,
    modifier = modifier.size(CONTROL_ICON_SIZE),
)

// Control glyph size and the gap between stacked buttons. The glyph stays well
// inside the 64 dp glass disc so the controls read as restrained map chrome.
private val CONTROL_ICON_SIZE = 28.dp
private val CONTROL_GAP = 10.dp

// Compass needle: half-width of the waist as a fraction of the glyph width,
// and the muted alpha of the south half.
private const val NEEDLE_WAIST_FRACTION = 0.22f
private const val SOUTH_NEEDLE_ALPHA = 0.45f

@PreviewLightDark
@Preview(name = "Map controls", widthDp = 120, heightDp = 320)
@Composable
private fun MapControlsPreview() {
    FemtoTheme {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MapCompass(
                bearingDeg = 35f,
                onTap = {},
                hazeState = rememberHazeState(),
                glassConfig = GlassConfig(),
            )
            MapControlColumn(
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
}
