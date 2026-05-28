package io.github.seijikohara.femto.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

internal class OpenMeteoApi(
    private val client: OkHttpClient,
    private val baseUrl: String = "https://api.open-meteo.com/",
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun forecast(
        latitude: Double,
        longitude: Double,
    ): ForecastResponse? =
        withContext(Dispatchers.IO) {
            runCatching {
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
                                "&forecast_days=5&timezone=auto",
                        ).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    response.body?.string()?.let { body ->
                        json.decodeFromString<ForecastResponse>(body)
                    }
                }
            }.getOrNull()
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
        val apparent_temperature: Double,
        val weathercode: Int,
        val windspeed_10m: Double,
        val relative_humidity_2m: Double? = null,
        val uv_index: Double? = null,
        val is_day: Int,
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
