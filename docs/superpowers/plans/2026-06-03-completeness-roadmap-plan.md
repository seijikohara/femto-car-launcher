# Completeness & Hardening Roadmap — Implementation Plan

| | |
| --- | --- |
| Date | 2026-06-03 |
| Owner | Seiji Kohara |
| Status | Draft, awaiting review |
| Source | Multi-agent completeness audit (19 subsystems, 110 verified findings) |
| Targets | `app/src/main/java/io/github/seijikohara/femto/**`, `app/src/test/**`, `app/src/androidTest/**`, build files, manifest, docs |

## 1. Goal

The home dashboard is functionally complete and well-architected (clean
UDF, idiomatic `callbackFlow` repositories with nullable fallbacks, a
consistent `FemtoDimens` / `FemtoTheme` design system). This plan does
**not** add new surfaces. It closes the gaps where the implementation is
**incomplete** (stubs, frozen data, swallowed errors, unreachable states)
or **worth hardening** (resilience of the long-lived HOME process,
multi-region correctness, glance readability, test coverage).

The work is sequenced by risk: restore the test safety net first, then
stop the launcher from breaking, then fix data that ships visibly wrong,
then polish.

## 2. How this plan was produced

A background `Workflow` fanned out one auditor per subsystem (19 areas),
adversarially verified each finding against `CLAUDE.md` and the spec's
section-2 non-goals (110 confirmed, 14 dropped as false or intentional),
and synthesized a prioritized roadmap. The P0 claims were then re-grounded
by hand against the current code:

- `app/src/androidTest/` references deleted composables `AppsBar(`,
  `ClockPanel(`, `MusicPanel(`, `WeatherPanel(`; production has
  `ClockOverlay`, `MusicCard`, `WeatherCard` and no `AppsBar`. The
  androidTest `fakeWeatherSnapshot` omits the now-required
  `humidityPercent` (no default in `WeatherSnapshot`). → source set does
  not compile, so `connectedAndroidTest` cannot run.
- `CalendarRepository` calls `ContentResolver.query` at two sites with no
  `runCatching` (every sibling repo guards its I/O).
- `AppsRepository.launch` calls `startMainActivity` unguarded; `queryApps`
  uses eager `.map(toAppEntry)` where `getIcon`/`toBitmap` can throw.

## 3. Non-goals (do not let these creep in)

The audit explicitly excluded the project's deliberate non-goals; this
plan inherits them. No driving-lockout / motion gating
(`CLAUDE.md#driving-lockout`), no OBD-II/CAN, no interactive map
pan/zoom/search, no ETA/routing, no music output-device switcher, no
theme marketplace, no cross-process weather persistence, no
user-configurable app-bar tiles, no notification-listener onboarding
wizard. MVP still exposes a single `FontTheme`; `FontPreferences` stays an
extension point.

## 4. Plan-wide conventions

- Style: Bold Minimal expression-chain Kotlin per `CLAUDE.md#kotlin-style`;
  every Composable takes `modifier: Modifier = Modifier` first
  (`CLAUDE.md#compose-architecture`).
- Values come from the symbol SSOT (`FemtoDimens.*`,
  `MaterialTheme.colorScheme.*`); never duplicate a literal
  (`CLAUDE.md#ssot-dry`).
- Test data comes from the `testfixtures/` package (one per source set);
  no ad-hoc `FakeFoo(...)` literals (`CLAUDE.md#testing`).
- TDD where a pure/JVM-testable unit exists: write the failing test,
  watch it fail, implement, watch it pass (`CLAUDE.md#testing`).
- After every task touching Kotlin/Gradle: `./gradlew spotlessApply`
  then the `verify-android-build` skill (`assembleDebug` + `lint`).
  Restoring `connectedAndroidTest` is itself Phase 0.
- Conventional Commits; **no** `Co-Authored-By: Claude` / "Generated with
  Claude Code" trailers (`CLAUDE.md#code-style`).
- One concern per commit; a fix lands with its regression test in the
  same commit wherever a test is feasible.

## 5. Priority legend

- **P0** — ships broken or risks crashing/banning the launcher. Do first.
- **P1** — important gap: visibly wrong data, missing coverage, or
  distribution risk.
- **P2** — valuable enhancement to correctness or glance UX.
- **P3** — nice-to-have cleanup / debt.

Effort: S (≲1h), M (≲½ day), L (≳½ day).

---

## Phase 0 — Restore the test safety net (P0, prerequisite)

The `androidTest` source set does not compile, so no instrumented
assertion guards any later fix. Nothing else in this plan can be verified
end-to-end until this phase is green.

### Task 0.1 — Repair the four stale UI tests (P0, M)

**Files:** `app/src/androidTest/.../ui/home/components/MusicPanelTest.kt`,
`WeatherPanelTest.kt`, `ClockPanelTest.kt`, `AppsBarTest.kt`

- Retarget `MusicPanel` → `MusicCard` (same signature); fix the
  `"Nothing playing"` assertion to `"Nothing is playing"`.
- Retarget `ClockPanel` → `ClockOverlay`.
- Retarget `WeatherPanel` → `WeatherCard`: drop the removed
  `LocalePreferences` / `speedUnit` / `is24Hour` args, use
  `ui.locale.TemperatureUnit`, add the required `city` arg.
- `AppsBar` was deleted: rename `AppsBarTest` → `DashboardFooterTest`
  exercising `DashboardFooter`; keep the `AppsBarShortcut` enum.
- **Verify:** `./gradlew compileDebugAndroidTestKotlin` is green.

### Task 0.2 — Sync androidTest `FakeWeatherSnapshot` (P0, S)

**Files:** `app/src/androidTest/.../testfixtures/FakeWeatherSnapshot.kt`

- Add a `humidityPercent: Int? = 55` param and pass it to the
  `WeatherSnapshot(...)` call, mirroring the JVM fixture (the field has no
  default in `WeatherSnapshot`, so the named-arg call fails today).
- **Verify:** part of the 0.1 compile check.

### Task 0.3 — Fix the broken fixed-clock assertion (P1, S)

**Files:** `app/src/androidTest/.../DashboardScaffoldTest.kt`

- `ClockOverlay` self-times `HH:mm:ss` and ignores the seeded
  `ClockTick`, so the `14:32` assertion is dead/flaky. Remove it; assert
  `uiState`-driven content instead. Defer real clock-format verification
  to a future `ClockOverlay` test with an injectable time source.

### Task 0.4 — Delete AGP template leftovers (P3, S)

**Files:** `app/src/test/.../ExampleUnitTest.kt`,
`app/src/androidTest/.../ExampleInstrumentedTest.kt` — remove both.

**Phase 0 exit:** `connectedAndroidTest` builds and runs on a
device/emulator. Coverage backfill (new UI + repo tests) is Phase 7 so
each fix below can land with its own regression test meanwhile.

---

## Phase 1 — Stop the launcher from breaking (resilience P0)

The launcher is the persistent HOME app; an unguarded throw on the
reactive path or a launch handler is catastrophic (relaunch loop or a
permanently frozen dashboard).

### Task 1.1 — Guard `CalendarRepository` queries (P0, S)

**Files:** `app/src/main/java/.../data/CalendarRepository.kt`

- Wrap both `ContentResolver.query` sites in `runCatching` with empty
  fallbacks (sibling-repo posture). Add a `snapshotFlow` `catch` that
  falls back to a clock-only snapshot as defense in depth. A
  `SecurityException` (permission revoked mid-stream) or an OEM-provider
  `SQLiteException` currently terminates `HomeViewModel.uiState`
  permanently, freezing clock, weather, music, and apps.
- **Test:** JVM test with a throwing fake `ContentResolver` asserting the
  flow keeps emitting.

### Task 1.2 — Make app launch crash-safe (P0, S)

**Files:** `data/AppsRepository.kt`, `MainActivity.kt`

- Wrap `startMainActivity` in `runCatching` for
  `ActivityNotFoundException` (stale-shortcut race after uninstall),
  returning a `Boolean`/`Result` so the caller can refresh. Both call
  sites (home + drawer) bypass any guard today.
- **Test:** unit test that `launch` does not propagate
  `ActivityNotFoundException`.

### Task 1.3 — Isolate icon/label resolution failures (P1, S)

**Files:** `data/AppsRepository.kt`

- Replace eager `.map(toAppEntry)` with per-entry `mapNotNull` +
  `runCatching` (prefer substituting a fallback bitmap so the app stays
  launchable). `getIcon` can throw `Resources.NotFoundException` and a
  pathological adaptive icon can OOM, aborting the whole list. Wrap
  `queryApps` callers in `runCatching` so the coroutine cannot die
  silently.

**Phase 1 exit:** no single bad package, revoked permission, or stale
shortcut can blank the grid or tear down the dashboard.

---

## Phase 2 — Live-data correctness (P1)

Surfaces that look functional but feed the UI frozen or zeroed data,
contradicting the spec's stated refresh contracts.

### Task 2.1 — Music card updates live (P1, M)

**Files:** `data/MusicSessionRepository.kt`, `ui/.../MusicCard.kt`

- `activeControllersFlow` only re-emits when the *set* of sessions
  changes, so the card is a one-shot snapshot: progress never advances,
  the play/pause icon never flips, in-session track/art changes are
  missed. Register a `MediaController.Callback`
  (`onPlaybackStateChanged`, `onMetadataChanged`); re-register on
  controller swap; unregister in `awaitClose`. Drive the progress bar
  from `playbackState.lastPositionUpdateTime + elapsed*speed` via a
  `withFrameNanos`/1 s loop gated on `isPlaying` — not the minute-grain
  `ClockTick`.

### Task 2.2 — Trip distance survives backgrounding (P1, M)

**Files:** `data/TripRepository.kt`

- Accumulators live in a cold flow under `WhileSubscribed(5_000)`, so
  distance silently resets ~5 s after the head unit foregrounds another
  app — violating the "since process start" contract in its own
  docstring. Hoist the accumulator into a repository-scoped `StateFlow`
  (`SharingStarted.Eagerly`/`Lazily`) so `WhileSubscribed` only gates UI
  delivery. Correct the docstrings.
- **Test:** drop and re-add the collector across the window; assert no
  reset to 0.

### Task 2.3 — Accrue distance on speed-less GPS (P1, S)

**Files:** `data/TripRepository.kt`, `ui/.../SpeedOverlay.kt`

- `current.speed` is `0.0` when `!hasSpeed` (common on cheap chips / raw
  `GPS_PROVIDER` HALs), so distance/avg freeze at 0 while driving. Use an
  effective speed: `current.speed` when `hasSpeed()`, else
  `previous.distanceTo(current) / deltaSeconds`; gate accrual on that.
  Apply the same fallback to the `SpeedOverlay` hero numeral.
- **Test:** both branches.

### Task 2.4 — Weather outage retry interval (P1, S)

**Files:** `data/WeatherRepository.kt`

- `shouldRefetch` returns `true` whenever `cached == null`, so a sustained
  Open-Meteo outage fires one request per ~1 Hz GPS tick against the
  public endpoint (ban risk; contradicts the 30 min / 5 km design).
  Record `lastAttemptAt` on every refresh entry and short-circuit within
  `MIN_RETRY_INTERVAL` even when cache is null (mirror the geocoder
  throttle).
- **Test:** `Clock`-driven JVM test.

### Task 2.5 — Decouple the loading gate from the apps list (P1, M)

**Files:** `ui/home/HomeViewModel.kt`, `ui/home/HomeScreen.kt`

- `isLoading == apps.isEmpty()` hides the whole dashboard behind a
  placeholder when the app query fails or a device legitimately has zero
  launchable apps. Track an explicit apps load state (nullable apps or a
  sealed `Loading`/`Loaded`/`Failed`), render the rest of the dashboard
  regardless, and show an apps-area-only empty/error affordance.

### Task 2.6 — App drawer loading/empty/error states (P1, M)

**Files:** `ui/drawer/AppDrawerRoute.kt`, `AppDrawerScreen.kt`

- Promote to a small stateful shape with a sealed `UiState`
  (`Loading`/`Content`/`Error`). Wrap `queryApps` in `runCatching`; show a
  progress indicator, an explicit empty message, and a retry affordance
  (≥64 dp, ≥18 sp). Today a failed query strands a blank black surface.

**Phase 2 exit:** music, trip, and weather reflect live reality; an apps
query failure degrades locally instead of hiding the dashboard.

---

## Phase 3 — Multi-region correctness (P1/P2)

Multi-region is a first-class principle; each item is a single baked-in
market convention, cheap to fix, visibly wrong in some locale.

### Task 3.1 — `uses-feature` location not required (P1, S)

**Files:** `app/src/main/AndroidManifest.xml`

- FINE/COARSE implicitly *hard-require* GPS + network hardware on Play,
  hiding the app from units lacking it — contrary to the graceful
  null-emit degradation and the multi-region principle. Add
  `android.hardware.location.gps` and `.network` with `required="false"`;
  verify in the merged manifest.

### Task 3.2 — Localize the wind unit (P1, S)

**Files:** `ui/.../WeatherCard.kt`, `ui/.../DashboardScaffold.kt`

- Wind is hardcoded `m/s` while the same dashboard shows mph for US/GB/MM
  — an internal inconsistency. Thread the in-scope `speedUnit` into
  `InfoPane`/`WeatherCard`; format imperial wind as mph via
  `SpeedUnit.fromKilometersPerHour` + `label`; keep m/s for metric per the
  mockup.
- **Test:** mph vs km/h locales.

### Task 3.3 — All-day events on the correct day (P1, M)

**Files:** `data/CalendarRepository.kt`, `ui/.../CalendarCard.kt`

- All-day `Instances` store `BEGIN` as UTC-midnight and must be read in
  UTC; `systemDefault` places the dot/event a day early west of UTC and
  shows a spurious clock time. Add `Instances.ALL_DAY` to both
  projections, compute all-day dates with `ZoneOffset.UTC`, make
  `EventItem.time` nullable (or add `isAllDay`) so the card renders
  "All day".
- **Test:** a negative-offset zone.

### Task 3.4 — Locale-aware calendar month label (P2, S)

**Files:** `data/CalendarRepository.kt`

- `monthLabel` hand-joins "Month Year" with English ordering (wrong in
  `ja`/`ko`). Use `DateFormat.getBestDateTimePattern(locale, "yMMMM")`.
- **Test:** `en`/`ja`/`ko` parameterized.

### Task 3.5 — Extract user-facing strings to resources (P2, M)

**Files:** `ui/home/HomeScreen.kt`, `WeatherCard.kt`, `MusicCard.kt`,
`CalendarCard.kt`, `MapPanel.kt`

- ~10 UI literals ship as inline English with zero `stringResource`.
  Sweep into `res/values/strings.xml`; use `stringResource`. This
  establishes the localization extension point the multi-region rule
  requires. Optionally enable the Hardcoded-text Lint check.

### Task 3.6 — Carry user position into the maps handoff (P2, S)

**Files:** `ui/home/HomeViewModel.kt`, `MainActivity.kt`

- Tapping the user-centred map opens the maps app with no position. Give
  `OpenMaps` a location payload from `uiState`; launch `ACTION_VIEW` with a
  `geo:lat,lon?z=15` URI (package-agnostic), falling back to the category
  selector when no fix. Reuse `MAP_ZOOM` as the single `z` source.

### Task 3.7 — Temperature unit from CLDR `LocalePreferences` (P2, S)

**Files:** `ui/locale/SystemUnits.kt`

- `temperatureUnitFor` uses a hand-maintained Fahrenheit country set
  (omits US territories GU/VI/AS/MP; ignores `-u-mu` overrides). Read
  `LocalePreferences.getTemperatureUnit()` first; fall back to the set
  only on unknown (consistent with the `is24Hour` path). Keep client-side
  `fromCelsius`.
- **Test:** a US-territory and a `-u-mu-fahrenhe` case.

---

## Phase 4 — Glance readability, accessibility & empty states (P1/P2)

For an automotive shell, fabricated zeros, blank rows, and below-floor CTA
copy degrade the at-a-glance read the design system exists to protect.

### Task 4.1 — Calendar permission-denied state reachable & honest (P1, M)

**Files:** `data/CalendarRepository.kt`, `ui/.../CalendarCard.kt`

- The repo never emits `null` after start, so the card's "Calendar access
  not granted" branch is unreachable; a denied user sees a full strip plus
  a misleading "No upcoming events". Add a `hasCalendarAccess` flag to the
  snapshot; branch into a denial message (ideally tappable to request).
  Keep `null` only for the loading frame. Fix the stale
  `CalendarSnapshot` docstring.

### Task 4.2 — Distinguish no-fix from standstill in `SpeedOverlay` (P2, S)

**Files:** `ui/.../SpeedOverlay.kt`

- A null location collapses to "0 km/h, 0.0 km" — indistinguishable from a
  real standstill and contrary to the permissions contract. Render an
  em-dash for the live-speed cell when location is null (reuse the
  `WeatherCard` convention); keep distance `0.0` for a fresh trip.

### Task 4.3 — 5-sample EMA on the speed numeral (P2, S)

**Files:** `ui/.../SpeedOverlay.kt`

- The 40 sp hero numeral reads raw 1 Hz GPS and flickers between integers
  on a steady cruise; the spec requires a 5-sample EMA in the panel. Add a
  remembered EMA accumulator (α≈0.33), seeded/reset on null. Extract the
  EMA as a pure testable function.

### Task 4.4 — Music: title fallback + keep paused sessions (P2, S)

**Files:** `data/MusicSessionRepository.kt`, `ui/.../MusicCard.kt`

- Fall back `METADATA_KEY_TITLE` → `DISPLAY_TITLE` (podcasts/radio show a
  blank 23 sp line today); blank-guard the title `Text`.
- Both display and command paths filter on `isActive` (false for
  `STATE_PAUSED`), so a paused track collapses to "Nothing is playing" and
  the transport command is a silent no-op. Select the highest-priority
  PLAYING-or-PAUSED controller via a shared `selectPrimaryController` so
  paused tracks render with `isPlaying = false` and resume works.

### Task 4.5 — Battery unknown/loading state (P2, S)

**Files:** `ui/.../DashboardFooter.kt`, `data/SystemStatus.kt`,
`data/SystemStatusRepository.kt`

- The footer renders `0%` during cold start / on battery-less units
  (reads as a dead battery). Use a nullable/`-1` sentinel for unknown and
  render an em-dash until a valid `0..100` reading arrives; clamp with
  `coerceIn(0..100)` at the repository SSOT.

### Task 4.6 — Footer NavRow clipping on narrow/portrait (P2, M)

**Files:** `ui/.../DashboardFooter.kt`

- Seven fixed 72 dp buttons + the status cluster need ~707 dp with zero
  slack; `SpaceBetween` cannot shrink them, so on a portrait/narrow unit
  (~480–600 dp) the rightmost buttons/cluster clip. Make the row
  width-aware (`horizontalScroll`, weighted toward `MinTouchTarget`, or
  `BoxWithConstraints` dropping the cluster at compact widths). Add a
  narrow `Preview` + a compact-width test. (Pairs with Task 6.7 adding
  `modifier` to the private composables.)

### Task 4.7 — Weather empty state = icon placeholder (P2, S)

**Files:** `ui/.../WeatherCard.kt`

- The cold-start empty state shows a flat "Weather unavailable" line — the
  inverse of the spec's icon-only placeholder, reading as an error during
  normal first-fetch. Render a dimmed weather glyph
  (`FemtoDimens.WeatherGlyphLarge`, `onSurfaceVariant`) like `MusicCard`
  `EmptyState`. No staleness/error cue (spec mandates no error UI).

### Task 4.8 — Music CTA copy above the 18 sp floor (P2, S)

**Files:** `ui/.../MusicCard.kt`

- The notification-access instruction (actionable copy) renders at 13 sp
  with no design-SSOT sanction. Use `bodyMedium`/`bodyLarge` or
  `FemtoDimens.MinBodyTextSize`. Leave the `EmptyState` 13 sp (mockup
  sanctions it).

---

## Phase 5 — Live-data resilience follow-ups (P2)

Lower-severity correctness items that share files with earlier tasks;
schedule alongside them.

### Task 5.1 — Re-attempt MapLibre style load (P2, M)

**Files:** `ui/.../MapPanel.kt` — `loadFailed` latches the fallback
permanently; add it (plus a throttled retry tick) to the style
`LaunchedEffect` keys with bounded backoff against the no-SLA OpenFreeMap
host.

### Task 5.2 — Forward `onLowMemory` to MapLibre (P2, S)

**Files:** `ui/.../MapPanel.kt`, `MainActivity.kt` — Lifecycle has no
low-memory event; register a `ComponentCallbacks2` (or override
`onLowMemory`/`onTrimMemory`) and call `mapView.onLowMemory`; unregister on
dispose.

### Task 5.3 — Harden Open-Meteo `Current` decoding (P2, S)

**Files:** `data/OpenMeteoApi.kt`, `data/WeatherRepository.kt` — make
`apparent_temperature`, `windspeed_10m`, `is_day` nullable (keep
`temperature_2m`, `weathercode` required) so a missing secondary field does
not discard a usable reading; map with fallbacks in `refresh`.

### Task 5.4 — Weather clock heartbeat (P2, S)

**Files:** `data/WeatherRepository.kt` — `refresh` is driven only from
`locationFlow`; when GPS stops emitting (indoors/underground) weather
freezes even with network. Combine in the existing `clockFlow` (as
`CalendarRepository` does) and re-evaluate on the cached location each
tick. Pairs with Task 2.4.

### Task 5.5 — Bluetooth indicator recovery after runtime grant (P2, M)

**Files:** `data/SystemStatusRepository.kt`, `MainActivity.kt` — a
`BLUETOOTH_CONNECT` grant from Settings leaves the icon dimmed until the
next BT event/restart. Forward the permission result / an `ON_RESUME`
signal into a trigger the flow merges, or scope collection to `RESUMED`.
- **Test:** false→true recovery.

### Task 5.6 — Share the location flow once (P2, M)

**Files:** `ui/home/HomeViewModel.kt`, `data/LocationRepository.kt` — the
single cold `locationFlow` is collected by four consumers → four platform
GPS registrations on an always-on device. `shareIn(WhileSubscribed(5_000),
replay = 1)` before fan-out collapses to one registration (the author
intent per the `TripRepository` KDoc).

### Task 5.7 — Refresh the app list on package events (P2, M)

**Files:** `data/AppsRepository.kt`, `ui/home/HomeViewModel.kt` —
`queryApps` is one-shot at VM construction, so the grid stays stale for the
whole process. Expose a `callbackFlow` registering a `LauncherApps.Callback`
that re-queries on install/uninstall/update; collect in VM + drawer.
Enables narrowing the VM param to a read-only `Flow`.

### Task 5.8 — Monotonic clock for trip delta (P2, S)

**Files:** `data/TripRepository.kt` — `deltaSeconds` uses wall-clock
`Location.time`; a mid-trip NTP/system-clock jump corrupts the delta.
Switch to `elapsedRealtimeNanos` (the fixture already sets it).
- **Test:** forward + backward clock jump.

---

## Phase 6 — Build, dependency & manifest hygiene (P2/P3)

### Task 6.1 — Remove `material-icons-extended` (P2, S)

**Files:** `app/build.gradle.kts`, `gradle/libs.versions.toml` — declared
but never imported (UI is Lucide-only); one of the largest Compose
artifacts. Delete; confirm `assembleDebug`.

### Task 6.2 — Wire the production geocoding host (P2, S)

**Files:** `ui/home/HomeViewModel.kt`, `app/build.gradle.kts` —
`NominatimApi` exposes `baseUrl`/`apiKey` but production ships against the
public Nominatim endpoint (a stated release requirement to change). Add
gitignored `buildConfigField`s for the geocoder base URL + key; public as
the debug default; require a non-public host for release.

### Task 6.3 — Resolve the `ACCESS_COARSE_LOCATION` contract mismatch (P2, M)

**Files:** `app/src/main/AndroidManifest.xml`, `data/LocationRepository.kt`
— the manifest + CLAUDE.md promise degraded precision on coarse-only, but
the code is `GPS_PROVIDER`-only with FINE-only gates, so an Approximate
grant yields no fix and re-prompts forever. Honor the contract: add a
`NETWORK_PROVIDER` fallback + `hasCoarseLocationPermission`, treat
coarse-only as granted-degraded, add `onProviderEnabled` re-seed for
tunnel/garage recovery. (Better automotive fit than dropping the
permission.)

### Task 6.4 — Remove unused test deps (P3, S)

**Files:** `app/build.gradle.kts`, `gradle/libs.versions.toml` —
`mockito-core`, `mockito-kotlin`, `androidx-test-core` are referenced by no
test (suites use Turbine + MockWebServer + Robolectric). Delete
declarations + catalog aliases.

### Task 6.5 — Collapse the duplicate Kotlin catalog key (P3, S)

**Files:** `gradle/libs.versions.toml` — `kotlin` and `kotlinTest` hold the
same value; point `kotlin-test` at `kotlin` and delete `kotlinTest` so a
manual bump cannot desync.

### Task 6.6 — Make the release minification posture explicit / R8-safe (P3, S)

**Files:** `app/build.gradle.kts`, `app/proguard-rules.pro` — release is
unminified with an empty rules file; flipping minify later would silently
break `kotlinx-serialization` decoding. Either document the intentional
posture or add `kotlinx-serialization` + MapLibre keep rules now. Make the
`NominatimApi`/`OpenMeteoApi` swallow observable (log on failure) so a
`SerializationException` is distinguishable from an empty result.

### Task 6.7 — Bound the reverse-geocode cache (P3, S)

**Files:** `data/ReverseGeocoderRepository.kt` — the per-bucket cache is an
unbounded `mutableMap` for the whole process (slow leak on a long drive)
with no TTL. Swap to `LruCache` / access-order `LinkedHashMap` capped
~256; optional TTL keyed off the injected `nowMs`.
- **Test:** eviction.

### Task 6.8 — Other quick wins (P3, S)

- Clamp battery percent at the repo SSOT (folded into Task 4.5).
- Drop the `events.isEmpty()` early-return in
  `CalendarRepository.readEventDates` so valid event-days keep their dot
  (fold into Task 1.1 / 3.3).

---

## Phase 7 — Test coverage backfill (P1/P2)

Land these alongside the fixes above where noted; this phase tracks the
remaining gaps once compilation is restored.

### Task 7.1 — JVM tests for untested repositories (P1, L)

**Files:** new `TripRepositoryTest.kt`, `CalendarRepositoryTest.kt`,
`SystemStatusRepositoryTest.kt`; extend `testfixtures/FakeLocation.kt`
(add `timeMs`, `elapsedRealtimeNanos`, `hasSpeed`).

- Trip: accumulation, stationary exclusion, gap dropping, non-positive
  delta, average (`runTest` + Turbine).
- Calendar (Robolectric): permission-denied empty path, dot mapping,
  BEGIN-after-now filter, `EVENT_LIST_LIMIT`, debounce.
- SystemStatus: BT-denied→false, battery math, Wi-Fi `VALIDATED` + `WIFI`
  predicate.

### Task 7.2 — UI tests for live cards/overlays/drawer (P1, L)

**Files:** new `DashboardFooterTest.kt`; tests for `MusicCard` (3 states),
`CalendarCard` (event-dot + permission), `SpeedOverlay` (km/h→mph,
km→mi), `AppDrawerScreen` (grid + launch dispatch). `createComposeRule`
wrapped in `FemtoTheme`; data from a shared androidTest `testfixtures`
package.

### Task 7.3 — Backfill omitted JVM cases (P2, M)

**Files:** `HomeViewModelTest.kt` (OpenBrowser/OpenSettings actions,
loading→loaded), `AddressComposerTest.kt` (non-JP East-Asian, no-ISO
Western, null-country), `WeatherRepositoryTest.kt` (add
`relative_humidity_2m` to the body, assert the round path),
`NominatimApiTest.kt` (accept-language reflects a non-`ja` language).
Extract a `FakeAppEntry` fixture to kill the inline `AppEntry` literal.

---

## Phase 8 — Tech-debt & docs accuracy (P2/P3)

### Task 8.1 — Type-safe `HomeViewModel` combine (P2, M)

**Files:** `ui/home/HomeViewModel.kt` — the 9-flow combine recovers values
via positional index casts with ~10 unchecked-cast suppressions; a reorder
fails at runtime. Compose typed `combine`s into a named holder (or 2–3
intermediate sub-states) so the compiler enforces arity/type.
- **Test:** each source maps to the correct field.

### Task 8.2 — Mark the superpowers spec/plan docs superseded (P3, S)

**Files:** `docs/superpowers/specs/2026-05-01-home-dashboard-design.md`,
`docs/superpowers/plans/2026-05-01-home-dashboard-plan.md` — both still
describe Google Maps SDK / `GmsAvailability` / AOSP `Geocoder` /
`MAPS_API_KEY` / `play-services-maps` (all replaced by MapLibre +
OpenFreeMap + Nominatim) with a "Draft awaiting review" status. Add a
one-line "superseded — see CLAUDE.md (SSOT)" banner and flip the status, so
a re-run of subagent-driven-development cannot re-introduce GMS/Geocoder.

### Task 8.3 — Prune dead code (P3, S)

- `AppsBarShortcut.kt`: trim the callerless `Maps`/`Camera`/`Navigation`
  entries and the unread `icon` field; fix `Phone` to a dialer category or
  relabel "Contacts" to match `APP_CONTACTS`.
- `ui/locale/SystemUnits.kt`: `fromKilometersPerHour` is needed by Task
  3.2; the `DistanceUnit` enum / `fromMeters` / `distanceUnitFor` have no
  callers — trim them (and their self-referential tests) or leave a TODO
  tying them to a planned elevation overlay.

### Task 8.4 — Design-token & docs hygiene (P3, S)

- Extend the `Color.kt` preview fallback palette to all consumed roles
  (~8 roles fall back to M3 baseline purple/teal today).
- Hoist the `SpeedOverlay` 20 dp corner to `FemtoDimens.SpeedOverlayCorner`
  (distinct from the 16 dp `OverlayCorner`).
- Add `modifier` to the `DashboardFooter` private composables
  (`NavButton`, `StatusIcon`, `BatteryIndicator`) — needed by Task 4.6.
- Make the active Home nav button non-clickable (no dead-tap ripple).

### Task 8.5 — Calendar single-window scan (P3, S)

**Files:** `data/CalendarRepository.kt` — collapse the two byte-identical
window queries into one pass; gate the per-minute re-query on day-rollover
/ observer rather than every tick. Best done with Tasks 1.1 / 3.3.

### Task 8.6 — Drawer enhancements (P3, M)

- Reuse the home VM app list in the drawer instead of re-querying +
  re-decoding all 192×192 icons per open (pairs with Task 5.7).
- Optional A–Z fast-scroll letter rail (64 dp targets) via
  `animateScrollToItem`.

---

## 9. Suggested execution order

1. **Phase 0** (test safety net) — unblocks verification of all else.
2. **Phase 1** (resilience P0) — stop catastrophic failures.
3. **Phase 2** (live-data P1) — fix visibly wrong data; land each with a
   Phase 7 regression test.
4. **Phase 3** (multi-region P1/P2) + **Phase 4** (glance/UX P1/P2) — can
   proceed in parallel branches; share `CalendarCard`/`SpeedOverlay`/
   `MusicCard`/`WeatherCard` files, so coordinate by file.
5. **Phase 5** (resilience follow-ups) + **Phase 6** (build/manifest).
6. **Phase 7** (remaining coverage) + **Phase 8** (debt/docs).

## 10. Acceptance checklist

- [ ] `./gradlew assembleDebug` and `./gradlew lint` green.
- [ ] `./gradlew test` green, including the new repo/VM cases.
- [ ] `./gradlew connectedAndroidTest` **compiles and runs** (Phase 0
      exit) and covers the live cards/overlays/drawer.
- [ ] `./gradlew spotlessCheck` green.
- [ ] No unguarded `ContentResolver.query` / `startMainActivity` /
      per-package icon decode on the reactive or launch paths.
- [ ] Music card, trip odometer, and weather reflect live state; no
      retry-storm during an Open-Meteo outage.
- [ ] No hardcoded units or English UI literals on the audited surfaces;
      `uses-feature` location is `required="false"`.
- [ ] `CLAUDE.md` permission audit table still matches the manifest; the
      2026-05-01 spec/plan docs are marked superseded.
