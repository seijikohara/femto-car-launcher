package io.github.seijikohara.femto.ui.home.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.Lucide
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoIcon
import io.github.seijikohara.femto.ui.theme.eyebrow

/**
 * Shared full-screen "maximize" glass panel for the calendar and weather cards,
 * mirroring the music Now Playing panel: one glass sheet floated inside the
 * dashboard's dock-inset overlay region (the map shows through, the dock stays
 * operable). Renders a top bar — collapse (left) + uppercased [title] eyebrow +
 * an "open external app" action (right) — over the caller's [content], which
 * lays out inside a [BoxWithConstraints] so it can reflow portrait vs landscape.
 * The system back gesture collapses via [onClose].
 */
@Composable
internal fun MaximizePanel(
    title: String,
    onClose: () -> Unit,
    onOpenExternal: () -> Unit,
    openExternalLabel: String,
    modifier: Modifier = Modifier,
    hazeState: HazeState = rememberHazeState(),
    glassConfig: GlassConfig = GlassConfig(),
    content: @Composable BoxWithConstraintsScope.() -> Unit,
) {
    BackHandler(onBack = onClose)
    Surface(
        modifier = modifier.glassChrome(MaterialTheme.shapes.large, hazeState, glassConfig),
        shape = MaterialTheme.shapes.large,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(FemtoDimens.CardPadding),
            verticalArrangement = Arrangement.spacedBy(FemtoDimens.CardSectionGap),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PanelIconButton(
                    icon = Lucide.ChevronDown,
                    description = stringResource(R.string.panel_collapse),
                    onClick = onClose,
                )
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.eyebrow(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                PanelIconButton(
                    icon = Lucide.ExternalLink,
                    description = openExternalLabel,
                    onClick = onOpenExternal,
                )
            }
            BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f), content = content)
        }
    }
}

// 64 dp glass-panel icon button (collapse / open-external), matching the music
// panel's top-bar buttons.
@Composable
private fun PanelIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = Box(
    modifier =
        modifier
            .size(FemtoDimens.MinTouchTarget)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
    contentAlignment = Alignment.Center,
) {
    FemtoIcon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(28.dp),
    )
}
