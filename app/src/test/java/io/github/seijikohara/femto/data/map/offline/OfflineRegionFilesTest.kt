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

    private val root: File get() = File(folder.root, "offline_map")

    private fun files(root: File = this.root) = OfflineRegionFiles(root, usableBytes = { FREE_BYTES })

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
    fun `the free space comes from the measure it is given without creating the root`() {
        val root = File(folder.root, "nowhere/offline_map")

        assertEquals(FREE_BYTES, files(root).freeBytes())
        assertFalse(root.exists())
    }

    @Test
    fun `reconcile drops a pending record that the saved region of the same size and etag describes`() {
        val files = files()
        val record = saved().copy(etag = pending().etag)
        files.partFile(record.id).fill(5)
        assertTrue(files.commit(record))
        // The commit stopped before its last step: the pending record is still on disk.
        assertTrue(files.writePending(pending()))

        files.reconcile()

        assertNull(files.pendingOrNull())
        assertEquals(listOf(record), files.savedRegions())
        assertTrue(files.dataFile(record.id).isFile)
    }

    @Test
    fun `reconcile deletes an empty record and one that is not json with their data files`() {
        val files = files()
        mapOf("a" to "", "b" to "not json {").forEach { (id, text) ->
            files.partFile(id).fill(5)
            assertTrue(files.commit(saved(id = id)))
            files.dataFile(id).recordFile().writeText(text)
        }

        files.reconcile()

        listOf("a", "b").forEach { id ->
            assertFalse(files.dataFile(id).recordFile().exists(), id)
            assertFalse(files.dataFile(id).exists(), id)
        }
    }

    @Test
    fun `reconcile keeps a record of another version with its data file`() {
        val files = files()
        files.dataFile("a").fill(5)
        // Minimal on purpose: a newer record need not decode as this version's class to be left alone.
        files.dataFile("a").recordFile().writeText("""{"version":2}""")

        files.reconcile()

        assertTrue(files.dataFile("a").recordFile().isFile)
        assertTrue(files.dataFile("a").isFile)
    }

    @Test
    fun `reconcile deletes a pending file that is empty or not json and sweeps its part`() {
        listOf("", "not json").forEachIndexed { index, text ->
            val root = File(folder.root, "pending-$index")
            val files = files(root)
            assertTrue(files.writePending(pending()))
            files.partFile("europe/norway").fill(3)
            File(root, "pending.json").writeText(text)

            files.reconcile()

            assertFalse(File(root, "pending.json").exists(), text)
            assertFalse(files.partFile("europe/norway").exists(), text)
        }
    }

    @Test
    fun `reconcile leaves everything alone beside a pending record of another version`() {
        val files = files()
        files.partFile("europe/norway").fill(3)
        files.dataFile("orphan").fill(5)
        File(root, "pending.json").writeText("""{"version":2}""")

        files.reconcile()

        assertTrue(File(root, "pending.json").isFile)
        assertTrue(files.partFile("europe/norway").isFile)
        assertTrue(files.dataFile("orphan").isFile)
    }

    @Test
    fun `a write or a rename that fails leaves no temporary file`() {
        // A directory where the temporary file goes: the write itself fails.
        val writeFails = files(File(folder.root, "write-fails"))
        val writeTemp = File(folder.root, "write-fails/pending.json.tmp").apply { mkdirs() }
        // A directory with a file in it where the record goes: the rename fails.
        val renameFails = files(File(folder.root, "rename-fails"))
        File(folder.root, "rename-fails/pending.json/kept").fill(1)

        assertFalse(writeFails.writePending(pending()))
        assertFalse(renameFails.writePending(pending()))

        assertFalse(writeTemp.exists())
        assertFalse(File(folder.root, "rename-fails/pending.json.tmp").exists())
    }

    @Test
    fun `reconcile deletes a temporary file left in the root`() {
        val files = files()
        assertTrue(files.writePending(pending()))
        File(root, "pending.json.tmp").writeText("{")

        files.reconcile()

        assertFalse(File(root, "pending.json.tmp").exists())
        assertEquals(pending(), files.pendingOrNull())
    }

    @Test
    fun `version 1 records read and write exactly the pinned json`() {
        val files = files()
        SAVED_FORMS.forEach { (json, record) ->
            val recordFile = files.dataFile(record.id).recordFile()
            recordFile.parentFile?.mkdirs()
            recordFile.writeText(json)
            assertEquals(listOf(record), files.savedRegions(), json)

            recordFile.delete()
            files.partFile(record.id).fill(5)
            assertTrue(files.commit(record), json)
            assertEquals(json, recordFile.readText())
            files.delete(record.id)
        }
        PENDING_FORMS.forEach { (json, record) ->
            val pendingFile = File(root, "pending.json")
            pendingFile.writeText(json)
            assertEquals(record, files.pendingOrNull(), json)

            assertTrue(files.writePending(record), json)
            assertEquals(json, pendingFile.readText())
        }
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

    private companion object {
        const val FREE_BYTES = 9_000_000_000L

        const val NORWAY_URL = "https://download.geofabrik.de/europe/norway-shortbread-1.0.mbtiles"
        const val MONACO_URL = "https://download.geofabrik.de/europe/monaco-shortbread-1.0.mbtiles"
        val NORWAY_RINGS =
            listOf(
                listOf(4.5, 57.9, 31.2, 57.9, 31.2, 71.2, 4.5, 57.9),
                listOf(10.5, 59.5, 11.0, 59.5, 11.0, 60.0, 10.5, 59.5),
            )
        val MONACO_RINGS = listOf(listOf(7.4, 43.7, 7.5, 43.7, 7.5, 43.8, 7.4, 43.7))
        const val NORWAY_POLYGON_JSON =
            "[[4.5,57.9,31.2,57.9,31.2,71.2,4.5,57.9],[10.5,59.5,11.0,59.5,11.0,60.0,10.5,59.5]]"

        // The format every saved region keeps for good: a renamed or reordered field fails here, not on a device.
        val SAVED_FORMS =
            listOf(
                """{"version":1,"id":"europe/norway","name":"Norway","url":"$NORWAY_URL","sizeBytes":58444771328,""" +
                    """"etag":"\"abc\"","lastModifiedEpochMs":1763400000000,"savedAtEpochMs":1763500000000,""" +
                    """"polygon":$NORWAY_POLYGON_JSON}""" to
                    SavedRegionRecord(
                        id = "europe/norway",
                        name = "Norway",
                        url = NORWAY_URL,
                        sizeBytes = 58_444_771_328L,
                        etag = "\"abc\"",
                        lastModifiedEpochMs = 1_763_400_000_000L,
                        savedAtEpochMs = 1_763_500_000_000L,
                        polygon = NORWAY_RINGS,
                    ),
                """{"version":1,"id":"monaco","name":"Monaco","url":"$MONACO_URL","sizeBytes":1728512,"etag":null,""" +
                    """"lastModifiedEpochMs":null,"savedAtEpochMs":1763500000000,""" +
                    """"polygon":[[7.4,43.7,7.5,43.7,7.5,43.8,7.4,43.7]]}""" to
                    SavedRegionRecord(
                        id = "monaco",
                        name = "Monaco",
                        url = MONACO_URL,
                        sizeBytes = 1_728_512L,
                        etag = null,
                        lastModifiedEpochMs = null,
                        savedAtEpochMs = 1_763_500_000_000L,
                        polygon = MONACO_RINGS,
                    ),
            )

        val PENDING_FORMS =
            listOf(
                """{"version":1,"id":"europe/norway","name":"Norway","url":"$NORWAY_URL","sizeBytes":58444771328,""" +
                    """"etag":"\"abc\"","lastModifiedEpochMs":1763400000000,""" +
                    """"polygon":$NORWAY_POLYGON_JSON}""" to
                    PendingDownloadRecord(
                        id = "europe/norway",
                        name = "Norway",
                        url = NORWAY_URL,
                        sizeBytes = 58_444_771_328L,
                        etag = "\"abc\"",
                        lastModifiedEpochMs = 1_763_400_000_000L,
                        polygon = NORWAY_RINGS,
                    ),
                """{"version":1,"id":"monaco","name":"Monaco","url":"$MONACO_URL","sizeBytes":1728512,"etag":null,""" +
                    """"lastModifiedEpochMs":null,"polygon":[[7.4,43.7,7.5,43.7,7.5,43.8,7.4,43.7]]}""" to
                    PendingDownloadRecord(
                        id = "monaco",
                        name = "Monaco",
                        url = MONACO_URL,
                        sizeBytes = 1_728_512L,
                        etag = null,
                        lastModifiedEpochMs = null,
                        polygon = MONACO_RINGS,
                    ),
            )
    }
}
