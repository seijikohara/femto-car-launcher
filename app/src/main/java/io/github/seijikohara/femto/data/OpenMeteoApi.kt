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

    suspend fun currentWeather(
        latitude: Double,
        longitude: Double,
    ): CurrentWeather? =
        withContext(Dispatchers.IO) {
            runCatching {
                val request =
                    Request
                        .Builder()
                        .url(
                            baseUrl.trimEnd('/') +
                                "/v1/forecast?latitude=$latitude&longitude=$longitude" +
                                "&current_weather=true",
                        ).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    response.body?.string()?.let { body ->
                        json.decodeFromString<ForecastResponse>(body).current_weather
                    }
                }
            }.getOrNull()
        }

    @Serializable
    data class ForecastResponse(
        val current_weather: CurrentWeather? = null,
    )

    @Serializable
    data class CurrentWeather(
        val temperature: Double,
        val weathercode: Int,
    )
}
