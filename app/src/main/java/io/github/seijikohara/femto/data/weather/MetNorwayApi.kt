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
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "MetNorwayApi"

// The "complete" product carries ultraviolet_index_clear_sky; the "compact" one
// drops it. The weather card surfaces UV, so complete is the contract.
private const val FORECAST_PATH = "/weatherapi/locationforecast/2.0/complete"

// HttpURLConnection predates RFC 6585 and has no constant for 429.
private const val HTTP_TOO_MANY_REQUESTS = 429

// MET allows at most four decimals (more returns 403/400), and full-precision
// GPS jitter would give every fix its own URL, defeating the HTTP cache. %.4f
// rounds rather than floor-truncates: the term's intent is a precision cap,
// and rounding stays nearest the actual fix (a ~11 m grid either way).
private fun coordinate(value: Double): String = String.format(Locale.ROOT, "%.4f", value)

/**
 * MET Norway Locationforecast 2.0 client. api.met.no is the only free global JSON
 * forecast usable in a commercial app (Open-Meteo's free tier is non-commercial).
 *
 * MET terms-of-service obligations honoured here (api.met.no/doc/TermsOfService):
 * - **User-Agent**: api.met.no returns 403 for a missing or generic UA, so an
 *   identifying [userAgent] with a contact URL is mandatory and supplied by the
 *   caller — never defaulted.
 * - **Coordinates**: lat/lon go out rounded to four decimals — see [coordinate].
 * - **Caching**: the injected [client] is expected to carry a disk `Cache` (see
 *   `HomeViewModelFactory`); OkHttp then enforces standard HTTP semantics — no
 *   request before the server's `Expires` horizon, revalidation with
 *   `If-Modified-Since`, transparent 304 reuse — and the entries survive process
 *   restarts. Without a cache every refresh degrades to a full fetch.
 * - **429**: a `Retry-After` answer is honoured — no request leaves this client
 *   before that horizon (throttled clients that keep hammering risk a ban).
 * - **203** marks a beta/deprecated product; the ToS asks clients to log it.
 *
 * [baseUrl] is configurable so a self-hosted caching proxy can be substituted
 * without a code change.
 */
internal class MetNorwayApi(
    private val client: OkHttpClient,
    private val baseUrl: String,
    private val userAgent: String,
    // Injectable clock for the Retry-After horizon; epoch millis rather than
    // java.time.Instant so the name never collides with the DTO's nested type.
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    private val json = Json { ignoreUnknownKeys = true }

    // Epoch-millis horizon announced by a 429 Retry-After; no request leaves
    // before it. Not synchronized: forecast() runs serialized under the
    // repository's refresh mutex.
    private var retryAfterUntilMs: Long? = null

    private fun apiUrl(path: String): String = baseUrl.trimEnd('/') + path

    suspend fun forecast(
        latitude: Double,
        longitude: Double,
    ): MetForecast? =
        withContext(Dispatchers.IO) {
            val suppressedUntil = retryAfterUntilMs
            if (suppressedUntil != null && nowMs() < suppressedUntil) {
                Log.w(TAG, "forecast suppressed by Retry-After for another ${(suppressedUntil - nowMs()) / 1000}s")
                return@withContext null
            }
            runCatching {
                val request =
                    Request
                        .Builder()
                        .url(apiUrl(FORECAST_PATH) + "?lat=${coordinate(latitude)}&lon=${coordinate(longitude)}")
                        .header("User-Agent", userAgent)
                        .build()
                client.newCall(request).execute().use { response ->
                    when {
                        response.code == HTTP_TOO_MANY_REQUESTS -> {
                            // Honour the server's horizon. Absent (or in the
                            // HTTP-date form, which MET does not use), pacing
                            // falls to the repository's attempt floor alone.
                            retryAfterUntilMs =
                                response
                                    .header("Retry-After")
                                    ?.trim()
                                    ?.toLongOrNull()
                                    ?.let { nowMs() + it * 1000 }
                            Log.w(TAG, "forecast HTTP 429; Retry-After horizon ${retryAfterUntilMs ?: "unset"}")
                            null
                        }

                        !response.isSuccessful -> {
                            // Status only; a sustained non-2xx (e.g. a 403 UA or
                            // coordinate rejection) is diagnosable instead of silent.
                            Log.w(TAG, "forecast HTTP ${response.code}")
                            null
                        }

                        else -> {
                            retryAfterUntilMs = null
                            // 203 marks a beta/deprecated product behind the API
                            // gateway; keep using the data but say so in the log.
                            if (response.code == HttpURLConnection.HTTP_NOT_AUTHORITATIVE) {
                                Log.w(TAG, "forecast served with 203 — beta/deprecated product")
                            }
                            json.decodeFromString<MetForecast>(response.body.string())
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
        // Meteorological "from" direction, degrees clockwise from north.
        @SerialName("wind_from_direction") val windFromDirection: Double? = null,
        @SerialName("relative_humidity") val relativeHumidity: Double? = null,
        @SerialName("ultraviolet_index_clear_sky") val ultravioletIndexClearSky: Double? = null,
    )

    @Serializable
    data class Period(
        val summary: Summary = Summary(),
        val details: PeriodDetails? = null,
    )

    @Serializable
    data class Summary(
        // e.g. "clearsky_day", "lightrain", "partlycloudy_night".
        @SerialName("symbol_code") val symbolCode: String? = null,
    )

    @Serializable
    data class PeriodDetails(
        // Expected precipitation over the period, millimetres.
        @SerialName("precipitation_amount") val precipitationAmount: Double? = null,
        // Percent; regional coverage — often absent outside the Nordics.
        @SerialName("probability_of_precipitation") val probabilityOfPrecipitation: Double? = null,
        // Period temperature envelope; carried by the 6-hour blocks and used for
        // the far-tail days where instant samples are 6 h apart.
        @SerialName("air_temperature_max") val airTemperatureMax: Double? = null,
        @SerialName("air_temperature_min") val airTemperatureMin: Double? = null,
    )
}
