# Dashboard Device-Feedback Redesign — Design Spec

- Date: 2026-06-04
- Status: Approved (design + PR plan approved by the maintainer on 2026-06-04)
- Source: real-device testing on an aftermarket AI box driving a
  **factory 9-inch** head-unit display. The maintainer reported 19 feedback
  items (16 in the first message + 3 follow-ups) covering visual design, layout
  responsiveness, two data bugs, and several new features.
- Investigation: a multi-agent read-only audit mapped every item to its code and
  root cause (see the session transcript). This spec records the approved design.

## 1. Goals and non-goals

**Goals.** Resolve all reported feedback while honoring the project rule SSOT
(`CLAUDE.md`): keep the Material 3 + automotive-overrides foundation, keep sizing
in `FemtoDimens`, keep the UDF three-Composable architecture, add no dependency
or permission without the proper discipline, and ship each change as an
independently-green, squash-merged PR.

**Non-goals.** No driving-lockout gate (per `CLAUDE.md#driving-lockout`). No
Navigation-Compose dependency. No Google Maps / GMS reintroduction. No new
runtime permission unless a feature strictly needs one (cellular and assistant
are designed to need none).

## 2. Decided directions (maintainer-approved 2026-06-04)

1. **Typography** — lighten the heavy M3 roles by one notch; revise the
   "Bold Minimal" design SSOT (`CLAUDE.md#design-system` and
   `docs/design/dashboard-v2-mockup.html`) to match. Body/label stay Normal.
2. **Map blank** — the on-device symptom was a **grey/black rectangle** (a GL
   surface that produced no frames), not the "map unavailable" fallback text.
   Root cause is therefore EGL-config rejection and/or `TextureView` compositing
   on the real GPU — not the `location == null` gate, not the network path, not
   R8 (minify is off). Fix targets the renderer surface/EGL config.
3. **In-app Settings** — units, theme (light/dark/system; **dynamic color stays
   on**), font, and a permissions / OS-settings shortcut section.
4. **Cellular indicator** — **connectivity-only** via `ConnectivityManager`
   `TRANSPORT_CELLULAR`; **no new permission**; auto-hide when the device has no
   telephony feature.
5. **Voice assistant** — launch the device's default assist app via
   `Intent.ACTION_ASSIST` (fallback `ACTION_VOICE_COMMAND`); **no permission**.
6. **App drawer** — present as a Material 3 `ModalBottomSheet` overlay on top of
   the dashboard; **no new dependency** (material3 is on the Compose BOM).
7. **Navigation** — keep the single-Activity, state-based router; replace the
   lone `showDrawer` boolean with a small **sealed destination state**; the
   drawer stays an overlay orthogonal to the destination.
8. **Music blank** — the maintainer saw no permission prompt, confirming the
   notification-listener access is simply **not granted**. The wiring is correct;
   improve discoverability (clearer CTA + first-run guidance) and add a
   metadata-edge safety net.
9. **Target hardware** — the real surface is the car's **factory 9-inch
   display**, into which an aftermarket AI box injects Android over wired
   CarPlay/AA at the **factory projection resolution (adaptive)**. Most likely
   **1280×720 (16:9) ≈ 640×360 dp at ~xhdpi**, with **1280×480 (8:3 ultra-wide)
   ≈ 640×240 dp** as a real alternative; the box's `Minimum Width` dp is
   user-tunable (~600 dp default). The layout is made **fully responsive across
   ~480–720 dp width**; previews target 640×360 dp (16:9), 640×240 dp (ultra-wide
   — the case that exposes vertical clipping), and a portrait case. The 640×360 dp
   budget directly explains the reported clipping: after the 80 dp footer + system
   insets + 24 dp screen padding, the two-pane row has ≈230 dp, so the fixed
   224 dp top row starves the music card to near-zero height.

## 3. Cross-cutting constraints (cited from CLAUDE.md)

- `#design-system` / `#automotive-overrides`: sizing reads from `FemtoDimens`;
  tap targets ≥ 64 dp (`MinTouchTarget`), body ≥ 18 sp (`MinBodyTextSize`) —
  cards may relax the 18 sp floor only for the sanctioned glance-metadata strips;
  card/surface elevation 0 dp; no ad-hoc `TextStyle`/hex outside the theme.
- `#compose-architecture`: UDF (state down via `UiState`, events up via
  `(Action) -> Unit`); three-Composable shape for stateful screens; `modifier`
  is the first non-state param; `FemtoTheme` wrapped once at the entry point.
- `#compose-performance`: `collectAsStateWithLifecycle`; stable `key`s; trust
  Compose 2 stability inference.
- `#dependencies`: version-catalog first; Compose via BOM.
- `#permissions`: every `<uses-permission>` via the `add-launcher-permission`
  skill with a commit-body justification and an audit-table entry.
- `#ssot-dry`: each value/fact in one place — new thresholds become named
  constants, new dimensions become `FemtoDimens` tokens, repeated literals
  (e.g. the 12 dp section gap) are promoted to tokens.
- `#code-style`: English artifacts, Conventional Commits, no Claude attribution.

## 4. Per-item design

Each item lists: root cause → approach → primary files → tests.

### Speed / Trip / Location

**[8] Impossible speed corrupting Distance/Avg (bug).**
Root cause: `LocationRepository.rawLocationFlow()` forwards both `GPS_PROVIDER`
and `NETWORK_PROVIDER` fixes verbatim through one listener; two consecutive fixes
from different providers can be milliseconds apart in `elapsedRealtimeNanos` but
tens-to-hundreds of metres apart in position. `TripRepository.accrue()` derives
speed as `previous.distanceTo(current) / deltaSeconds` for speed-less fixes; a
`deltaSeconds ≈ 0.02` passes the `MIN_DELTA_SECONDS = 0.001` gate and yields
thousands of km/h, which is then accumulated into `totalMeters` / `totalSeconds`,
permanently inflating Avg and Distance. No upper bound exists.
Approach (defence-in-depth, thresholds as named consts):
- Require a meaningfully larger `deltaSeconds` (e.g. `MIN_TRUSTWORTHY_DELTA ≈
  0.5 s`) before trusting a **position-derived** speed.
- Reject any fix whose implied speed exceeds a plausible ceiling
  (`MAX_PLAUSIBLE_SPEED_MS ≈ 110 m/s ≈ 400 km/h`): do **not** accrue its metres
  or seconds, do **not** publish it as `currentSpeedMs`.
- Prefer GPS for trip math: tag fixes by provider in `LocationRepository` (or
  filter in `TripRepository`) and ignore `NETWORK_PROVIDER` for distance/speed.
  Network fixes must still feed map centering / weather / geocoding unchanged.
Files: `TripRepository.kt`, `LocationRepository.kt` (provider tag), `TripState.kt`.
Tests: small-positive-dt with a position delta must not yield a huge speed;
over-ceiling speed rejected from all three metrics; a network fix immediately
after a GPS fix must not corrupt totals. Fixtures via `fakeLocation`.

**[6] Speed panel width fluctuates with content.**
Root cause: `SpeedOverlay` outer `Column` uses `Modifier.width(IntrinsicSize.Max)`
and the metric cells have no reserved width, so digit-count changes reflow the
card (tabular figures fix per-digit width, not overall string width).
Approach: reserve a stable width per variable cell (`widthIn(min = …)` from a new
`FemtoDimens` token sized for the worst case implied by the [8] ceiling) or fix
the overlay width; re-establish the divider boxing if `IntrinsicSize.Max` is
dropped. Files: `SpeedOverlay.kt`, `FemtoDimens.kt`.

**[19] Reset button for Distance/Avg.**
Root cause/extension point: `TripRepository` totals are in-memory mutable fields
(`totalMeters`, `totalSeconds`, `currentSpeedMs`); the output is a **cold flow**
that only emits on the next location fix, so a naive reset would not update the
UI until a fix arrives.
Approach: add `TripRepository.reset()` that zeroes the fields and signals an
internal `MutableSharedFlow` trigger `merge`d into `stateFlow()` (mirrors the
`WeatherRepository` clock-trigger pattern) so a zeroed `TripState` is pushed
immediately; leave `lastLocation` in place. Wire UDF:
`HomeAction.ResetTrip` → inject `resetTrip: () -> Unit` into `HomeViewModel`
(mirror `sendMusicCommand`, wired `resetTrip = trip::reset` in the factory) →
`SpeedOverlay` gains `onReset: () -> Unit` → call site in `DashboardScaffold`
passes `onReset = { onAction(HomeAction.ResetTrip) }`. Button: top-right of the
overlay, `Lucide.RotateCcw` (~20 dp glyph) inside a 64 dp hit area following the
`DashboardFooter.NavButton` box/glyph pattern.
Tests: `TripRepositoryTest` — reset emits a zeroed `TripState` without a further
fix; accrual resumes after reset.

### Map

**[7] OpenFreeMap/MapLibre blank (grey/black rectangle).**
Root cause (ranked, given the grey-rectangle symptom): #1 EGL config rejection on
the head-unit GPU — `translucentTextureSurface(false)` forces an opaque RGB
config some drivers reject; #2 `TextureView` compositing on a vendor ROM that
does not hardware-composite the launcher window. Ruled out by evidence: location
gate (#user saw a rectangle, not the fallback text), network/style (fallback
would show on failure), R8 (minify off), lifecycle (correctly forwarded), API key
(OpenFreeMap is keyless).
Approach (cannot be reproduced on the local/emulator GL stack — confirm
on-device): switch the map to a `SurfaceView` (`textureMode(false)`) and relax
the EGL config (`translucentTextureSurface(true)`), keeping the OpenGL ES
backend. Keep the rounded-corner/overlay treatment working with a SurfaceView
(clip the parent / order overlays). If the rectangle persists on-device, the
follow-up is to swap the MapLibre backend to the default (Vulkan-capable)
artifact via the version catalog. Files: `MapPanel.kt`, possibly
`gradle/libs.versions.toml` (follow-up only). Update
`MEMORY/map_maplibre_openfreemap.md` with the outcome.

### Typography

**[1] Text too bold.**
Root cause: deliberate Bold Minimal weights in `Type.kt`
(`femtoTypography()` display=ExtraBold(800)/headline=Bold(700)/title=SemiBold,
`bigNumber()`=ExtraBold, `sectionLabel()`=Bold) plus ~18 ad-hoc call-site
`fontWeight` overrides re-asserting heavy weights.
Approach: drop heavy roles one notch — Black→ExtraBold, ExtraBold→Bold,
Bold→SemiBold, SemiBold→Medium; `bigNumber()`→Bold, `sectionLabel()`→SemiBold;
body/label unchanged (Normal/Medium). Audit the call-site overrides and remove
those that merely duplicate the (now lighter) role default; keep the few paired
with sanctioned sub-18 sp glance metadata. **Update the SSOT**:
`CLAUDE.md#design-system` wording and the `font-weight` values in
`docs/design/dashboard-v2-mockup.html`. Files: `Type.kt`, the six
`ui/home/components/*` with overrides, `CLAUDE.md`, the mockup. No tests assert
weights.

### Layout / spacing / responsiveness

**[2][3] Excessive whitespace between and inside panels.**
Root cause: `ScreenPadding=24dp` and a single overloaded `PaneGap=16dp` (reused
for outer pane gap, info-pane vertical gap, and card-to-card gap), plus
`CardPadding=16dp` and a repeated literal `12dp` section gap — all transcribed
from the fixed 1280×720 mockup.
Approach: split `PaneGap` into distinct tokens (`OuterGap` / `PaneGap` /
`CardGap`); promote the 12 dp section gap to a `FemtoDimens` token; tighten the
values; scale gaps down at the Compact size class. Files: `FemtoDimens.kt`,
`DashboardScaffold.kt`, the cards.

**[4][16] Panels clipped; layout not responsive.**
Root cause: the info-pane top row is pinned to a fixed `TopRowHeight=224dp` and
the music card takes leftover `weight(1f)`; every card is a `Surface` (which
clips) wrapping a non-scrolling `Column` of intrinsic-height children. On a
viewport shorter/denser than the mockup (minus system-bar insets), content
overflows the clip. The only adaptive code anywhere is the footer's
`CompactFooterWidth` drop.
Approach: introduce a top-level size strategy via `BoxWithConstraints` (hand-rolled
breakpoints to avoid a new dependency): (a) replace the fixed top-row height with
weight-based vertical distribution so the top row and music card share available
height; (b) choose portrait (stacked map-over-info) vs landscape (side-by-side)
by aspect ratio; (c) scale `BigNumberFontSize` / `MusicArtSize` / gaps off the
size class; (d) add `maxLines` + `TextOverflow.Ellipsis` (and/or compress-to-fit
spacers) so content never silently clips; (e) add `@PreviewLightDark` at
640×360 dp (16:9), 640×240 dp (8:3 ultra-wide — the vertical-clip case), and a
portrait size. The 64 dp / 18 sp floors hold in every branch.
Files: `DashboardScaffold.kt`, `FemtoDimens.kt`, all four cards, previews.
The **music card empty/connect-state clip [4-music]** is fixed here: give
`ConnectState` the slack-absorbing spacer structure `EmptyState` already uses, and
add `maxLines`/ellipsis to both hints.

### Footer / status

**[9] Footer too tall.** Reduce `FooterHeight` 80→~72 dp (the 64 dp button box is
the hard floor; ~8 dp slack remains). Update the token doc comment.

**[10] Remove Home button.** Delete the Home `NavButton`; remove the now-dead
`active`/`ActiveIndicator` machinery; recompute `CompactFooterWidth`.

**[14] Cellular indicator.** Add a `cellularConnected` (or level-less connected)
field to `SystemStatus`; add a `cellularFlow` (`ConnectivityManager`
`TRANSPORT_CELLULAR`, mirroring `wifiFlow`) as a 4th source in the combine; render
a `StatusIcon`; **auto-hide** when `PackageManager.FEATURE_TELEPHONY` is absent.
No new permission.

**[15] Charging-status text under the battery.** The `charging` boolean and
`batteryPercent` already exist. Restructure `BatteryIndicator` so a small
"Charging" caption sits with the percent (consistent with the footer's existing
13 sp glance allowance), replacing the inline "⚡" glyph; add a `status_charging`
string. Must be reconciled with [9]'s height (keep the cluster within the
trimmed footer height).
Files: `DashboardFooter.kt`, `SystemStatus.kt`, `SystemStatusRepository.kt`,
`strings.xml`.

### Navigation / drawer / settings / assistant

**[11] App drawer as a sheet.** Render `HomeRoute` always; overlay a
`ModalBottomSheet` hosting the existing `AppDrawerScreen` grid; `onDismissRequest`
replaces the manual `BackHandler`. Reuse `AppDrawerUiState`/`AppsRepository`.
Files: `MainActivity.kt`, `ui/drawer/*`.

**[13] In-app Settings screen + persistence.** Replace `showDrawer: Boolean` with
a sealed `AppDestination` (Home / Settings) in `MainActivity`; the drawer stays a
sheet flag. Scaffold `ui/settings/` (Route + Screen + ViewModel + UiState via the
`add-viewmodel` skill). Repoint `HomeAction.OpenSettings` to a new
`HomeEvent.OpenInAppSettings` (keep a system-settings affordance inside the new
screen). Add `UnitPreferences` and `ThemePreferences` DataStores (modeled on
`FontPreferences`); thread a unit override over the Locale default where
`speedUnitFor()`/`temperatureUnitFor()` are read; route the theme override
(light/dark/system) through `FemtoTheme` (dynamic color stays on). Settings
contents: units, theme, font, permissions/OS-settings links.
Files: `MainActivity.kt`, `ui/settings/*`, `data/*Preferences.kt`, `FemtoTheme.kt`,
`HomeScreen.kt`, `HomeAction/Event/ViewModel`.

**[12] Voice assistant button.** Add an 8th footer `NavButton` (`Lucide.Mic`) →
`HomeAction.OpenAssistant` → `HomeEvent.OpenAssistant` → `MainActivity` launches
`ACTION_ASSIST` (fallback `ACTION_VOICE_COMMAND`) via the existing
`startActivityIfResolved` helper. Watch the footer width budget (it benefits from
[10] freeing a slot); hide on narrow units like the status cluster if needed.

### Calendar

**[5] Date selection (+ [4-calendar] clip).** Extend the model to carry per-day
events for the 6-day window: `DayCell.events: List<EventItem>` (derive `hasEvent`
from it); group `CalendarRepository.readWindow` rows by day; relax the global
`EVENT_LIST_LIMIT` future-only filter (keep a per-day cap). Add `selectedDate`
(card-local `rememberSaveable` defaulting to today; auto-reset to today on day
rollover). Make strip cells `clickable` (sub-64 dp is accepted for the in-card
mini-calendar — a deliberate exception noted here); drive the highlight off
`selectedDate`; the events area shows the selected day; the big-day header stays
on **today**. Bottom-anchor the events section and add overflow handling so a busy
selected day cannot worsen the clip. Files: `CalendarCard.kt`,
`CalendarSnapshot.kt`, `CalendarRepository.kt`. Tests: per-day grouping; selection
changes the shown events.

### Music

**[17] Not showing while playing.** Confirmed: notification-listener access not
granted (the maintainer saw no prompt; the screenshot is the `NeedsPermission`
state). Wiring is correct. Improve discoverability (clearer CTA copy / first-run
hint that the card is tappable to grant access). Add a safety net for the
`metadata == null` edge so a granted-but-metadata-less session degrades to the
"nothing playing" state cleanly rather than appearing broken.
Files: `MusicCard.kt` (ConnectState copy), `MusicSessionRepository.kt` (edge),
`strings.xml`.

**[18] Source app icon (top-right) + tap to launch.** `NowPlaying.packageName`
already exists. Resolve the source icon in `MusicSessionRepository` (add
`sourceIcon: ImageBitmap?` to `NowPlaying`, mirroring `albumArt`). In
`MusicCard.PlayingState`, add a top-right app-icon button (64 dp hit area, smaller
visible icon). Launch via a new `HomeAction.LaunchMusicSource(packageName)` that
resolves the package to its launcher `ComponentName` (`LauncherApps`) and reuses
the existing `HomeEvent.LaunchComponent` → `AppsRepository.launch` path.
Files: `NowPlaying.kt`, `MusicSessionRepository.kt`, `MusicCard.kt`,
`HomeAction/Event/ViewModel`, `DashboardScaffold.kt`.

## 5. PR plan (sequential, squash-merge, rebase as needed)

Each PR builds green (`spotlessCheck` / `test` / `lint` / `assembleDebug`, plus
`compileDebugAndroidTestKotlin` when androidTest changes) before squash-merge.

| PR | Scope | Items | Depends on |
|----|-------|-------|------------|
| 1 | Trip correctness + stable speed width + Distance/Avg reset | 8, 6, 19 | — |
| 2 | Map renderer GPU compatibility (SurfaceView + EGL relax) | 7 | — |
| 3 | Typography one-notch lighten + SSOT/mockup update | 1 | — |
| 4 | Spacing tokens + responsive layout + card clipping (incl. music empty-state) | 2, 3, 4, 16 | — |
| 5 | Footer redesign + status cluster (height, remove Home, cellular, charging text) | 9, 10, 14, 15 | — |
| 6 | Navigation backbone (sealed destination) + app drawer → ModalBottomSheet | 11 | — |
| 7 | In-app Settings screen + Unit/Theme preferences + gear rewire | 13 | 6 |
| 8 | Voice assistant button | 12 | 5 |
| 9 | Calendar date selection (+ calendar clip) | 5 | 4 |
| 10 | Music session UX (discoverability + metadata edge) + source app icon/launch | 17, 18 | 4 |

Granularity may be revisited (e.g. PR4 split into tokens vs responsive) if a diff
grows too large to review.

## 6. Testing strategy

- JVM unit tests for all data-layer changes (`TripRepository` thresholds + reset,
  cellular flow shape, calendar per-day grouping, preferences) via the existing
  Robolectric + Turbine + `runTest` pattern; fixtures through `testfixtures/`.
- Compose UI tests (`createComposeRule`, wrapped in `FemtoTheme`) for new
  interactions (reset button invokes `onReset`, strip-cell selection, settings
  controls, assistant/source-launch callbacks).
- New `@PreviewLightDark` at 640×360 dp (16:9), 640×240 dp (8:3 ultra-wide), and
  a portrait size to lock the responsive behavior in Studio.
- The map fix (PR2) cannot be verified locally; it is validated by on-device
  install. State this explicitly in the PR.

## 7. Open / confirm-on-device

- Exact native projection resolution on this car's factory 9-inch display
  (research narrowed it to 1280×720 / 16:9 most-likely, or 1280×480 / 8:3
  ultra-wide; responsive design handles any value in the ~480–720 dp range).
- PR2 map fix outcome (grey-rectangle resolved?) — confirm by installing the APK.
