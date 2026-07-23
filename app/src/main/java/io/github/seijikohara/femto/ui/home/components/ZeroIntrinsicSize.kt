package io.github.seijikohara.femto.ui.home.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.unit.Constraints

/**
 * Wrap [content] (exactly one layout node) so it reports ZERO intrinsic size on
 * one axis while passing the real measure — and the other axis's intrinsics —
 * straight through to the child. This takes the child out of an
 * intrinsic-driven parent's size vote on the zeroed axis: under
 * `width(IntrinsicSize.Max)` / `height(IntrinsicSize.Min)` the parent asks each
 * child for its intrinsic size, and a child whose intrinsic must not sway that
 * vote abstains by reporting zero; at measure time it simply fills whatever the
 * parent settled on.
 *
 * One SSOT, two call sites (the axis is the only difference — hence one policy
 * parameterised by it rather than two near-identical `MeasurePolicy` scaffolds):
 *  - [ZeroIntrinsicWidth] keeps SpeedOverlay's ticking geocoded address out of
 *    the overlay's max-intrinsic-width vote — ellipsis never shrinks intrinsics,
 *    so a long address would stretch the card and the next, shorter one shrink
 *    it back.
 *  - [ZeroIntrinsicHeight] keeps the music card's album art — an `aspectRatio`
 *    square — out of the row's min-intrinsic-height vote, so the art sizes off
 *    the meta column's height rather than its own width-derived height.
 *
 * The `modifier` on either wrapper must never carry a size-affecting modifier on
 * the zeroed axis — sizing this node would re-enter it into the very intrinsic
 * vote it exists to sit out.
 */
@Composable
internal fun ZeroIntrinsicWidth(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) = Layout(content = content, modifier = modifier, measurePolicy = ZeroIntrinsicWidthPolicy)

@Composable
internal fun ZeroIntrinsicHeight(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) = Layout(content = content, modifier = modifier, measurePolicy = ZeroIntrinsicHeightPolicy)

// Stateless, so hoisted to file-level singletons — no per-composition allocation.
private val ZeroIntrinsicWidthPolicy = ZeroIntrinsicSizePolicy(zeroWidth = true)
private val ZeroIntrinsicHeightPolicy = ZeroIntrinsicSizePolicy(zeroWidth = false)

// Passthrough single-child layout: nulls the intrinsic size on one axis and
// delegates the other axis (and the real measure) to the child. [zeroWidth]
// picks the axis — true zeroes the width intrinsics (height delegates to the
// child), false the reverse. Delegating the non-zeroed axis to the child's own
// intrinsic matches the `MeasurePolicy` default (which would measure this same
// passthrough child), so nulling one axis is the only behavioural change.
private class ZeroIntrinsicSizePolicy(
    private val zeroWidth: Boolean,
) : MeasurePolicy {
    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurables.single().measure(constraints)
        return layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurables: List<IntrinsicMeasurable>,
        height: Int,
    ): Int = if (zeroWidth) 0 else measurables.single().minIntrinsicWidth(height)

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurables: List<IntrinsicMeasurable>,
        height: Int,
    ): Int = if (zeroWidth) 0 else measurables.single().maxIntrinsicWidth(height)

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int,
    ): Int = if (zeroWidth) measurables.single().minIntrinsicHeight(width) else 0

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int,
    ): Int = if (zeroWidth) measurables.single().maxIntrinsicHeight(width) else 0
}
