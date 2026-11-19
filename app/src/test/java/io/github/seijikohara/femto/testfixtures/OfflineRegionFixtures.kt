package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.map.offline.RegionCatalog
import io.github.seijikohara.femto.data.map.offline.regionCatalogOrNull
import io.github.seijikohara.femto.data.map.offline.shortbreadSizesOrNull

private object OfflineRegionResources

private fun regionResource(fileName: String) =
    requireNotNull(OfflineRegionResources::class.java.getResourceAsStream("/offlinemap/regions/$fileName")) {
        "Missing test resource offlinemap/regions/$fileName"
    }

/** The catalogue built from the two sample files under `app/src/test/resources/offlinemap/regions/`. */
internal fun sampleRegionCatalog(): RegionCatalog =
    requireNotNull(
        regionCatalogOrNull(
            index = regionResource("region-index-sample.json"),
            sizes = requireNotNull(shortbreadSizesOrNull(regionResource("shortbread-regions-sample.json"))),
        ),
    )
