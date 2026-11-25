package io.github.seijikohara.femto.data.places

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Shares shaped to make a backtracking regex engine try every way to split a
 * long run of spaces between the parser's optional parts: each is read well
 * inside a time limit generous for CI, and reads the place it should.
 *
 * The JVM's regex engine and Android's ICU engine backtrack differently, so the
 * time limit here guards the shape of the patterns, not the device's timing;
 * the real protection is in the parser itself: runs of spaces collapse to one
 * before any pattern runs, and every optional space in a pattern is a single
 * space, never a run.
 *
 * A complete pair padded with spaces between its halves reads as the pair:
 * once the run is collapsed, it is the same share as an unpadded one. With
 * 1,990 spaces the longitude lies past the 2,000-character cap and is
 * dropped, so that share holds no pair; 1,970 spaces keep it inside.
 */
@RunWith(Parameterized::class)
internal class ReadReceivedTimeTest(
    private val case: String,
    private val text: String?,
    private val subject: String?,
    private val expectedPoint: PlaceTarget.Point?,
) {
    @Test
    fun `reads a padded share quickly`() {
        val started = System.nanoTime()
        val read = readSharedText(text, subject, title = null)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000

        assertTrue(elapsedMs < TIME_LIMIT_MS, "$case took $elapsedMs ms")
        assertEquals(expectedPoint, (read as? ReceivedPlace.Found)?.target as? PlaceTarget.Point, case)
    }

    companion object {
        private const val TIME_LIMIT_MS = 500L
        private val SPACES = " ".repeat(1_990)
        private val FEWER_SPACES = " ".repeat(1_970)
        private const val HALF_PAIR = "1°1'1\""
        private const val LATITUDE = "33°50'44.2\"N"
        private const val LONGITUDE = "132°45'56.2\"E"
        private val MARKS = "°'\"".repeat(667)

        // The parser's own sum, so the expected point compares exactly.
        private val CASTLE = PlaceTarget.Point(33 + 50 / 60.0 + 44.2 / 3_600.0, 132 + 45 / 60.0 + 56.2 / 3_600.0)

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any?>> =
            listOf(
                arrayOf("half a pair, then spaces", HALF_PAIR + SPACES, null, null),
                arrayOf("spaces, then half a pair", SPACES + HALF_PAIR, null, null),
                arrayOf(
                    "half a pair, then spaces, as the subject",
                    "https://maps.example.com/p/1",
                    HALF_PAIR + SPACES,
                    null,
                ),
                arrayOf("spaces past the cap between the halves of a pair", LATITUDE + SPACES + LONGITUDE, null, null),
                arrayOf("spaces between the halves of a pair", LATITUDE + FEWER_SPACES + LONGITUDE, null, CASTLE),
                arrayOf(
                    "spaces between the halves of a pair, as the subject",
                    null,
                    LATITUDE + FEWER_SPACES + LONGITUDE,
                    CASTLE,
                ),
                arrayOf("degree, minute and second marks only", MARKS, null, null),
                arrayOf("degree, minute and second marks only, as the subject", null, MARKS, null),
            )
    }
}
