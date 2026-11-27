package io.github.seijikohara.femto.data.map.offline

import android.content.Context
import android.os.storage.StorageManager
import android.util.Log
import androidx.core.content.getSystemService
import io.github.seijikohara.femto.data.common.download.ProbeResult
import io.github.seijikohara.femto.data.common.download.RemoteFile
import io.github.seijikohara.femto.data.common.download.TransferResult
import io.github.seijikohara.femto.data.common.download.allocatableBytesOrZero
import io.github.seijikohara.femto.data.common.download.downloadHttpClient
import io.github.seijikohara.femto.data.common.download.hasRoomFor
import io.github.seijikohara.femto.data.common.femtoUserAgent
import io.github.seijikohara.femto.data.display.DisplayPreferences
import io.github.seijikohara.femto.data.map.offline.RegionDownloadFailure.CORRUPT
import io.github.seijikohara.femto.data.map.offline.RegionDownloadFailure.GONE
import io.github.seijikohara.femto.data.map.offline.RegionDownloadFailure.NETWORK
import io.github.seijikohara.femto.data.map.offline.RegionDownloadFailure.NO_SPACE
import io.github.seijikohara.femto.data.map.offline.RegionDownloadFailure.OVER_LIMIT
import io.github.seijikohara.femto.data.map.offline.RegionDownloadFailure.STORAGE
import io.github.seijikohara.femto.data.map.offline.RegionDownloadPhase.FAILED
import io.github.seijikohara.femto.data.map.offline.RegionDownloadPhase.PAUSED
import io.github.seijikohara.femto.data.map.offline.RegionDownloadPhase.RUNNING
import io.github.seijikohara.femto.data.map.offline.RegionDownloadPhase.VERIFYING
import io.github.seijikohara.femto.data.map.offline.RegionOffer.Unavailable.Reason
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

private const val TAG = "OfflineRegionRepository"

private const val ROOT_DIR = "offline_map"
private const val INDEX_CACHE_PATH = "offline_map/index-v1.json"
private const val INDEX_ETAG_SUFFIX = ".etag"
private const val SIZE_SNAPSHOT_ASSET = "map/offline/shortbread-regions.json"

private val SavedRegionOrder = compareBy<SavedRegion, String>(String.CASE_INSENSITIVE_ORDER) {
    it.name
}.thenBy { it.id }

internal data class SavedRegion(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    /** The Last-Modified of the file the region was saved from. */
    val dataDateEpochMs: Long?,
    val savedAtEpochMs: Long,
)

internal enum class RegionDownloadPhase { RUNNING, VERIFYING, PAUSED, FAILED }

internal enum class RegionDownloadFailure { NETWORK, STORAGE, NO_SPACE, OVER_LIMIT, GONE, CORRUPT }

internal data class RegionDownload(
    val regionId: String,
    val name: String,
    val receivedBytes: Long,
    val totalBytes: Long,
    val phase: RegionDownloadPhase,
    /** Why a download is paused or failed; null while it runs, and for one restored after a restart. */
    val failure: RegionDownloadFailure? = null,
)

internal data class OfflineRegionsState(
    val loaded: Boolean = false,
    /** By name, case-insensitive. */
    val saved: List<SavedRegion> = emptyList(),
    val download: RegionDownload? = null,
    val usedBytes: Long = 0,
    val freeBytes: Long = 0,
)

internal sealed interface RegionCatalogResult {
    data class Loaded(
        val catalog: RegionCatalog,
        /** The smallest region with a file around the position, unless it or a region above it is saved. */
        val suggestedId: String?,
    ) : RegionCatalogResult

    data object Unavailable : RegionCatalogResult
}

/** What saving one region would take, judged when it is asked for. */
internal sealed interface RegionOffer {
    data class Ready(
        val region: CatalogRegion,
        val file: RemoteFile,
        val usedAfterBytes: Long,
        val limitBytes: Long?,
        val freeBytes: Long,
        /** The place that answered with [file], which the download fetches from. */
        val fileUrl: String = region.fileUrl,
    ) : RegionOffer

    data class OverLimit(
        val region: CatalogRegion,
        val sizeBytes: Long,
        val overByBytes: Long,
        val limitBytes: Long,
    ) : RegionOffer

    data class NoSpace(
        val region: CatalogRegion,
        val sizeBytes: Long,
        val freeBytes: Long,
    ) : RegionOffer

    data class Unavailable(
        val region: CatalogRegion,
        val reason: Reason,
    ) : RegionOffer {
        enum class Reason { NETWORK, GONE, SIZE_UNKNOWN, BUSY, ALREADY_SAVED }
    }
}

/**
 * The saved map regions and the one download that may run, shared by the sheet and Diagnostics.
 *
 * A singleton (mirrors `UpdateRepository`), so a download outlives the sheet that started it. Construction touches
 * no file: [load] does the first read, and every other entry point calls it first. One [Mutex] orders every change
 * to the files with the state written after it, which reads the files again, so the state never describes files
 * that are gone; claiming the one download slot happens under it too, so two starts cannot both win. The transfer
 * runs outside the lock, and its progress updates the state alone. The constructor stays injectable for JVM tests;
 * production wiring goes through [get].
 */
internal class OfflineRegionRepository internal constructor(
    private val files: OfflineRegionFiles,
    private val feed: RegionFeed,
    private val check: RegionFileCheck,
    private val limit: suspend () -> OfflineStorageLimit,
    private val sizes: suspend () -> Map<String, Long>,
    private val indexCache: File,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow(OfflineRegionsState())
    val state: StateFlow<OfflineRegionsState> = _state.asStateFlow()

    // Not reentrant: entry points call load() before they take it.
    private val lock = Mutex()

    // The transport writes the index through one temporary file beside it, so two fetches must not overlap.
    private val indexLock = Mutex()

    private val indexEtagFile = indexCache.resolveSibling(indexCache.name + INDEX_ETAG_SUFFIX)

    // The record a download started or paused with; null when no record of ours is on disk. A running transfer
    // may store a newer one for another file, and leaves it here when it pauses. Guarded by [lock].
    private var pendingRecord: PendingDownloadRecord? = null

    // Guarded by [lock].
    private var transferJob: Job? = null

    /** Repair what a stopped process left, list the saved regions, and restore a pending download; idempotent. */
    suspend fun load() {
        if (_state.value.loaded) return
        lock.withLock {
            if (_state.value.loaded) return@withLock
            val restored =
                withContext(ioDispatcher) {
                    files.reconcile()
                    files.pendingOrNull()?.let { record -> record to files.partFile(record.id).length() }
                }
            pendingRecord = restored?.first
            publish { restored?.let { (record, heldBytes) -> record.download(heldBytes, PAUSED) } }
        }
    }

    /**
     * Fetch the region index, revalidated against the cached copy, which stands in when the host does not answer.
     * The suggestion is the smallest region with a file around the position, unless that region or one above it is
     * saved.
     */
    suspend fun catalog(
        longitude: Double?,
        latitude: Double?,
    ): RegionCatalogResult {
        load()
        return indexLock
            .withLock { fetchedCatalogOrNull() }
            ?.let { RegionCatalogResult.Loaded(it, it.suggestionOrNull(longitude, latitude)) }
            ?: RegionCatalogResult.Unavailable
    }

    /** Judge what saving [region] would take now; a download starts only from a [RegionOffer.Ready]. */
    suspend fun offer(region: CatalogRegion): RegionOffer {
        load()
        return when {
            isSaved(region.id) -> {
                RegionOffer.Unavailable(region, Reason.ALREADY_SAVED)
            }

            _state.value.download != null -> {
                RegionOffer.Unavailable(region, Reason.BUSY)
            }

            else -> {
                offerAt(region, listOfNotNull(region.fileUrl, region.alternateFileUrl))
            }
        }
    }

    // The index does not say which of the region's places holds its file, so each is asked in turn, the one beside
    // the extract first, where the index implies it is. Only "no such file" leads on to the next place. Any other
    // answer says nothing about where the file is, and asking on would turn a host that did not answer into "no
    // map file".
    private suspend fun offerAt(
        region: CatalogRegion,
        places: List<String>,
    ): RegionOffer =
        when (val probe = feed.probe(places.first())) {
            is ProbeResult.Found -> {
                offerFor(region, probe.file, places.first())
            }

            ProbeResult.Gone -> {
                places
                    .drop(1)
                    .takeIf { it.isNotEmpty() }
                    ?.let { offerAt(region, it) }
                    ?: RegionOffer.Unavailable(region, Reason.GONE)
            }

            // Without a size neither the limit nor the free space can be checked, limit or not.
            ProbeResult.SizeUnknown -> {
                RegionOffer.Unavailable(region, Reason.SIZE_UNKNOWN)
            }

            ProbeResult.Unavailable -> {
                RegionOffer.Unavailable(region, Reason.NETWORK)
            }
        }

    /** Start the download [offer] describes, unless a download exists or its region has been saved since. */
    fun start(offer: RegionOffer.Ready) {
        scope.launch {
            load()
            lock.withLock {
                if (_state.value.download == null && !isSaved(offer.region.id)) begin(offer.pendingRecord())
            }
        }
    }

    /** Continue a paused download from its part, when the rest of its file still fits the free space. */
    fun resume() {
        scope.launch {
            load()
            lock.withLock {
                val record = pendingRecord
                if (_state.value.download?.phase == PAUSED && record != null) proceed(record)
            }
        }
    }

    /** Stop the download in any phase, discard its part and its record, and clear it from the state. */
    fun cancel() {
        scope.launch {
            load()
            lock.withLock {
                // Joined before the files go, so no write of the transfer lands after them.
                transferJob?.cancelAndJoin()
                transferJob = null
                val download = _state.value.download ?: return@withLock
                if (pendingRecord != null) {
                    withContext(ioDispatcher) {
                        files.partFile(download.regionId).delete()
                        files.discardPending()
                    }
                }
                pendingRecord = null
                publish { null }
            }
        }
    }

    /** Remove a saved region's files; an id that is not saved changes nothing. */
    suspend fun delete(regionId: String) {
        load()
        lock.withLock {
            if (isSaved(regionId)) {
                withContext(ioDispatcher) { files.delete(regionId) }
                publish { it }
            }
        }
    }

    private fun isSaved(id: String): Boolean = _state.value.saved.any { it.id == id }

    private suspend fun fetchedCatalogOrNull(): RegionCatalog? {
        // Without the cached file a 304 would leave nothing to read, so its ETag goes only with it.
        val etag = withContext(ioDispatcher) { indexEtagFile.takeIf { indexCache.isFile }?.textOrNull() }
        val result = feed.index(indexCache, etag)
        return withContext(ioDispatcher) {
            if (result is IndexResult.Fresh) storeIndexEtag(result.etag)
            // A cached file that does not parse must be fetched whole next time, not revalidated into another 304.
            cachedCatalogOrNull(sizes()).also { if (it == null) indexEtagFile.delete() }
        }
    }

    // An ETag that cannot be stored must not stay behind beside a newer body.
    private fun storeIndexEtag(etag: String?) {
        if (etag == null || runCatching { indexEtagFile.writeText(etag) }.isFailure) indexEtagFile.delete()
    }

    private fun cachedCatalogOrNull(sizes: Map<String, Long>): RegionCatalog? =
        try {
            indexCache.inputStream().buffered().use { regionCatalogOrNull(it, sizes) }
        } catch (_: IOException) {
            null
        }

    private fun RegionCatalog.suggestionOrNull(
        longitude: Double?,
        latitude: Double?,
    ): String? =
        when {
            longitude == null || latitude == null -> {
                null
            }

            // A saved region's file covers its sub-regions, which the picker does not open either.
            else -> {
                smallestContainingOrNull(longitude, latitude)
                    ?.takeUnless { region -> lineageOf(region).any { isSaved(it.id) } }
                    ?.id
            }
        }

    private suspend fun offerFor(
        region: CatalogRegion,
        file: RemoteFile,
        fileUrl: String,
    ): RegionOffer {
        val limitBytes = limit().bytes
        val (keptBytes, freeBytes) = withContext(ioDispatcher) { files.keptBytes() to files.freeBytes() }
        val decision =
            storageDecision(limitBytes, keptBytes, reservedBytes = 0, replacedBytes = 0, incomingBytes = file.sizeBytes)
        return when (decision) {
            is StorageDecision.OverLimit -> {
                RegionOffer.OverLimit(region, file.sizeBytes, decision.overByBytes, decision.limitBytes)
            }

            StorageDecision.Allowed if !hasRoomFor(file.sizeBytes, freeBytes) -> {
                RegionOffer.NoSpace(region, file.sizeBytes, freeBytes)
            }

            StorageDecision.Allowed -> {
                RegionOffer.Ready(region, file, keptBytes + file.sizeBytes, limitBytes, freeBytes, fileUrl)
            }
        }
    }

    // Runs under [lock] with no download in the state.
    private suspend fun begin(record: PendingDownloadRecord) {
        if (withContext(ioDispatcher) { files.writePending(record) }) {
            pendingRecord = record
            publish { record.download(receivedBytes = 0, RUNNING) }
            transferJob = scope.launch { transfer(record) }
        } else {
            // Nothing of ours was stored, so nothing is discarded.
            publish { record.download(receivedBytes = 0, FAILED, STORAGE) }
        }
    }

    // Runs under [lock] with the download paused.
    private suspend fun proceed(record: PendingDownloadRecord) {
        val (heldBytes, freeBytes) =
            withContext(ioDispatcher) { files.partFile(record.id).length() to files.freeBytes() }
        if (hasRoomFor(record.sizeBytes - heldBytes, freeBytes)) {
            publish { record.download(heldBytes, RUNNING) }
            transferJob = scope.launch { transfer(record) }
        } else {
            publish { record.download(heldBytes, PAUSED, NO_SPACE) }
        }
    }

    private suspend fun transfer(start: PendingDownloadRecord) {
        // The record of the file being fetched: an accepted restart replaces it.
        var record = start
        // Why onRestart declined the file the server sent, for the refusal the transport reports next.
        var refusal: RegionDownloadFailure? = null
        val result =
            feed.download(
                url = start.url,
                part = files.partFile(start.id),
                expected = start.remoteFile(),
                onRestart = { file ->
                    val declined = restartRefusalOrNull(record, file)
                    if (declined == null) {
                        record = record.forFile(file)
                        progress(receivedBytes = 0, totalBytes = file.sizeBytes)
                    }
                    refusal = declined
                    declined == null
                },
                onProgress = ::progress,
            )
        when (result) {
            is TransferResult.Complete -> verify(record.forFile(result.file))

            TransferResult.Interrupted, TransferResult.Unavailable -> pause(record, NETWORK)

            TransferResult.StorageFailed -> pause(record, STORAGE)

            TransferResult.Gone -> lock.withLock { discard(record, GONE) }

            // Only a declined restart ends in a refusal, and it names its reason.
            is TransferResult.Refused -> lock.withLock { discard(record, refusal ?: OVER_LIMIT) }
        }
    }

    // Lock-free: a transfer reports often, and the saved sizes stand in for a walk of the directory per report.
    private fun progress(
        receivedBytes: Long,
        totalBytes: Long,
    ) = _state.update { current ->
        current.copy(
            download = current.download?.copy(receivedBytes = receivedBytes, totalBytes = totalBytes),
            usedBytes = current.saved.sumOf { it.sizeBytes } + receivedBytes,
        )
    }

    // The checks of a new download, run again on the file the server sent instead. The old part is still on disk
    // and goes once the restart is accepted, so its length counts as free. It goes before the record of the new
    // file is stored: a process that dies in between must not leave the old file's bytes beside that record, where
    // Resume would append the new file's tail to them. Null when the file is accepted and its record stored.
    private suspend fun restartRefusalOrNull(
        record: PendingDownloadRecord,
        file: RemoteFile,
    ): RegionDownloadFailure? {
        val limitBytes = limit().bytes
        return withContext(ioDispatcher) {
            val decision =
                storageDecision(
                    limitBytes,
                    files.keptBytes(),
                    reservedBytes = 0,
                    replacedBytes = 0,
                    incomingBytes = file.sizeBytes,
                )
            val part = files.partFile(record.id)
            val freeBytes = files.freeBytes() + part.length()
            when {
                decision is StorageDecision.OverLimit -> OVER_LIMIT

                !hasRoomFor(file.sizeBytes, freeBytes) -> NO_SPACE

                else -> STORAGE.takeUnless {
                    (part.delete() || !part.exists()) &&
                        files.writePending(record.forFile(file))
                }
            }
        }
    }

    private suspend fun verify(record: PendingDownloadRecord) {
        lock.withLock { publish { record.download(record.sizeBytes, VERIFYING) } }
        // Outside the lock: a cancel takes it, stops this transfer at the end of the check, and nothing is committed.
        val usable = withContext(ioDispatcher) { check.isUsable(files.partFile(record.id), record.sizeBytes) }
        lock.withLock {
            val committed = usable && withContext(ioDispatcher) { files.commit(record.toSavedRecord(clock())) }
            when {
                committed -> {
                    pendingRecord = null
                    publish { null }
                }

                !usable -> {
                    discard(record, CORRUPT)
                }

                else -> {
                    // A commit can stop after the rename; the data file it left is not a saved region.
                    withContext(ioDispatcher) { files.delete(record.id) }
                    discard(record, STORAGE)
                }
            }
        }
    }

    private suspend fun pause(
        record: PendingDownloadRecord,
        failure: RegionDownloadFailure,
    ) = lock.withLock {
        pendingRecord = record
        // The transport keeps the part or deletes it depending on the answer; only its length on disk says which.
        val heldBytes = withContext(ioDispatcher) { files.partFile(record.id).length() }
        publish { record.download(heldBytes, PAUSED, failure) }
    }

    // Runs under [lock]: the download ends here, and nothing of it stays on disk.
    private suspend fun discard(
        record: PendingDownloadRecord,
        failure: RegionDownloadFailure,
    ) {
        withContext(ioDispatcher) {
            files.partFile(record.id).delete()
            files.discardPending()
        }
        pendingRecord = null
        publish { record.download(receivedBytes = 0, FAILED, failure) }
    }

    // Runs under [lock]. Reads the files after every change, so the state never describes files that are gone.
    private suspend fun publish(download: (RegionDownload?) -> RegionDownload?) {
        val (saved, usedBytes, freeBytes) =
            withContext(ioDispatcher) {
                Triple(
                    files.savedRegions().map { it.toSavedRegion() }.sortedWith(SavedRegionOrder),
                    files.usedBytes(),
                    files.freeBytes(),
                )
            }
        _state.update { current ->
            current.copy(
                loaded = true,
                saved = saved,
                download = download(current.download),
                usedBytes = usedBytes,
                freeBytes = freeBytes,
            )
        }
    }

    companion object {
        @Volatile
        private var instance: OfflineRegionRepository? = null

        fun get(context: Context): OfflineRegionRepository =
            instance ?: synchronized(this) { instance ?: create(context.applicationContext).also { instance = it } }

        private fun create(app: Context): OfflineRegionRepository {
            val display = DisplayPreferences(app)
            // Read once, on first use and off the caller's thread: the snapshot ships with the app.
            val snapshotSizes by lazy { snapshotSizesOf(app) }
            val noBackupDir = app.noBackupFilesDir
            val storage = app.getSystemService<StorageManager>()
            return OfflineRegionRepository(
                files =
                    OfflineRegionFiles(
                        root = File(noBackupDir, ROOT_DIR),
                        usableBytes = { storage?.allocatableBytesOrZero(noBackupDir) ?: 0L },
                    ),
                feed = RegionFeedApi(downloadHttpClient(), femtoUserAgent),
                check = SqliteRegionFileCheck,
                limit = { display.settings.first().offlineStorageLimit },
                sizes = { snapshotSizes },
                indexCache = File(app.cacheDir, INDEX_CACHE_PATH),
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            )
        }

        private fun snapshotSizesOf(app: Context): Map<String, Long> =
            runCatching { app.assets.open(SIZE_SNAPSHOT_ASSET).use(::shortbreadSizesOrNull) }
                .onFailure { Log.w(TAG, "size snapshot unreadable", it) }
                .getOrNull()
                .orEmpty()
    }
}

private fun File.textOrNull(): String? = runCatching { readText() }.getOrNull()?.takeIf { it.isNotEmpty() }

private fun RegionOffer.Ready.pendingRecord(): PendingDownloadRecord =
    PendingDownloadRecord(
        id = region.id,
        name = region.name,
        url = fileUrl,
        sizeBytes = file.sizeBytes,
        etag = file.etag,
        lastModifiedEpochMs = file.lastModifiedEpochMs,
        polygon = region.polygon.rings.map { it.toList() },
    )

private fun PendingDownloadRecord.remoteFile(): RemoteFile = RemoteFile(sizeBytes, etag, lastModifiedEpochMs)

// The download answer is the authority: the record takes the size, ETag and date of the file it brought.
private fun PendingDownloadRecord.forFile(file: RemoteFile): PendingDownloadRecord =
    copy(sizeBytes = file.sizeBytes, etag = file.etag, lastModifiedEpochMs = file.lastModifiedEpochMs)

private fun PendingDownloadRecord.download(
    receivedBytes: Long,
    phase: RegionDownloadPhase,
    failure: RegionDownloadFailure? = null,
): RegionDownload = RegionDownload(id, name, receivedBytes, sizeBytes, phase, failure)

private fun SavedRegionRecord.toSavedRegion(): SavedRegion =
    SavedRegion(
        id = id,
        name = name,
        sizeBytes = sizeBytes,
        dataDateEpochMs = lastModifiedEpochMs,
        savedAtEpochMs = savedAtEpochMs,
    )
