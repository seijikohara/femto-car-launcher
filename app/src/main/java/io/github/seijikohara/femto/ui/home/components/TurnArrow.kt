package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.seijikohara.femto.data.routing.TurnKind
import io.github.seijikohara.femto.ui.home.TurnManeuver
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import io.github.seijikohara.femto.ui.theme.turnExitNumber
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The turn card's arrow for [maneuver] (issue #389 phase 4), drawn on a Canvas in the square the caller sizes it to,
 * in [color]; faint parts take the same colour at a lower alpha. The destination is a flag in the same stroke. A
 * roundabout's ring carries its exit number only with [showExitNumber]: in the small arrow of the turn after next the
 * number would be about 10 dp tall and sit on the arrowheads.
 *
 * The arrow shows a physical direction, so it never mirrors in a right-to-left layout: a Canvas draws in absolute
 * coordinates whatever the layout direction, and the glyph is composed under a left-to-right direction besides, so the
 * ring's number cannot pick a mirrored form up from a parent. The parent still places the
 * square where its own direction says.
 */
@Composable
internal fun TurnArrow(
    maneuver: TurnManeuver,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    showExitNumber: Boolean = true,
) = CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    // Kept while the manoeuvre is the same, so the Canvas is skipped as the distance beside it counts down.
    val shape =
        remember(maneuver, showExitNumber) {
            turnGlyph(maneuver).shape().let { shape -> if (showExitNumber) shape else shape.copy(label = null) }
        }
    GlyphCanvas(shape = shape, color = color, modifier = modifier)
}

/** The kind of glyph a manoeuvre gets, before any geometry: what the shape tests check. */
internal sealed interface TurnGlyph {
    /** A stem with a head segment [degrees] from straight on, negative to the left: 0, ±45, ±90 or ±135. */
    data class Bend(
        val degrees: Int,
    ) : TurnGlyph

    /** A fork whose two branches leave the stem at a shallow angle; the [leftward] one, or the other, is taken. */
    data class Fork(
        val leftward: Boolean,
    ) : TurnGlyph

    /** The stem going on straight, faint, and an exit branch leaving it to the [leftward] side, or the other. */
    data class Exit(
        val leftward: Boolean,
    ) : TurnGlyph

    /** A hook that comes back down on the [leftward] side, or the other. */
    data class UTurn(
        val leftward: Boolean,
    ) : TurnGlyph

    /** A ring whose arrowheads show the direction of travel, with [exitNumber] in its middle (none for 0). */
    data class Ring(
        val clockwise: Boolean,
        val exitNumber: Int,
    ) : TurnGlyph

    /** The destination: a pole with a pennant flying to the right. */
    data object Flag : TurnGlyph
}

/** Return the glyph for [maneuver]. Off-route and beeline hints, which the card never announces, read as straight. */
internal fun turnGlyph(maneuver: TurnManeuver): TurnGlyph =
    when (maneuver) {
        TurnManeuver.Arrive -> TurnGlyph.Flag
        is TurnManeuver.Turn -> turnGlyph(maneuver.kind, maneuver.exitNumber, maneuver.leftward)
    }

private fun turnGlyph(
    kind: TurnKind,
    exitNumber: Int,
    leftward: Boolean,
): TurnGlyph =
    when (kind) {
        TurnKind.STRAIGHT, TurnKind.OFF_ROUTE, TurnKind.BEELINE -> TurnGlyph.Bend(0)
        TurnKind.SLIGHT_LEFT -> TurnGlyph.Bend(-SLIGHT_DEGREES)
        TurnKind.LEFT -> TurnGlyph.Bend(-NORMAL_DEGREES)
        TurnKind.SHARP_LEFT -> TurnGlyph.Bend(-SHARP_DEGREES)
        TurnKind.SLIGHT_RIGHT -> TurnGlyph.Bend(SLIGHT_DEGREES)
        TurnKind.RIGHT -> TurnGlyph.Bend(NORMAL_DEGREES)
        TurnKind.SHARP_RIGHT -> TurnGlyph.Bend(SHARP_DEGREES)
        TurnKind.KEEP_LEFT -> TurnGlyph.Fork(leftward = true)
        TurnKind.KEEP_RIGHT -> TurnGlyph.Fork(leftward = false)
        TurnKind.EXIT_LEFT -> TurnGlyph.Exit(leftward = true)
        TurnKind.EXIT_RIGHT -> TurnGlyph.Exit(leftward = false)
        TurnKind.U_TURN_LEFT -> TurnGlyph.UTurn(leftward = true)
        TurnKind.U_TURN_RIGHT -> TurnGlyph.UTurn(leftward = false)
        TurnKind.U_TURN -> TurnGlyph.UTurn(leftward = leftward)
        TurnKind.ROUNDABOUT_COUNTERCLOCKWISE -> TurnGlyph.Ring(clockwise = false, exitNumber = exitNumber)
        TurnKind.ROUNDABOUT_CLOCKWISE -> TurnGlyph.Ring(clockwise = true, exitNumber = exitNumber)
    }

private const val SLIGHT_DEGREES = 45
private const val NORMAL_DEGREES = 90
private const val SHARP_DEGREES = 135

/** One stroked line of a glyph through [points]; a [faint] one is the way not taken. */
internal data class GlyphStroke(
    val points: List<Offset>,
    val faint: Boolean = false,
)

/** A filled triangle, an arrowhead or the flag's pennant: its [tip], then the two corners of its base. */
internal data class GlyphHead(
    val points: List<Offset>,
) {
    val tip: Offset get() = points[0]
    val baseCenter: Offset get() = (points[1] + points[2]) / 2f
}

/**
 * A glyph's geometry in a unit square, x to the right and y down: its strokes, drawn in one stroke style, its
 * arrowheads, and the [label] set in its middle.
 */
internal data class GlyphShape(
    val strokes: List<GlyphStroke>,
    val heads: List<GlyphHead>,
    val label: String? = null,
)

// Every size below is a fraction of the glyph's square, so one glyph looks the same at 64 dp and at 28 dp and every
// glyph shares the stroke. The stroke is an eighth of the square: at the 28 dp small arrow still over 3 dp.
private const val GLYPH_STROKE = 0.12f

// The arrowhead: a filled triangle three strokes wide, its corners rounded by an outline of HEAD_OUTLINE.
private const val HEAD_LENGTH = 0.26f
private const val HEAD_HALF_WIDTH = 0.18f
internal const val HEAD_OUTLINE = 0.05f

/** The clear band every glyph's ink keeps from its square's edges, so nothing is clipped at any size. */
internal const val GLYPH_MARGIN = 0.04f

// The length of a bend's head segment before its arrowhead, by the bend's angle. A sharp bend's segment runs back
// alongside the stem, so it is long enough for the arrowhead to clear the stem.
private const val STRAIGHT_SEGMENT = 0.12f
private const val SLIGHT_SEGMENT = 0.20f
private const val NORMAL_SEGMENT = 0.22f
private const val SHARP_SEGMENT = 0.44f

// A keep fork: each branch leaves the stem for FORK_SPREAD sideways over FORK_RISE (about 33° from straight on), then
// the branch taken rises FORK_TAKEN_RISE more to its arrowhead and the other FORK_OTHER_RISE more, ending below it.
private const val FORK_SPREAD = 0.17f
private const val FORK_RISE = 0.26f
private const val FORK_TAKEN_RISE = 0.04f
private const val FORK_OTHER_RISE = 0.08f

// An exit: the branch leaves at 45° for EXIT_BRANCH, the faint stem goes on EXIT_ON past the branch point.
private const val EXIT_BRANCH = 0.34f
private const val EXIT_ON = 0.30f

// A U-turn: half a circle of UTURN_RADIUS over the top, and the leg it comes back down before its arrowhead.
private const val UTURN_RADIUS = 0.22f
private const val UTURN_LEG = 0.10f

// A roundabout: the ring, broken by a gap of RING_GAP_DEGREES on either side, and its two smaller arrowheads, each
// RING_HEAD_DEGREES of the ring long, which leaves the arrowhead clear of the next arc's end.
private const val RING_RADIUS = 0.29f
private const val RING_GAP_DEGREES = 60f
private const val RING_HEAD_DEGREES = 34f
private const val RING_HEAD_HALF_WIDTH = 0.12f

// The flag: its pennant's height down the pole and how far it flies out, as a share of the square. The pole spans the
// height every stemmed glyph spans, so the flag stands as tall as the arrows beside it.
private const val PENNANT_HEIGHT = 0.40f
private const val PENNANT_WIDTH = 0.46f

// The steps each curve is drawn in: at 64 dp a step is under 2 dp of ring, which the round joins smooth over.
private const val CURVE_STEPS = 24

// The share of the square the ring's exit number is set at: one digit large in the ring, and two a size smaller so
// they keep clear of the arrowheads, whose inner corners reach to within half the ring's radius of its middle.
private const val LABEL_SIZE = 0.38f
private const val TWO_DIGIT_LABEL_SIZE = 0.20f

/** Return the share of the square the ring's exit number [label] is set at. */
internal fun ringLabelSize(label: String): Float = if (label.length > 1) TWO_DIGIT_LABEL_SIZE else LABEL_SIZE

/** The alpha of a faint part, against the glyph's own colour. */
private const val FAINT_ALPHA = 0.35f

/** Return the glyph's geometry. */
internal fun TurnGlyph.shape(): GlyphShape =
    when (this) {
        is TurnGlyph.Bend -> bendShape(degrees)
        is TurnGlyph.Fork -> forkShape(side = if (leftward) -1f else 1f)
        is TurnGlyph.Exit -> exitShape(side = if (leftward) -1f else 1f)
        is TurnGlyph.UTurn -> uTurnShape(side = if (leftward) -1f else 1f)
        is TurnGlyph.Ring -> ringShape(clockwise, exitNumber)
        TurnGlyph.Flag -> flagShape()
    }

/**
 * Return the box the glyph inks: every stroke point grown by half the stroke (a round cap or join reaches no
 * farther), every arrowhead corner by half its outline.
 */
internal fun GlyphShape.inkBounds(): Rect =
    (
        strokes.flatMap { stroke -> stroke.points.map { it to GLYPH_STROKE / 2f } } +
            heads.flatMap { head -> head.points.map { it to HEAD_OUTLINE / 2f } }
    ).let { reaches ->
        Rect(
            left = reaches.minOf { (point, reach) -> point.x - reach },
            top = reaches.minOf { (point, reach) -> point.y - reach },
            right = reaches.maxOf { (point, reach) -> point.x + reach },
            bottom = reaches.maxOf { (point, reach) -> point.y + reach },
        )
    }

// A stem with a head segment at [degrees] from the stem's top.
private fun bendShape(degrees: Int): GlyphShape =
    direction(degrees.toFloat()).let { heading ->
        (heading * bendSegment(abs(degrees))).let { base ->
            stemmed(
                stemTop = Offset.Zero,
                strokes = listOf(GlyphStroke(listOf(Offset.Zero, base))),
                heads = listOf(head(base, heading)),
            )
        }
    }

private fun bendSegment(degrees: Int): Float =
    when {
        degrees == 0 -> STRAIGHT_SEGMENT
        degrees <= SLIGHT_DEGREES -> SLIGHT_SEGMENT
        degrees <= NORMAL_DEGREES -> NORMAL_SEGMENT
        else -> SHARP_SEGMENT
    }

// A fork from the stem's top: the branch to [side] (-1 left, 1 right) taken, the other faint and shorter.
private fun forkShape(side: Float): GlyphShape {
    val takenSplit = Offset(side * FORK_SPREAD, -FORK_RISE)
    val otherSplit = Offset(-side * FORK_SPREAD, -FORK_RISE)
    val takenEnd = takenSplit + Offset(0f, -FORK_TAKEN_RISE)
    return stemmed(
        stemTop = Offset.Zero,
        strokes =
            listOf(
                GlyphStroke(listOf(Offset.Zero, otherSplit, otherSplit + Offset(0f, -FORK_OTHER_RISE)), faint = true),
                GlyphStroke(listOf(Offset.Zero, takenSplit, takenEnd)),
            ),
        heads = listOf(head(takenEnd, Up)),
    )
}

// The stem going on straight, faint, past the point an exit branch leaves it to [side] at 45°.
private fun exitShape(side: Float): GlyphShape =
    direction(side * SLIGHT_DEGREES).let { heading ->
        (heading * EXIT_BRANCH).let { branchEnd ->
            stemmed(
                stemTop = Offset.Zero,
                strokes =
                    listOf(
                        GlyphStroke(listOf(Offset.Zero, Offset(0f, -EXIT_ON)), faint = true),
                        GlyphStroke(listOf(Offset.Zero, branchEnd)),
                    ),
                heads = listOf(head(branchEnd, heading)),
            )
        }
    }

// Up the side away from [side], over the top in half a circle, and down on [side] to an arrowhead pointing down.
private fun uTurnShape(side: Float): GlyphShape {
    val legEnd = Offset(side * UTURN_RADIUS, UTURN_LEG)
    val arc =
        (0..CURVE_STEPS).map { step ->
            (PI * step / CURVE_STEPS).let { angle ->
                Offset(-side * UTURN_RADIUS * cos(angle).toFloat(), -UTURN_RADIUS * sin(angle).toFloat())
            }
        }
    return stemmed(
        stemTop = arc.first(),
        strokes = listOf(GlyphStroke(arc + legEnd)),
        heads = listOf(head(legEnd, Down)),
    )
}

// The ring as two arcs, its upper and lower halves, with a gap on either side; an arrowhead at one end of each arc
// points into the gap the way traffic goes round. Angles run from the right-hand side, so with y down they grow
// clockwise: clockwise traffic leaves each arc at its end, counter-clockwise traffic at its start. Both rings draw the
// same arcs and differ only in their arrowheads.
private fun ringShape(
    clockwise: Boolean,
    exitNumber: Int,
): GlyphShape {
    val halfGap = RING_GAP_DEGREES / 2f
    val arcs = listOf(180f + halfGap to 360f - halfGap, halfGap to 180f - halfGap)
    return GlyphShape(
        strokes =
            arcs.map { (from, to) ->
                GlyphStroke((0..CURVE_STEPS).map { step -> ringPoint(from + (to - from) * step / CURVE_STEPS) })
            },
        heads =
            arcs.map { (from, to) ->
                if (clockwise) ringHead(to, to + RING_HEAD_DEGREES) else ringHead(from, from - RING_HEAD_DEGREES)
            },
        label = exitNumber.takeIf { it > 0 }?.let { cardNumber(it.toDouble(), decimals = 0) },
    ).translated(Offset(0.5f, 0.5f))
}

// A pole from the bottom margin to the top one, and a pennant at its top: a filled triangle like an arrowhead, its
// tip flying to the right. Centred in the square by its ink, as the stemmed glyphs are.
private fun flagShape(): GlyphShape =
    (1f - 2f * GLYPH_MARGIN - GLYPH_STROKE).let { poleLength ->
        Offset(0f, -poleLength).let { top ->
            GlyphShape(
                strokes = listOf(GlyphStroke(listOf(Offset.Zero, top))),
                heads =
                    listOf(
                        GlyphHead(
                            listOf(
                                top + Offset(PENNANT_WIDTH, PENNANT_HEIGHT / 2f),
                                top,
                                top + Offset(0f, PENNANT_HEIGHT),
                            ),
                        ),
                    ),
            ).let { shape -> shape.translated(Offset(0.5f, 0.5f) - shape.inkBounds().center) }
        }
    }

// An arrowhead from the ring's point at [baseDegrees] to its tip on the ring at [tipDegrees]: along the chord, so the
// tip stays on the ring rather than standing off it.
private fun ringHead(
    baseDegrees: Float,
    tipDegrees: Float,
): GlyphHead =
    ringPoint(baseDegrees).let { base ->
        (ringPoint(tipDegrees) - base).let { chord ->
            head(base, chord / chord.getDistance(), chord.getDistance(), RING_HEAD_HALF_WIDTH)
        }
    }

private fun ringPoint(degrees: Float): Offset =
    (degrees * PI / 180.0).let { radians ->
        Offset(RING_RADIUS * cos(radians).toFloat(), RING_RADIUS * sin(radians).toFloat())
    }

// The glyph of [strokes] and [heads] above a stem that rises from the bottom margin to [stemTop]: the stem is as long
// as the square leaves it, so every stemmed glyph spans the same height, and the whole is centred in the square. The
// stem comes first, which is how the tests find it.
private fun stemmed(
    stemTop: Offset,
    strokes: List<GlyphStroke>,
    heads: List<GlyphHead>,
): GlyphShape =
    GlyphShape(strokes + GlyphStroke(listOf(stemTop)), heads)
        .inkBounds()
        .top
        .let { top -> Offset(stemTop.x, top + 1f - 2f * GLYPH_MARGIN - GLYPH_STROKE / 2f) }
        .let { stemBottom -> GlyphShape(listOf(GlyphStroke(listOf(stemBottom, stemTop))) + strokes, heads) }
        .let { shape -> shape.translated(Offset(0.5f, 0.5f) - shape.inkBounds().center) }

// An arrowhead whose base is centred on [base], pointing along [heading] (a unit vector).
private fun head(
    base: Offset,
    heading: Offset,
    length: Float = HEAD_LENGTH,
    halfWidth: Float = HEAD_HALF_WIDTH,
): GlyphHead =
    Offset(-heading.y, heading.x).let { across ->
        GlyphHead(listOf(base + heading * length, base + across * halfWidth, base - across * halfWidth))
    }

// The unit vector [degrees] from straight up, positive to the right.
private fun direction(degrees: Float): Offset =
    (degrees * PI / 180.0).let { radians -> Offset(sin(radians).toFloat(), -cos(radians).toFloat()) }

private val Up = Offset(0f, -1f)
private val Down = Offset(0f, 1f)

private fun GlyphShape.translated(by: Offset): GlyphShape =
    copy(
        strokes = strokes.map { stroke -> stroke.copy(points = stroke.points.map { it + by }) },
        heads = heads.map { head -> GlyphHead(head.points.map { it + by }) },
    )

// The glyph drawn opaque on a layer that takes [color]'s own alpha, so where a stroke and an arrowhead overlap the
// colour does not build up; the faint parts go first, under the solid ones.
@Composable
private fun GlyphCanvas(
    shape: GlyphShape,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val typography = MaterialTheme.typography
    Canvas(
        modifier =
            modifier.graphicsLayer {
                alpha = color.alpha
                compositingStrategy = CompositingStrategy.Offscreen
            },
    ) {
        val side = size.minDimension
        val origin = Offset((size.width - side) / 2f, (size.height - side) / 2f)
        val solid = color.copy(alpha = 1f)
        val line = Stroke(width = GLYPH_STROKE * side, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val outline = Stroke(width = HEAD_OUTLINE * side, cap = StrokeCap.Round, join = StrokeJoin.Round)
        shape.strokes.sortedBy { !it.faint }.forEach { stroke ->
            drawPath(
                path = stroke.points.toPath(origin, side),
                color = if (stroke.faint) solid.copy(alpha = FAINT_ALPHA) else solid,
                style = line,
            )
        }
        shape.heads.forEach { head ->
            val path = head.points.toPath(origin, side).apply { close() }
            drawPath(path = path, color = solid)
            drawPath(path = path, color = solid, style = outline)
        }
        shape.label?.let { label ->
            drawCentredLabel(
                layout =
                    measurer.measure(
                        text = label,
                        style =
                            typography.turnExitNumber((ringLabelSize(label) * side).toSp()),
                    ),
                color = solid,
                center = origin + Offset(side / 2f, side / 2f),
            )
        }
    }
}

// The label with its ink, not its line box, centred on [center]: digits stand on the baseline, so the line box's
// centre sits below theirs, by an amount that differs from font to font.
private fun DrawScope.drawCentredLabel(
    layout: TextLayoutResult,
    color: Color,
    center: Offset,
) = layout.getPathForRange(0, layout.layoutInput.text.length).getBounds().let { ink ->
    drawText(textLayoutResult = layout, color = color, topLeft = center - ink.center)
}

private fun List<Offset>.toPath(
    origin: Offset,
    side: Float,
): Path =
    Path().apply {
        forEachIndexed { index, point ->
            (origin + point * side).let { if (index == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) }
        }
    }

/**
 * Every glyph at the card's two sizes with its kind's name under it, left kinds in the first row over their right
 * mirror images in the second: the preview below and the arrows' goldens (TurnArrowScreenshotTest) both show it. The
 * rows use absolute arrangements and the names a left-to-right direction of their own, so under a right-to-left
 * direction the grid keeps its order and only the arrows themselves could change, which they must not.
 */
@Composable
internal fun TurnArrowGrid(modifier: Modifier = Modifier) =
    Column(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainer).padding(GridGap),
        verticalArrangement = Arrangement.spacedBy(GridGap),
        horizontalAlignment = AbsoluteAlignment.Left,
    ) {
        GridManeuvers.chunked(GRID_COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.Absolute.spacedBy(GridGap)) {
                row.forEach { maneuver -> GridCell(maneuver) }
            }
        }
    }

@Composable
private fun GridCell(maneuver: TurnManeuver) =
    Column(modifier = Modifier.width(GridCellWidth), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            horizontalArrangement = Arrangement.Absolute.spacedBy(GridGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TurnArrow(maneuver, Modifier.size(TurnArrowSize), color = MaterialTheme.colorScheme.onSurface)
            TurnArrow(
                maneuver,
                Modifier.size(ThenArrowSize),
                color = MaterialTheme.colorScheme.onSurface,
                showExitNumber = false,
            )
        }
        // The names are the code's, read left to right whatever the direction around them.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text(
                text = maneuver.gridLabel(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 3,
            )
        }
    }

// The kind's name as the code spells it, with the side of a plain U-turn and a roundabout's exit.
private fun TurnManeuver.gridLabel(): String =
    when (this) {
        TurnManeuver.Arrive -> {
            "arrive"
        }

        is TurnManeuver.Turn -> {
            listOfNotNull(
                kind.name.lowercase().replace('_', ' '),
                (if (leftward) "(left)" else "(right)").takeIf { kind == TurnKind.U_TURN },
                "exit $exitNumber".takeIf { turnGlyph(this) is TurnGlyph.Ring },
            ).joinToString(" ")
        }
    }

private const val GRID_COLUMNS = 6
private val GridGap = 12.dp
private val GridCellWidth = 128.dp

private val GridManeuvers: List<TurnManeuver> =
    listOf(
        TurnKind.SLIGHT_LEFT,
        TurnKind.LEFT,
        TurnKind.SHARP_LEFT,
        TurnKind.KEEP_LEFT,
        TurnKind.EXIT_LEFT,
        TurnKind.U_TURN_LEFT,
        TurnKind.SLIGHT_RIGHT,
        TurnKind.RIGHT,
        TurnKind.SHARP_RIGHT,
        TurnKind.KEEP_RIGHT,
        TurnKind.EXIT_RIGHT,
        TurnKind.U_TURN_RIGHT,
        TurnKind.STRAIGHT,
    ).map { TurnManeuver.Turn(it) } +
        listOf(
            TurnManeuver.Turn(TurnKind.U_TURN, leftward = true),
            TurnManeuver.Turn(TurnKind.U_TURN, leftward = false),
            TurnManeuver.Turn(TurnKind.ROUNDABOUT_COUNTERCLOCKWISE, exitNumber = 2),
            TurnManeuver.Turn(TurnKind.ROUNDABOUT_COUNTERCLOCKWISE),
            TurnManeuver.Turn(TurnKind.ROUNDABOUT_CLOCKWISE, exitNumber = 3),
            TurnManeuver.Turn(TurnKind.ROUNDABOUT_CLOCKWISE, exitNumber = 12),
            TurnManeuver.Turn(TurnKind.OFF_ROUTE),
            TurnManeuver.Turn(TurnKind.BEELINE),
            TurnManeuver.Arrive,
        )

@PreviewLightDark
@Composable
private fun TurnArrowGridPreview() {
    FemtoTheme {
        TurnArrowGrid()
    }
}
