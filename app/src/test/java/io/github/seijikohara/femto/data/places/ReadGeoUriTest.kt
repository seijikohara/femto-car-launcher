package io.github.seijikohara.femto.data.places

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.test.assertEquals

/**
 * Decision table for [readGeoUri]: every `geo:` form of RFC 5870 and Android's
 * common intents, the launcher's own hand-off read back ([geoHandoffUri]), and
 * the malformed input a home screen must survive without a crash.
 */
@RunWith(Parameterized::class)
internal class ReadGeoUriTest(
    private val case: String,
    private val uri: String,
    private val expected: ReceivedPlace,
) {
    @Test
    fun `reads the place a geo URI carries`() {
        assertEquals(expected, readGeoUri(uri), case)
    }

    companion object {
        private val TOWER = PlaceTarget.Point(35.6586, 139.7454)

        private fun point(
            target: PlaceTarget,
            label: String = "",
        ) = ReceivedPlace.Found(target, label)

        private fun query(text: String) = ReceivedPlace.Found(PlaceTarget.Query(text))

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> =
            listOf(
                arrayOf("bare position", "geo:35.6586,139.7454", point(TOWER)),
                arrayOf("position with zoom", "geo:35.6586,139.7454?z=15", point(TOWER)),
                arrayOf("position with uncertainty", "geo:35.6586,139.7454;u=35", point(TOWER)),
                arrayOf("position with altitude", "geo:35.6586,139.7454,40", point(TOWER)),
                arrayOf("upper-case scheme", "GEO:35.6586,139.7454", point(TOWER)),
                arrayOf("integer position", "geo:35,139", point(PlaceTarget.Point(35.0, 139.0))),
                arrayOf("null island with a coordinate q", "geo:0,0?q=35.6586,139.7454", point(TOWER)),
                arrayOf(
                    "null island with a labelled q",
                    "geo:0,0?q=35.6586,139.7454(Tokyo%20Tower)",
                    point(TOWER, "Tokyo Tower"),
                ),
                arrayOf(
                    "plus as a space in the label",
                    "geo:0,0?q=35.6586,139.7454(Tokyo+Tower)",
                    point(TOWER, "Tokyo Tower"),
                ),
                arrayOf("encoded comma in q", "geo:0,0?q=35.6586%2C139.7454", point(TOWER)),
                arrayOf("position with a text q", "geo:35.6586,139.7454?q=Tokyo+Tower", point(TOWER, "Tokyo Tower")),
                arrayOf(
                    "null island with a text q",
                    "geo:0,0?q=1st%20%26%20Pike%2C%20Seattle",
                    query("1st & Pike, Seattle"),
                ),
                arrayOf("q after another parameter", "geo:0,0?z=10&q=Central+Station", query("Central Station")),
                arrayOf("text q decodes UTF-8", "geo:0,0?q=%E6%9D%B1%E4%BA%AC%E9%A7%85", query("東京駅")),
                arrayOf("malformed percent kept literally", "geo:0,0?q=100%25%zz%4", query("100%%zz%4")),
                arrayOf("invalid UTF-8 survives", "geo:0,0?q=Caf%C3", query("Caf�")),
                arrayOf("decoded control characters are dropped", "geo:0,0?q=Gate%003%0A%1B", query("Gate 3")),
                arrayOf("null island alone", "geo:0,0", ReceivedPlace.Unreadable),
                arrayOf("null island with a blank q", "geo:0,0?q=+", ReceivedPlace.Unreadable),
                arrayOf("latitude out of range", "geo:91.0,10.0", ReceivedPlace.Unreadable),
                arrayOf("longitude out of range in q", "geo:0,0?q=10.0,181.0(Nowhere)", ReceivedPlace.Unreadable),
                arrayOf("out-of-range q keeps the path position", "geo:35.6586,139.7454?q=95,10", point(TOWER)),
                arrayOf("exponent is no coordinate", "geo:1e1,2", ReceivedPlace.Unreadable),
                arrayOf("NaN is no coordinate", "geo:NaN,0", ReceivedPlace.Unreadable),
                arrayOf("garbage after the scheme", "geo:;;,,??&&==((", ReceivedPlace.Unreadable),
                arrayOf("empty", "", ReceivedPlace.Unreadable),
                arrayOf("another scheme", "https://example.com/?q=35.6586,139.7454", ReceivedPlace.Unreadable),
                arrayOf(
                    "round trip of a labelled point",
                    geoHandoffUri(PlaceTarget.Point(35.681236, 139.767125), label = "Home (new) #2 & more"),
                    point(PlaceTarget.Point(35.681236, 139.767125), "Home (new) #2 & more"),
                ),
                arrayOf(
                    "round trip of an unlabelled point",
                    geoHandoffUri(PlaceTarget.Point(-33.856784, 151.215297)),
                    point(PlaceTarget.Point(-33.856784, 151.215297)),
                ),
                arrayOf(
                    "round trip of a query",
                    geoHandoffUri(PlaceTarget.Query("Gate #3? (east) 東京駅 + 1st & Pike")),
                    query("Gate #3? (east) 東京駅 + 1st & Pike"),
                ),
            )
    }
}
