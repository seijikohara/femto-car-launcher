package io.github.seijikohara.femto.catalog

import io.github.seijikohara.femto.data.display.DockPosition
import io.github.seijikohara.femto.data.display.DriverSide
import io.github.seijikohara.femto.data.display.UiScale
import io.github.seijikohara.femto.testfixtures.DashboardGeometries
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class CatalogMatrixTest {
    @Test
    fun entries_are_the_full_product_of_the_five_axes() =
        assertEquals(
            DashboardGeometries.all.size * UiScale.entries.size * 2 * DriverSide.entries.size *
                DockPosition.entries.size,
            CatalogMatrix.entries.size,
        )

    @Test
    fun entry_ids_are_unique_five_part_slugs() {
        val ids = CatalogMatrix.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        ids.forEach { id -> assertTrue(id, Regex("^[a-z0-9-]+(__[a-z0-9-]+){4}$").matches(id)) }
    }

    @Test
    fun entry_id_joins_the_axis_slugs_in_axis_order() =
        assertEquals(
            "head-unit-853x512__large__dark__left__top",
            CatalogEntry(
                geometry = DashboardGeometries.all.first { it.id == "head-unit-853x512" },
                scale = UiScale.LARGE,
                darkTheme = true,
                driverSide = DriverSide.LEFT,
                dockPosition = DockPosition.TOP,
            ).id,
        )

    @Test
    fun geometry_is_the_outermost_axis_and_dock_the_innermost() {
        val first = CatalogMatrix.entries.first()
        val second = CatalogMatrix.entries[1]
        assertEquals("floor-800x480__small__light__right__bottom", first.id)
        assertEquals("floor-800x480__small__light__right__top", second.id)
    }

    @Test
    fun filter_keeps_only_matching_ids() =
        assertEquals(
            listOf("head-unit-853x512__medium__light__right__bottom"),
            CatalogMatrix.entries(Regex("head-unit-853x512__medium__light__right__bottom")).map { it.id },
        )

    @Test
    fun null_filter_keeps_everything() = assertEquals(CatalogMatrix.entries, CatalogMatrix.entries(null))

    @Test
    fun manifest_round_trips_through_json() {
        val manifest =
            CatalogMatrix.manifest(
                entries = CatalogMatrix.entries.take(3),
                generatedAt = Instant.parse("2026-09-20T12:00:00Z"),
                gitSha = "abc1234",
            )
        val decoded = Json.decodeFromString<CatalogManifest>(
            CatalogJson.encodeToString(CatalogManifest.serializer(), manifest),
        )
        assertEquals(manifest, decoded)
    }

    // The round trip above cannot catch a missing schemaVersion: decoding fills the
    // default back in. The site reads the text, so the text must carry it.
    @Test
    fun manifest_json_carries_the_schema_version() =
        assertTrue(
            CatalogJson
                .encodeToString(
                    CatalogManifest.serializer(),
                    CatalogMatrix.manifest(
                        CatalogMatrix.entries.take(1),
                        Instant.parse("2026-09-20T12:00:00Z"),
                        "abc1234",
                    ),
                ).contains("\"schemaVersion\": 1"),
        )

    @Test
    fun manifest_describes_axes_values_and_entries() {
        val manifest =
            CatalogMatrix.manifest(CatalogMatrix.entries.take(1), Instant.parse("2026-09-20T12:00:00Z"), "abc1234")
        assertEquals(1, manifest.schemaVersion)
        assertEquals("2026-09-20T12:00:00Z", manifest.generatedAt)
        assertEquals("abc1234", manifest.gitSha)
        assertEquals(listOf("geometry", "scale", "theme", "driver", "dock"), manifest.axes.map { it.id })
        assertEquals(
            listOf("small", "compact", "medium", "comfortable", "large"),
            manifest.axes[1].values.map { it.id },
        )
        assertEquals(listOf("Light", "Dark"), manifest.axes[2].values.map { it.label })
        val entry = manifest.entries.single()
        assertEquals("floor-800x480__small__light__right__bottom", entry.id)
        assertEquals("img/floor-800x480__small__light__right__bottom.png", entry.file)
        assertEquals(800, entry.widthDp)
        assertEquals(480, entry.heightDp)
        assertEquals(
            mapOf(
                "geometry" to "floor-800x480",
                "scale" to "small",
                "theme" to "light",
                "driver" to "right",
                "dock" to "bottom",
            ),
            entry.values,
        )
    }

    @Test
    fun every_entry_value_names_a_declared_axis_value() {
        val declared = CatalogMatrix.axes.associate { axis -> axis.id to axis.values.map { it.id }.toSet() }
        CatalogMatrix.entries.forEach { entry ->
            entry.values().forEach { (axisId, valueId) ->
                assertTrue("$axisId=$valueId", declared.getValue(axisId).contains(valueId))
            }
        }
    }
}
