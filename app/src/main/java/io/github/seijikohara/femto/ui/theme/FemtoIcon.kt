package io.github.seijikohara.femto.ui.theme

import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath

/**
 * App-wide icon with a lighter stroke. The Lucide set bakes a fixed 2.0 stroke
 * into every [ImageVector] with no runtime override, so [FemtoIcon] rebuilds the
 * vector at [FEMTO_ICON_STROKE] before handing it to the Material [Icon]. Only
 * stroked paths (Lucide's outline icons) are thinned; filled vectors pass through
 * unchanged. The rebuilt vector is remembered per source, so the copy runs once.
 *
 * Mirrors the [Icon] signature so it is a drop-in replacement at the call sites.
 */
@Composable
internal fun FemtoIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    val thinned = remember(imageVector) { imageVector.withStrokeWidth(FEMTO_ICON_STROKE) }
    Icon(imageVector = thinned, contentDescription = contentDescription, modifier = modifier, tint = tint)
}

// Lucide ships at a 2.0 stroke; 1.5 reads lighter while staying legible on a dim
// head unit. One knob for the whole app's icon weight.
private const val FEMTO_ICON_STROKE = 1.5f

// Rebuild [this] with [width] as the stroke width on every stroked path, leaving
// filled paths (no stroke) untouched. ImageVector is immutable, so the node tree
// is copied through a fresh Builder.
private fun ImageVector.withStrokeWidth(width: Float): ImageVector =
    ImageVector
        .Builder(
            name = name,
            defaultWidth = defaultWidth,
            defaultHeight = defaultHeight,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            tintColor = tintColor,
            tintBlendMode = tintBlendMode,
            autoMirror = autoMirror,
        ).addThinnedNodes(root, width)
        .build()

private fun ImageVector.Builder.addThinnedNodes(
    group: VectorGroup,
    width: Float,
): ImageVector.Builder {
    group.forEach { node ->
        when (node) {
            is VectorPath -> {
                addPath(
                    pathData = node.pathData,
                    pathFillType = node.pathFillType,
                    name = node.name,
                    fill = node.fill,
                    fillAlpha = node.fillAlpha,
                    stroke = node.stroke,
                    strokeAlpha = node.strokeAlpha,
                    // Only restroke outline icons; a filled path carries no stroke.
                    strokeLineWidth = if (node.stroke != null) width else node.strokeLineWidth,
                    strokeLineCap = node.strokeLineCap,
                    strokeLineJoin = node.strokeLineJoin,
                    strokeLineMiter = node.strokeLineMiter,
                    trimPathStart = node.trimPathStart,
                    trimPathEnd = node.trimPathEnd,
                    trimPathOffset = node.trimPathOffset,
                )
            }

            is VectorGroup -> {
                // Push the group, copy its children thinned, then pop — the Builder
                // exposes the addGroup / clearGroup stack, not a group {} DSL.
                addGroup(
                    name = node.name,
                    rotate = node.rotation,
                    pivotX = node.pivotX,
                    pivotY = node.pivotY,
                    scaleX = node.scaleX,
                    scaleY = node.scaleY,
                    translationX = node.translationX,
                    translationY = node.translationY,
                    clipPathData = node.clipPathData,
                )
                addThinnedNodes(node, width)
                clearGroup()
            }
        }
    }
    return this
}
