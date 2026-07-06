package io.github.seijikohara.femto.ui.home.components.driving

import android.location.Location
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MapPin
import com.composables.icons.lucide.Music
import com.composables.icons.lucide.Navigation2
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.calendar.CalendarSnapshot
import io.github.seijikohara.femto.data.calendar.DayCell
import io.github.seijikohara.femto.data.calendar.EventItem
import io.github.seijikohara.femto.data.calendar.UpcomingEvent
import io.github.seijikohara.femto.data.calendar.todayEventOrNull
import io.github.seijikohara.femto.data.display.DriverSide
import io.github.seijikohara.femto.data.geocoding.ShortAddress
import io.github.seijikohara.femto.data.location.TripState
import io.github.seijikohara.femto.data.music.MusicCardState
import io.github.seijikohara.femto.data.music.NowPlaying
import io.github.seijikohara.femto.data.weather.WeatherCode
import io.github.seijikohara.femto.data.weather.WeatherSnapshot
import io.github.seijikohara.femto.ui.home.HomeAction
import io.github.seijikohara.femto.ui.home.HomeUiState
import io.github.seijikohara.femto.ui.home.components.BriefingConfig
import io.github.seijikohara.femto.ui.home.components.FemtoVerticalDivider
import io.github.seijikohara.femto.ui.home.components.GlassConfig
import io.github.seijikohara.femto.ui.home.components.MapCompass
import io.github.seijikohara.femto.ui.home.components.MapControlColumn
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
import java.time.format.DateTimeFormatter
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
 *
 * The map controls (compass + zoom/recenter column) mirror the cockpit face's
 * — same composables, same [driverSide]-mirrored side — but stacked together at
 * the pane's vertical centre instead of the cockpit's top-corner compass /
 * mid-edge column split: the top corner on the [driverSide]-unmirrored side is
 * already claimed by the location strip above, so a top-anchored compass would
 * collide with it every time the driver side is not mirrored.
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
    following: Boolean,
    bearingDeg: Float,
    driverSide: DriverSide,
    onBarHeightChange: (Int) -> Unit,
    onRecenter: () -> Unit,
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
                    .padding(outerPad),
        )
    }

    // Map controls render only when the map does (a fix exists) — same gate the
    // cockpit face uses. Stacked (compass above the zoom/recenter column) at the
    // pane's vertical centre on the [driverSide]-mirrored side; see the class KDoc
    // for why this face stacks them instead of cockpit's top-corner + mid-edge split.
    if (uiState.location != null) {
        val mirror = driverSide == DriverSide.LEFT
        Column(
            modifier =
                Modifier
                    .align(if (mirror) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(if (mirror) PaddingValues(end = outerPad) else PaddingValues(start = outerPad)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MapControlsStackGap),
        ) {
            MapCompass(
                bearingDeg = bearingDeg,
                onTap = { onAction(HomeAction.ToggleMapNorthUp) },
                hazeState = hazeState,
                glassConfig = glassConfig,
            )
            MapControlColumn(
                showLocate = true,
                following = following,
                onLocate = onRecenter,
                onZoomIn = { onAction(HomeAction.AdjustMapZoom(1)) },
                onZoomOut = { onAction(HomeAction.AdjustMapZoom(-1)) },
                hazeState = hazeState,
                glassConfig = glassConfig,
            )
        }
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
    // The heading badge is a small arrow rather than a compass-point letter, so
    // it reads at a glance without stopping to parse an abbreviation; north is
    // "up" on this fixed (never map-rotated) strip, so rotating the glyph by
    // the point's angle alone points it at the travel direction. The
    // compass-point label still backs the content description for
    // accessibility parity with the text it replaces.
    if (heading != null) {
        FemtoIcon(
            imageVector = Lucide.Navigation2,
            contentDescription = heading.label(),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(FemtoDimens.InlineIconSize).rotate(heading.degrees),
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
) = Box(
    // The glass card itself stays a full-width floating shelf; only the content
    // row below is capped + centred (see FemtoDimens.DrivingBarContentMaxWidth),
    // so a wide bar does not stretch the anti-reflow spacer between the two
    // anchored clusters into a large empty middle.
    modifier = modifier.glassChrome(MaterialTheme.shapes.large, hazeState, glassConfig),
    contentAlignment = Alignment.Center,
) {
    Row(
        modifier =
            Modifier
                .widthIn(max = FemtoDimens.DrivingBarContentMaxWidth)
                .fillMaxWidth()
                .padding(
                    horizontal = FemtoDimens.OverlayPaddingHorizontal,
                    vertical = FemtoDimens.OverlayPaddingVertical,
                ),
        verticalAlignment = Alignment.CenterVertically,
        // A stable two-cluster layout: the speed + now-playing anchor left, the briefing
        // (event + weather) anchors right, with a flexible spacer between. Everything that
        // changes width as the drive updates — the speed's digit count, the music title,
        // the next event's presence and length — grows into the middle spacer, so the
        // anchored elements never shift. (SpaceBetween re-centred the middle cluster on
        // every such change, and even jumped the music to the far edge when the briefing
        // was absent, because it distributes by child count + width.)
        //
        // No Arrangement.spacedBy here — the gaps are explicit Spacers below so a cluster
        // divider can use its own tighter flank (see DrivingBarDividerFlankGap) instead of
        // the full cluster gap on both sides. spacedBy would insert the divider as one more
        // fully-gapped child, doubling that gap's cost; at the reference 853 dp head unit
        // that extra width, stacked on the now-fixed-width now-playing title, squeezed the
        // flexible event text down to nothing.
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
        // Aligned by baseline (with the event text and WeatherBlock below) so the
        // now-playing title reads on the same visual line as its siblings regardless of
        // the taller transport row / weather glyph beside it. The divider ahead of it is
        // gated on the same `?.let` — never drawn against the flexible middle spacer that
        // follows when there is no music.
        (uiState.musicState as? MusicCardState.Playing)?.let { playing ->
            Spacer(Modifier.width(DrivingBarSegmentGap))
            DrivingBarDivider()
            Spacer(Modifier.width(DrivingBarDividerFlankGap))
            if (showTitle) {
                NowPlayingMini(
                    nowPlaying = playing.nowPlaying,
                    onAction = onAction,
                    modifier = Modifier.alignByBaseline(),
                )
            } else {
                TransportRow(
                    isPlaying = playing.nowPlaying.isPlaying,
                    onCommand = { onAction(HomeAction.Music(it)) },
                )
            }
        }

        Spacer(Modifier.width(DrivingBarSegmentGap))

        // The event block (or, absent a today event, a "No events" label) fills the
        // flexible middle — right-aligned so it groups with the weather — or a spacer
        // holds the gap when the event half is toggled off entirely; either way the
        // left cluster and the weather anchor stay put. Gated by [briefingConfig] and
        // by [showBriefing] (dropped on a narrow pane); the weather block anchors the
        // right and is never squeezed, so the event yields first. TODAY-only: see
        // [todayEventOrNull] — [BriefingConfig.scope] no longer bears on this half.
        val showEventHalf = showBriefing && briefingConfig.showEvent
        val todaysEvent = if (showEventHalf) uiState.calendar.todayEventOrNull(uiState.clock) else null
        val weather = uiState.weather.takeIf { showBriefing && briefingConfig.showWeather }
        if (showEventHalf) {
            if (todaysEvent != null) {
                EventBlock(event = todaysEvent, modifier = Modifier.weight(1f).alignByBaseline())
            } else {
                Text(
                    text = stringResource(R.string.calendar_no_events),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f).alignByBaseline(),
                )
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
        // The event ↔ weather divider requires BOTH halves on screen: with the event
        // half toggled off, the element ahead of the weather block is the flexible
        // (blank) spacer above, and a divider there would float against empty space
        // rather than sit between two segments that are actually on screen. The
        // "No events" label still counts as the event half being on screen.
        if (showEventHalf && weather != null) {
            Spacer(Modifier.width(DrivingBarDividerFlankGap))
            DrivingBarDivider()
        }
        // Aligned by baseline (see above) so the temperature — the meaningful line in
        // this stacked glyph-over-temp block — reads level with the event/music text
        // instead of sitting lower, which a plain centre alignment left it (the block's
        // own height, glyph + temp, is much taller than a single text line, so centring
        // its whole bounding box centred the glyph too and dropped the text below the
        // other clusters' shared line).
        if (weather != null) {
            Spacer(
                Modifier.width(
                    if (showEventHalf) DrivingBarDividerFlankGap else DrivingBarSegmentGap,
                ),
            )
            WeatherBlock(
                weather = weather,
                temperatureUnit = temperatureUnit,
                modifier = Modifier.alignByBaseline(),
            )
        }
    }
}

// Cluster-boundary divider for the driving bar (speed ↔ now-playing, event ↔
// weather), matching the dock's dividers (same FemtoVerticalDivider recipe,
// same 48 dp height) so the bar's chrome reads as one family with the rest of
// the dashboard. Its own flanking gap ([DrivingBarDividerFlankGap], set by the
// call sites above) is deliberately tighter than the ordinary cluster gap — see
// the gating comment there. Never call this beside the flexible middle spacer
// or an absent segment.
@Composable
private fun DrivingBarDivider() = FemtoVerticalDivider(modifier = Modifier.height(DrivingBarDividerHeight))

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
    // Reserve the widest realistic (3-digit) numeral so the rest of the bar never
    // shifts as the speed's digit count changes; the live value right-aligns into
    // the fixed slot (bigNumber's tabular digits keep it steady within a count).
    Box(contentAlignment = Alignment.BottomEnd) {
        Text(
            text = SPEED_NUMERAL_SIZER,
            style = MaterialTheme.typography.bigNumber(size = numeralSize),
            maxLines = 1,
            // Invisible width reservation only: alpha hides it from sight,
            // clearAndSetSemantics keeps the placeholder digits out of the
            // accessibility tree so a screen reader announces just the live speed.
            modifier = Modifier.alpha(0f).clearAndSetSemantics {},
        )
        Text(
            text = speedText,
            style = MaterialTheme.typography.bigNumber(size = numeralSize),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
    Text(
        text = speedUnit.label(),
        style = MaterialTheme.typography.sectionLabel(12, 0.12f),
        // Full-strength onSurfaceVariant: the prior 0.7 alpha read as too faint at a
        // glance next to the hero numeral (onSurface already carries the hierarchy).
        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    NowPlayingArt(
        albumArt = nowPlaying.albumArt,
        modifier = Modifier.size(NowPlayingArtSize),
    )
    Text(
        text = nowPlaying.title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        // Fixed (not capped) width: a widthIn(max = …) let the attached transport
        // row slide horizontally as the title's rendered length changed track to
        // track, since a shorter title only shrank the Row's content instead of
        // reserving the full slot. A fixed width keeps the whole now-playing
        // cluster (art + title + transport) glued in place regardless of the
        // title's length.
        modifier = Modifier.width(NowPlayingTitleWidth),
    )
    TransportRow(
        isPlaying = nowPlaying.isPlaying,
        onCommand = { onAction(HomeAction.Music(it)) },
    )
}

// The now-playing mini's leading album-art thumbnail: a plain crossfade-free Image
// when art is available, or a primary→tertiary gradient with a music glyph when it
// is not — the same idiom as MusicCardMeta's AlbumArt, simplified for this glance
// bar (no hold / grace-window / dissolve: a bar-width thumbnail does not need to
// bridge a staged metadata update the way the full music card's larger art does).
@Composable
private fun NowPlayingArt(
    albumArt: ImageBitmap?,
    modifier: Modifier = Modifier,
) = Box(
    modifier =
        modifier
            .clip(MaterialTheme.shapes.small)
            .then(
                if (albumArt == null) {
                    Modifier.background(
                        Brush.linearGradient(
                            listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary),
                        ),
                    )
                } else {
                    Modifier
                },
            ),
    contentAlignment = Alignment.Center,
) {
    if (albumArt != null) {
        Image(
            bitmap = albumArt,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        FemtoIcon(
            imageVector = Lucide.Music,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(NowPlayingArtPlaceholderGlyphSize),
        )
    }
}

// Weather as a compact stacked block (glyph over the temperature) so both read at
// a glance on the driving bar — the horizontal one-liner made each element too
// small. The glyph sizes up to [FemtoDimens.WeatherGlyphMedium] and the temp to
// titleLarge.
@Composable
private fun WeatherBlock(
    weather: WeatherSnapshot,
    temperatureUnit: TemperatureUnit,
    modifier: Modifier = Modifier,
) = Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(1.dp),
) {
    val glyphs = weatherGlyphs()
    FemtoIcon(
        imageVector = glyphIconFor(weather.code, weather.isDay),
        contentDescription = null,
        tint = glyphTintFor(weather.code, weather.isDay, glyphs),
        modifier = Modifier.size(FemtoDimens.WeatherGlyphMedium),
    )
    Text(
        text = "${temperatureUnit.fromCelsius(weather.tempC).roundToInt()}${temperatureUnit.label()}",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
    )
}

// TODAY's next-up event as a compact stacked block (a small time header over the
// title) so it reads with the same at-a-glance parity as [WeatherBlock]'s
// glyph-over-temp beside it — the plain single-line "HH:mm title" this replaces
// had no visual pairing with the weather block. All-day events show the "All
// day" label (the same string CalendarCard / CalendarPanel use for the same
// case) as the header instead of a time. 24-hour notation for v1 — the driving
// face carries no 12/24h setting yet.
@Composable
private fun EventBlock(
    event: UpcomingEvent,
    modifier: Modifier = Modifier,
) = Column(
    modifier = modifier,
    horizontalAlignment = Alignment.End,
    verticalArrangement = Arrangement.spacedBy(1.dp),
) {
    Text(
        text = event.event.time?.format(EventTimeFormatter) ?: stringResource(R.string.calendar_all_day),
        style = MaterialTheme.typography.sectionLabel(12, 0.12f),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
    Text(
        text = event.event.title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

// Em-dash stands in for the live speed with no fix, mirroring SpeedOverlay's
// permissions contract (an unknown speed is never shown as "0").
private const val NO_SPEED_PLACEHOLDER = "—"

// The widest realistic speed (three tabular digits) the numeral slot reserves so
// the bar's layout is stable across 1–3 digit speeds.
private const val SPEED_NUMERAL_SIZER = "000"

// Bar breakpoints on the pane width. Below [BarTitleBreakpoint] the big speed shrinks
// and the now-playing title drops (transport only) so the fixed transport row fits;
// [BarBriefingBreakpoint] is where the briefing has room to join the speed + music.
private val BarTitleBreakpoint = 720.dp
private val BarBriefingBreakpoint = 840.dp

// Hero-speed numeral sizes: the full glance size on wide bars, a compact size on the
// narrow bar so the big number and the fixed-width transport row coexist.
private val BigSpeedFontFull = 72.sp
private val BigSpeedFontCompact = 40.sp

// Fixed width for the now-playing title so it always ellipsizes to the same slot
// and the transport row stays attached to it as one cluster instead of sliding
// horizontally as the title's rendered length changes between tracks. (The event
// text alongside it needs no such fix — it takes the flexible middle and
// ellipsizes into whatever the anchors leave, so there is no attached sibling to
// keep in place.)
//
// Trimmed down from the old widthIn(max = 200.dp) cap: that cap was rarely fully
// claimed (most titles render well under 200 dp), so the flexible event text next
// to it in practice got most of that width back. Reserving it unconditionally
// (this dimen's whole point) claims it every time instead, and at the reference
// 853 dp head unit that left only a couple of characters for the event text —
// worse than the modest extra title truncation this smaller width costs on a
// long title.
private val NowPlayingTitleWidth = 150.dp

// The now-playing mini's album-art thumbnail: a touch larger than the inline
// icon it replaces so a small piece of real artwork still reads as art rather
// than another glyph-sized icon.
private val NowPlayingArtSize = 22.dp

// Placeholder music glyph inside NowPlayingArt when there is no album art —
// smaller than the art slot itself so it reads as a centred icon, not a crop.
private val NowPlayingArtPlaceholderGlyphSize = 14.dp

// Gap between clusters (speed ↔ now-playing, now-playing ↔ briefing middle);
// applied as explicit Spacers rather than Arrangement.spacedBy (see DrivingBar)
// so a cluster divider can substitute its own tighter DrivingBarDividerFlankGap
// on either side instead of this full gap twice over.
private val DrivingBarSegmentGap = 20.dp

// Cluster-divider height, matching DashboardDock's HorizontalDockDivider so the
// bar's chrome reads as the same family as the dock's.
private val DrivingBarDividerHeight = 48.dp

// Gap flanking a cluster divider (speed ↔ now-playing, event ↔ weather) — tighter
// than [DrivingBarSegmentGap] so inserting the divider costs roughly the same
// total width as the plain gap it replaces (2 * flank + the 1 dp rule ≈ the
// original gap), instead of adding a second full segment gap on top of it. At
// the reference 853 dp head unit, the now-playing title's new fixed width
// already claims most of the room the flexible event text used to get; a
// double-gapped divider was enough to squeeze that text down to nothing.
private val DrivingBarDividerFlankGap = 10.dp

// Gap between the stacked compass and zoom/recenter column (see the class KDoc
// for why this face stacks them). Matches the spacing MapControlsPreview
// (MapControls.kt) already establishes between the same two composables.
private val MapControlsStackGap = 12.dp

// The event block's time header — bare "HH:mm", matching [WeatherBlock]'s plain
// numeric temperature (no day-relative prefix: the event is always today's, by
// construction of [todayEventOrNull]).
private val EventTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

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
            following = true,
            bearingDeg = 0f,
            driverSide = DriverSide.RIGHT,
            onBarHeightChange = {},
            onRecenter = {},
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
