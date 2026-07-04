package io.github.seijikohara.femto.data.geocoding

internal data class ShortAddress(
    val locality: String,
    val region: String?,
    // The current street / thoroughfare name (e.g. "Oak St"), or null when the
    // geocoder gives none. Each backend follows its own source: the on-device
    // Geocoder carries whatever thoroughfare the device returns (naturally empty
    // for Japan's administrative addressing, a real road for Korea's road-name
    // addressing — no market is privileged), while the OSM/Nominatim backend
    // omits `road` for East-Asian addresses on purpose (there OSM's road is
    // usually a POI/station, not a street — see AddressComposer). Distinct from
    // [line] (the full formatted address); the driving location strip shows
    // [road] for a glance.
    val road: String? = null,
    val line: String = "",
) {
    /**
     * Return the full formatted street address for the location overlay
     * (e.g. "東京都新宿区西新宿１丁目１−３"). Falls back to the compact
     * "locality, region" pair when the geocoder gives no address line.
     */
    fun displayString(): String = line.ifBlank { listOfNotNull(locality, region).joinToString(", ") }
}
