package io.github.seijikohara.femto.data

internal data class ShortAddress(
    val locality: String,
    val region: String?,
) {
    fun displayString(): String = listOfNotNull(locality, region).joinToString(", ")
}
