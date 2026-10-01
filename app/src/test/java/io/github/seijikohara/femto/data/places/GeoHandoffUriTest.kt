package io.github.seijikohara.femto.data.places

import org.junit.Test
import java.util.Locale
import kotlin.test.assertEquals

class GeoHandoffUriTest {
    @Test
    fun query_is_sent_at_the_null_island_origin_with_spaces_and_ampersand_encoded() =
        assertEquals(
            "geo:0,0?q=1st%20%26%20Pike%2C%20Seattle",
            geoHandoffUri(PlaceTarget.Query("1st & Pike, Seattle")),
        )

    @Test
    fun query_encodes_hash_and_question_mark_so_they_stay_inside_q() =
        assertEquals(
            "geo:0,0?q=Gate%20%233%3F",
            geoHandoffUri(PlaceTarget.Query("Gate #3?")),
        )

    @Test
    fun query_encodes_non_latin_scripts_as_utf8_octets() =
        assertEquals(
            "geo:0,0?q=%E6%9D%B1%E4%BA%AC%E9%A7%85",
            geoHandoffUri(PlaceTarget.Query("東京駅")),
        )

    @Test
    fun query_keeps_unreserved_characters_verbatim() =
        assertEquals(
            "geo:0,0?q=A-z_0.9~",
            geoHandoffUri(PlaceTarget.Query("A-z_0.9~")),
        )

    @Test
    fun query_ignores_the_label() =
        assertEquals(
            "geo:0,0?q=Airport",
            geoHandoffUri(PlaceTarget.Query("Airport"), label = "Flights"),
        )

    @Test
    fun point_pins_the_coordinates_with_the_label_in_parentheses() =
        assertEquals(
            "geo:35.681236,139.767125?q=35.681236,139.767125(Home)",
            geoHandoffUri(PlaceTarget.Point(35.681236, 139.767125), label = "Home"),
        )

    @Test
    fun point_label_parentheses_are_encoded_so_the_label_syntax_stays_intact() =
        assertEquals(
            "geo:1.000000,2.000000?q=1.000000,2.000000(Home%20%28new%29)",
            geoHandoffUri(PlaceTarget.Point(1.0, 2.0), label = "Home (new)"),
        )

    @Test
    fun point_without_a_label_omits_the_parentheses() =
        assertEquals(
            "geo:1.000000,2.000000?q=1.000000,2.000000",
            geoHandoffUri(PlaceTarget.Point(1.0, 2.0), label = " "),
        )

    @Test
    fun point_coordinates_stay_plain_decimals_never_scientific_notation() =
        assertEquals(
            "geo:-0.000100,-73.500000?q=-0.000100,-73.500000",
            geoHandoffUri(PlaceTarget.Point(-0.0001, -73.5)),
        )

    @Test
    fun point_coordinates_use_a_dot_whatever_the_default_locale() {
        val saved = Locale.getDefault()
        Locale.setDefault(Locale.GERMANY)
        try {
            assertEquals(
                "geo:48.137154,11.576124?q=48.137154,11.576124",
                geoHandoffUri(PlaceTarget.Point(48.137154, 11.576124)),
            )
        } finally {
            Locale.setDefault(saved)
        }
    }
}
