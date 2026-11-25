package io.github.seijikohara.femto.data.places

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Positions shared in degrees, minutes and seconds. The first row is a real
 * share of a dropped pin from a maps app: the position as the subject and a
 * short link as the text, with no name. A pair read as a position is no name;
 * one that is no position (minutes past 59, a missing hemisphere) stays text.
 */
@RunWith(Parameterized::class)
internal class ReadDmsTest(
    private val case: String,
    private val text: String,
    private val subject: String?,
    private val title: String?,
    private val expected: Expected,
) {
    /** The point to read, about, and its label; or [NotAPoint]. */
    sealed interface Expected

    data class At(
        val latitude: Double,
        val longitude: Double,
        val label: String = "",
    ) : Expected

    data object NotAPoint : Expected

    @Test
    fun `reads a position in degrees minutes and seconds`() {
        val read = readSharedText(text, subject, title)
        val point = ((read as? ReceivedPlace.Found)?.target as? PlaceTarget.Point)
        when (expected) {
            is At -> {
                assertTrue(
                    point != null &&
                        abs(point.latitude - expected.latitude) < TOLERANCE &&
                        abs(point.longitude - expected.longitude) < TOLERANCE,
                    "$case: $read",
                )
                assertEquals(expected.label, (read as ReceivedPlace.Found).label, case)
            }

            NotAPoint -> {
                assertEquals(null, point, case)
            }
        }
    }

    companion object {
        // Five decimals, as the panel shows a point: about a metre.
        private const val TOLERANCE = 0.000_01

        private const val LINK = "https://maps.app.goo.gl/AbCdEf123?g_st=am "
        private val CASTLE = At(33.84561, 132.76561)

        private fun row(
            case: String,
            subject: String?,
            expected: Expected,
            text: String = LINK,
            title: String? = null,
        ): Array<Any?> = arrayOf(case, text, subject, title, expected)

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any?>> =
            listOf(
                row("a dropped pin's share", "33°50'44.2\"N 132°45'56.2\"E", CASTLE),
                row("south and west", "33°51'25.0\"S 74°02'40.2\"W", At(-33.85694, -74.04450)),
                row("letters before the numbers", "N 33°50'44.2\" E 132°45'56.2\"", CASTLE),
                row("prime characters and a comma", "33°50′44.2″N, 132°45′56.2″E", CASTLE),
                row("whole seconds", "33°50'44\"N 132°45'56\"E", At(33.84556, 132.76556)),
                row(
                    "in the text, under a name line",
                    null,
                    CASTLE.copy(label = "Matsuyama Castle"),
                    text = "Matsuyama Castle\n33°50'44.2\"N 132°45'56.2\"E",
                ),
                row(
                    "the title names a pin in the subject",
                    "33°50'44.2\"N 132°45'56.2\"E",
                    CASTLE.copy(label = "Castle"),
                    title = "Castle",
                ),
                // A capital N, S, E or W of a neighbouring word is no hemisphere letter: taken as one, it gives the
                // side two letters, or a letter the share never had.
                row("a word before the pair", "GPS 35°39'31\"N 139°44'43\"E", At(35.65861, 139.74528, label = "GPS")),
                row(
                    "a word ending in a hemisphere letter before the pair",
                    "LOCATION 33°50'44.2\"N 132°45'56.2\"E",
                    CASTLE.copy(label = "LOCATION"),
                ),
                row(
                    "a word after the pair",
                    "33°50'44.2\"N 132°45'56.2\"E West gate",
                    CASTLE.copy(label = "West gate"),
                ),
                row("a word's first letter is no hemisphere", "33°50'44.2\"N 132°45'56.2\" Exit 5", NotAPoint),
                // A word that ends in no hemisphere letter: one that does ("GPS") was no position before this rule
                // either, since its last letter gave the latitude two.
                row("a pair glued to a word", "ABC35°39'31\"N 139°44'43\"E", NotAPoint),
                row("minutes past 59", "33°60'44.2\"N 132°45'56.2\"E", NotAPoint),
                row("seconds past 59", "33°50'60.0\"N 132°45'56.2\"E", NotAPoint),
                row("latitude past 90", "91°00'00.0\"N 132°45'56.2\"E", NotAPoint),
                row("longitude past 180", "33°50'44.2\"N 181°00'00.0\"E", NotAPoint),
                row("no hemisphere", "33°50'44.2\" 132°45'56.2\"", NotAPoint),
                row("two hemispheres on one side", "N 33°50'44.2\"S 132°45'56.2\"E", NotAPoint),
                row("swapped hemispheres", "33°50'44.2\"E 132°45'56.2\"N", NotAPoint),
                row("no seconds", "33°50'N 132°45'E", NotAPoint),
                row("full-width digits", "３３°50'44.2\"N 132°45'56.2\"E", NotAPoint),
                row("garbage", "°'\"NE°'\"", NotAPoint),
            )
    }
}
