package io.github.seijikohara.femto.data.geocoding

internal data class ShortAddress(
    val locality: String,
    val region: String?,
    // The current street / thoroughfare name (e.g. "Oak St"), or null when the
    // geocoder gives none — including East-Asian administrative addresses, where a
    // street name is not the addressing unit. Distinct from [line] (the full
    // formatted address); the driving location strip shows [road] for a glance.
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
