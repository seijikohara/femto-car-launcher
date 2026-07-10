<div align="center">

<img src="logo.svg" alt="Femto Car Launcher logo" width="128">

# Femto Car Launcher

**A glanceable Android home launcher for in-car displays.**

[![CI](https://github.com/seijikohara/femto-car-launcher/actions/workflows/ci.yml/badge.svg)](https://github.com/seijikohara/femto-car-launcher/actions/workflows/ci.yml)
[![Download nightly APK](https://img.shields.io/badge/download-nightly_APK-3BE0AE?logo=android&logoColor=white)](https://github.com/seijikohara/femto-car-launcher/releases/tag/nightly)
[![Android 13+](https://img.shields.io/badge/Android-13%2B_(API_33)-3DDC84?logo=android&logoColor=white)](https://developer.android.com/about/versions/13)
[![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/develop/ui/compose)

</div>

Femto Car Launcher is an Android home-screen replacement (launcher) for
in-car displays. It targets three hardware classes: aftermarket CarPlay /
Android Auto AI boxes that inject Android into a factory display,
built-in Android head units, and smartphones mounted as a car-navigation
display. The launcher replaces the stock home screen with a single
glanceable dashboard tuned for automotive viewing distances and touch
accuracy. On a phone it also runs as a regular app — becoming the
default home screen is optional.

The minimum supported platform is Android 13 (Application Programming
Interface (API) level 33). The launcher is built for multi-region
distribution: no single market is privileged, locale-specific behaviour
(language, units, font fallback, regulation) is parameterised, and the
strictest applicable rule wins when markets diverge.

## Features

The home screen is a fixed dashboard rather than a scrolling grid of apps.

- **Map panel** — a live vector map rendered by MapLibre GL JS inside a
  WebView. The default backend uses OpenStreetMap tiles served by
  the keyless OpenFreeMap service. An optional Mapbox backend — offering
  satellite imagery, real-time traffic, and Mapbox 3D styles — is
  available by entering your own Mapbox public access token in
  Settings → Map. A further optional Google Maps backend — offering the
  roadmap, satellite, hybrid, and terrain map types with a traffic
  overlay, rendered by the Google Maps JavaScript API — is available by
  entering your own Google Maps Platform API key in the same screen. The
  view is heading-up (the map rotates so the travel
  direction points up) and offers an optional three-dimensional terrain
  relief layer (Mapterhorn elevation tiles).
- **Trip overlay** — current speed, trip distance, and average speed
  derived from the Global Positioning System (GPS), plus the
  reverse-geocoded address of the current position.
- **Weather card** — current conditions, apparent ("feels-like")
  temperature, wind, humidity, and an hourly forecast from MET Norway
  (`api.met.no`). Tap the header to open a full-screen forecast panel.
- **Calendar card** — a six-day strip and the upcoming events read from the
  device calendar, with a Settings option to choose which of the
  device's calendars are shown. Tap the header to open a full-screen panel.
- **Now-playing card** — the active media session (title, artist, source
  app, and transport controls), read through a notification-listener
  service. Tap the cover art to open a full-screen player.
- **Clock overlay** — a self-updating clock that honours the system
  12/24-hour setting, with an optional seconds display.
- **Status cluster** — graduated Wi-Fi and cellular signal strength,
  Bluetooth connection state, GPS reception, and battery level and charging
  state.
- **App dock and drawer** — an application dock that the user can attach
  to any screen edge (bottom, top, left, or right). Long-press a dock
  button or status icon to reorder or hide it. A full app drawer offers
  search, a pinned-apps row, a recently-used-apps row, an A-Z fast-scroll
  index, and small / medium / large icon-size presets.
- **Voice assistant** — a microphone button opens a bottom sheet that
  captures speech in-launcher (`android.speech.SpeechRecognizer`) with a
  live transcript. When no on-device recognizer exists or the microphone
  permission is denied, the sheet degrades to launching the system voice
  assistant, voice command, or voice search.
- **Fonts** — the head-unit system font by default, any Google Fonts
  family chosen per slot (a Latin face plus a CJK — Chinese, Japanese,
  Korean — fallback face), downloaded on demand and cached on disk, or
  any font already installed on the device, read directly with no
  download. No fonts are bundled in the APK and no Play Services are
  required.
- **Settings** — in-app preferences grouped into seven sections:
  **Appearance** (theme, accent colour, map colour matching,
  glass-effect blur and opacity, and font pairing), **Screen** (UI
  scale, screen orientation, fullscreen, keep-screen-on, dock edge,
  a dock-layout reset, driver-side card anchoring, motion reduction, and
  voice-assistant launch behaviour), **Units** (speed and temperature units,
  clock format and seconds), **Map** (backend choice, an optional Mapbox
  access token or Google Maps Platform API key to enable the paid backends,
  3D buildings, terrain, and camera behaviour), **Location**
  (location-update tuning), **Panels** (show or hide the calendar, weather,
  and music cards; which device calendars the calendar card shows; and the
  music card's spectrum, album-name, and cover-art visibility toggles), and
  **System** (shortcuts to the system notification-access and Android
  settings screens, in-app diagnostics, the open-source licenses list, and
  the privacy policy, plus a one-tap reset to defaults).

Each panel degrades gracefully. A panel whose runtime permission is denied,
or whose data source is unavailable, renders an empty or reduced state
instead of failing. The dashboard renders the same tree regardless of
vehicle motion; distraction responsibility stays with the driver and the
vehicle's own cluster.

## Download

Every merge to `main` publishes a release-signed APK as a rolling
prerelease tagged
[`nightly`](https://github.com/seijikohara/femto-car-launcher/releases/tag/nightly).
The tag is re-pointed to the built commit each run, so the release always
reflects the latest `main`. Install it with `adb install -r <apk>` or by
sideloading on the head unit. Play Store publication is not planned at this
time; sideloading is the supported installation path.

## Architecture

- **UI:** Jetpack Compose with Material 3 and Material You dynamic colour.
- **Pattern:** unidirectional data flow (UDF). State flows down through an
  immutable `UiState`; events flow up through an `(Action) -> Unit` lambda.
  Stateful screens follow the `Route` + `Screen` + `ViewModel` shape.
- **Entry point:** a single `ComponentActivity` (`MainActivity`) registered
  as a `HOME` launcher with `launchMode="singleTask"`.
- **Data:** one repository per dashboard source, each exposing a Kotlin
  `Flow`. User preferences persist through Jetpack DataStore.
- **Map page:** the live map is a separate TypeScript application under
  [`webmap/`](webmap/) (Vite + MapLibre GL JS). Gradle builds it with a
  self-provisioned Node.js + pnpm toolchain and embeds the output in the
  app's assets; the launcher loads it in a WebView.
- **Network:** the map is keyless. Weather uses MET Norway
  (`api.met.no`, free for commercial use); reverse geocoding uses the
  on-device platform geocoder by default. Both accept a configurable
  self-hosted host (see [Configuration](#configuration)).

## Tech stack

- Kotlin and Jetpack Compose (Material 3), built with the Android Gradle
  Plugin (AGP) 9.
- Gradle 9, a Java Development Kit (JDK) 21 toolchain, and Java 11
  source/target compatibility.
- `minSdk = 33` (Android 13), `targetSdk = 36`.
- Web map page: TypeScript, Vite, and MapLibre GL JS, managed with pnpm.
  The Gradle build provisions Node.js and pnpm itself, so no local
  Node.js installation is needed. Vite targets `chrome109` — the
  Android 13 factory WebView floor.

[`gradle/libs.versions.toml`](gradle/libs.versions.toml) is the single
source of truth for Gradle dependency versions
([`webmap/package.json`](webmap/package.json) for the web page).

## Project layout

```text
app/src/main/
├── java/io/github/seijikohara/femto/
│   ├── MainActivity.kt   # single launcher entry (HOME activity)
│   ├── data/             # repositories, DataStore wrappers, network APIs
│   └── ui/
│       ├── home/         # the dashboard: panels, overlays, status cluster
│       ├── drawer/       # full app drawer with pinned dock
│       ├── assistant/    # voice-assistant bottom sheet
│       ├── settings/     # in-app settings
│       ├── diagnostics/  # in-app diagnostics report
│       ├── licenses/     # open-source licenses screen
│       ├── fontpicker/   # per-slot Google Fonts picker
│       ├── common/       # shared modal-sheet helpers
│       ├── locale/       # unit and locale formatting
│       └── theme/        # FemtoTheme, design tokens, previews
└── res/                  # themes, strings (per-locale), launcher icon
webmap/                   # TypeScript source of the live map page
                          # (built into app assets by Gradle)
```

## Build and run

**Prerequisites:** JDK 21, Android Studio (latest stable), and an Android
13+ device or Android Virtual Device (AVD). Node.js and pnpm are **not**
prerequisites — the Gradle build downloads its own copies to compile the
`webmap/` page.

```bash
./gradlew assembleDebug      # build the debug APK (includes the webmap build)
./gradlew test               # JVM unit tests
./gradlew lint               # Android Lint
./gradlew connectedAndroidTest  # instrumented tests (device / emulator)
./gradlew spotlessCheck      # format / lint check
./gradlew spotlessApply      # auto-fix format violations
```

The debug Android Package (APK) is written to
`app/build/outputs/apk/debug/app-debug.apk`. Install it with
`adb install -r <apk>` or run the app from Android Studio. To smoke-test on
an emulator, create an AVD that approximates a head-unit display (a wide
landscape profile) and launch the app as the home activity, or use a
regular phone AVD in portrait — the dashboard adapts to both.

## Configuration

The launcher runs with no configuration: `./gradlew assembleDebug`
produces a working build. The default OSM map backend (MapLibre + OpenFreeMap)
needs no API key. To enable the optional Mapbox backend, enter your own Mapbox
public access token (`pk.…`) in Settings → Map on the device; the token is
stored on-device and requires no build-time configuration. The optional Google
Maps backend works the same way: enter your own Google Maps Platform API key in
Settings → Map. The key needs the Maps JavaScript API enabled and an
HTTP-referrer restriction that allows `https://appassets.androidplatform.net/*`;
it is stored on-device and requires no build-time configuration. Optionally,
also enter a **Map ID** (created in the Google Cloud console) to render a vector
map with heading-up rotation, tilt, and 3D; leave it blank for a flat north-up
raster map. An invalid Map ID renders nothing — clear it to fall back to the
raster map.

Two network services default to shared public endpoints that are fine for
development and evaluation but are rate-limited and unsuitable for
production traffic. Override them for a release build through the git-ignored
`local.properties` file, which feeds `BuildConfig` at build time:

| Property | Purpose | Default |
| --- | --- | --- |
| `WEATHER_BASE_URL` | MET Norway host (or a self-hosted proxy) | `https://api.met.no/` |
| `GEOCODER_BASE_URL` | Self-hosted Nominatim-compatible reverse-geocoding host | empty (on-device platform geocoder) |
| `GEOCODER_API_KEY` | Geocoder key (when the host requires one) | empty |

Weather defaults to the public MET Norway endpoint (free for commercial use,
ToS-compliant). Reverse geocoding has **no** public default: when
`GEOCODER_BASE_URL` is empty the launcher uses the on-device platform geocoder;
set it to a self-hosted host to use network geocoding instead.

## Continuous integration

The [`CI`](.github/workflows/ci.yml) workflow runs on every pull request
and on pushes to `main`:

- **Checks** (all triggers): three parallel jobs on Temurin JDK 21
  runners — `static-checks` (`spotlessCheck lint`), `unit-tests`
  (`test verifyRoborazziDebug`), and `assemble` (`assembleDebug`).
- **Validate** (all triggers): a lightweight gate that requires every
  `Checks` job to succeed; this is the single required status check.
- **Publish nightly** (`main` pushes and manual `workflow_dispatch`
  only, after `Validate` passes): builds the release-signed APK and
  AAB behind the [Download](#download) badge.

The nightly APK is **release-signed**; local and contributor
`./gradlew assembleRelease` builds stay **unsigned**, because the signing
config is registered only when the keystore environment variables are
present. Maintainer setup for the signing secrets lives in
[`.github/RELEASING.md`](.github/RELEASING.md).
