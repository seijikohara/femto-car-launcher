package io.github.seijikohara.femto.data.geocoding

internal data class ShortAddress(
    val locality: String,
    val region: String?,
    val line: String = "",
) {
    /**
     * Return the full formatted street address for the location overlay
     * (e.g. "東京都新宿区西新宿１丁目１−３"). Falls back to the compact
     * "locality, region" pair when the geocoder gives no address line.
     */
    fun displayString(): String = line.ifBlank { listOfNotNull(locality, region).joinToString(", ") }
}
