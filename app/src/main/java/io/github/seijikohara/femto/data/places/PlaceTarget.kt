package io.github.seijikohara.femto.data.places

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Where a destination points: a free-text [Query] the navigation app searches
 * for, or a fixed [Point]. Navigate hands the target to whichever app handles
 * `geo:` (see [geoHandoffUri]) unresolved. Only a route on the launcher's own
 * map resolves a [Query] itself, through the device's geocoder
 * (`PlaceLocator`).
 */
@Serializable
internal sealed interface PlaceTarget {
    @Serializable
    @SerialName("query")
    data class Query(
        val text: String,
    ) : PlaceTarget

    @Serializable
    @SerialName("point")
    data class Point(
        val latitude: Double,
        val longitude: Double,
    ) : PlaceTarget
}
