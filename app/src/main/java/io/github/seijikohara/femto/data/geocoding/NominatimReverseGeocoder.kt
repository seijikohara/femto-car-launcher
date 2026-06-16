package io.github.seijikohara.femto.data.geocoding

/**
 * Reverse-geocode through a Nominatim-compatible host and compose the structured
 * address. Opt-in via `GEOCODER_BASE_URL` (the public Nominatim endpoint is not
 * ToS-compliant for distribution — see [ReverseGeocoder]); a self-hosted or
 * keyed host (e.g. LocationIQ) returns the same jsonv2 shape.
 */
internal class NominatimReverseGeocoder(
    private val api: NominatimApi,
) : ReverseGeocoder {
    override suspend fun reverse(
        latitude: Double,
        longitude: Double,
    ): ShortAddress? = api.reverse(latitude, longitude)?.address?.let(AddressComposer::composeAddress)
}
