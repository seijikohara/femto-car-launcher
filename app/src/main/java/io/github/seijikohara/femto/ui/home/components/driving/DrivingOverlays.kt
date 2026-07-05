package io.github.seijikohara.femto.ui.home.components.driving

import android.location.Location
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
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
import io.github.seijikohara.femto.data.calendar.UpcomingEvent
import io.github.seijikohara.femto.data.calendar.nextUpcomingEventOrNull
import io.github.seijikohara.femto.data.geocoding.ShortAddress
import io.github.seijikohara.femto.data.location.TripState
import io.github.seijikohara.femto.data.music.MusicCardState
import io.github.seijikohara.femto.data.music.NowPlaying
import io.github.seijikohara.femto.data.weather.WeatherCode
import io.github.seijikohara.femto.data.weather.WeatherSnapshot
import io.github.seijikohara.femto.ui.home.HomeAction
import io.github.seijikohara.femto.ui.home.HomeUiState
import io.github.seijikohara.femto.ui.home.components.BriefingConfig
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
import io.github.seijikohara.femto.ui.theme.sectionLabel
import io.github.seijikohara.femto.ui.theme.weatherGlyphs
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt

/**
 * The simplified "driving" glance face, drawn over the shared map (the dock is
 * drawn by the caller, outside this composable). Traced from `cockpit-mockup`
 * variant A: a top glass location strip and a bottom glass bar carrying the big
 * speed, a now-playing mini + transport, and a one-line briefing.
 *
 * The location strip shows the current road name and GPS-fix heading alongside
 * the city — either may be absent (an East-Asian address carries no road; a fix
 * with no heading omits the badge) and the strip degrades gracefully. The big
 * speed reuses [SpeedOverlay]'s reading rule (source
 * `tripState.currentSpeedMs`, converted through [speedUnit], em-dash when there
 * is no fix) but shows the raw rounded value: a driving glance does not need the
 * overlay's per-fix EMA smoothing. Every glass surface samples the shared
 * [hazeState] + [glassConfig], so the driving face blurs the same map the
 * cockpit face does.
 *
 * The face is a [BoxWithConstraints] so the bottom bar reflows against the actual
 * pane width, and it takes the responsive [outerPad] its host computes so its
 * margins match the cockpit and dock. It reports the bar's glass-card height via
 * [onBarHeightChange] so the host can reserve a bottom safe band exactly tall
 * enough to keep the self-marker above the bar.
 */
@Composable
internal fun DrivingOverlays(
    uiState: HomeUiState,
    speedUnit: SpeedUnit,
    temperatureUnit: TemperatureUnit,
    glassConfig: GlassConfig,
    hazeState: HazeState,
    outerPad: Dp,
    briefingConfig: BriefingConfig,
    onBarHeightChange: (Int) -> Unit,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
) = BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    // Location strip, top: road name + city, plus a heading badge. Rendered only
    // when a road or city has resolved so the face never floats an empty glass
    // pill before the first reverse-geocode lands.
    val road = uiState.address?.road
    val city = uiState.address?.locality?.takeIf { it.isNotBlank() } ?: uiState.address?.displayString().orEmpty()
    // The GPS-fix bearing (not the map camera bearing, [DashboardScaffold]'s
    // `bearingDeg`). A 0f reading is indistinguishable from an unset bearing on
    // many fixes, so it is treated as "no heading" rather than due north.
    val heading = uiState.location?.takeIf { it.hasBearing() && it.bearing != 0f }?.let {
        compassDirectionOf(
            it.bearing,
        )
    }
    if (road != null || city.isNotBlank()) {
        LocationStrip(
            road = road,
            city = city,
            heading = heading,
            hazeState = hazeState,
            glassConfig = glassConfig,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(outerPad)
                    // Reserve the top-right for the PassengerPill (a top-level sibling
                    // anchored top-end on the driving face) so a long road + city
                    // ellipsizes before reaching it rather than running underneath.
                    .widthIn(max = (maxWidth - PassengerPillReserve).coerceAtLeast(0.dp)),
        )
    }

    DrivingBar(
        uiState = uiState,
        speedUnit = speedUnit,
        temperatureUnit = temperatureUnit,
        briefingConfig = briefingConfig,
        onAction = onAction,
        hazeState = hazeState,
        glassConfig = glassConfig,
        // Below the narrow breakpoint the big speed + full transport already fill the
        // bar, so the now-playing title and the briefing are dropped in turn as the
        // pane widens: transport-only, then title, then the briefing.
        showTitle = maxWidth >= BarTitleBreakpoint,
        showBriefing = maxWidth >= BarBriefingBreakpoint,
        modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(outerPad)
                // Report the bar's glass-card height (outerPad is applied above this
                // node, so it is excluded) so the host reserves the marker's bottom band.
                .onSizeChanged { onBarHeightChange(it.height) },
    )
}

@Composable
private fun LocationStrip(
    road: String?,
    city: String,
    heading: CompassDirection?,
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
        modifier = Modifier.size(FemtoDimens.InlineIconSize),
    )
    Text(
        text = listOfNotNull(road, city.takeIf { it.isNotBlank() }).joinToString(" · "),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        // Weight lets the road + city ellipsize within the strip's capped width while
        // the fixed heading badge beside it always keeps its two letters.
        modifier = Modifier.weight(1f, fill = false),
    )
    // The heading badge never wraps or shrinks — a two-letter point label always
    // fits, and truncating it would leave an ambiguous single letter behind.
    if (heading != null) {
        Text(
            text = heading.label(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Visible,
        )
    }
}

@Composable
private fun DrivingBar(
    uiState: HomeUiState,
    speedUnit: SpeedUnit,
    temperatureUnit: TemperatureUnit,
    briefingConfig: BriefingConfig,
    onAction: (HomeAction) -> Unit,
    hazeState: HazeState,
    glassConfig: GlassConfig,
    showTitle: Boolean,
    showBriefing: Boolean,
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
    // Spread the segments across the full bar: big speed pinned left, the briefing
    // (widest panes only) pinned right, the now-playing cluster centred between them.
    // The bar reads balanced with or without music instead of huddling to the left,
    // and every child keeps its intrinsic width so none collapses to zero.
    horizontalArrangement = Arrangement.SpaceBetween,
) {
    // The narrow bar shrinks the hero numeral so the fixed-width transport row still
    // fits beside it; the wider bars keep the full-size speed.
    BigSpeed(
        location = uiState.location,
        tripState = uiState.tripState,
        speedUnit = speedUnit,
        numeralSize = if (showTitle) BigSpeedFontFull else BigSpeedFontCompact,
    )

    // Now-playing, gated on Playing exactly like the music card. A narrow bar has no
    // room for the title beside the big speed, so it keeps just the transport controls
    // (playback stays operable); wider bars add the music glyph + ellipsizing title.
    (uiState.musicState as? MusicCardState.Playing)?.let { playing ->
        if (showTitle) {
            NowPlayingMini(nowPlaying = playing.nowPlaying, onAction = onAction)
        } else {
            TransportRow(
                isPlaying = playing.nowPlaying.isPlaying,
                onCommand = { onAction(HomeAction.Music(it)) },
            )
        }
    }

    // Briefing line: next event + weather one-liner. Only the widest bars carry it —
    // it needs room the big speed + transport leave on a narrow pane — and only when
    // at least one enabled half has data. Each half is independently gated by
    // [briefingConfig]: the event is looked up within its scope only when enabled, and
    // the weather half is dropped when disabled, so a fully-off briefing renders
    // nothing (and no stray divider — Briefing draws the separator only between two
    // present halves).
    val upcomingEvent =
        if (briefingConfig.showEvent) {
            uiState.calendar.nextUpcomingEventOrNull(uiState.clock, briefingConfig.scope)
        } else {
            null
        }
    val weather = uiState.weather.takeIf { briefingConfig.showWeather }
    if (showBriefing && (upcomingEvent != null || weather != null)) {
        Briefing(
            upcomingEvent = upcomingEvent,
            today = uiState.clock.date,
            weather = weather,
            temperatureUnit = temperatureUnit,
        )
    }
}

@Composable
private fun BigSpeed(
    location: Location?,
    tripState: TripState,
    speedUnit: SpeedUnit,
    numeralSize: TextUnit,
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
        style = MaterialTheme.typography.bigNumber(size = numeralSize),
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
        // Cap the title and let it ellipsize so the transport stays attached to it as
        // one compact cluster rather than drifting to the far edge on a wide bar.
        modifier = Modifier.widthIn(max = NowPlayingTitleMaxWidth),
    )
    TransportRow(
        isPlaying = nowPlaying.isPlaying,
        onCommand = { onAction(HomeAction.Music(it)) },
    )
}

@Composable
private fun Briefing(
    upcomingEvent: UpcomingEvent?,
    today: LocalDate,
    weather: WeatherSnapshot?,
    temperatureUnit: TemperatureUnit,
) = Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
) {
    if (upcomingEvent != null) {
        Text(
            text = briefingLabel(event = upcomingEvent.event, eventDate = upcomingEvent.date, today = today),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // Cap the event so a long title ellipsizes instead of pushing the weather
            // off a tight bar; the weather half keeps its intrinsic width.
            modifier = Modifier.widthIn(max = BriefingEventMaxWidth),
        )
    }
    if (upcomingEvent != null && weather != null) {
        Text(
            text = "·",
            style = MaterialTheme.typography.titleMedium,
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
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
    )
    FemtoIcon(
        imageVector = glyphIconFor(weather.code, weather.isDay),
        contentDescription = null,
        tint = glyphTintFor(weather.code, weather.isDay, glyphs),
        modifier = Modifier.size(FemtoDimens.InlineIconSize),
    )
}

// Em-dash stands in for the live speed with no fix, mirroring SpeedOverlay's
// permissions contract (an unknown speed is never shown as "0").
private const val NO_SPEED_PLACEHOLDER = "—"

// Right-side room the location strip leaves for the PassengerPill (top-end on the
// driving face): the pill's own width plus its edge margin, so a long road + city
// ellipsizes before reaching it.
private val PassengerPillReserve = 190.dp

// Bar breakpoints on the pane width. Below [BarTitleBreakpoint] the big speed shrinks
// and the now-playing title drops (transport only) so the fixed transport row fits;
// [BarBriefingBreakpoint] is where the briefing has room to join the speed + music.
private val BarTitleBreakpoint = 720.dp
private val BarBriefingBreakpoint = 840.dp

// Hero-speed numeral sizes: the full glance size on wide bars, a compact size on the
// narrow bar so the big number and the fixed-width transport row coexist.
private val BigSpeedFontFull = 72.sp
private val BigSpeedFontCompact = 40.sp

// Caps that keep the now-playing title and the briefing event ellipsizing instead of
// stretching the bar or shoving the weather / transport off a tight pane.
private val NowPlayingTitleMaxWidth = 220.dp
private val BriefingEventMaxWidth = 200.dp

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
            outerPad = FemtoDimens.ScreenPadding,
            briefingConfig = BriefingConfig(),
            onBarHeightChange = {},
            onAction = {},
        )
    }
}

// Inline HomeUiState sample — previews cannot import the androidTest / sharedTest
// fixtures, so the driving face builds its own from production types.
private fun drivingPreviewUiState(): HomeUiState =
    HomeUiState.Initial.copy(
        location =
            Location("preview").apply {
                speed = 13.2f
                bearing = 45f
            },
        address = ShortAddress(locality = "Shibuya", region = "Tokyo", road = "Oak St"),
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
