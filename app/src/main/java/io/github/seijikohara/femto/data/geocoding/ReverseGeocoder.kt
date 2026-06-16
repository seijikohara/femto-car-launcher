package io.github.seijikohara.femto.data.geocoding

/**
 * A source that turns a coordinate into a readable [ShortAddress], or null when
 * no address is available. Implementations are interchangeable so the caching /
 * pacing layer in [ReverseGeocoderRepository] is agnostic to where the address
 * comes from:
 * - [PlatformReverseGeocoder] — the on-device Android `Geocoder`; the free,
 *   ToS-free default (no public reverse-geocoding API is ToS-compliant for a
 *   distributed app).
 * - [NominatimReverseGeocoder] — a self-hosted Nominatim-compatible host, opted
 *   into via `GEOCODER_BASE_URL`.
 */
internal interface ReverseGeocoder {
    suspend fun reverse(
        latitude: Double,
        longitude: Double,
    ): ShortAddress?
}
