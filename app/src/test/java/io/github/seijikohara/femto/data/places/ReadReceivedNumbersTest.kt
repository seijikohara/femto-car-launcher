package io.github.seijikohara.femto.data.places

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.test.assertEquals

/**
 * Numbers in received text that are no coordinates: other scripts' digits,
 * exponents, NaN and Infinity spelled out, a lone sign, an empty number. Each
 * reads as no point and never throws; the launcher is the home screen, and any
 * app can send it this text.
 *
 * On Android the regex shorthand for a digit matches every script's digits,
 * which `toDouble` cannot read; the JVM these tests run on matches ASCII only,
 * so these rows alone would pass with the shorthand back in the patterns.
 * [ReceivedDigitClassTest] guards the patterns' spelling for that reason.
 */
@RunWith(Parameterized::class)
internal class ReadReceivedNumbersTest(
    private val case: String,
    private val read: () -> ReceivedPlace,
    private val expected: ReceivedPlace,
) {
    @Test
    fun `reads no point from numbers that are no coordinates`() {
        assertEquals(expected, read(), case)
    }

    companion object {
        private fun query(text: String) = ReceivedPlace.Found(PlaceTarget.Query(text))

        private fun geo(
            case: String,
            uri: String,
            expected: ReceivedPlace,
        ): Array<Any> = arrayOf(case, { readGeoUri(uri) }, expected)

        private fun shared(
            case: String,
            text: String,
            expected: ReceivedPlace,
        ): Array<Any> = arrayOf(case, { readSharedText(text, subject = null, title = null) }, expected)

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> =
            listOf(
                geo("Arabic-Indic digits in a geo path", "geo:٣٥.٦,١٣٩.٧", ReceivedPlace.Unreadable),
                geo("full-width digits in a geo path", "geo:３５.６,１３９.７", ReceivedPlace.Unreadable),
                geo("Devanagari digits in a geo q", "geo:0,0?q=३५.६,१३९.७", query("३५.६,१३९.७")),
                geo("an exponent in a geo path", "geo:1e999,2", ReceivedPlace.Unreadable),
                geo("NaN and Infinity in a geo path", "geo:NaN,Infinity", ReceivedPlace.Unreadable),
                geo("NaN and Infinity in a geo q", "geo:0,0?q=Infinity,NaN", query("Infinity,NaN")),
                geo("a lone sign in a geo path", "geo:-,+", ReceivedPlace.Unreadable),
                geo("an empty number in a geo path", "geo:,5", ReceivedPlace.Unreadable),
                geo("an empty number in a geo q", "geo:0,0?q=,5", query(",5")),
                geo("a number past Double's range", "geo:${"9".repeat(400)}.5,1", ReceivedPlace.Unreadable),
                shared("Arabic-Indic digits in a bare pair", "٣٥.٦٥, ١٣٩.٧٤", query("٣٥.٦٥, ١٣٩.٧٤")),
                shared("full-width digits in a bare pair", "３５.６５, １３９.７４", query("３５.６５, １３９.７４")),
                shared("Devanagari digits in a bare pair", "३५.६५, १३९.७४", query("३५.६५, १३९.७४")),
                shared(
                    "full-width digits in a link query",
                    "https://maps.example.com/?q=３５.６,１３９.７",
                    ReceivedPlace.Unreadable,
                ),
                shared(
                    "Arabic-Indic digits after an at sign",
                    "https://maps.example.com/@٣٥.٦,١٣٩.٧",
                    ReceivedPlace.Unreadable,
                ),
                shared("an exponent in a bare pair", "1e999, 2.0", query("1e999, 2.0")),
                shared("NaN and Infinity spelled out", "NaN, Infinity", query("NaN, Infinity")),
                shared("a lone sign", "-, +", query("-, +")),
            )
    }
}

/**
 * The receive parser's patterns spell their character classes in ASCII. On
 * Android the regex shorthands for digits, word characters, word boundaries
 * and whitespace, and their negations, take their Unicode meaning: other
 * scripts' digits would reach the number parse, and a name glued to a link or a
 * pair would read differently. The JVM's shorthands are ASCII-only, which hides
 * that from every other test here. Gradle runs unit tests in the module
 * directory, so the source is at hand.
 */
class ReceivedDigitClassTest {
    @Test
    fun `the receive parser spells its character classes in ASCII`() {
        val source = java.io.File("src/main/java/io/github/seijikohara/femto/data/places/ReceivedPlace.kt").readText()

        val found = UNICODE_SHORTHANDS.map { letter -> "\\" + letter }.filter { it in source }

        assertEquals(emptyList(), found, "ReceivedPlace.kt uses regex shorthands whose meaning differs on Android")
    }

    private companion object {
        val UNICODE_SHORTHANDS = listOf('d', 'D', 'w', 'W', 'b', 's', 'S')
    }
}
