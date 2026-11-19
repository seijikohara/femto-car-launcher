package io.github.seijikohara.femto.data.map.offline

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OfflineRegionFilesTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun files(root: File = File(folder.root, "offline_map")) = OfflineRegionFiles(root)

    private fun pending(
        id: String = "europe/norway",
        sizeBytes: Long = 5L,
    ) = PendingDownloadRecord(
        id = id,
        name = "Norway",
        url = "https://download.geofabrik.de/europe/norway.mbtiles",
        sizeBytes = sizeBytes,
        etag = "\"abc\"",
        lastModifiedEpochMs = 1_000L,
        polygon = listOf(listOf(0.0, 0.0, 1.0, 0.0, 1.0, 1.0)),
    )

    private fun saved(
        id: String = "europe/norway",
        sizeBytes: Long = 5L,
        savedAtEpochMs: Long = 2_000L,
    ) = SavedRegionRecord(
        id = id,
        name = "Norway",
        url = "https://download.geofabrik.de/europe/norway.mbtiles",
        sizeBytes = sizeBytes,
        etag = null,
        lastModifiedEpochMs = null,
        savedAtEpochMs = savedAtEpochMs,
        polygon = listOf(listOf(0.0, 0.0, 1.0, 0.0, 1.0, 1.0)),
    )

    /** Create the file (and the regions directory above it) with [count] bytes. */
    private fun File.fill(count: Int) {
        parentFile?.mkdirs()
        writeBytes(ByteArray(count) { 1 })
    }

    private fun File.recordFile() = File(parentFile, name.removeSuffix(".mbtiles") + ".json")

    @Test
    fun `a committed region is listed with its record and its part becomes the data file`() {
        val files = files()
        assertTrue(files.writePending(pending()))
        files.partFile("europe/norway").fill(5)

        assertTrue(files.commit(saved()))

        assertEquals(listOf(saved()), files.savedRegions())
        assertTrue(files.dataFile("europe/norway").isFile)
        assertFalse(files.partFile("europe/norway").exists())
        assertNull(files.pendingOrNull())
    }

    @Test
    fun `the pending record is stored and read back`() {
        val files = files()
        assertTrue(files.writePending(pending()))
        assertEquals(pending(), files.pendingOrNull())
    }

    @Test
    fun `the record survives a round trip with a null etag and a multi-ring polygon`() {
        val files = files()
        val record =
            saved().copy(
                polygon = listOf(listOf(0.0, 0.0, 1.0, 0.0, 1.0, 1.0), listOf(10.5, 10.5, 11.5, 10.5, 11.5, 11.5)),
            )
        files.partFile(record.id).fill(5)

        assertTrue(files.commit(record))

        assertEquals(listOf(record), files.savedRegions())
    }

    @Test
    fun `a record of another version is not listed and not deleted`() {
        val files = files()
        files.partFile("a").fill(5)
        assertTrue(files.commit(saved(id = "a")))
        val recordFile = files.dataFile("a").recordFile()
        recordFile.writeText(recordFile.readText().replace("\"version\":1", "\"version\":2"))

        assertEquals(emptyList(), files.savedRegions())
        assertTrue(recordFile.isFile)
    }

    @Test
    fun `a record that is not json is not listed`() {
        val files = files()
        files.partFile("a").fill(5)
        assertTrue(files.commit(saved(id = "a")))
        files.dataFile("a").recordFile().writeText("not json {")

        assertEquals(emptyList(), files.savedRegions())
    }

    @Test
    fun `deleting a region removes its data and its record`() {
        val files = files()
        files.partFile("a").fill(5)
        assertTrue(files.commit(saved(id = "a")))

        files.delete("a")

        assertEquals(emptyList(), files.savedRegions())
        assertFalse(files.dataFile("a").exists())
        assertFalse(files.dataFile("a").recordFile().exists())
    }

    @Test
    fun `discarding the pending download removes its part`() {
        val files = files()
        assertTrue(files.writePending(pending()))
        files.partFile("europe/norway").fill(3)

        files.discardPending()

        assertNull(files.pendingOrNull())
        assertFalse(files.partFile("europe/norway").exists())
    }

    @Test
    fun `the key of an id with a slash stays inside the regions directory`() {
        val files = files()
        assertEquals("us%2Fcalifornia", regionFileKey("us/california"))
        assertEquals(files.dataFile("us/california").parentFile, files.partFile("us/california").parentFile)
        assertEquals("regions", files.dataFile("us/california").parentFile?.name)
    }

    @Test
    fun `the key of a hostile id has no path separator and no dot`() {
        listOf("../x", "a/../../b", "").forEach { id ->
            val key = regionFileKey(id)
            assertFalse('/' in key, key)
            assertFalse('\\' in key, key)
            assertFalse('.' in key, key)
        }
    }

    @Test
    fun `used bytes add every file and kept bytes only the saved data`() {
        val files = files()
        files.partFile("a").fill(5)
        assertTrue(files.commit(saved(id = "a")))
        assertTrue(files.writePending(pending(id = "b")))
        files.partFile("b").fill(7)

        val everyFile = File(folder.root, "offline_map").walkTopDown().filter { it.isFile }.sumOf { it.length() }

        assertEquals(5L, files.keptBytes())
        assertEquals(everyFile, files.usedBytes())
        assertTrue(files.usedBytes() > 12L)
    }

    @Test
    fun `reconcile finishes a commit that stopped after the rename`() {
        val files = files()
        assertTrue(files.writePending(pending(sizeBytes = 5)))
        files.dataFile("europe/norway").fill(5)
        files.dataFile("europe/norway").setLastModified(3_000_000L)

        files.reconcile()

        assertEquals(1, files.savedRegions().size)
        assertEquals(3_000_000L, files.savedRegions().single().savedAtEpochMs)
        assertEquals("Norway", files.savedRegions().single().name)
        assertNull(files.pendingOrNull())
    }

    @Test
    fun `a missing root still reports the free space of the disk`() {
        val root = File(folder.root, "nowhere/offline_map")

        assertTrue(OfflineRegionFiles(root).freeBytes() > 0L)
        assertFalse(root.exists())
    }

    @Test
    fun `reconcile does not finish a commit while a part or a record exists`() {
        val withPart = files(File(folder.root, "with-part"))
        assertTrue(withPart.writePending(pending(sizeBytes = 5)))
        withPart.dataFile("europe/norway").fill(5)
        withPart.partFile("europe/norway").fill(2)

        withPart.reconcile()

        assertEquals(pending(sizeBytes = 5), withPart.pendingOrNull())
        assertTrue(withPart.partFile("europe/norway").isFile)
        assertEquals(emptyList(), withPart.savedRegions())

        val withRecord = files(File(folder.root, "with-record"))
        withRecord.partFile("europe/norway").fill(5)
        assertTrue(withRecord.commit(saved()))
        assertTrue(withRecord.writePending(pending(sizeBytes = 5)))

        withRecord.reconcile()

        assertEquals(pending(sizeBytes = 5), withRecord.pendingOrNull())
        assertEquals(listOf(saved()), withRecord.savedRegions())
    }

    @Test
    fun `reconcile deletes a data file nobody describes`() {
        val files = files()
        files.dataFile("orphan").fill(5)

        files.reconcile()

        assertFalse(files.dataFile("orphan").exists())
    }

    @Test
    fun `reconcile deletes a record without data and a part without a pending record`() {
        val files = files()
        files.partFile("a").fill(5)
        assertTrue(files.commit(saved(id = "a")))
        files.dataFile("a").delete()
        files.partFile("stray").fill(2)

        files.reconcile()

        assertEquals(emptyList(), files.savedRegions())
        assertFalse(files.dataFile("a").recordFile().exists())
        assertFalse(files.partFile("stray").exists())
    }

    @Test
    fun `reconcile keeps a pending record that has no part yet`() {
        val files = files()
        assertTrue(files.writePending(pending()))

        files.reconcile()

        assertEquals(pending(), files.pendingOrNull())
    }

    @Test
    fun `a missing root lists nothing and uses nothing`() {
        val files = files(File(folder.root, "nowhere"))

        files.reconcile()

        assertEquals(emptyList(), files.savedRegions())
        assertNull(files.pendingOrNull())
        assertEquals(0L, files.usedBytes())
        assertEquals(0L, files.keptBytes())
    }
}
