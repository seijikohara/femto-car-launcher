package io.github.seijikohara.femto.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

private const val TAG = "OpenMeteoApi"

internal class OpenMeteoApi(
    private val client: OkHttpClient,
    private val baseUrl: String = "https://api.open-meteo.com/",
    // Optional Open-Meteo API key. When set, baseUrl points at the keyed customer
    // host and the request appends &apikey=; the public endpoint needs none.
    // Request-only secret — never logged (the failure log records the status code,
    // not the key-bearing URL).
    private val apiKey: String? = null,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun forecast(
        latitude: Double,
        longitude: Double,
    ): ForecastResponse? =
        withContext(Dispatchers.IO) {
            runCatching {
                val apiKeyParam = apiKey?.let { "&apikey=$it" }.orEmpty()
                val request =
                    Request
                        .Builder()
                        .url(
                            baseUrl.trimEnd('/') +
                                "/v1/forecast?latitude=$latitude&longitude=$longitude" +
                                "&current=temperature_2m,apparent_temperature,weathercode," +
                                "windspeed_10m,relative_humidity_2m,uv_index,is_day" +
                                "&hourly=temperature_2m,weathercode" +
                                "&daily=sunrise,sunset,weathercode," +
                                "temperature_2m_max,temperature_2m_min" +
                                "&forecast_days=5&timezone=auto" +
                                apiKeyParam,
                        ).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        // Log the status only (the URL carries the apiKey) so a
                        // sustained non-2xx outage — e.g. a 429 rate-limit on the
                        // public endpoint — is diagnosable instead of silently empty.
                        Log.w(TAG, "forecast HTTP ${response.code}")
                        return@use null
                    }
                    response.body?.string()?.let { body ->
                        json.decodeFromString<ForecastResponse>(body)
                    }
                }
            }.onFailure { Log.w(TAG, "forecast failed", it) }
                .getOrNull()
        }

    @Serializable
    data class ForecastResponse(
        val timezone: String? = null,
        val current: Current? = null,
        val hourly: Hourly? = null,
        val daily: Daily? = null,
    )

    @Serializable
    data class Current(
        val time: String,
        val temperature_2m: Double,
        val weathercode: Int,
        // Secondary fields are nullable: a payload that drops them must still
        // yield a usable temperature + code reading instead of failing decoding
        // with MissingFieldException and discarding the whole snapshot.
        val apparent_temperature: Double? = null,
        val windspeed_10m: Double? = null,
        val relative_humidity_2m: Double? = null,
        val uv_index: Double? = null,
        val is_day: Int? = null,
    )

    @Serializable
    data class Hourly(
        val time: List<String> = emptyList(),
        val temperature_2m: List<Double> = emptyList(),
        val weathercode: List<Int> = emptyList(),
    )

    @Serializable
    data class Daily(
        val time: List<String> = emptyList(),
        val sunrise: List<String> = emptyList(),
        val sunset: List<String> = emptyList(),
        val weathercode: List<Int> = emptyList(),
        val temperature_2m_max: List<Double> = emptyList(),
        val temperature_2m_min: List<Double> = emptyList(),
    )
}
