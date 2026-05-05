# Home Dashboard (Plan B) — Design Spec

| | |
| --- | --- |
| Date | 2026-05-01 |
| Owner | Seiji Kohara |
| Status | Draft, awaiting review |
| Targets | `app/src/main/java/io/github/seijikohara/femto/ui/home/`, new `ui/drawer/`, expanded `data/` |

## 1. Goal

Replace the current single-purpose home screen (one full-screen `LazyVerticalGrid` of installed apps) with a LecoAuto-inspired multi-panel **dashboard**: a hero map on the left with speed / altitude / address overlays, a vertical stack of three ambient panels on the right (clock, weather, now-playing), and a fixed application bar at the bottom that opens a separate full-screen drawer for the complete grid.

The dashboard surfaces information that head-unit users — driver and passenger alike — actually consult, and stays in the same shape regardless of vehicle motion. Distraction responsibility is delegated to the driver and to the OEM cluster, in line with comparable aftermarket launchers (LecoAuto, Car Launcher Pro, AGAMA, CarWebGuru).

## 2. Non-goals

The following are **explicitly deferred** and must not creep into Plan B:

- Driving-lockout UI gating. The launcher renders the same dashboard whether the vehicle is moving or stopped. Driver-distraction responsibility lies with the driver and the OEM cluster, not the launcher. This reverses the previous project policy; see the updated CLAUDE.md and the removal of the `gate-driving-visible-feature` skill in the same change set
- OBD-II / CAN integration
- Interactive map (pan / zoom / search inside the launcher)
- ETA / routing UI
- Music output device switcher (Bluetooth, A2DP routing)
- Onboarding wizard for the notification-listener permission
- Theme / wallpaper marketplace
- Persisting weather snapshots across process restarts
- Custom user-configurable app-bar tiles
- Portrait-specific layout optimization (portrait must work but follows the same vertical column order as a degenerate case)
- Any per-region special-case (regulation references stay parameterized, not hard-coded — see CLAUDE.md multi-region rule)

## 3. Layout (C2)

**Landscape orientation, two columns. Map column ~60% width, right column ~40%. Single layout — vehicle motion does not change the rendered tree.**

```
+----------------------+--------------+
|  35 km/h   ↑ 47 m    |  TIME        |
|        [overlay]     |  14:32       |
|                      |  Tue, May 1  |
|        Map           +--------------+
|        (Lite Mode)   |  WEATHER     |
|                      |  ☀ 18°       |
|                      +--------------+
|                      |  NOW PLAYING |
|  Shibuya, Tokyo      |  ▶ Strobe    |
|        [overlay]     |  ◀◀  ⏯  ▶▶ |
+----------------------+--------------+
|  ≡   phone  music  maps  cam  nav   |  AppsBar
+--------------------------------------+
```

| Region | Composable | Notes |
| --- | --- | --- |
| Left hero | `MapPanel` | Google Maps SDK Lite Mode, ~60% of width, full inner column height. Two text overlays on a translucent gradient: top-left = `speed` + `altitude`, bottom-left = `ShortAddress`. Tap fires `Intent.ACTION_VIEW geo:` and lets the OS pick the maps app |
| Right column | `ClockPanel`, `WeatherPanel`, `MusicPanel` | Three M3-squircle surfaces, equal vertical share. `WeatherPanel` shows weather icon and temperature only — address lives on the map overlay (SSOT) |
| Bottom bar | `AppsBar` | Fixed height, six tiles: ≡ (drawer), and five action shortcuts resolved by intent category, not by hard-coded package name |

## 4. File and component layout

```
app/src/main/java/io/github/seijikohara/femto/
├── data/
│   ├── ClockRepository.kt              new
│   ├── LocationRepository.kt           new (replaces deleted DrivingStateRepository)
│   ├── ReverseGeocoderRepository.kt    new
│   ├── WeatherRepository.kt            new (Open-Meteo)
│   ├── MusicSessionRepository.kt       new (NotificationListenerService bridge)
│   ├── MusicSessionListenerService.kt  new (NotificationListenerService impl)
│   ├── GmsAvailability.kt              new
│   ├── LocationPermissions.kt          new (file-private extension hosting hasFineLocationPermission)
│   ├── AppEntry.kt                     unchanged
│   ├── AppsRepository.kt               unchanged
│   └── FontPreferences.kt              unchanged
│
│  removed:
│   - DrivingState.kt                   (rememberDrivingLockState no longer needed)
│   - DrivingStateRepository.kt         (lockedFlow no longer needed)
├── ui/
│   ├── home/
│   │   ├── HomeRoute.kt                expanded VM wiring (no lockout doc reference)
│   │   ├── HomeScreen.kt               rewritten as DashboardScaffold (single layout)
│   │   ├── HomeUiState.kt              expanded data class
│   │   ├── HomeViewModel.kt            expanded with combine
│   │   ├── HomeAction.kt               new (extracted sealed interface)
│   │   └── components/
│   │       ├── AppTile.kt              unchanged, reused
│   │       ├── ClockPanel.kt           new
│   │       ├── WeatherPanel.kt         new
│   │       ├── MusicPanel.kt           new
│   │       ├── MapPanel.kt             new (AndroidView wrapper around MapView Lite Mode plus overlays)
│   │       ├── AppsBar.kt              new
│   │       └── DashboardScaffold.kt    new (overall C2 frame, no motion-state branching)
│   └── drawer/
│       ├── AppDrawerRoute.kt           new
│       └── AppDrawerScreen.kt          new (the existing LazyVerticalGrid lifted into its own screen)
└── MainActivity.kt                      one new NavHost destination for AppDrawer

app/src/test/.../testfixtures/           new package: FakeLocation, FakeWeatherSnapshot, FakeNowPlaying, FakeAddress
app/src/androidTest/.../testfixtures/    new package: parallel fixtures for UI tests
```

**Naming.** New repositories follow the existing `<Concept>Repository.kt` shape; new panels follow `<Concept>Panel.kt` to distinguish dashboard surfaces from generic widgets. The previous `DrivingState.kt` and `DrivingStateRepository.kt` are removed; the `hasFineLocationPermission()` extension that lived in the old `DrivingState.kt` is preserved by relocating it to `LocationPermissions.kt`.

## 5. Data flow

### 5.1 Repositories

| Repository | Output | Refresh trigger | Failure / absence behaviour | Underlying API |
| --- | --- | --- | --- | --- |
| `ClockRepository` | `Flow<ClockTick>` | `Intent.ACTION_TIME_TICK` system broadcast (1/min), plus immediate value on subscribe | Cannot fail (OS-supplied) | `Context.registerReceiver` |
| `LocationRepository` | `Flow<Location?>` | `LocationManagerCompat.requestLocationUpdates` on `GPS_PROVIDER`, 1 Hz default interval | No `ACCESS_FINE_LOCATION` -> emits `null` | `androidx.core.location` |
| `ReverseGeocoderRepository` | `Flow<ShortAddress?>` | `LocationRepository` debounced by 100 m / 30 s | `Geocoder.isPresent() == false`, exception, or empty result -> `null` | `Geocoder` async (API 33) |
| `WeatherRepository` | `Flow<WeatherSnapshot?>` | `LocationRepository` gated to one fetch per 30 min or 5 km displacement | HTTP / parse failure -> last cached value (in-memory) -> `null` | Open-Meteo `/v1/forecast` |
| `MusicSessionRepository` | `Flow<NowPlaying?>` | `MediaSessionManager` + `NotificationListener` callbacks | Listener access not granted -> `null` | `MediaSessionManager.OnActiveSessionsChangedListener` via `MusicSessionListenerService` |
| `GmsAvailability` | `suspend fun check(): Boolean` | One-shot at `HomeViewModel` init | Not present -> `false` | `GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable` |

`LocationRepository` exposes the raw `Location?` directly; consumers read `speed`, `altitude`, latitude, longitude as needed. Speed and altitude are smoothed by a 5-sample exponential moving average inside the panel that displays them, not inside the repository, so other consumers (map centering, weather, geocoder) see unsmoothed values.

All repositories apply `flowOn(Dispatchers.IO)` internally so consumers do not manage dispatchers.

### 5.2 Value objects

```kotlin
data class ClockTick(val time: LocalTime, val date: LocalDate)

data class ShortAddress(val locality: String, val region: String?)

data class WeatherSnapshot(
    val tempC: Double,
    val code: WeatherCode,           // WMO weather code mapped to internal enum
    val fetchedAt: Instant,
)

data class NowPlaying(
    val title: String,
    val artist: String?,
    val albumArt: ImageBitmap?,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val packageName: String,         // routes media commands back to the same player
)

enum class TemperatureUnit { CELSIUS, FAHRENHEIT, KELVIN }
```

### 5.3 ViewModel aggregation

```kotlin
val uiState: StateFlow<HomeUiState> = combine(
    clockRepo.tickFlow(),
    locationRepo.locationFlow(),
    weatherRepo.snapshotFlow(),
    geocoderRepo.addressFlow(),
    musicRepo.nowPlayingFlow(),
    appsRepo.appsFlow(),
) { clock, location, weather, address, music, apps ->
    HomeUiState(
        clock = clock,
        location = location,
        weather = weather,
        address = address,
        nowPlaying = music,
        apps = apps,
        mapAvailable = gmsAvailable,
        isLoading = apps.isEmpty(),
    )
}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Initial)
```

`location` is forwarded so `MapPanel` can render the speed / altitude overlay and centre the map on the latest fix. The dashboard tree never branches on motion state.

`HomeAction` covers `LaunchApp(ComponentName)`, `OpenAppDrawer`, `OpenMaps`, and `Music(MusicCommand)` where `MusicCommand` is a sealed interface over `Play`, `Pause`, `SkipNext`, `SkipPrevious`. Actions resolve in the ViewModel; UI emits them and never side-effects directly.

## 6. Locale and units

System locale and per-app locale preferences drive every user-facing format. No hard-coded units, no per-region branches.

| Concern | API | Fallback |
| --- | --- | --- |
| 12 h vs 24 h | `DateFormat.is24HourFormat(context)` | (the API itself falls back to locale) |
| Date format | `DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.getDefault())` | locale default |
| Temperature unit | `LocalePreferences.getTemperatureUnit()` from `androidx.core.i18n` | locale CLDR default |
| Speed unit | derived from `Locale.getDefault().country`: `US`, `GB`, `MM` -> mph; otherwise km/h | km/h |
| Distance / altitude unit | derived from the same country list as speed: `US`, `GB`, `MM` -> ft; otherwise m | m |
| Address language | `Geocoder(context, Locale.getDefault())` | platform default |

`LocalePreferences` does not expose a speed or distance unit, so the project defines its own `SpeedUnit` and `DistanceUnit` enums and a single `systemSpeedUnit()` / `systemDistanceUnit()` helper file with the `country` lookup above. The lookup is intentionally narrow — adding more countries flows through that file, never through the panels.

The Open-Meteo request appends `temperature_unit=fahrenheit` or `celsius` based on `LocalePreferences.getTemperatureUnit()` so the API returns server-converted values; the client never converts.

## 7. Motion-state policy (no UI gating)

The launcher renders the same dashboard tree regardless of vehicle motion. Two reasons:

- **Passenger operation.** A passenger commonly operates the head unit; gating the whole UI on motion punishes the passenger for the driver being in motion.
- **OEM cluster owns driving information.** Speed, RPM, fuel, warnings come from the vehicle cluster. The launcher's role is the head-unit shell, not a redundant driver display.

Distraction responsibility is therefore delegated to the driver and to the OEM cluster, in line with how comparable aftermarket launchers (LecoAuto, Car Launcher Pro, AGAMA, CarWebGuru) ship today. Map tiles in Lite Mode do not animate, the apps bar does not scroll, and there are no inline videos in the launcher itself, so the always-on dashboard does not introduce a new distraction class beyond what those competitors already ship.

The `DrivingStateRepository`, `DrivingState.kt` `rememberDrivingLockState()`, and the `gate-driving-visible-feature` skill are removed in the same change set as this spec. The `FemtoDimens.MinBodyTextSize` (18 sp) and `FemtoDimens.MinTouchTarget` (64 dp) tokens stay — they continue to express glance-readability and tap accuracy targets that matter independently of motion.

If a future feature has a clear and specific distraction profile (e.g., embedded video playback inside the launcher, a chat composer with a multi-line input), that feature gates itself locally on motion or on a passenger toggle. There is no global gate to inherit from.

## 8. Permissions

### 8.1 `<uses-permission>` audit log delta (CLAUDE.md#permissions)

| Permission | New / existing | One-line justification |
| --- | --- | --- |
| `ACCESS_FINE_LOCATION` | existing — justification rewritten | Centre the map, derive speed / altitude / address overlays, and locate the user for weather lookups |
| `ACCESS_NETWORK_STATE` | new | Maps SDK best-practice probe before tile fetch |
| `INTERNET` | new | Open-Meteo weather API + Google Maps Lite tile fetch |

Both new permissions are added through the `add-launcher-permission` skill in a single commit with the justifications above.

### 8.2 Notification listener (out-of-band)

`BIND_NOTIFICATION_LISTENER_SERVICE` is granted to `MusicSessionListenerService` via the `<service android:permission=...>` attribute, not via `<uses-permission>`. The user must enable the launcher under **Settings -> Notifications -> Notification access**. The launcher routes the user there with `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS` from the `MusicPanel` placeholder when listener access is missing. No silent fallback.

### 8.3 Maps API key

| Aspect | Decision |
| --- | --- |
| Storage | `local.properties` key `MAPS_API_KEY=` (already in `.gitignore`) |
| Injection | `app/build.gradle.kts` reads via `Properties.load(localProperties)` and sets `manifestPlaceholders["MAPS_API_KEY"]`. Default `""` when missing |
| Manifest | `<meta-data android:name="com.google.android.geo.API_KEY" android:value="${MAPS_API_KEY}"/>` |
| Absent key | `MapView` initialization fails -> `MapPanel` collapses to the GMS-absent branch and renders a static placeholder |
| Cloud Console restriction | Android app restriction tied to package name + debug + release SHA-1 fingerprints |
| Cost monitoring | Cloud Console alert at 80% of the Dynamic Maps free tier (8,000 map loads / month) |

## 9. Map approach (R1', GMS-first with graceful fallback)

`GmsAvailability.check()` runs once at ViewModel init and stores a `Boolean` on `HomeUiState.mapAvailable`.

- `mapAvailable = true`: `MapPanel` hosts a `MapView` configured with `GoogleMapOptions().liteMode(true).mapToolbarEnabled(false).compassEnabled(false)`. Center follows `LocationRepository` updates, snapped to the latest fix when the panel is visible. A tap dispatches `HomeAction.OpenMaps`, which fires `Intent.ACTION_VIEW` with a `geo:lat,lon?z=15` URI and lets the OS pick the user's default maps app.
- `mapAvailable = false`: `MapPanel` renders a flat `Surface` with the locality string (if available) and an icon, no map tiles. Tap still dispatches `HomeAction.OpenMaps` so users with a non-Google maps app installed retain quick access.

Lite Mode counts as a single map load per panel composition. To prevent unrelated `HomeUiState` changes from triggering new loads, the panel keys its `MapView` on the latest location quantized to 0.001 degree (~100 m at the equator); subsequent recompositions within the same bucket reuse the existing `MapView` instance via `key()` and `remember`. Tile caching beyond the SDK's own behaviour is forbidden by the Maps Platform terms; the design does not add any cache layer.

## 10. Dependencies

Added to `gradle/libs.versions.toml` via the `update-gradle-dependency` skill:

```toml
[versions]
play-services-maps = "19.0.0"
okhttp = "5.0.0-alpha.14"
kotlinx-serialization = "1.7.3"

[libraries]
play-services-maps = { module = "com.google.android.gms:play-services-maps", version.ref = "play-services-maps" }
okhttp = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }
okhttp-mockwebserver = { module = "com.squareup.okhttp3:mockwebserver", version.ref = "okhttp" }   # testImplementation only
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinx-serialization" }
turbine = { module = "app.cash.turbine:turbine", version = "1.2.1" }                                # testImplementation only
robolectric = { module = "org.robolectric:robolectric", version = "4.14.1" }                        # testImplementation only

[plugins]
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

`androidx.core.i18n.LocalePreferences` ships inside the existing `androidx.core:core` artifact (already on the classpath through the BOM). No extra entry needed.

The project does **not** add `com.google.maps.android:maps-compose` in v1. `MapView` is wrapped with `AndroidView`, which keeps the dependency surface minimal and avoids transitively pulling Compose Maps' own opinions about Composable lifecycles. A future refactor may revisit this.

`OkHttp` plus `kotlinx-serialization` is the chosen HTTP path, used solely for Open-Meteo. Maps SDK has its own internal HTTP stack and is not routed through OkHttp.

## 11. Testing

| Layer | Style | Coverage |
| --- | --- | --- |
| `data/` repositories | JVM unit (`runTest` + `TestDispatcher`) | HTTP response parsing (mockwebserver), GMS-absent branch, debounce timing, in-memory cache validity, `Geocoder.isPresent()` false path (Robolectric) |
| `HomeViewModel` | JVM unit (Turbine) | `combine` ordering on initial empty Flows, `HomeAction.OpenMaps` chooses the geo URI built from the latest location, `HomeAction.LaunchApp` resolves the right `ComponentName` |
| Compose UI | `createComposeRule` (`androidTest`) | C2 dashboard renders all four panels under representative state; GMS-absent state hides map tiles and shows the static fallback; music-null state shows `Connect a player`; map overlays render speed, altitude, and address when the underlying flows emit values |
| Manual smoke | TBox-Mock AVD + TBox Ambient real device | Cold start under one second, panel updates at expected cadences, `geo fix` injection updates the speed / altitude / address overlays without changing the layout shape |

Test fixtures live in `testfixtures/` packages (one per source set). Builders for `Location`, `WeatherSnapshot`, `NowPlaying`, and `ShortAddress` are the SSOT for those values; tests must not declare their own `data class FakeFoo(...)` literals (CLAUDE.md#ssot-dry).

## 12. Risks and mitigations

| Risk | Mitigation |
| --- | --- |
| Carlinkit / OTTOCAST SKU without GMS reaches a real user | `mapAvailable = false` + `Geocoder.isPresent() = false` branches both kick in. Music, weather, clock, and the speed / altitude overlays keep working. Documented in CLAUDE.md as a graceful degradation, not a failure |
| Open-Meteo outage | In-memory snapshot survives until the next successful fetch. `WeatherPanel` shows the stale value with no error UI; on cold start with no network the panel renders an icon-only placeholder |
| Maps API key leak | App restriction (package + SHA-1) on the Cloud Console. Cost alarm at 80% of the free tier. The key is not committed to git |
| Maps cost growth past the free tier | `MapPanel` deliberately uses Lite Mode and a stable key; one composition = one map load. Monitoring catches MAU growth before billing crosses zero |
| User denies location permission | All location-driven surfaces (weather, address, map tiles, speed and altitude overlays) collapse to their absent branches. Clock and music continue to work, the apps bar continues to function |
| Driver-distraction concerns from store review or end users | The launcher behaves like comparable aftermarket launchers (LecoAuto, Car Launcher Pro, AGAMA): no embedded video, no inline scrolling content larger than the apps bar, Lite-Mode static map. If a future feature is genuinely distraction-prone, it gates itself locally — there is no project-wide gate to maintain |

## 13. Acceptance checklist

- [ ] `./gradlew assembleDebug` and `./gradlew lint` are green
- [ ] `./gradlew test` covers each repository and the ViewModel combine
- [ ] `./gradlew connectedAndroidTest` covers the dashboard render under representative state combinations (loaded / loading, GMS present / absent, music null / playing, location null / fixed)
- [ ] Cold start on TBox-Mock AVD remains under one second
- [ ] Permission audit table in CLAUDE.md is updated to include INTERNET and ACCESS_NETWORK_STATE with their justifications, and the ACCESS_FINE_LOCATION justification is rewritten to drop the lockout reference
- [ ] `local.properties` key `MAPS_API_KEY=` is documented in the project README's developer setup section
- [ ] CLAUDE.md `#driving-lockout` rule is rewritten to record the no-UI-gating policy, and the `gate-driving-visible-feature` skill plus its references in other skills and agents are removed
