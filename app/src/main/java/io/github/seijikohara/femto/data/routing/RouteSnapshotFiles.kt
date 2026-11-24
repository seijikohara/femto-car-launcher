package io.github.seijikohara.femto.data.routing

import android.util.Log
import io.github.seijikohara.femto.data.common.writeAtomically
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.roundToInt

private const val TAG = "RouteSnapshotFiles"

/**
 * The directory under the app's files directory that holds the kept route. The backup rules exclude it whole, which
 * covers both files and the temp files of their writes.
 */
internal const val ACTIVE_ROUTE_DIR = "active-route"

private const val SNAPSHOT_FILE = "route.json"
private const val STAMP_FILE = "last-driven"

// A file of another version reads as none: a later build may change the format, and a downgrade must not misread it.
private const val SNAPSHOT_VERSION = 1

// ignoreUnknownKeys: a later build of the same version may add fields.
private val SnapshotJson = Json { ignoreUnknownKeys = true }

/** A route kept across a restart: what bringing its session back needs. */
internal data class KeptRoute(
    val route: Route,
    val destination: RoutePoint,
    val conditions: RouteConditions,
)

/**
 * The two files that keep the active route across a restart of the launcher's process, in [dir]: the snapshot of the
 * route ([KeptRoute]) and the last-driven stamp, a time in epoch milliseconds.
 *
 * Each file is replaced whole or not at all ([writeAtomically]), so a process that dies in the middle of a write
 * leaves the old file or the new one. A file that cannot be read all the same is deleted and reads as none, so it
 * never blocks a start-up. The stamp is a file of its own: it changes about once a minute while the vehicle drives,
 * and a route of thousands of points must not be written each time. Blocking; callers run it off the main thread.
 * The writer stays injectable for JVM tests.
 */
internal class RouteSnapshotFiles(
    private val dir: File,
    private val writeFile: (File, ByteArray) -> Boolean = ::writeAtomically,
) {
    private val snapshotFile: File get() = File(dir, SNAPSHOT_FILE)
    private val stampFile: File get() = File(dir, STAMP_FILE)

    /** The kept route, or null when there is none or its file cannot be read, which is then deleted. */
    fun readOrNull(): KeptRoute? =
        snapshotFile.takeIf(File::isFile)?.let { file ->
            runCatching { SnapshotJson.decodeFromString(SnapshotRecord.serializer(), file.readText()) }
                .getOrNull()
                ?.toKeptRouteOrNull()
                // The parser's message quotes the file, coordinates and all, so none of it reaches the log.
                ?: null.also {
                    Log.w(TAG, "unreadable route snapshot deleted")
                    file.delete()
                }
        }

    /** Replace the snapshot with [kept], and report whether it was replaced. */
    fun write(kept: KeptRoute): Boolean =
        writeFile(snapshotFile, SnapshotJson.encodeToString(SnapshotRecord.serializer(), kept.toRecord()).toByteArray())

    /** The last-driven stamp in epoch milliseconds, or null when there is none or it cannot be read, which is deleted. */
    fun readStampOrNull(): Long? =
        stampFile.takeIf(File::isFile)?.let { file ->
            runCatching { file.readText().trim().toLong() }.getOrNull() ?: null.also { file.delete() }
        }

    /** Replace the last-driven stamp with [epochMs], and report whether it was replaced; the snapshot stays as it is. */
    fun writeStamp(epochMs: Long): Boolean = writeFile(stampFile, epochMs.toString().toByteArray())

    /** Delete the snapshot, the stamp and any temp file a write left behind; whether none is left. */
    fun delete(): Boolean = dir.deleteRecursively()
}

/**
 * The snapshot as the file holds it. The line is flat: the latitude and longitude of each point in whole micro-degrees,
 * BRouter's own resolution, so no point of a computed route moves, at less than half the size of a named pair of
 * decimals per point. The destination is the point the driver chose, kept in full.
 */
@Serializable
private data class SnapshotRecord(
    val version: Int,
    val destinationLatitude: Double,
    val destinationLongitude: Double,
    val avoidMotorways: Boolean,
    val avoidTolls: Boolean,
    val distanceMeters: Int,
    val durationSeconds: Int,
    val line: List<Int>,
)

private fun KeptRoute.toRecord(): SnapshotRecord =
    SnapshotRecord(
        version = SNAPSHOT_VERSION,
        destinationLatitude = destination.latitude,
        destinationLongitude = destination.longitude,
        avoidMotorways = conditions.avoidMotorways,
        avoidTolls = conditions.avoidTolls,
        distanceMeters = route.distanceMeters,
        durationSeconds = route.durationSeconds,
        line = route.points.flatMap { listOf(it.latitude.toMicrodegrees(), it.longitude.toMicrodegrees()) },
    )

// A line needs two points, as a route the engine returns has.
private fun SnapshotRecord.toKeptRouteOrNull(): KeptRoute? =
    takeIf { version == SNAPSHOT_VERSION && line.size >= 4 && line.size % 2 == 0 }?.let {
        KeptRoute(
            route = Route(points = line.toPoints(), distanceMeters = distanceMeters, durationSeconds = durationSeconds),
            destination = RoutePoint(destinationLatitude, destinationLongitude),
            conditions = RouteConditions(avoidMotorways = avoidMotorways, avoidTolls = avoidTolls),
        )
    }

private fun List<Int>.toPoints(): List<RoutePoint> =
    chunked(2) { (latitude, longitude) -> RoutePoint(latitude.toDegrees(), longitude.toDegrees()) }

private fun Double.toMicrodegrees(): Int = (this * MICRODEGREES_PER_DEGREE).roundToInt()

private fun Int.toDegrees(): Double = this / MICRODEGREES_PER_DEGREE
