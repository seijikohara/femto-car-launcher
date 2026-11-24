package io.github.seijikohara.femto.data.places

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.test.assertEquals

/**
 * Decision table for [readSharedText]: a position wins (a `geo:` URI, then a
 * decimal pair in the prose, then a pair inside a link); without one, the
 * subject (then the title, then the text's own lines) becomes a search text;
 * a share of links only says nothing the launcher can read. The first row is
 * the share a maps app sends, as seen on the emulator: the place's name as the
 * subject and a short link, with a trailing space, as the text.
 */
@RunWith(Parameterized::class)
internal class ReadSharedTextTest(
    private val case: String,
    private val text: String?,
    private val subject: String?,
    private val title: String?,
    private val expected: ReceivedPlace,
) {
    @Test
    fun `reads the place a share carries`() {
        assertEquals(expected, readSharedText(text, subject, title), case)
    }

    companion object {
        private val TOWER = PlaceTarget.Point(35.6586, 139.7454)
        private val SHORT = PlaceTarget.Point(35.6, 139.7)

        private fun at(
            target: PlaceTarget,
            label: String = "",
        ) = ReceivedPlace.Found(target, label)

        private fun query(text: String) = ReceivedPlace.Found(PlaceTarget.Query(text))

        private fun row(
            case: String,
            text: String?,
            expected: ReceivedPlace,
            subject: String? = null,
            title: String? = null,
        ): Array<Any?> = arrayOf(case, text, subject, title, expected)

        private val LONG_PREFIX = "x".repeat(MAX_RECEIVED_TEXT_LENGTH + 500)

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any?>> =
            listOf(
                row(
                    "a maps app's share: name as subject, short link as text",
                    "https://maps.app.goo.gl/AbCdEf123?g_st=am ",
                    query("Matsuyama Castle"),
                    subject = "Matsuyama Castle",
                ),
                row(
                    "a geo URI in the text",
                    "Meet here geo:0,0?q=35.6586,139.7454(Tokyo%20Tower)",
                    at(TOWER, "Tokyo Tower"),
                ),
                row(
                    "an unlabelled geo URI takes the subject",
                    "geo:35.6586,139.7454",
                    at(TOWER, "Tower"),
                    subject = "Tower",
                ),
                row("a geo URI with a search text", "geo:0,0?q=Central+Station", query("Central Station")),
                row("a decimal pair", "35.6586, 139.7454", at(TOWER)),
                row("a decimal pair without a space", "35.6586,139.7454", at(TOWER)),
                row("a pair ending a sentence", "We are at 35.6586, 139.7454.", at(TOWER)),
                row("a name line above a pair", "Tokyo Tower\n35.6586, 139.7454", at(TOWER, "Tokyo Tower")),
                row("the subject names a pair", "35.6586, 139.7454", at(TOWER, "Tower"), subject = "Tower"),
                row("a negative pair", "-33.8568, 151.2153", at(PlaceTarget.Point(-33.8568, 151.2153))),
                row("a q pair inside a link", "https://maps.example.com/?q=35.6,139.7", at(SHORT)),
                row("an ll pair inside a link", "https://maps.example.com/?z=3&ll=35.6,139.7", at(SHORT)),
                row("an at-sign pair inside a link", "https://maps.example.com/@35.6,139.7,15z", at(SHORT)),
                row("an encoded pair inside a link", "https://maps.example.com/?q=35.6%2C139.7", at(SHORT)),
                row(
                    "a name line names a link pair",
                    "Tokyo Tower\nhttps://maps.example.com/@35.6,139.7,15z",
                    at(SHORT, "Tokyo Tower"),
                ),
                row("a name line above a link", "Tokyo Tower\nhttps://maps.example.com/p/123", query("Tokyo Tower")),
                row(
                    "several name lines joined",
                    "Tokyo Tower\r\n4-2-8 Shibakoen, Minato City\nhttps://maps.example.com/p/123",
                    query("Tokyo Tower, 4-2-8 Shibakoen, Minato City"),
                ),
                row(
                    "a name before a link on one line",
                    "Tokyo Tower https://maps.example.com/p/123",
                    query("Tokyo Tower"),
                ),
                row(
                    "the title when there is no subject",
                    "https://maps.example.com/p/123",
                    query("Tower"),
                    title = "Tower",
                ),
                row(
                    "the subject before the title",
                    "https://maps.example.com/p/123",
                    query("Castle"),
                    subject = "Castle",
                    title = "Tower",
                ),
                row(
                    "a blank subject falls to the text",
                    "Tokyo Tower https://maps.example.com/p/123",
                    query("Tokyo Tower"),
                    subject = "  ",
                ),
                row(
                    "a subject that is only a link",
                    "https://maps.example.com/p/1",
                    ReceivedPlace.Unreadable,
                    subject = "https://x.example/2",
                ),
                row("only a link", "https://maps.app.goo.gl/AbCdEf123", ReceivedPlace.Unreadable),
                row("several links", "https://a.example/1\nhttps://b.example/2 ", ReceivedPlace.Unreadable),
                row("empty text", "", ReceivedPlace.Unreadable),
                row("no text at all", null, ReceivedPlace.Unreadable),
                row("whitespace only", " \n\t ", ReceivedPlace.Unreadable),
                row("an integer pair is no place", "10, 20 people", query("10, 20 people")),
                row("a latitude out of range is no place", "95.1, 139.7", query("95.1, 139.7")),
                row("a longitude out of range is no place", "35.6, 180.5", query("35.6, 180.5")),
                row(
                    "an out-of-range link pair is no place",
                    "https://maps.example.com/?q=95.1,139.7",
                    ReceivedPlace.Unreadable,
                ),
                row("a version number is no place", "v1.2,3.4", query("v1.2,3.4")),
                // On Android the regex shorthands for a word character and a word boundary cover every script, so
                // these two read differently there unless the patterns spell their classes in ASCII; on the JVM they
                // pass either way (ReceivedDigitClassTest guards the spelling).
                row("a name glued to a link", "ここで会おうhttps://maps.app.goo.gl/AbC", query("ここで会おう")),
                row("a pair glued to a word of another script", "35.6586,139.7454付近", at(TOWER)),
                row("control characters are stripped", "Tokyo\u0000 Tower\u001B\u0007", query("Tokyo Tower")),
                row(
                    "control characters in the subject",
                    "https://maps.example.com/p/1",
                    query("Castle"),
                    subject = "Cas\u0000tle\u0085",
                ),
                row(
                    "text past the cap is dropped, with a pair in it",
                    "$LONG_PREFIX 35.6586, 139.7454",
                    query("x".repeat(MAX_RECEIVED_TEXT_LENGTH)),
                ),
                row(
                    "a cut never leaves half a surrogate pair",
                    "x".repeat(MAX_RECEIVED_TEXT_LENGTH - 1) + "🚗",
                    query("x".repeat(MAX_RECEIVED_TEXT_LENGTH - 1)),
                ),
            )
    }
}
