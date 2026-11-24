package io.github.seijikohara.femto.data.routing

import io.github.seijikohara.femto.data.common.ATOMIC_WRITE_TEMP_SUFFIX
import io.github.seijikohara.femto.data.common.writeAtomically
import io.github.seijikohara.femto.testfixtures.fakeKeptRoute
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RouteSnapshotFilesTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val dir: File get() = File(tempFolder.root, ACTIVE_ROUTE_DIR)

    // The files each write replaced, by name, in order.
    private val written = mutableListOf<String>()

    private val files: RouteSnapshotFiles by lazy {
        RouteSnapshotFiles(dir) { target, bytes -> writeAtomically(target, bytes).also { written += target.name } }
    }

    @Test
    fun `no files read as none`() {
        assertEquals(null to null, files.readOrNull() to files.readStampOrNull())
    }

    // An engine route, whose points lie on whole micro-degrees, and a destination the driver picked to the last digit.
    @Test
    fun `a snapshot reads back as it was written`() {
        files.write(KEPT)

        assertEquals(KEPT, files.readOrNull())
    }

    // The temp file's path is taken by a directory that cannot be deleted, so the new bytes can go nowhere.
    @Test
    fun `a write that fails keeps the old snapshot`() {
        files.write(KEPT)
        File(dir, "route.json$ATOMIC_WRITE_TEMP_SUFFIX").apply { mkdirs() }.resolve("blocker").writeText("")

        val replaced = files.write(KEPT.copy(conditions = RouteConditions(avoidMotorways = true)))

        assertFalse(replaced)
        assertEquals(KEPT, files.readOrNull())
    }

    // What a write cut short by a power loss would leave, were the file written in place.
    @Test
    fun `a partial snapshot reads as none and is deleted`() {
        files.write(KEPT)
        val snapshot = File(dir, "route.json")
        snapshot.writeText(snapshot.readText().take(40))

        val read = files.readOrNull()

        assertNull(read)
        assertFalse(snapshot.exists())
    }

    @Test
    fun `a snapshot of another version reads as none and is deleted`() {
        files.write(KEPT)
        val snapshot = File(dir, "route.json")
        snapshot.writeText(snapshot.readText().replace("\"version\":1", "\"version\":2"))

        val read = files.readOrNull()

        assertNull(read)
        assertFalse(snapshot.exists())
    }

    @Test
    fun `the stamp reads and writes on its own`() {
        files.write(KEPT)
        written.clear()

        files.writeStamp(STAMP_MS)

        assertEquals(STAMP_MS, files.readStampOrNull())
        assertEquals(listOf("last-driven"), written)
        assertEquals(KEPT, files.readOrNull())
    }

    @Test
    fun `an unreadable stamp reads as none and is deleted`() {
        val stamp = File(dir, "last-driven").apply { parentFile?.mkdirs() }
        stamp.writeText("not a time")

        val read = files.readStampOrNull()

        assertNull(read)
        assertFalse(stamp.exists())
    }

    @Test
    fun `delete removes both files and any temp file a write left`() {
        files.write(KEPT)
        files.writeStamp(STAMP_MS)
        File(dir, "last-driven$ATOMIC_WRITE_TEMP_SUFFIX").writeText("1")

        val deleted = files.delete()

        assertTrue(deleted)
        assertFalse(dir.exists())
    }

    private companion object {
        val KEPT = fakeKeptRoute()

        // 2026-11-22T09:00:00Z.
        const val STAMP_MS = 1_795_338_000_000L
    }
}
