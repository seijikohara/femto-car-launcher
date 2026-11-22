package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.geocoding.PlaceLocator
import io.github.seijikohara.femto.data.geocoding.PlaceLookup

/**
 * In-memory [PlaceLocator]: [available] stands for `Geocoder.isPresent()`, and [locate] answers from [answers] by
 * text, [PlaceLookup.NotFound] for any other.
 */
internal class FakePlaceLocator(
    override val available: Boolean = true,
    private val answers: Map<String, PlaceLookup> = emptyMap(),
) : PlaceLocator {
    /** Every looked-up text, in call order. */
    val lookedUp = mutableListOf<String>()

    override suspend fun locate(text: String): PlaceLookup {
        lookedUp += text
        return answers[text] ?: PlaceLookup.NotFound
    }
}
