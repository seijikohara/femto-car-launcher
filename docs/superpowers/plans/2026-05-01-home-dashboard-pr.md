# Pull request: home dashboard (Plan B)

## Title (≤70 chars)

`feat: home dashboard with map, clock, weather, music, and apps bar`

## Body

### Summary

- Replace the single-purpose apps grid with a LecoAuto-inspired
  multi-panel dashboard. Layout C2 — map hero on the left, vertical
  stack of three panels (clock, weather, now-playing) on the right,
  apps bar pinned at the bottom. The full apps grid moves into a
  separate AppDrawer route.
- Six data repositories produce Kotlin Flows (`ClockRepository`,
  `LocationRepository`, `ReverseGeocoderRepository`,
  `WeatherRepository` over Open-Meteo, `MusicSessionRepository` via
  `MediaSessionManager`, `GmsAvailability`). `HomeViewModel` combines
  them into a single `HomeUiState`.
- Maps integration uses Google Maps SDK Lite Mode with a runtime
  GMS-availability check that gracefully degrades to a static
  fallback on AOSP-only Carlinkit / OTTOCAST builds.
- Weather temperature reads
  `androidx.core.text.util.LocalePreferences.getTemperatureUnit()`;
  speed/altitude unit derives from the imperial-country set
  (`US`/`GB`/`MM`) in `ui/locale/SystemUnits.kt`.
- The launcher renders the same dashboard regardless of vehicle
  motion. The earlier driving-lockout policy is fully retracted —
  the spec, `CLAUDE.md`, and the `gate-driving-visible-feature`
  skill have all been removed.
- 30 commits on `docs/home-dashboard-spec`, all green:
  `assembleDebug`, `test`, `lint`, `spotlessCheck`.

### Test plan

Local verification (already green on this branch):

- [x] `./gradlew spotlessCheck`
- [x] `./gradlew assembleDebug`
- [x] `./gradlew test` — 18 unit tests pass (SystemUnits,
  ReverseGeocoderRepository, WeatherRepository, GmsAvailability,
  HomeViewModel)
- [x] `./gradlew lint` — 0 errors after the
  `ACCESS_COARSE_LOCATION` fix; warnings about newer dependency
  versions deferred to a separate dependency-bump PR

Smoke verification (deferred until reviewer has hardware):

- [ ] `./gradlew connectedDebugAndroidTest` on a TBox-Mock AVD
  (the 5 Compose UI tests cover ClockPanel, WeatherPanel,
  MusicPanel, AppsBar, DashboardScaffold)
- [ ] Real-device install on a TBox Ambient or equivalent
  Carlinkit / OTTOCAST head unit — confirm:
  - Map tiles render when a `MAPS_API_KEY` is configured in
    `local.properties`
  - The static-fallback branch renders cleanly when no key is set
  - `ACCESS_FINE_LOCATION` runtime grant flow works
  - MediaSession picks up the active player after the user grants
    Notification Listener access

### Known v1 limits

- `HomeAction.OpenAppDrawer` does not yet trigger the drawer.
  `MainActivity` reads a local `showDrawer` flag but no event flow
  bridges the ViewModel to the activity. Follow-up: introduce a
  `SharedFlow<HomeEvent>` from the ViewModel.
- `HomeAction.OpenMaps`, `Shortcut`, `Music`, and
  `ConnectMusicPlayer` are wired to `HomeViewModel.onAction()` but
  the body is a stub. Follow-up: route them to real intent / media
  controls.
- `MapView` is hosted via `AndroidView` with only `onCreate(null)`;
  the full lifecycle binding is deferred. Lite Mode renders a
  static bitmap so this is acceptable for v1; revisit when adding
  interactive panning.

### Out of scope (deliberate)

- Theme switching UI (font / wallpaper / color) — spec change only.
- OBD-II integration.
- Voice assistant.
- Per-app settings / drawer customisation.

### Documents

- Spec: `docs/superpowers/specs/2026-05-01-home-dashboard-design.md`
- Plan: `docs/superpowers/plans/2026-05-01-home-dashboard-plan.md`

### Permissions audit

This PR adds three permissions, all justified in
`CLAUDE.md#permissions`:

- `ACCESS_COARSE_LOCATION` (Android 12+ pairing requirement)
- `ACCESS_NETWORK_STATE` (Maps SDK best-practice probe)
- `INTERNET` (Open-Meteo HTTP + Maps tile fetch)

The `<service>` for `MusicSessionListenerService` declares
`android:permission="BIND_NOTIFICATION_LISTENER_SERVICE"` — that
attribute names the **caller's** required permission, so it does
not register a new `<uses-permission>` for our app.
