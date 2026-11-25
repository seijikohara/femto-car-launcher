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
import kotlin.test.assertNotNull
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

    @Test
    fun `a snapshot with turns reads back with them`() {
        val kept = KEPT.copy(route = KEPT.route.copy(turns = TURNS))
        files.write(kept)

        assertEquals(kept, files.readOrNull())
    }

    // No turn and no turn data must stay apart across the file.
    @Test
    fun `a snapshot with an empty turn list reads back with an empty list`() {
        files.write(KEPT.copy(route = KEPT.route.copy(turns = emptyList())))

        assertEquals(emptyList(), files.readOrNull()?.route?.turns)
    }

    @Test
    fun `a snapshot without turn data reads back without them`() {
        files.write(KEPT)

        assertNull(assertNotNull(files.readOrNull()).route.turns)
    }

    // What a build before the turn hints wrote: the same version, and no turns key.
    @Test
    fun `a snapshot of an earlier build reads back with its line and no turn data`() {
        writeSnapshotText(turnsEntry = "")

        assertEquals(EARLIER_BUILD_ROUTE, files.readOrNull()?.route)
    }

    // The line has two points, so index 1 is its last and index 2 lies past it.
    @Test
    fun `a damaged turn list reads back with the line and no turn data, and the file stays`() {
        mapOf(
            "a length that is not a multiple of four" to "[1,5,0,84,0,5,0]",
            "an unknown kind code" to "[1,99,0,84]",
            "an index past the line" to "[2,5,0,84]",
            "an index before the line" to "[-1,5,0,84]",
            "indexes out of order" to "[1,5,0,84,0,3,0,-55]",
        ).forEach { (damage, turns) ->
            writeSnapshotText(turnsEntry = ""","turns":$turns""")

            val read = files.readOrNull()

            assertEquals(EARLIER_BUILD_ROUTE, read?.route, damage)
            assertTrue(File(dir, "route.json").exists(), damage)
        }
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

    // The snapshot as an earlier build wrote it, plus [turnsEntry] (a `,"key":value` fragment, or none) before its end.
    private fun writeSnapshotText(turnsEntry: String) {
        File(dir, "route.json")
            .apply { parentFile?.mkdirs() }
            .writeText(
                """{"version":1,"destinationLatitude":34.07,"destinationLongitude":133.0,"avoidMotorways":false,""" +
                    """"avoidTolls":false,"distanceMeters":41200,"durationSeconds":2950,""" +
                    """"line":[33840000,132770000,34070000,133000000]$turnsEntry}""",
            )
    }

    private companion object {
        val KEPT = fakeKeptRoute()

        // Turns on the first, the middle and the last point of KEPT's line.
        val TURNS =
            listOf(
                RouteTurn(pointIndex = 0, kind = TurnKind.RIGHT, exitNumber = 0, angleDegrees = 84),
                RouteTurn(pointIndex = 1, kind = TurnKind.ROUNDABOUT_CLOCKWISE, exitNumber = 2, angleDegrees = -236),
                RouteTurn(pointIndex = 2, kind = TurnKind.SLIGHT_LEFT, exitNumber = 0, angleDegrees = -55),
            )

        val EARLIER_BUILD_ROUTE =
            Route(
                points = listOf(RoutePoint(33.84, 132.77), RoutePoint(34.07, 133.0)),
                distanceMeters = 41_200,
                durationSeconds = 2_950,
            )

        // 2026-11-22T09:00:00Z.
        const val STAMP_MS = 1_795_338_000_000L
    }
}
