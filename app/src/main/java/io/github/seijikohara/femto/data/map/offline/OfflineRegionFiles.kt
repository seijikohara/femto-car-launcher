package io.github.seijikohara.femto.data.map.offline

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** Bumped only by a format change; a record of another version is left alone and not listed. */
internal const val REGION_RECORD_VERSION = 1

/** One saved region: its record is permanent, so later versions of the app must read it. */
@Serializable
internal data class SavedRegionRecord(
    val version: Int = REGION_RECORD_VERSION,
    val id: String,
    val name: String,
    val url: String,
    val sizeBytes: Long,
    val etag: String?,
    val lastModifiedEpochMs: Long?,
    val savedAtEpochMs: Long,
    val polygon: List<List<Double>>, // rings as packed longitude/latitude pairs
)

/** The one download that is running or waiting to be resumed. */
@Serializable
internal data class PendingDownloadRecord(
    val version: Int = REGION_RECORD_VERSION,
    val id: String,
    val name: String,
    val url: String,
    val sizeBytes: Long,
    val etag: String?,
    val lastModifiedEpochMs: Long?,
    val polygon: List<List<Double>>,
)

// encodeDefaults keeps `version` in every written record; the default alone would drop it from the file.
private val RecordJson =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

private const val REGIONS_DIR = "regions"
private const val PENDING_FILE_NAME = "pending.json"
private const val DATA_SUFFIX = ".mbtiles"
private const val RECORD_SUFFIX = ".json"
private const val PART_SUFFIX = ".mbtiles.part"
private const val TEMP_SUFFIX = ".tmp"

/** A file name for the id that cannot leave its directory: percent-encodes everything but `[a-z0-9-]`. */
internal fun regionFileKey(id: String): String =
    id
        .toByteArray(Charsets.UTF_8)
        .joinToString(separator = "") { byte ->
            val char = (byte.toInt() and 0xFF).toChar()
            if (char in 'a'..'z' || char in '0'..'9' || char == '-') char.toString() else "%%%02X".format(byte)
        }

/** Blocking file work; callers run it on an IO dispatcher. */
internal class OfflineRegionFiles(
    private val root: File,
    private val usableBytes: () -> Long = {
        generateSequence(root) { it.parentFile }.firstOrNull { it.exists() }?.usableSpace
            ?: 0L
    },
) {
    private val regionsDir get() = File(root, REGIONS_DIR)
    private val pendingFile get() = File(root, PENDING_FILE_NAME)

    fun partFile(id: String): File = File(regionsDir, regionFileKey(id) + PART_SUFFIX)

    fun dataFile(id: String): File = File(regionsDir, regionFileKey(id) + DATA_SUFFIX)

    private fun recordFile(id: String): File = File(regionsDir, regionFileKey(id) + RECORD_SUFFIX)

    fun savedRegions(): List<SavedRegionRecord> =
        regionsDir
            .listFiles { file -> file.name.endsWith(RECORD_SUFFIX) }
            .orEmpty()
            .mapNotNull(::readSavedOrNull)
            .filter { it.version == REGION_RECORD_VERSION }

    fun pendingOrNull(): PendingDownloadRecord? =
        readOrNull(pendingFile, PendingDownloadRecord.serializer())
            ?.takeIf { it.version == REGION_RECORD_VERSION }

    fun writePending(record: PendingDownloadRecord): Boolean =
        writeAtomically(pendingFile, RecordJson.encodeToString(PendingDownloadRecord.serializer(), record))

    fun discardPending() {
        pendingOrNull()?.let { partFile(it.id).delete() }
        pendingFile.delete()
    }

    fun commit(record: SavedRegionRecord): Boolean =
        partFile(record.id).renameTo(dataFile(record.id)) &&
            writeAtomically(recordFile(record.id), RecordJson.encodeToString(SavedRegionRecord.serializer(), record)) &&
            // The pending record may already be absent; only the two steps above can fail the commit.
            pendingFile.delete().let { true }

    fun delete(id: String) {
        // The record goes first so a crash leaves an undescribed data file, which reconcile removes.
        recordFile(id).delete()
        dataFile(id).delete()
    }

    fun keptBytes(): Long = savedRegions().sumOf { dataFile(it.id).length() }

    fun usedBytes(): Long = root.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun freeBytes(): Long = usableBytes()

    /** Repair what a crash can leave; see the rules in the task brief. */
    fun reconcile() {
        val pending = pendingOrNull()
        // A pending file that does not parse belongs to another version: touch nothing it may describe.
        if (pending == null && pendingFile.exists()) return

        pending
            // A part or a record beside the data file means a re-download of a saved region is running,
            // not a commit that stopped after the rename; that download is left to be resumed.
            ?.takeIf {
                dataFile(it.id).let { data -> data.isFile && data.length() == it.sizeBytes } &&
                    !partFile(it.id).exists() &&
                    !recordFile(it.id).exists()
            }?.let(::finishCommit)

        val pendingKey = pendingOrNull()?.let { regionFileKey(it.id) }
        val files = regionsDir.listFiles().orEmpty()
        val recordKeys = files
            .filter {
                it.name.endsWith(
                    RECORD_SUFFIX,
                )
            }.map { it.name.removeSuffix(RECORD_SUFFIX) }
            .toSet()

        files
            .filter { it.name.endsWith(RECORD_SUFFIX) }
            .filter { record -> readSavedOrNull(record)?.version == REGION_RECORD_VERSION }
            .filterNot { record -> File(regionsDir, record.name.removeSuffix(RECORD_SUFFIX) + DATA_SUFFIX).exists() }
            .forEach(File::delete)
        files
            .filter { it.name.endsWith(DATA_SUFFIX) }
            .filterNot { it.name.removeSuffix(DATA_SUFFIX).let { key -> key in recordKeys || key == pendingKey } }
            .forEach(File::delete)
        files
            .filter { it.name.endsWith(PART_SUFFIX) }
            .filterNot { it.name.removeSuffix(PART_SUFFIX) == pendingKey }
            .forEach(File::delete)
        files.filter { it.name.endsWith(TEMP_SUFFIX) }.forEach(File::delete)
    }

    private fun finishCommit(pending: PendingDownloadRecord) {
        val record =
            SavedRegionRecord(
                id = pending.id,
                name = pending.name,
                url = pending.url,
                sizeBytes = pending.sizeBytes,
                etag = pending.etag,
                lastModifiedEpochMs = pending.lastModifiedEpochMs,
                savedAtEpochMs = dataFile(pending.id).lastModified(),
                polygon = pending.polygon,
            )
        if (writeAtomically(
                recordFile(pending.id),
                RecordJson.encodeToString(SavedRegionRecord.serializer(), record),
            )
        ) {
            pendingFile.delete()
        }
    }

    private fun readSavedOrNull(file: File): SavedRegionRecord? = readOrNull(file, SavedRegionRecord.serializer())

    private fun <T> readOrNull(
        file: File,
        serializer: kotlinx.serialization.KSerializer<T>,
    ): T? = runCatching { RecordJson.decodeFromString(serializer, file.readText()) }.getOrNull()

    // A temp file plus a rename makes a record whole or absent, never half-written.
    private fun writeAtomically(
        target: File,
        text: String,
    ): Boolean =
        runCatching {
            target.parentFile?.mkdirs()
            val temp = File(target.parentFile, target.name + TEMP_SUFFIX)
            temp.writeText(text)
            temp.renameTo(target).also { if (!it) temp.delete() }
        }.getOrDefault(false)
}
