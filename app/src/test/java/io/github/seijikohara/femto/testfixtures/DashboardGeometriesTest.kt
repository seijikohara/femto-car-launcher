package io.github.seijikohara.femto.testfixtures

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardGeometriesTest {
    @Test
    fun lists_the_twelve_golden_geometries_in_golden_order() =
        assertEquals(
            listOf(
                "floor-800x480",
                "head-unit-853x512",
                "budget-1024x600",
                "mainstream-1280x720",
                "ultrawide-1920x720",
                "flagship-1920x1080",
                "premium-2000x1200",
                "phone-landscape-915x412",
                "phone-portrait-412x915",
                "tablet-800x1280",
                "car-portrait-1024x1365",
                "car-portrait-tall-1200x1920",
            ),
            DashboardGeometries.all.map { it.id },
        )

    @Test
    fun qualifiers_match_the_dp_size_of_every_geometry() =
        DashboardGeometries.all.forEach { geometry ->
            assertEquals(geometry.id, "w${geometry.widthDp}dp-h${geometry.heightDp}dp-mdpi", geometry.qualifiers)
        }

    @Test
    fun ids_are_url_and_file_name_safe_slugs() =
        DashboardGeometries.all.forEach { geometry ->
            assertTrue(geometry.id, Regex("^[a-z0-9]+(-[a-z0-9]+)*$").matches(geometry.id))
        }
}
