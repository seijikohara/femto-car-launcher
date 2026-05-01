# Home Dashboard (Plan B) — Design Spec

| | |
| --- | --- |
| Date | 2026-05-01 |
| Owner | Seiji Kohara |
| Status | Draft, awaiting review |
| Targets | `app/src/main/java/io/github/seijikohara/femto/ui/home/`, new `ui/drawer/`, expanded `data/` |

## 1. Goal

Replace the current single-purpose home screen (one full-screen `LazyVerticalGrid` of installed apps) with a LecoAuto-inspired multi-panel **dashboard**: a hero map on the left, a vertical stack of three ambient panels on the right (clock, weather, now-playing), and a fixed application bar at the bottom that opens a separate full-screen drawer for the complete grid.

The dashboard surfaces five information classes that drivers actually consult on a stationary head unit, while keeping the launcher's "out of the driver's way during motion" promise from `gate-driving-visible-feature`.

## 2. Non-goals

The following are **explicitly deferred** and must not creep into Plan B:

- Speed, altitude, or any vehicle-state numeric display
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

**Landscape orientation, all dimensions adapt to available width.**

```
+----------+--------------+---------+
|          |   TIME       |         |
|          |   14:32      |         |
|          |   Tue May 1  |         |
|          +--------------+         |
|  Map     |   WEATHER    |         |
|  (Lite)  |   ☀ 18°      |         |
|          |   Shibuya,   |         |
|          |   Tokyo      |         |
|          +--------------+         |
|          |   NOW PLAYING|         |
|          |   ▶ Strobe   |         |
|          |   deadmau5   |         |
|          |   ◀◀  ⏯  ▶▶ |         |
+----------+--------------+---------+
|  ≡  phone  music  maps  cam  nav  |  AppsBar
+------------------------------------+
```

| Region | Composable | Notes |
| --- | --- | --- |
| Left hero | `MapPanel` | Google Maps SDK Lite Mode, ~60% of width, full inner column height |
| Right column | `ClockPanel`, `WeatherPanel`, `MusicPanel` | Three M3-squircle surfaces, equal vertical share |
| Bottom bar | `AppsBar` | Fixed height, six tiles: ≡ (drawer), and five action shortcuts resolved by intent category, not by hard-coded package name |

**Driving-locked subset (when `isDrivingLocked = true`)**:

```
+----------+--------------+---------+
|          |   14:32      |         |
| Map      |   Tue May 1  |         |
| hidden   +--------------+         |
| (locked  |   ☀ 18°      |         |
|  place-  |   Shibuya    |         |
|  holder) +--------------+         |
|          |   ▶ Strobe   |         |
|          |   ◀◀  ⏯  ▶▶ |         |
+----------+--------------+---------+
|       Available when stopped       |
+------------------------------------+
```

The clock, weather, address and music transport remain visible because they are read-only ambient surfaces with large tap targets. The map and the apps bar are replaced with a single `Available when stopped` placeholder. Rationale and policy live in `CLAUDE.md#driving-lockout` and the `gate-driving-visible-feature` skill, which is the SSOT.

## 4. File and component layout

```
app/src/main/java/io/github/seijikohara/femto/
├── data/
│   ├── ClockRepository.kt              new
│   ├── LocationRepository.kt           new (extracted from DrivingStateRepository)
│   ├── DrivingStateRepository.kt       refactored to derive from LocationRepository
│   ├── ReverseGeocoderRepository.kt    new
│   ├── WeatherRepository.kt            new (Open-Meteo)
│   ├── MusicSessionRepository.kt       new (NotificationListenerService bridge)
│   ├── MusicSessionListenerService.kt  new (NotificationListenerService impl)
│   ├── GmsAvailability.kt              new
│   ├── AppEntry.kt                     unchanged
│   ├── AppsRepository.kt               unchanged
│   ├── DrivingState.kt                 unchanged surface (rememberDrivingLockState still works)
│   └── FontPreferences.kt              unchanged
├── ui/
│   ├── home/
│   │   ├── HomeRoute.kt                expanded VM wiring
│   │   ├── HomeScreen.kt               rewritten as DashboardScaffold
│   │   ├── HomeUiState.kt              expanded data class
│   │   ├── HomeViewModel.kt            expanded with combine
│   │   ├── HomeAction.kt               new (extracted sealed interface)
│   │   └── components/
│   │       ├── AppTile.kt              unchanged, reused
│   │       ├── ClockPanel.kt           new
│   │       ├── WeatherPanel.kt         new
│   │       ├── MusicPanel.kt           new
│   │       ├── MapPanel.kt             new (AndroidView wrapper around MapView Lite Mode)
│   │       ├── AppsBar.kt              new
│   │       └── DashboardScaffold.kt    new (overall C2 frame, switches subset on lockout)
│   └── drawer/
│       ├── AppDrawerRoute.kt           new
│       └── AppDrawerScreen.kt          new (the existing LazyVerticalGrid lifted into its own screen)
└── MainActivity.kt                      one new NavHost destination for AppDrawer

app/src/test/.../testfixtures/           new package: FakeLocation, FakeWeatherSnapshot, FakeNowPlaying, FakeAddress
app/src/androidTest/.../testfixtures/    new package: parallel fixtures for UI tests
```

**Naming.** New repositories follow the existing `<Concept>Repository.kt` shape; new panels follow `<Concept>Panel.kt` to distinguish dashboard surfaces from generic widgets. The existing `DrivingState.kt` `rememberDrivingLockState()` Composable keeps its public surface so callers do not change.

## 5. Data flow

### 5.1 Repositories

| Repository | Output | Refresh trigger | Failure / absence behaviour | Underlying API |
| --- | --- | --- | --- | --- |
| `ClockRepository` | `Flow<ClockTick>` | `Intent.ACTION_TIME_TICK` system broadcast (1/min), plus immediate value on subscribe | Cannot fail (OS-supplied) | `Context.registerReceiver` |
| `LocationRepository` | `Flow<Location?>` | `LocationManagerCompat.requestLocationUpdates` on `GPS_PROVIDER`, 1 Hz default interval | No `ACCESS_FINE_LOCATION` -> emits `null` | `androidx.core.location` |
| `DrivingStateRepository` | `Flow<Boolean>` (locked) | Maps `LocationRepository` via `speed >= 5 km/h` | Unknown speed -> `true` (safe default) | derived |
| `ReverseGeocoderRepository` | `Flow<ShortAddress?>` | `LocationRepository` debounced by 100 m / 30 s | `Geocoder.isPresent() == false`, exception, or empty result -> `null` | `Geocoder` async (API 33) |
| `WeatherRepository` | `Flow<WeatherSnapshot?>` | `LocationRepository` gated to one fetch per 30 min or 5 km displacement | HTTP / parse failure -> last cached value (in-memory) -> `null` | Open-Meteo `/v1/forecast` |
| `MusicSessionRepository` | `Flow<NowPlaying?>` | `MediaSessionManager` + `NotificationListener` callbacks | Listener access not granted -> `null` | `MediaSessionManager.OnActiveSessionsChangedListener` via `MusicSessionListenerService` |
| `GmsAvailability` | `suspend fun check(): Boolean` | One-shot at `HomeViewModel` init | Not present -> `false` | `GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable` |

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
    weatherRepo.snapshotFlow(),
    geocoderRepo.addressFlow(),
    musicRepo.nowPlayingFlow(),
    drivingStateRepo.lockedFlow(),
    appsRepo.appsFlow(),
) { clock, weather, address, music, locked, apps ->
    HomeUiState(
        clock = clock,
        weather = weather,
        address = address,
        nowPlaying = music,
        isDrivingLocked = locked,
        apps = apps,
        mapAvailable = gmsAvailable,
        isLoading = apps.isEmpty(),
    )
}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Initial)
```

`HomeAction` covers `LaunchApp(ComponentName)`, `OpenAppDrawer`, `OpenMaps`, and `Music(MusicCommand)` where `MusicCommand` is a sealed interface over `Play`, `Pause`, `SkipNext`, `SkipPrevious`. Actions resolve in the ViewModel; UI emits them and never side-effects directly.

## 6. Locale and units

System locale and per-app locale preferences drive every user-facing format. No hard-coded units, no per-region branches.

| Concern | API | Fallback |
| --- | --- | --- |
| 12 h vs 24 h | `DateFormat.is24HourFormat(context)` | (the API itself falls back to locale) |
| Date format | `DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.getDefault())` | locale default |
| Temperature unit | `LocalePreferences.getTemperatureUnit()` from `androidx.core.i18n` | locale CLDR default |
| Address language | `Geocoder(context, Locale.getDefault())` | platform default |

The Open-Meteo request appends `temperature_unit=fahrenheit` or `celsius` based on `LocalePreferences.getTemperatureUnit()` so the API returns server-converted values; the client never converts.

## 7. Driving lockout

`DashboardScaffold` selects between two render trees on `uiState.isDrivingLocked`:

- **Stopped**: Map (if `mapAvailable`) + right stack + apps bar.
- **Locked**: `Available when stopped` placeholder occupies the map region and replaces the apps bar; the right stack stays, with its inner typography promoted one M3 step (e.g., `headlineSmall` -> `headlineMedium`) to favour glance-readability.

Per `gate-driving-visible-feature`, all visible-while-locked text uses `MaterialTheme.typography.*` styles that meet `FemtoDimens.MinBodyTextSize` (18 sp). Music transport buttons enforce `FemtoDimens.MinTouchTarget` (64 dp). Map tiles, animated transitions, and arbitrary app launches are gated.

The "phone exception" some markets allow at the OS dialer level is **out of scope**; the launcher does not surface phone shortcuts during motion. An incoming call surface comes from the dialer, not from the launcher.

## 8. Permissions

### 8.1 `<uses-permission>` audit log delta (CLAUDE.md#permissions)

| Permission | New / existing | One-line justification |
| --- | --- | --- |
| `ACCESS_FINE_LOCATION` | existing | Driving-lockout via GPS speed (no change) |
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
| `HomeViewModel` | JVM unit (Turbine) | `combine` ordering on initial empty Flows, lockout transition flips the rendered subset, `HomeAction.OpenMaps` chooses the geo URI built from the latest location |
| Compose UI | `createComposeRule` (`androidTest`) | C2 dashboard renders all four panels in stopped state; locked state hides map and apps bar; GMS-absent state hides map and shows fallback; music-null state shows `Connect a player` |
| Manual smoke | TBox-Mock AVD + TBox Ambient real device | Cold start under one second, panel updates at expected cadences, `geo fix` driving simulation flips the lockout subset |

Test fixtures live in `testfixtures/` packages (one per source set). Builders for `Location`, `WeatherSnapshot`, `NowPlaying`, and `ShortAddress` are the SSOT for those values; tests must not declare their own `data class FakeFoo(...)` literals (CLAUDE.md#ssot-dry).

## 12. Risks and mitigations

| Risk | Mitigation |
| --- | --- |
| Carlinkit / OTTOCAST SKU without GMS reaches a real user | `mapAvailable = false` + `Geocoder.isPresent() = false` branches both kick in. Music, weather, clock keep working. Documented in CLAUDE.md as a graceful degradation, not a failure |
| Open-Meteo outage | In-memory snapshot survives until the next successful fetch. `WeatherPanel` shows the stale value with no error UI; on cold start with no network the panel renders an icon-only placeholder |
| Maps API key leak | App restriction (package + SHA-1) on the Cloud Console. Cost alarm at 80% of the free tier. The key is not committed to git |
| Maps cost growth past the free tier | `MapPanel` deliberately uses Lite Mode and a stable key; one composition = one map load. Monitoring catches MAU growth before billing crosses zero |
| User denies location permission | All location-driven panels (weather, address, map) collapse to their absent branches. Clock and music continue to work, the apps bar continues to function, the lockout stays at its safe `true` default |
| Driver-distraction policy review (Play Store, EU UN-ECE R10, NHTSA, JP, CN, KR, AU) | The SSOT is `gate-driving-visible-feature`. The design routes every driver-visible surface through the lockout switch documented there |

## 13. Acceptance checklist

- [ ] `./gradlew assembleDebug` and `./gradlew lint` are green
- [ ] `./gradlew test` covers each repository and the ViewModel combine
- [ ] `./gradlew connectedAndroidTest` covers the four dashboard render states
- [ ] Cold start on TBox-Mock AVD remains under one second
- [ ] Permission audit table in CLAUDE.md is updated to include INTERNET and ACCESS_NETWORK_STATE with their justifications
- [ ] `local.properties` key `MAPS_API_KEY=` is documented in the project README's developer setup section
- [ ] `gate-driving-visible-feature` skill is cited in the new dashboard composables that gate on `isDrivingLocked`
