package io.github.seijikohara.femto.ui.home.components

import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.seijikohara.femto.ui.theme.FemtoDimens

/**
 * Shared hairline-divider recipe for the dashboard's glass chrome: a thin M3
 * divider at [FemtoDimens.DividerAlpha] over `outlineVariant`, so every seam
 * between adjacent segments (e.g. the dock's nav / status boundary) reads at
 * the same weight over the map. Callers own their own sizing and insets
 * (height / width, padding) — this composable only owns the color + thickness
 * recipe, so it does not get re-copied at every call site.
 */
@Composable
internal fun FemtoVerticalDivider(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = FemtoDimens.DividerAlpha),
) = VerticalDivider(modifier = modifier, color = color)

/** [FemtoVerticalDivider]'s counterpart for a horizontal seam. */
@Composable
internal fun FemtoHorizontalDivider(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = FemtoDimens.DividerAlpha),
) = HorizontalDivider(modifier = modifier, color = color)

/**
 * Shared cluster-divider length: the dock's nav/status seam dividers
 * ([DashboardDock]) size to this one constant so the chrome reads as the
 * same family across the dock's orientations.
 */
internal val FemtoDividerLength: Dp = 48.dp
