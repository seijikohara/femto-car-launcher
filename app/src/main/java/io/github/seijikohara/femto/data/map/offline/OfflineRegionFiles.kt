package io.github.seijikohara.femto.data.map.offline

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

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

/** The record the region of a finished download is saved with. */
internal fun PendingDownloadRecord.toSavedRecord(savedAtEpochMs: Long): SavedRegionRecord =
    SavedRegionRecord(
        id = id,
        name = name,
        url = url,
        sizeBytes = sizeBytes,
        etag = etag,
        lastModifiedEpochMs = lastModifiedEpochMs,
        savedAtEpochMs = savedAtEpochMs,
        polygon = polygon,
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

/** What one record file on disk holds. */
private sealed interface StoredRecord<out T> {
    data object Absent : StoredRecord<Nothing>

    data class Current<T>(
        val record: T,
    ) : StoredRecord<T>

    /** JSON with another `version`, which a newer app wrote, or a file that cannot be read at all. */
    data object Foreign : StoredRecord<Nothing>

    /** Empty, not JSON, or JSON of this version that is not a whole record. */
    data object Garbage : StoredRecord<Nothing>
}

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
    // Given, not measured here: on a device the figure comes from StorageManager, which also counts the cached
    // data the system can clear to make room.
    private val usableBytes: () -> Long,
) {
    private val regionsDir get() = File(root, REGIONS_DIR)
    private val pendingFile get() = File(root, PENDING_FILE_NAME)

    fun partFile(id: String): File = File(regionsDir, regionFileKey(id) + PART_SUFFIX)

    fun dataFile(id: String): File = File(regionsDir, regionFileKey(id) + DATA_SUFFIX)

    private fun recordFile(id: String): File = File(regionsDir, regionFileKey(id) + RECORD_SUFFIX)

    fun savedRegions(): List<SavedRegionRecord> =
        recordFiles().mapNotNull { it.storedRecord(SavedRegionRecord.serializer()).recordOrNull() }

    fun pendingOrNull(): PendingDownloadRecord? =
        pendingFile.storedRecord(PendingDownloadRecord.serializer()).recordOrNull()

    fun writePending(record: PendingDownloadRecord): Boolean =
        writeAtomically(pendingFile, RecordJson.encodeToString(PendingDownloadRecord.serializer(), record))

    fun discardPending() {
        pendingOrNull()?.let { partFile(it.id).delete() }
        pendingFile.delete()
    }

    // A head unit stops by losing power, and a rename can reach the disk before the bytes it names: the part is
    // synced first, so a saved region is never a renamed but incomplete file.
    fun commit(record: SavedRegionRecord): Boolean =
        partFile(record.id).let { part -> part.syncToDisk() && part.renameTo(dataFile(record.id)) } &&
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

    /**
     * Repair what a stopped process can leave, by these rules:
     * - A record or `pending.json` that is empty, is not JSON, or is JSON of this version but not a whole record is
     *   garbage and is deleted; what it described then falls under the rules below. One that is JSON with another
     *   `version` was written by a newer app and is left alone with its data file or part, and so is a file that
     *   cannot be read at all. Such a `pending.json` leaves everything alone, since this version cannot tell which
     *   region it describes.
     * - A record whose data file is gone is deleted.
     * - A pending record whose data file has its size, with no part beside it, belongs to a commit that stopped
     *   after the rename. Without a record, the record is written from it; beside a record of the same size and
     *   ETag, the pending record alone is deleted. Beside a record of another size or ETag it describes an update
     *   of a saved region, and it stays.
     * - A data file that neither a record nor the pending record describes is deleted, and so is a part of any
     *   region but the pending one, and the temporary file of a write that never finished.
     */
    fun reconcile() {
        val pending =
            when (val stored = pendingFile.storedRecord(PendingDownloadRecord.serializer())) {
                StoredRecord.Foreign -> {
                    return
                }

                StoredRecord.Garbage -> {
                    pendingFile.delete()
                    null
                }

                StoredRecord.Absent -> {
                    null
                }

                is StoredRecord.Current -> {
                    stored.record
                }
            }
        // Garbage records go before the pending record is settled, so a commit whose record was lost is finished.
        recordFiles().filter(::isStaleRecord).forEach(File::delete)
        pending?.let(::settle)
        sweepOrphans(pendingKey = pendingOrNull()?.let { regionFileKey(it.id) })
    }

    private fun recordFiles(): List<File> =
        regionsDir.listFiles { file -> file.name.endsWith(RECORD_SUFFIX) }.orEmpty().toList()

    private fun isStaleRecord(record: File): Boolean =
        when (record.storedRecord(SavedRegionRecord.serializer())) {
            StoredRecord.Garbage -> true
            is StoredRecord.Current -> !File(regionsDir, record.name.removeSuffix(RECORD_SUFFIX) + DATA_SUFFIX).exists()
            StoredRecord.Absent, StoredRecord.Foreign -> false
        }

    // A commit renames the part, writes the record, then deletes the pending record; reconcile names what each
    // stop leaves.
    private fun settle(pending: PendingDownloadRecord) {
        val data = dataFile(pending.id)
        val renamed = data.isFile && data.length() == pending.sizeBytes && !partFile(pending.id).exists()
        val saved = recordFile(pending.id).storedRecord(SavedRegionRecord.serializer())
        when {
            !renamed -> Unit
            saved is StoredRecord.Absent -> finishCommit(pending)
            saved is StoredRecord.Current && saved.record.isFileOf(pending) -> pendingFile.delete()
        }
    }

    private fun finishCommit(pending: PendingDownloadRecord) {
        val record = pending.toSavedRecord(savedAtEpochMs = dataFile(pending.id).lastModified())
        if (writeAtomically(
                recordFile(pending.id),
                RecordJson.encodeToString(SavedRegionRecord.serializer(), record),
            )
        ) {
            pendingFile.delete()
        }
    }

    private fun sweepOrphans(pendingKey: String?) {
        val files = regionsDir.listFiles().orEmpty()
        val recordKeys = files
            .filter { it.name.endsWith(RECORD_SUFFIX) }
            .map { it.name.removeSuffix(RECORD_SUFFIX) }
            .toSet()

        files
            .filter { it.name.endsWith(DATA_SUFFIX) }
            .filterNot { it.name.removeSuffix(DATA_SUFFIX).let { key -> key in recordKeys || key == pendingKey } }
            .forEach(File::delete)
        files
            .filter { it.name.endsWith(PART_SUFFIX) }
            .filterNot { it.name.removeSuffix(PART_SUFFIX) == pendingKey }
            .forEach(File::delete)
        (files.toList() + root.listFiles().orEmpty()).filter { it.name.endsWith(TEMP_SUFFIX) }.forEach(File::delete)
    }

    // A temp file plus a rename makes a record whole or absent, never half-written. The sync before the rename keeps
    // a power cut from leaving a renamed but empty record, and a temp file that a failed write leaves goes at once.
    private fun writeAtomically(
        target: File,
        text: String,
    ): Boolean {
        val temp = File(target.parentFile, target.name + TEMP_SUFFIX)
        return runCatching {
            target.parentFile?.mkdirs()
            FileOutputStream(temp).use { output ->
                output.write(text.toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            temp.renameTo(target)
        }.getOrDefault(false)
            .also { written -> if (!written) temp.delete() }
    }
}

private fun SavedRegionRecord.isFileOf(pending: PendingDownloadRecord): Boolean =
    sizeBytes == pending.sizeBytes && etag == pending.etag

private fun <T> StoredRecord<T>.recordOrNull(): T? =
    when (this) {
        is StoredRecord.Current -> record
        else -> null
    }

private fun <T> File.storedRecord(serializer: KSerializer<T>): StoredRecord<T> =
    if (exists()) {
        runCatching { readText() }.map { storedRecordOf(it, serializer) }.getOrDefault(StoredRecord.Foreign)
    } else {
        StoredRecord.Absent
    }

// Judged on the JSON tree before the class: a newer app's record need not decode as this version's, and must not
// be taken for garbage and deleted together with its data.
private fun <T> storedRecordOf(
    text: String,
    serializer: KSerializer<T>,
): StoredRecord<T> =
    runCatching { RecordJson.parseToJsonElement(text) }
        .getOrNull()
        ?.let { tree ->
            when (tree.recordVersionOrNull() ?: REGION_RECORD_VERSION) {
                REGION_RECORD_VERSION -> {
                    runCatching { RecordJson.decodeFromJsonElement(serializer, tree) }
                        .map { StoredRecord.Current(it) }
                        .getOrDefault(StoredRecord.Garbage)
                }

                else -> {
                    StoredRecord.Foreign
                }
            }
        }
        ?: StoredRecord.Garbage

private fun JsonElement.recordVersionOrNull(): Int? =
    ((this as? JsonObject)?.get("version") as? JsonPrimitive)?.intOrNull

// Flushes the file's bytes to the disk without truncating it; false for a missing file or a failed sync.
private fun File.syncToDisk(): Boolean =
    isFile && runCatching { RandomAccessFile(this, "rw").use { it.fd.sync() } }.isSuccess
