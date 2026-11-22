package io.github.seijikohara.femto.data.routing

import android.content.Context
import io.github.seijikohara.femto.data.common.femtoUserAgent
import io.github.seijikohara.femto.data.common.syncToDisk
import io.github.seijikohara.femto.data.common.writeAtomically
import io.github.seijikohara.femto.data.map.offline.ProbeResult
import io.github.seijikohara.femto.data.map.offline.RegionFeedApi
import io.github.seijikohara.femto.data.map.offline.RemoteFile
import io.github.seijikohara.femto.data.map.offline.RemoteFileFeed
import io.github.seijikohara.femto.data.map.offline.RevalidationResult
import io.github.seijikohara.femto.data.map.offline.TransferResult
import io.github.seijikohara.femto.data.map.offline.regionHttpClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** Where brouter.de publishes the routing data of the whole world, one `<tile>.rd5` file per 5° tile. */
internal const val ROUTING_TILE_HOST_PREFIX = "https://brouter.de/brouter/segments4/"

/** brouter.de rebuilds the routing data once a week, so a tile is checked against the server at most that often. */
internal const val ROUTING_TILE_CHECK_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000

private const val ROUTING_DIR = "routing"
private const val SEGMENTS_DIR = "segments"
private const val RECORDS_DIR = "records"
private const val DOWNLOADS_DIR = "downloads"
private const val TILE_SUFFIX = ".rd5"
private const val PART_SUFFIX = ".rd5.part"
private const val RECORD_SUFFIX = ".json"

private val TileNamePattern = Regex("[EW]\\d{1,3}_[NS]\\d{1,2}")

private val RecordJson = Json { ignoreUnknownKeys = true }

/** What the store knows about one tile beside its data file. */
@Serializable
internal data class RoutingTileRecord(
    /** The installed file as the server described it; null when the server has no file for the tile (open sea). */
    val file: RemoteFile?,
    /** When the server last answered for the tile. */
    val checkedAtEpochMs: Long,
)

internal data class InstalledRoutingTile(
    val name: String,
    val sizeBytes: Long,
    /** The Last-Modified of the installed file, when the server sent one. */
    val dataDateEpochMs: Long?,
    val checkedAtEpochMs: Long?,
)

internal enum class TileFetchResult {
    INSTALLED,

    /** The server has no file for the tile: no road crosses it, and a route does without it. */
    NO_DATA,

    /** No answer, or one that ended early; a part is kept for a resume. */
    NETWORK_FAILED,

    STORAGE_FAILED,
}

internal enum class TileRefreshResult {
    NOT_INSTALLED,

    /** Checked within the last [ROUTING_TILE_CHECK_INTERVAL_MS]; nothing was asked. */
    NOT_DUE,

    /** The server still has the installed file, or has none any more and the installed one stays. */
    UNCHANGED,
    UPDATED,
    NETWORK_FAILED,
    STORAGE_FAILED,
}

/**
 * The routing tiles on the device, and their downloads from brouter.de.
 *
 * A tile is installed when its `.rd5` file is in [segmentDir], which holds nothing else: the engine reads that
 * directory, and a download reaches it only by a rename once the part has the size the server announced. A record
 * beside each tile keeps what the server said about it, so a later check can ask with a conditional request; a record
 * without a file marks a tile the server has none for, which is not asked again until it is due. One [Mutex] orders
 * every change to the files. The constructor stays injectable for JVM tests; production wiring goes through [get].
 */
internal class RoutingTileStore(
    private val root: File,
    private val feed: RemoteFileFeed,
    private val hostPrefix: String = ROUTING_TILE_HOST_PREFIX,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val lock = Mutex()

    /** The directory the engine reads the tiles from. */
    val segmentDir: File get() = File(root, SEGMENTS_DIR)

    private val recordsDir get() = File(root, RECORDS_DIR)
    private val downloadsDir get() = File(root, DOWNLOADS_DIR)

    /** Make sure the tile [name] is installed, downloading it when it is not; an installed tile asks nothing. */
    suspend fun fetch(name: String): TileFetchResult =
        guarded(name) {
            val record = recordOrNull(name)
            when {
                dataFile(name).isFile -> TileFetchResult.INSTALLED
                record != null && record.file == null && !record.isDue() -> TileFetchResult.NO_DATA
                else -> download(name)
            }
        }

    /**
     * Check an installed tile against the server when its last check is [ROUTING_TILE_CHECK_INTERVAL_MS] old, and
     * replace it when the server has a newer file. The installed file stays in place until the new one is whole.
     */
    suspend fun refresh(name: String): TileRefreshResult =
        guarded(name) {
            val data = dataFile(name)
            // A tile installed without a record has nothing to send back and is compared by a plain look.
            val record = recordOrNull(name)?.takeIf { it.file != null }
            when {
                !data.isFile -> TileRefreshResult.NOT_INSTALLED
                record != null && !record.isDue() -> TileRefreshResult.NOT_DUE
                else -> revalidate(name, kept = record?.file ?: RemoteFile(data.length(), null, null))
            }
        }

    /** The names among [names] that are neither installed nor known to have no data. */
    suspend fun missingTiles(names: List<String>): List<String> =
        withContext(ioDispatcher) {
            names.filter { name ->
                requireTileName(name)
                !dataFile(name).isFile && recordOrNull(name)?.let { it.file == null } != true
            }
        }

    /** The installed tiles, by name. */
    suspend fun installedTiles(): List<InstalledRoutingTile> =
        withContext(ioDispatcher) {
            segmentDir
                .listFiles { file -> file.isFile && file.name.endsWith(TILE_SUFFIX) }
                .orEmpty()
                .map { file -> file.name.removeSuffix(TILE_SUFFIX) }
                .filter(TileNamePattern::matches)
                .sorted()
                .map { name ->
                    val record = recordOrNull(name)
                    InstalledRoutingTile(
                        name = name,
                        sizeBytes = dataFile(name).length(),
                        dataDateEpochMs = record?.file?.lastModifiedEpochMs,
                        checkedAtEpochMs = record?.checkedAtEpochMs,
                    )
                }
        }

    /** Remove the tile [name], its record, and any part of it. */
    suspend fun delete(name: String) {
        guarded(name) {
            listOf(dataFile(name), recordFile(name), partFile(name), pendingFile(name)).forEach(File::delete)
        }
    }

    /** Remove every tile, record and part. */
    suspend fun deleteAll() {
        lock.withLock {
            withContext(ioDispatcher) { listOf(segmentDir, recordsDir, downloadsDir).forEach(File::deleteRecursively) }
        }
    }

    private suspend fun <T> guarded(
        name: String,
        block: suspend () -> T,
    ): T {
        requireTileName(name)
        return lock.withLock { withContext(ioDispatcher) { block() } }
    }

    private suspend fun revalidate(
        name: String,
        kept: RemoteFile,
    ): TileRefreshResult =
        when (val check = feed.revalidate(tileUrl(name), kept)) {
            // A tile the server no longer has keeps routing on the installed copy.
            RevalidationResult.NotModified, RevalidationResult.Gone -> {
                writeRecord(name, RoutingTileRecord(kept, clock()))
                TileRefreshResult.UNCHANGED
            }

            is RevalidationResult.Changed -> {
                when (install(name, check.file)) {
                    TileFetchResult.INSTALLED -> TileRefreshResult.UPDATED
                    TileFetchResult.NO_DATA -> TileRefreshResult.UNCHANGED
                    TileFetchResult.NETWORK_FAILED -> TileRefreshResult.NETWORK_FAILED
                    TileFetchResult.STORAGE_FAILED -> TileRefreshResult.STORAGE_FAILED
                }
            }

            RevalidationResult.Unavailable -> {
                TileRefreshResult.NETWORK_FAILED
            }
        }

    private suspend fun download(name: String): TileFetchResult =
        when (val probe = feed.probe(tileUrl(name))) {
            is ProbeResult.Found -> install(name, probe.file)
            ProbeResult.Gone -> TileFetchResult.NO_DATA.also { writeRecord(name, RoutingTileRecord(null, clock())) }
            ProbeResult.SizeUnknown, ProbeResult.Unavailable -> TileFetchResult.NETWORK_FAILED
        }

    // The part is resumed only when the pending record says it holds the start of [file]: the bytes of another file
    // would precede the new file's tail. A file the server sends instead replaces the part, which goes before the
    // record of the new file is stored, so a process that dies in between leaves no old bytes beside that record.
    private suspend fun install(
        name: String,
        file: RemoteFile,
    ): TileFetchResult {
        val part = partFile(name)
        val ready = pendingOrNull(name) == file || (part.deletedOrAbsent() && writePending(name, file))
        if (!ready) return TileFetchResult.STORAGE_FAILED
        val result =
            feed.download(
                url = tileUrl(name),
                part = part,
                expected = file,
                onRestart = { sent -> part.deletedOrAbsent() && writePending(name, sent) },
                onProgress = { _, _ -> },
            )
        return when (result) {
            is TransferResult.Complete -> {
                commit(name, result.file)
            }

            TransferResult.Interrupted, TransferResult.Unavailable -> {
                TileFetchResult.NETWORK_FAILED
            }

            TransferResult.StorageFailed, is TransferResult.Refused -> {
                TileFetchResult.STORAGE_FAILED
            }

            TransferResult.Gone -> {
                pendingFile(name).delete()
                // An installed tile stays; only a tile never installed is marked as having no data.
                if (!dataFile(name).isFile) writeRecord(name, RoutingTileRecord(null, clock()))
                TileFetchResult.NO_DATA
            }
        }
    }

    // The engine reads every .rd5 in the directory, so only a whole file of the announced size is renamed into it,
    // synced first: a head unit stops by losing power, and a rename can reach the disk before the bytes it names.
    private fun commit(
        name: String,
        file: RemoteFile,
    ): TileFetchResult {
        val part = partFile(name)
        val data = dataFile(name)
        val committed =
            part.length() == file.sizeBytes &&
                part.syncToDisk() &&
                (segmentDir.isDirectory || segmentDir.mkdirs()) &&
                part.renameTo(data)
        if (!committed) part.delete()
        pendingFile(name).delete()
        return if (committed) {
            // A tile whose record could not be written still routes; its next check compares it by a plain look.
            writeRecord(name, RoutingTileRecord(file, clock()))
            TileFetchResult.INSTALLED
        } else {
            TileFetchResult.STORAGE_FAILED
        }
    }

    private fun RoutingTileRecord.isDue(): Boolean =
        clock() - checkedAtEpochMs !in 0 until ROUTING_TILE_CHECK_INTERVAL_MS

    private fun tileUrl(name: String) = hostPrefix + name + TILE_SUFFIX

    private fun dataFile(name: String) = File(segmentDir, name + TILE_SUFFIX)

    private fun recordFile(name: String) = File(recordsDir, name + RECORD_SUFFIX)

    private fun partFile(name: String) = File(downloadsDir, name + PART_SUFFIX)

    private fun pendingFile(name: String) = File(downloadsDir, name + RECORD_SUFFIX)

    private fun recordOrNull(name: String): RoutingTileRecord? =
        recordFile(name).decodedOrNull(RoutingTileRecord.serializer())

    private fun pendingOrNull(name: String): RemoteFile? = pendingFile(name).decodedOrNull(RemoteFile.serializer())

    private fun writeRecord(
        name: String,
        record: RoutingTileRecord,
    ): Boolean =
        writeAtomically(
            recordFile(name),
            RecordJson.encodeToString(RoutingTileRecord.serializer(), record).toByteArray(),
        )

    private fun writePending(
        name: String,
        file: RemoteFile,
    ): Boolean =
        writeAtomically(pendingFile(name), RecordJson.encodeToString(RemoteFile.serializer(), file).toByteArray())

    companion object {
        @Volatile
        private var instance: RoutingTileStore? = null

        // Construction touches no file and opens no connection.
        fun get(context: Context): RoutingTileStore =
            instance ?: synchronized(this) {
                instance ?: RoutingTileStore(
                    root = routingDir(context.applicationContext),
                    feed = RegionFeedApi(regionHttpClient(), femtoUserAgent, hostPrefix = ROUTING_TILE_HOST_PREFIX),
                ).also { instance = it }
            }
    }
}

/** The routing data's home: outside backup, since a tile is up to 100 MB and can be fetched again. */
internal fun routingDir(app: Context): File = File(app.noBackupFilesDir, ROUTING_DIR)

private fun requireTileName(name: String) = require(TileNamePattern.matches(name)) { "not a routing tile name: $name" }

private fun File.deletedOrAbsent(): Boolean = delete() || !exists()

// A record that cannot be read counts as none: the tile is then fetched or checked again, which rewrites it.
private fun <T> File.decodedOrNull(serializer: KSerializer<T>): T? =
    takeIf { it.isFile }?.let { file ->
        runCatching { RecordJson.decodeFromString(serializer, file.readText()) }.getOrNull()
    }
