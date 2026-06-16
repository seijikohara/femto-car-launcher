package io.github.seijikohara.femto.data.weather

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.HttpURLConnection
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "MetNorwayApi"

// The "complete" product carries ultraviolet_index_clear_sky; the "compact" one
// drops it. The weather card surfaces UV, so complete is the contract.
private const val FORECAST_PATH = "/weatherapi/locationforecast/2.0/complete"

/**
 * MET Norway Locationforecast 2.0 client. api.met.no is the only free global JSON
 * forecast usable in a commercial app (Open-Meteo's free tier is non-commercial).
 *
 * Two MET terms-of-service obligations are honoured here:
 * - **User-Agent**: api.met.no returns 403 for a missing or generic UA, so an
 *   identifying [userAgent] with a contact URL is mandatory and supplied by the
 *   caller — never defaulted.
 * - **Conditional requests**: the previous `Last-Modified` is replayed as
 *   `If-Modified-Since`; on a 304 the cached forecast is returned so the caller
 *   refreshes its timestamp without re-downloading. The validator is keyed by the
 *   requested coordinates so a moved fix never sends a stale validator.
 *
 * [baseUrl] is configurable so a self-hosted caching proxy can be substituted
 * without a code change.
 */
internal class MetNorwayApi(
    private val client: OkHttpClient,
    private val baseUrl: String = "https://api.met.no/",
    private val userAgent: String,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private var cachedLatLon: Pair<Double, Double>? = null
    private var lastModified: String? = null
    private var cachedForecast: MetForecast? = null

    private fun apiUrl(path: String): String = baseUrl.trimEnd('/') + path

    suspend fun forecast(
        latitude: Double,
        longitude: Double,
    ): MetForecast? =
        withContext(Dispatchers.IO) {
            runCatching {
                val requested = latitude to longitude
                val request =
                    Request
                        .Builder()
                        .url(apiUrl(FORECAST_PATH) + "?lat=$latitude&lon=$longitude")
                        .header("User-Agent", userAgent)
                        .apply {
                            if (cachedLatLon == requested) {
                                lastModified?.let { header("If-Modified-Since", it) }
                            }
                        }.build()
                client.newCall(request).execute().use { response ->
                    when {
                        // Data unchanged: reuse the cached forecast (non-null
                        // whenever a 304 is possible — it only follows a stored
                        // Last-Modified, which is set alongside cachedForecast).
                        response.code == HttpURLConnection.HTTP_NOT_MODIFIED -> {
                            cachedForecast
                        }

                        !response.isSuccessful -> {
                            // Status only; a sustained non-2xx (e.g. a 429 throttle
                            // or 403 UA rejection) is diagnosable instead of silent.
                            Log.w(TAG, "forecast HTTP ${response.code}")
                            null
                        }

                        else -> {
                            json.decodeFromString<MetForecast>(response.body.string()).also {
                                cachedLatLon = requested
                                lastModified = response.header("Last-Modified")
                                cachedForecast = it
                            }
                        }
                    }
                }
            }.onFailure {
                // runCatching also traps cancellation; rethrow so a cancelled call
                // propagates instead of logging as a phantom outage.
                if (it is CancellationException) throw it
                Log.w(TAG, "forecast failed", it)
            }.getOrNull()
        }
}

@Serializable
internal data class MetForecast(
    val properties: Properties = Properties(),
) {
    @Serializable
    data class Properties(
        val timeseries: List<Timeseries> = emptyList(),
    )

    @Serializable
    data class Timeseries(
        // ISO 8601 instant in UTC, e.g. "2026-05-01T11:00:00Z".
        val time: String,
        val data: EntryData = EntryData(),
    )

    @Serializable
    data class EntryData(
        val instant: Instant = Instant(),
        @SerialName("next_1_hours") val next1Hours: Period? = null,
        @SerialName("next_6_hours") val next6Hours: Period? = null,
        @SerialName("next_12_hours") val next12Hours: Period? = null,
    )

    @Serializable
    data class Instant(
        val details: InstantDetails = InstantDetails(),
    )

    @Serializable
    data class InstantDetails(
        @SerialName("air_temperature") val airTemperature: Double? = null,
        // metres per second; the domain model carries km/h.
        @SerialName("wind_speed") val windSpeed: Double? = null,
        @SerialName("relative_humidity") val relativeHumidity: Double? = null,
        @SerialName("ultraviolet_index_clear_sky") val ultravioletIndexClearSky: Double? = null,
    )

    @Serializable
    data class Period(
        val summary: Summary = Summary(),
    )

    @Serializable
    data class Summary(
        // e.g. "clearsky_day", "lightrain", "partlycloudy_night".
        @SerialName("symbol_code") val symbolCode: String? = null,
    )
}
