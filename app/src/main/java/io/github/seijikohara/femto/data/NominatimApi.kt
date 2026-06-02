package io.github.seijikohara.femto.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Reverse-geocode a coordinate through an OSM Nominatim-compatible endpoint.
 *
 * The PoC targets the public Nominatim service; production swaps in LocationIQ,
 * which returns the same jsonv2 shape — only [baseUrl] and [apiKey] change.
 * Nominatim blocks stock HTTP User-Agents, so [userAgent] is mandatory.
 */
internal class NominatimApi(
    private val client: OkHttpClient,
    private val baseUrl: String = "https://nominatim.openstreetmap.org/",
    private val userAgent: String,
    private val language: String = "ja",
    // Token for a keyed Nominatim-compatible host (e.g. LocationIQ). Null for the
    // public keyless Nominatim service; when set it is appended as `key`.
    private val apiKey: String? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun reverse(
        lat: Double,
        lon: Double,
    ): NominatimResponse? =
        withContext(ioDispatcher) {
            runCatching {
                val url =
                    baseUrl
                        .toHttpUrl()
                        .newBuilder()
                        .addPathSegment("reverse")
                        .addQueryParameter("format", "jsonv2")
                        .addQueryParameter("addressdetails", "1")
                        .addQueryParameter("accept-language", language)
                        .addQueryParameter("lat", lat.toString())
                        .addQueryParameter("lon", lon.toString())
                        .apply { apiKey?.let { addQueryParameter("key", it) } }
                        .build()
                val request =
                    Request
                        .Builder()
                        .url(url)
                        .header("User-Agent", userAgent)
                        .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    response.body?.string()?.let { body ->
                        json.decodeFromString<NominatimResponse>(body)
                    }
                }
            }.getOrNull()
        }

    @Serializable
    data class NominatimResponse(
        val address: NominatimAddress? = null,
        @SerialName("display_name") val displayName: String? = null,
    )

    @Serializable
    data class NominatimAddress(
        val road: String? = null,
        @SerialName("house_number") val houseNumber: String? = null,
        val neighbourhood: String? = null,
        val quarter: String? = null,
        @SerialName("city_district") val cityDistrict: String? = null,
        val district: String? = null,
        val borough: String? = null,
        val suburb: String? = null,
        val city: String? = null,
        val town: String? = null,
        val village: String? = null,
        val municipality: String? = null,
        val county: String? = null,
        val state: String? = null,
        val province: String? = null,
        val region: String? = null,
        @SerialName("state_district") val stateDistrict: String? = null,
        @SerialName("city_block") val cityBlock: String? = null,
        val residential: String? = null,
        @SerialName("ISO3166-2-lvl4") val isoLvl4: String? = null,
        val postcode: String? = null,
        val country: String? = null,
        @SerialName("country_code") val countryCode: String? = null,
    )
}
