package io.github.seijikohara.femto.ui.home.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import io.github.seijikohara.femto.data.routing.TurnKind
import io.github.seijikohara.femto.ui.home.TurnManeuver
import org.junit.Test
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The turn card's arrows, checked on their shape description rather than on pixels: which glyph each turn kind gets,
 * which side it bends to, the head segment's angle, that left and right are mirror images, and that every glyph keeps
 * inside its square with the margin, which is what keeps it unclipped at every size.
 */
class TurnGlyphTest {
    @Test
    fun `every turn kind and the destination get their glyph`() {
        assertEquals(
            EXPECTED_GLYPHS,
            EXPECTED_GLYPHS.keys.associateWith { turnGlyph(it) },
        )
    }

    @Test
    fun `the glyph table covers every turn kind`() {
        assertEquals(
            TurnKind.entries.toSet(),
            EXPECTED_GLYPHS.keys
                .filterIsInstance<TurnManeuver.Turn>()
                .map { it.kind }
                .toSet(),
        )
    }

    @Test
    fun `the head segment leaves the stem at the kind's canonical angle`() {
        assertEquals(
            listOf(0, -45, -90, -135, 45, 90, 135),
            listOf(0, -45, -90, -135, 45, 90, 135).map { headAngleDegrees(TurnGlyph.Bend(it).shape()) },
        )
    }

    @Test
    fun `left kinds bend to the left of the stem, right kinds to the right`() {
        assertEquals(
            SIDED_KINDS.map { (left, right) -> left.name to right.name },
            SIDED_KINDS.map { (left, right) ->
                (if (headSide(shapeOf(left)) < 0) left.name else "") to
                    (if (headSide(shapeOf(right)) > 0) right.name else "")
            },
        )
    }

    @Test
    fun `a plain U-turn turns to the side it is given`() {
        assertEquals(
            -1 to 1,
            headSide(turnGlyph(uTurn(leftward = true)).shape()) to headSide(turnGlyph(uTurn(leftward = false)).shape()),
        )
    }

    @Test
    fun `every left glyph is the mirror image of its right glyph`() {
        SIDED_KINDS.forEach { (left, right) ->
            assertSameShape(shapeOf(right).mirrored(), shapeOf(left), "${left.name} against ${right.name}")
        }
    }

    @Test
    fun `the clockwise ring is the mirror image of the counter-clockwise one`() {
        assertSameShape(
            ring(TurnKind.ROUNDABOUT_COUNTERCLOCKWISE).mirrored(),
            ring(TurnKind.ROUNDABOUT_CLOCKWISE),
            "rings",
        )
    }

    @Test
    fun `clockwise and counter-clockwise rings differ only in where the arrowheads point`() {
        val clockwise = ring(TurnKind.ROUNDABOUT_CLOCKWISE)
        val counterClockwise = ring(TurnKind.ROUNDABOUT_COUNTERCLOCKWISE)
        assertEquals(
            Triple(true, true, false),
            Triple(
                clockwise.strokes == counterClockwise.strokes,
                clockwise.label == counterClockwise.label,
                clockwise.heads.headTips() == counterClockwise.heads.headTips(),
            ),
        )
    }

    // Seen from above with y down, clockwise traffic moves down on the ring's right-hand side.
    @Test
    fun `the clockwise ring's arrowheads point clockwise`() {
        val rightHead = ring(TurnKind.ROUNDABOUT_CLOCKWISE).heads.maxBy { it.tip.x }
        assertTrue(rightHead.tip.y > rightHead.baseCenter.y, "the right-hand arrowhead does not point down")
    }

    @Test
    fun `the ring sets the exit number in its middle, and none for exit 0`() {
        assertEquals(
            "2" to null,
            ring(TurnKind.ROUNDABOUT_COUNTERCLOCKWISE, exitNumber = 2).label to
                ring(TurnKind.ROUNDABOUT_CLOCKWISE).label,
        )
    }

    // Keep right: one arrowhead, on the right-hand branch; the left-hand branch faint and ending below that head.
    // The number's box taken generously, each digit 0.6 of the number's size wide and 0.75 of it tall, as no face's
    // digits reach; its gap to each arrowhead, past the arrowhead's outline, must stay open.
    @Test
    fun `a two-digit exit number keeps clear of the ring's arrowheads`() {
        val label = "12"
        val size = ringLabelSize(label)
        val box =
            Rect(
                left = 0.5f - label.length * DIGIT_ADVANCE * size / 2f,
                top = 0.5f - DIGIT_HEIGHT * size / 2f,
                right = 0.5f + label.length * DIGIT_ADVANCE * size / 2f,
                bottom = 0.5f + DIGIT_HEIGHT * size / 2f,
            )
        val gaps =
            listOf(TurnKind.ROUNDABOUT_CLOCKWISE, TurnKind.ROUNDABOUT_COUNTERCLOCKWISE).flatMap { kind ->
                ring(kind, exitNumber = 12).heads.map { head -> gap(box, head.points) }
            }
        assertTrue(gaps.all { it > HEAD_OUTLINE / 2f }, "gaps $gaps")
    }

    @Test
    fun `keep is a fork with the branch to take headed and the other faint and shorter`() {
        val shape = shapeOf(TurnKind.KEEP_RIGHT)
        val faint = shape.strokes.single { it.faint }
        assertEquals(
            Triple(1, true, true),
            Triple(
                shape.heads.size,
                faint.points.last().x < stemX(shape),
                faint.points.minOf { it.y } > shape.heads
                    .single()
                    .tip.y,
            ),
        )
    }

    // Exit right: the faint stroke goes on straight up from the stem's line, above where the stem ends.
    @Test
    fun `exit carries the stem on straight and faint past the branch`() {
        val shape = shapeOf(TurnKind.EXIT_RIGHT)
        val faint = shape.strokes.single { it.faint }
        assertEquals(
            true to true,
            faint.points.all { abs(it.x - stemX(shape)) < EPSILON } to
                (
                    faint.points.minOf { it.y } < shape.strokes
                        .first()
                        .points
                        .minOf { it.y }
                ),
        )
    }

    @Test
    fun `a U-turn's arrowhead points back down`() {
        val head = shapeOf(TurnKind.U_TURN_LEFT).heads.single()
        assertTrue(head.tip.y > head.baseCenter.y)
    }

    @Test
    fun `every glyph keeps inside its square with the margin`() {
        EXPECTED_GLYPHS.values.distinct().forEach { glyph ->
            val ink = glyph.shape().inkBounds()
            assertTrue(
                ink.left >= GLYPH_MARGIN - EPSILON &&
                    ink.top >= GLYPH_MARGIN - EPSILON &&
                    ink.right <= 1f - GLYPH_MARGIN + EPSILON &&
                    ink.bottom <= 1f - GLYPH_MARGIN + EPSILON,
                "$glyph inks $ink",
            )
        }
    }

    // Drawn in the arrows' own stroke, a pole as tall as their stems with a solid pennant at its top flying right.
    @Test
    fun `the destination is a pole with a pennant at its top`() {
        val shape = turnGlyph(TurnManeuver.Arrive).shape()
        val pole = shape.strokes.single().points
        val pennant = shape.heads.single()
        assertEquals(
            listOf(true, true, true, true),
            listOf(
                abs(pole.first().x - pole.last().x) < EPSILON,
                pennant.points.drop(1).all { abs(it.x - pole.first().x) < EPSILON },
                pennant.tip.x > pole.first().x,
                pennant.points.minOf { it.y } - pole.minOf { it.y } < EPSILON,
            ),
        )
    }

    @Test
    fun `the flag stands as tall as a stemmed arrow`() {
        assertEquals(
            turnGlyph(TurnManeuver.Turn(TurnKind.STRAIGHT)).shape().inkBounds().height,
            turnGlyph(TurnManeuver.Arrive).shape().inkBounds().height,
            EPSILON,
        )
    }

    // The distance between [box] and [triangle], or -1 where they meet: each outline sampled finely and measured
    // against the other's edges.
    private fun gap(
        box: Rect,
        triangle: List<Offset>,
    ): Float {
        val boxEdges = listOf(box.topLeft, box.topRight, box.bottomRight, box.bottomLeft).closedEdges()
        val triangleEdges = triangle.closedEdges()
        val boxPoints = boxEdges.flatMap { it.samples() }
        val trianglePoints = triangleEdges.flatMap { it.samples() }
        return if (boxPoints.any { it.inside(triangle) } || trianglePoints.any { box.contains(it) }) {
            -1f
        } else {
            minOf(
                boxPoints.minOf { point -> triangleEdges.minOf { point.distanceTo(it) } },
                trianglePoints.minOf { point -> boxEdges.minOf { point.distanceTo(it) } },
            )
        }
    }

    private fun List<Offset>.closedEdges(): List<Pair<Offset, Offset>> = zipWithNext() + (last() to first())

    private fun Pair<Offset, Offset>.samples(): List<Offset> =
        (0..EDGE_SAMPLES).map { step -> first + (second - first) * (step.toFloat() / EDGE_SAMPLES) }

    private fun Offset.distanceTo(edge: Pair<Offset, Offset>): Float {
        val along = edge.second - edge.first
        val t = (((this - edge.first).x * along.x + (this - edge.first).y * along.y) / along.getDistanceSquared())
        return (this - (edge.first + along * t.coerceIn(0f, 1f))).getDistance()
    }

    private fun Offset.inside(triangle: List<Offset>): Boolean =
        triangle.closedEdges().map { (a, b) -> (b.x - a.x) * (y - a.y) - (b.y - a.y) * (x - a.x) }.let { sides ->
            sides.all { it >= 0f } || sides.all { it <= 0f }
        }

    private fun shapeOf(kind: TurnKind): GlyphShape = turnGlyph(TurnManeuver.Turn(kind)).shape()

    private fun ring(
        kind: TurnKind,
        exitNumber: Int = 0,
    ): GlyphShape = turnGlyph(TurnManeuver.Turn(kind, exitNumber = exitNumber)).shape()

    private fun uTurn(leftward: Boolean) = TurnManeuver.Turn(TurnKind.U_TURN, leftward = leftward)

    // The stem is the first stroke, drawn up from the bottom of the square.
    private fun stemX(shape: GlyphShape): Float =
        shape.strokes
            .first()
            .points
            .first()
            .x

    // The first solid head's direction, in degrees from straight on, positive to the right.
    private fun headAngleDegrees(shape: GlyphShape): Int =
        shape.heads.first().let { head ->
            (head.tip - head.baseCenter).let { Math.toDegrees(atan2(it.x, -it.y).toDouble()).roundToInt() }
        }

    // Which side of the stem the headed branch ends on: -1 left, 1 right.
    private fun headSide(shape: GlyphShape): Int =
        shape.heads
            .first()
            .tip.x
            .minus(stemX(shape))
            .let { if (it < 0f) -1 else 1 }

    // Each stroke as the set of its points and the heads as a set of corner sets: mirroring runs the ring the other way
    // round and swaps a head's two base corners, and the ring lists its two heads from either side.
    private fun assertSameShape(
        expected: GlyphShape,
        actual: GlyphShape,
        message: String,
    ) = assertEquals(expected.comparable(), actual.comparable(), message)

    private fun GlyphShape.comparable() =
        Triple(
            strokes.map { stroke -> stroke.faint to stroke.points.map { it.rounded() }.toSet() },
            heads.map { head -> head.points.map { it.rounded() }.toSet() }.toSet(),
            label,
        )

    // The shape reflected about the square's vertical centre line.
    private fun GlyphShape.mirrored(): GlyphShape =
        copy(
            strokes = strokes.map { stroke -> stroke.copy(points = stroke.points.map { it.mirrored() }) },
            heads = heads.map { head -> GlyphHead(head.points.map { it.mirrored() }) },
        )

    private fun Offset.mirrored(): Offset = Offset(1f - x, y)

    private fun List<GlyphHead>.headTips(): Set<Pair<Int, Int>> = map { it.tip.rounded() }.toSet()

    // To a hundredth of the square, which mirroring's float error stays well below.
    private fun Offset.rounded(): Pair<Int, Int> = (x * 100f).roundToInt() to (y * 100f).roundToInt()

    private companion object {
        const val EPSILON = 1e-4f

        // A generous digit box, as a share of the number's size: wider and taller than any face's digits.
        const val DIGIT_ADVANCE = 0.6f
        const val DIGIT_HEIGHT = 0.75f

        const val EDGE_SAMPLES = 40

        val SIDED_KINDS =
            listOf(
                TurnKind.SLIGHT_LEFT to TurnKind.SLIGHT_RIGHT,
                TurnKind.LEFT to TurnKind.RIGHT,
                TurnKind.SHARP_LEFT to TurnKind.SHARP_RIGHT,
                TurnKind.KEEP_LEFT to TurnKind.KEEP_RIGHT,
                TurnKind.EXIT_LEFT to TurnKind.EXIT_RIGHT,
                TurnKind.U_TURN_LEFT to TurnKind.U_TURN_RIGHT,
            )

        val EXPECTED_GLYPHS: Map<TurnManeuver, TurnGlyph> =
            linkedMapOf(
                TurnManeuver.Turn(TurnKind.STRAIGHT) to TurnGlyph.Bend(0),
                TurnManeuver.Turn(TurnKind.SLIGHT_LEFT) to TurnGlyph.Bend(-45),
                TurnManeuver.Turn(TurnKind.LEFT) to TurnGlyph.Bend(-90),
                TurnManeuver.Turn(TurnKind.SHARP_LEFT) to TurnGlyph.Bend(-135),
                TurnManeuver.Turn(TurnKind.SLIGHT_RIGHT) to TurnGlyph.Bend(45),
                TurnManeuver.Turn(TurnKind.RIGHT) to TurnGlyph.Bend(90),
                TurnManeuver.Turn(TurnKind.SHARP_RIGHT) to TurnGlyph.Bend(135),
                TurnManeuver.Turn(TurnKind.KEEP_LEFT) to TurnGlyph.Fork(leftward = true),
                TurnManeuver.Turn(TurnKind.KEEP_RIGHT) to TurnGlyph.Fork(leftward = false),
                TurnManeuver.Turn(TurnKind.EXIT_LEFT) to TurnGlyph.Exit(leftward = true),
                TurnManeuver.Turn(TurnKind.EXIT_RIGHT) to TurnGlyph.Exit(leftward = false),
                TurnManeuver.Turn(TurnKind.U_TURN_LEFT) to TurnGlyph.UTurn(leftward = true),
                TurnManeuver.Turn(TurnKind.U_TURN_RIGHT) to TurnGlyph.UTurn(leftward = false),
                TurnManeuver.Turn(TurnKind.U_TURN, leftward = true) to TurnGlyph.UTurn(leftward = true),
                TurnManeuver.Turn(TurnKind.U_TURN, leftward = false) to TurnGlyph.UTurn(leftward = false),
                TurnManeuver.Turn(TurnKind.ROUNDABOUT_COUNTERCLOCKWISE, exitNumber = 2) to
                    TurnGlyph.Ring(clockwise = false, exitNumber = 2),
                TurnManeuver.Turn(TurnKind.ROUNDABOUT_CLOCKWISE, exitNumber = 12) to
                    TurnGlyph.Ring(clockwise = true, exitNumber = 12),
                TurnManeuver.Turn(TurnKind.ROUNDABOUT_CLOCKWISE) to TurnGlyph.Ring(clockwise = true, exitNumber = 0),
                TurnManeuver.Turn(TurnKind.OFF_ROUTE) to TurnGlyph.Bend(0),
                TurnManeuver.Turn(TurnKind.BEELINE) to TurnGlyph.Bend(0),
                TurnManeuver.Arrive to TurnGlyph.Flag,
            )
    }
}
