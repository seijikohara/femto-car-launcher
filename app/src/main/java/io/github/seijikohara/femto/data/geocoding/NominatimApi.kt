package io.github.seijikohara.femto.data.geocoding

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale

private const val TAG = "NominatimApi"

/**
 * Reverse-geocode a coordinate through an OSM Nominatim-compatible endpoint.
 *
 * Development builds target the public Nominatim service; production swaps in
 * LocationIQ, which returns the same jsonv2 shape — only [baseUrl] and [apiKey]
 * change.
 * Nominatim blocks stock HTTP User-Agents, so [userAgent] is mandatory.
 */
internal class NominatimApi(
    private val client: OkHttpClient,
    private val baseUrl: String = "https://nominatim.openstreetmap.org/",
    private val userAgent: String,
    // Follow the device locale by default: the launcher is multi-region and no
    // single language may be privileged (CLAUDE.md, "multi-region distribution").
    private val language: String = Locale.getDefault().language,
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
                    if (!response.isSuccessful) {
                        // 429 in particular signals a Nominatim usage-policy
                        // violation and must leave a trail, not a silent null.
                        Log.w(TAG, "reverse geocode HTTP ${response.code}")
                        return@use null
                    }
                    response.body?.string()?.let { body ->
                        json.decodeFromString<NominatimResponse>(body)
                    }
                }
            }.onFailure { Log.w(TAG, "reverse geocode failed", it) }
                .getOrNull()
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
