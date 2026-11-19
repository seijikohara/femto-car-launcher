package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.map.offline.CatalogRegion
import io.github.seijikohara.femto.data.map.offline.PendingDownloadRecord
import io.github.seijikohara.femto.data.map.offline.RegionCatalog
import io.github.seijikohara.femto.data.map.offline.RemoteFile
import io.github.seijikohara.femto.data.map.offline.SavedRegionRecord
import io.github.seijikohara.femto.data.map.offline.regionCatalogOrNull
import io.github.seijikohara.femto.data.map.offline.shortbreadSizesOrNull

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
