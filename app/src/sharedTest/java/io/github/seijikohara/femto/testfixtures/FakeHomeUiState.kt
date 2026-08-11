package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.music.MusicCardState
import io.github.seijikohara.femto.data.weather.WeatherSnapshot
import io.github.seijikohara.femto.ui.home.HomeUiState

/**
 * A fully populated dashboard state: every card has data, so a render exercises
 * the real layout rather than the empty-state placeholders.
 *
 * The location and address are San Francisco to match the still map backdrop the
 * screenshot goldens composite the dashboard over — a mismatched reverse-geocoded
 * line would contradict the map underneath it.
 */
internal fun fakeHomeUiState(weather: WeatherSnapshot? = fakeWeatherSnapshot()): HomeUiState =
    HomeUiState.Initial.copy(
        // A fix is the gate for the map surface, the compass, the control column
        // and the self-marker — with none of them a render shows an empty region
        // that looks nothing like the running app.
        location = fakeLocation(latitude = 37.7793, longitude = -122.4193),
        address = fakeAddress(locality = "San Francisco", region = "CA"),
        weather = weather,
        calendar = fakeCalendarSnapshot(),
        musicState = MusicCardState.Playing(fakeNowPlaying()),
        systemStatus = fakeSystemStatus(),
        tripState = fakeTripState(),
    )
