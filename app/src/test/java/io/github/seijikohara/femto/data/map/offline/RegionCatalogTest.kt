package io.github.seijikohara.femto.data.map.offline

import io.github.seijikohara.femto.testfixtures.sampleRegionCatalog
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RegionCatalogTest {
    private val catalog = sampleRegionCatalog()

    @Test
    fun `the roots are the continents that have something to offer`() =
        assertEquals(listOf("Asia", "Europe", "North America"), catalog.childrenOf(null).map { it.name })

    @Test
    fun `a region without a file and without children is not offered`() =
        assertEquals(listOf("Cyprus", "Germany", "Monaco", "Poland"), catalog.childrenOf("europe").map { it.name })

    @Test
    fun `children are listed by name`() =
        assertEquals(listOf("Bayern", "Berlin"), catalog.childrenOf("germany").map { it.name })

    @Test
    fun `a region whose sub-regions have no file has no offered children`() {
        assertFalse(catalog.hasOfferedChildren("bayern"))
        assertTrue(catalog.hasOfferedChildren("germany"))
    }

    @Test
    fun `a subtree without any file is not offered`() =
        assertEquals(listOf("United States of America"), catalog.childrenOf("north-america").map { it.name })

    @Test
    fun `an id with a slash takes its parent from the id, not from the index`() {
        assertEquals("us", catalog.regionOrNull("us/california")?.parentId)
        assertEquals(listOf("California", "Nevada"), catalog.childrenOf("us").map { it.name })
        assertFalse(catalog.hasOfferedChildren("us/california"))
    }

    @Test
    fun `markup in a name becomes plain text`() =
        assertEquals(
            "Województwo dolnośląskie (Lower Silesian Voivodeship)",
            catalog.regionOrNull("dolnoslaskie")?.name,
        )

    // The host keeps every extract in a directory named after its region, and at the time of writing the Shortbread
    // files beside that directory. The index names the extract only, so the catalogue names both places.
    @Test
    fun `an extract in its region's own directory names the file inside it and the one beside it`() {
        val kanto = catalog.regionOrNull("kanto")

        assertEquals("https://download.geofabrik.de/asia/japan/kanto/kanto-shortbread-1.0.mbtiles", kanto?.fileUrl)
        assertEquals("https://download.geofabrik.de/asia/japan/kanto-shortbread-1.0.mbtiles", kanto?.alternateFileUrl)
    }

    @Test
    fun `a top-level region's other place is the host's root`() =
        assertEquals(
            "https://download.geofabrik.de/asia-shortbread-1.0.mbtiles",
            catalog.regionOrNull("asia")?.alternateFileUrl,
        )

    @Test
    fun `an extract beside its siblings names one place only`() {
        val region =
            catalogOf(feature(id = "a", pbf = "https://download.geofabrik.de/asia/japan/a-latest.osm.pbf"))
                .regionOrNull("a")

        assertEquals("https://download.geofabrik.de/asia/japan/a-shortbread-1.0.mbtiles", region?.fileUrl)
        assertNull(region?.alternateFileUrl)
    }

    // A file at the host's root has no directory above it to be beside.
    @Test
    fun `an extract at the host's root names one place only`() =
        assertNull(catalogOf(feature(id = "a")).regionOrNull("a")?.alternateFileUrl)

    @Test
    fun `the size comes from the snapshot`() {
        assertEquals(862_351_360L, catalog.regionOrNull("kanto")?.approxBytes)
        assertNull(catalog.regionOrNull("japan")?.approxBytes)
    }

    @Test
    fun `a region that is not in the snapshot can still be a parent`() =
        assertEquals(listOf("Kantō region", "Shikoku"), catalog.childrenOf("japan").map { it.name })

    @Test
    fun `the suggestion is the smallest region with a file around the point`() {
        assertEquals("kanto", catalog.smallestContainingOrNull(139.76, 35.68)?.id)
        assertEquals("monaco", catalog.smallestContainingOrNull(7.42, 43.73)?.id)
        assertEquals("bayern", catalog.smallestContainingOrNull(11.58, 48.14)?.id)
        assertEquals("us/nevada", catalog.smallestContainingOrNull(-115.14, 36.17)?.id)
    }

    @Test
    fun `there is no suggestion outside every region`() = assertNull(catalog.smallestContainingOrNull(-150.0, 0.0))

    @Test
    fun `a region from another host is dropped`() {
        val catalog = catalogOf(feature(id = "a", pbf = "https://example.org/a-latest.osm.pbf"))
        assertNull(catalog.regionOrNull("a"))
    }

    @Test
    fun `a url that is not an extract is dropped`() {
        val catalog = catalogOf(feature(id = "a", pbf = "https://download.geofabrik.de/a.osm.pbf"))
        assertNull(catalog.regionOrNull("a"))
    }

    @Test
    fun `a region without urls is dropped`() {
        val catalog = catalogOf("""{"type":"Feature","properties":{"id":"a","name":"A"},"geometry":null}""")
        assertNull(catalog.regionOrNull("a"))
    }

    @Test
    fun `an id that is not a plain slug is dropped`() {
        val catalog = catalogOf(feature(id = "../x", pbf = "https://download.geofabrik.de/x-latest.osm.pbf"))
        assertNull(catalog.regionOrNull("../x"))
    }

    @Test
    fun `tags in a name are removed and line breaks become a space`() {
        val catalog = catalogOf(feature(id = "a", name = "<b>X</b><br />Y"))
        assertEquals("X Y", catalog.regionOrNull("a")?.name)
    }

    @Test
    fun `a blank name falls back to the last segment of the id`() {
        val catalog = catalogOf(feature(id = "us/new-york", name = "<br>"))
        assertEquals("New York", catalog.regionOrNull("us/new-york")?.name)
    }

    @Test
    fun `parents that loop are dropped and the rest is kept`() {
        val catalog =
            catalogOf(
                feature(id = "a", parent = "b"),
                feature(id = "b", parent = "a"),
                feature(id = "c"),
            )
        assertNull(catalog.regionOrNull("a"))
        assertNull(catalog.regionOrNull("b"))
        assertNotNull(catalog.regionOrNull("c"))
    }

    @Test
    fun `a parent that is not in the index makes the region a root`() {
        val catalog = catalogOf(feature(id = "a", parent = "missing"))
        assertNotNull(catalog.regionOrNull("a"))
        assertNull(catalog.regionOrNull("a")?.parentId)
    }

    @Test
    fun `a geometry that is not a multi-polygon is kept and contains nothing`() {
        val catalog =
            catalogOf(
                feature(
                    id = "a",
                    geometry = """{"type":"Polygon","coordinates":[[[0,0],[10,0],[10,10],[0,10],[0,0]]]}""",
                ),
            )
        assertNotNull(catalog.regionOrNull("a"))
        assertNull(catalog.smallestContainingOrNull(5.0, 5.0))
    }

    @Test
    fun `a ring with fewer than four positions is ignored`() {
        val catalog =
            catalogOf(
                feature(id = "a", geometry = """{"type":"MultiPolygon","coordinates":[[[[0,0],[10,0],[5,10]]]]}"""),
            )
        assertNull(catalog.smallestContainingOrNull(5.0, 5.0))
    }

    @Test
    fun `a body that is not JSON is not a region index`() =
        assertNull(regionCatalogOrNull("<html>nope</html>".byteInputStream(), emptyMap()))

    @Test
    fun `JSON without features is not a region index`() =
        assertNull(regionCatalogOrNull("""{"type":"FeatureCollection"}""".byteInputStream(), emptyMap()))

    @Test
    fun `a snapshot entry with a negative or non-numeric size is ignored`() {
        val sizes =
            shortbreadSizesOrNull(
                """{"regions":{"a":5,"b":-1,"c":"big","d":null,"e":1.5}}""".byteInputStream(),
            )
        assertEquals(mapOf("a" to 5L), sizes)
    }

    @Test
    fun `a body that is not a snapshot has no sizes`() {
        assertNull(shortbreadSizesOrNull("[]".byteInputStream()))
        assertNull(shortbreadSizesOrNull("""{"generated":"x"}""".byteInputStream()))
    }

    // Every hostile case needs a file, or the catalogue would not tell a dropped region from an unoffered one.
    private fun feature(
        id: String,
        parent: String? = null,
        name: String = id.uppercase(),
        pbf: String = "https://download.geofabrik.de/$id-latest.osm.pbf",
        geometry: String = """{"type":"MultiPolygon","coordinates":[[[[0,0],[10,0],[10,10],[0,10],[0,0]]]]}""",
    ): String {
        val parentField = parent?.let { """"parent":"$it",""" }.orEmpty()
        return """{"type":"Feature","properties":{"id":"$id",$parentField"name":"$name","urls":{"pbf":"$pbf"}},""" +
            """"geometry":$geometry}"""
    }

    private fun catalogOf(vararg features: String): RegionCatalog =
        requireNotNull(
            regionCatalogOrNull(
                index = """{"features":[${features.joinToString(",")}]}""".byteInputStream(),
                sizes = HostileSizes,
            ),
        )

    private companion object {
        val HostileSizes = listOf("a", "b", "c", "../x", "us/new-york").associateWith { 1L }
    }
}
