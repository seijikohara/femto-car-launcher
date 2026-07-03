package io.github.seijikohara.femto.ui.home.components.driving

import android.location.Location
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MapPin
import com.composables.icons.lucide.Music
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState
import io.github.seijikohara.femto.data.calendar.CalendarSnapshot
import io.github.seijikohara.femto.data.calendar.DayCell
import io.github.seijikohara.femto.data.calendar.EventItem
import io.github.seijikohara.femto.data.geocoding.ShortAddress
import io.github.seijikohara.femto.data.location.TripState
import io.github.seijikohara.femto.data.music.MusicCardState
import io.github.seijikohara.femto.data.music.NowPlaying
import io.github.seijikohara.femto.data.weather.WeatherCode
import io.github.seijikohara.femto.data.weather.WeatherSnapshot
import io.github.seijikohara.femto.ui.home.HomeAction
import io.github.seijikohara.femto.ui.home.HomeUiState
import io.github.seijikohara.femto.ui.home.components.GlassConfig
import io.github.seijikohara.femto.ui.home.components.TransportRow
import io.github.seijikohara.femto.ui.home.components.glassChrome
import io.github.seijikohara.femto.ui.home.components.glyphIconFor
import io.github.seijikohara.femto.ui.home.components.glyphTintFor
import io.github.seijikohara.femto.ui.locale.SpeedUnit
import io.github.seijikohara.femto.ui.locale.TemperatureUnit
import io.github.seijikohara.femto.ui.locale.fromCelsius
import io.github.seijikohara.femto.ui.locale.fromMetersPerSecond
import io.github.seijikohara.femto.ui.locale.label
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoIcon
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import io.github.seijikohara.femto.ui.theme.bigNumber
import io.github.seijikohara.femto.ui.theme.glanceBody
import io.github.seijikohara.femto.ui.theme.sectionLabel
import io.github.seijikohara.femto.ui.theme.weatherGlyphs
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * The simplified "driving" glance face, drawn over the shared map (the dock is
 * drawn by the caller, outside this composable). Traced from `cockpit-mockup`
 * variant A: a top glass location strip and a bottom glass bar carrying the big
 * speed, a now-playing mini + transport, and a one-line briefing.
 *
 * PR1 keeps the location strip driver-agnostic — the city only; road + heading
 * arrive in PR2. The big speed reuses [SpeedOverlay]'s reading rule (source
 * `tripState.currentSpeedMs`, converted through [speedUnit], em-dash when there
 * is no fix) but shows the raw rounded value: a driving glance does not need the
 * overlay's per-fix EMA smoothing. Every glass surface samples the shared
 * [hazeState] + [glassConfig], so the driving face blurs the same map the
 * cockpit face does.
 */
@Composable
internal fun DrivingOverlays(
    uiState: HomeUiState,
    speedUnit: SpeedUnit,
    temperatureUnit: TemperatureUnit,
    glassConfig: GlassConfig,
    hazeState: HazeState,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
) = Box(modifier = modifier.fillMaxSize()) {
    // Location strip, top. Rendered only when a city has resolved so the face
    // never floats an empty glass pill before the first reverse-geocode lands.
    val city = uiState.address?.displayString().orEmpty()
    if (city.isNotBlank()) {
        LocationStrip(
            city = city,
            hazeState = hazeState,
            glassConfig = glassConfig,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(FemtoDimens.ScreenPadding),
        )
    }

    DrivingBar(
        uiState = uiState,
        speedUnit = speedUnit,
        temperatureUnit = temperatureUnit,
        onAction = onAction,
        hazeState = hazeState,
        glassConfig = glassConfig,
        modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(FemtoDimens.ScreenPadding),
    )
}

@Composable
private fun LocationStrip(
    city: String,
    hazeState: HazeState,
    glassConfig: GlassConfig,
    modifier: Modifier = Modifier,
) = Row(
    modifier =
        modifier
            .glassChrome(MaterialTheme.shapes.large, hazeState, glassConfig)
            .padding(
                horizontal = FemtoDimens.OverlayPaddingHorizontal,
                vertical = FemtoDimens.OverlayPaddingVertical,
            ),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
) {
    FemtoIcon(
        imageVector = Lucide.MapPin,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(FemtoDimens.WeatherGlyphSmall),
    )
    Text(
        text = city,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun DrivingBar(
    uiState: HomeUiState,
    speedUnit: SpeedUnit,
    temperatureUnit: TemperatureUnit,
    onAction: (HomeAction) -> Unit,
    hazeState: HazeState,
    glassConfig: GlassConfig,
    modifier: Modifier = Modifier,
) = Row(
    modifier =
        modifier
            .glassChrome(MaterialTheme.shapes.large, hazeState, glassConfig)
            .padding(
                horizontal = FemtoDimens.OverlayPaddingHorizontal,
                vertical = FemtoDimens.OverlayPaddingVertical,
            ),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(16.dp),
) {
    BigSpeed(location = uiState.location, tripState = uiState.tripState, speedUnit = speedUnit)

    // Now-playing mini + transport, gated on Playing exactly like the music card.
    (uiState.musicState as? MusicCardState.Playing)?.let { playing ->
        BarDivider()
        NowPlayingMini(
            nowPlaying = playing.nowPlaying,
            onAction = onAction,
            modifier = Modifier.weight(1f),
        )
    }

    // Briefing line: next event + weather one-liner. Rendered only when at least
    // one half has data so an empty bar segment never carries a stray divider.
    val nextEvent = uiState.calendar.nextEventOrNull()
    if (nextEvent != null || uiState.weather != null) {
        BarDivider()
        Briefing(nextEvent = nextEvent, weather = uiState.weather, temperatureUnit = temperatureUnit)
    }
}

@Composable
private fun BigSpeed(
    location: Location?,
    tripState: TripState,
    speedUnit: SpeedUnit,
) = Row(
    verticalAlignment = Alignment.Bottom,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
) {
    // Raw rounded speed from the trip's effective speed; em-dash with no fix, so a
    // missing/denied location reads as "unknown" rather than a standstill "0".
    val speedText =
        location
            ?.let { "${speedUnit.fromMetersPerSecond(tripState.currentSpeedMs.toFloat()).roundToInt()}" }
            ?: NO_SPEED_PLACEHOLDER
    Text(
        text = speedText,
        style = MaterialTheme.typography.bigNumber(size = 72.sp),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
    )
    Text(
        text = speedUnit.label(),
        style = MaterialTheme.typography.sectionLabel(12, 0.12f),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        maxLines = 1,
        modifier = Modifier.padding(bottom = 10.dp),
    )
}

@Composable
private fun NowPlayingMini(
    nowPlaying: NowPlaying,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
) = Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
) {
    FemtoIcon(
        imageVector = Lucide.Music,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(FemtoDimens.InlineIconSize),
    )
    Text(
        text = nowPlaying.title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
    )
    TransportRow(
        isPlaying = nowPlaying.isPlaying,
        onCommand = { onAction(HomeAction.Music(it)) },
    )
}

@Composable
private fun Briefing(
    nextEvent: EventItem?,
    weather: WeatherSnapshot?,
    temperatureUnit: TemperatureUnit,
) = Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
) {
    if (nextEvent != null) {
        Text(
            text = nextEvent.briefingLabel(),
            style = MaterialTheme.typography.glanceBody().copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    if (nextEvent != null && weather != null) {
        Text(
            text = "·",
            style = MaterialTheme.typography.glanceBody(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (weather != null) {
        WeatherOneLiner(weather = weather, temperatureUnit = temperatureUnit)
    }
}

@Composable
private fun WeatherOneLiner(
    weather: WeatherSnapshot,
    temperatureUnit: TemperatureUnit,
) = Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
) {
    val glyphs = weatherGlyphs()
    Text(
        text = "${temperatureUnit.fromCelsius(weather.tempC).roundToInt()}${temperatureUnit.label()}",
        style = MaterialTheme.typography.glanceBody().copy(fontWeight = FontWeight.Medium),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
    )
    FemtoIcon(
        imageVector = glyphIconFor(weather.code, weather.isDay),
        contentDescription = null,
        tint = glyphTintFor(weather.code, weather.isDay, glyphs),
        modifier = Modifier.size(FemtoDimens.WeatherGlyphSmall),
    )
}

// A thin vertical hairline between the bar's segments, at the shared divider
// weight the dashboard's other overlays use.
@Composable
private fun BarDivider() =
    VerticalDivider(
        modifier = Modifier.height(40.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = FemtoDimens.DividerAlpha),
    )

// The earliest event to surface in the briefing: the first event of the first
// forward-rolling day that has one. Calendar access / query faults degrade to
// null so a denied or broken calendar shows no briefing rather than a false
// "free" line. Null-safe on the whole snapshot so a not-yet-loaded calendar is
// simply empty. A pure extension so the selection is unit-testable without
// Compose.
private fun CalendarSnapshot?.nextEventOrNull(): EventItem? =
    this
        ?.takeIf { it.hasCalendarAccess && !it.queryFailed }
        ?.days
        ?.firstOrNull { it.hasEvent }
        ?.events
        ?.firstOrNull()

// "10:30 Team standup" for a timed event, the title alone for an all-day one.
// 24-hour notation for v1 — the driving face carries no 12/24h setting yet.
private fun EventItem.briefingLabel(): String = time?.let { "${it.format(BriefingTimeFormatter)} $title" } ?: title

private val BriefingTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

// Em-dash stands in for the live speed with no fix, mirroring SpeedOverlay's
// permissions contract (an unknown speed is never shown as "0").
private const val NO_SPEED_PLACEHOLDER = "—"

@PreviewLightDark
@Preview(name = "Driving face", widthDp = 640, heightDp = 360)
@Composable
private fun DrivingOverlaysPreview() {
    FemtoTheme {
        DrivingOverlays(
            uiState = drivingPreviewUiState(),
            speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
            temperatureUnit = TemperatureUnit.CELSIUS,
            glassConfig = GlassConfig(),
            hazeState = rememberHazeState(),
            onAction = {},
        )
    }
}

// Inline HomeUiState sample — previews cannot import the androidTest / sharedTest
// fixtures, so the driving face builds its own from production types.
private fun drivingPreviewUiState(): HomeUiState =
    HomeUiState.Initial.copy(
        location = Location("preview").apply { speed = 13.2f },
        address = ShortAddress(locality = "Shibuya", region = "Tokyo"),
        tripState = TripState(distanceMeters = 24_400.0, avgSpeedMs = 11.7, currentSpeedMs = 13.2),
        musicState =
            MusicCardState.Playing(
                NowPlaying(
                    title = "Strobe",
                    artist = "deadmau5",
                    album = "For Lack of a Better Name",
                    albumArt = null,
                    isPlaying = true,
                    positionMs = 232_000L,
                    durationMs = 632_000L,
                    packageName = "com.spotify.music",
                ),
            ),
        weather =
            WeatherSnapshot(
                tempC = 18.0,
                apparentTempC = 17.0,
                code = WeatherCode.CLEAR,
                windKmh = 9.6,
                humidityPercent = 58,
                uvIndex = 4.0,
                isDay = true,
                sunrise = LocalTime.of(5, 42),
                sunset = LocalTime.of(19, 14),
                hourly = emptyList(),
                daily = emptyList(),
                fetchedAt = Instant.parse("2026-05-01T05:32:00Z"),
            ),
        calendar =
            CalendarSnapshot(
                today = LocalDate.of(2026, 5, 1),
                weekday = "Friday",
                monthLabel = "May 2026",
                days =
                    listOf(
                        DayCell(
                            date = LocalDate.of(2026, 5, 1),
                            weekdayLetter = "Fri",
                            events = listOf(EventItem(time = LocalTime.of(10, 30), title = "Team standup")),
                        ),
                    ),
                hasCalendarAccess = true,
            ),
    )
