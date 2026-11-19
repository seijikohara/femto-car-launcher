package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.map.offline.CatalogRegion
import io.github.seijikohara.femto.data.map.offline.PendingDownloadRecord
import io.github.seijikohara.femto.data.map.offline.REGION_HOST_PREFIX
import io.github.seijikohara.femto.data.map.offline.RegionCatalog
import io.github.seijikohara.femto.data.map.offline.RegionDownload
import io.github.seijikohara.femto.data.map.offline.RegionDownloadFailure
import io.github.seijikohara.femto.data.map.offline.RegionDownloadPhase
import io.github.seijikohara.femto.data.map.offline.RegionPolygon
import io.github.seijikohara.femto.data.map.offline.RemoteFile
import io.github.seijikohara.femto.data.map.offline.SavedRegion
import io.github.seijikohara.femto.data.map.offline.SavedRegionRecord
import io.github.seijikohara.femto.data.map.offline.regionCatalogOrNull
import io.github.seijikohara.femto.data.map.offline.shortbreadSizesOrNull
import java.util.Locale

private object OfflineRegionResources

private fun regionResource(fileName: String) =
    requireNotNull(OfflineRegionResources::class.java.getResourceAsStream("/offlinemap/regions/$fileName")) {
        "Missing test resource offlinemap/regions/$fileName"
    }

/** The sample index under `app/src/test/resources/offlinemap/regions/`, byte for byte, as the host serves it. */
internal fun sampleRegionIndex(): ByteArray = regionResource("region-index-sample.json").use { it.readBytes() }

/** The sizes of the sample snapshot under `app/src/test/resources/offlinemap/regions/`, by region id. */
internal fun sampleShortbreadSizes(): Map<String, Long> =
    regionResource("shortbread-regions-sample.json").use { requireNotNull(shortbreadSizesOrNull(it)) }

/** The catalogue built from the two sample files under `app/src/test/resources/offlinemap/regions/`. */
internal fun sampleRegionCatalog(): RegionCatalog =
    requireNotNull(regionCatalogOrNull(index = sampleRegionIndex().inputStream(), sizes = sampleShortbreadSizes()))

/** Return the region [id], which the catalogue must hold. */
internal fun RegionCatalog.region(id: String): CatalogRegion = requireNotNull(regionOrNull(id)) { "No region $id" }

/** Build the record of a download of [region] that expects [file]. */
internal fun pendingDownloadRecord(
    region: CatalogRegion,
    file: RemoteFile,
): PendingDownloadRecord =
    PendingDownloadRecord(
        id = region.id,
        name = region.name,
        url = region.fileUrl,
        sizeBytes = file.sizeBytes,
        etag = file.etag,
        lastModifiedEpochMs = file.lastModifiedEpochMs,
        polygon = region.polygon.rings.map { it.toList() },
    )

/** Build the record of [region] saved from [file] at [savedAtEpochMs]. */
internal fun savedRegionRecord(
    region: CatalogRegion,
    file: RemoteFile,
    savedAtEpochMs: Long,
): SavedRegionRecord =
    SavedRegionRecord(
        id = region.id,
        name = region.name,
        url = region.fileUrl,
        sizeBytes = file.sizeBytes,
        etag = file.etag,
        lastModifiedEpochMs = file.lastModifiedEpochMs,
        savedAtEpochMs = savedAtEpochMs,
        polygon = region.polygon.rings.map { it.toList() },
    )

/** Build a catalogue region with no area, for tests that walk the tree rather than locate a position. */
internal fun catalogRegion(
    id: String,
    name: String,
    parentId: String? = null,
    approxBytes: Long? = null,
): CatalogRegion =
    CatalogRegion(
        id = id,
        parentId = parentId,
        name = name,
        fileUrl = "$REGION_HOST_PREFIX$id-shortbread-1.0.mbtiles",
        approxBytes = approxBytes,
        polygon = RegionPolygon(emptyList()),
    )

/** Build a saved region as the repository lists it. */
internal fun savedRegion(
    id: String,
    name: String,
    sizeBytes: Long,
    dataDateEpochMs: Long? = null,
): SavedRegion =
    SavedRegion(
        id = id,
        name = name,
        sizeBytes = sizeBytes,
        dataDateEpochMs = dataDateEpochMs,
        savedAtEpochMs = SAVED_AT_EPOCH_MS,
    )

/** Build the download of the region [name], whose id is the name in lower case. */
internal fun regionDownload(
    name: String,
    phase: RegionDownloadPhase,
    receivedBytes: Long = 0,
    totalBytes: Long = 1_000_000_000,
    failure: RegionDownloadFailure? = null,
): RegionDownload =
    RegionDownload(
        regionId = name.lowercase(Locale.ROOT),
        name = name,
        receivedBytes = receivedBytes,
        totalBytes = totalBytes,
        phase = phase,
        failure = failure,
    )

private const val SAVED_AT_EPOCH_MS = 1_763_500_000_000L
