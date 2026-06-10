# Femto Car Launcher

Femto Car Launcher is an Android home-screen replacement (launcher) for
in-car head units. It targets two hardware classes: aftermarket CarPlay /
Android Auto AI boxes that inject Android into a factory display, and
built-in Android head units. The launcher replaces the stock home screen
with a single glanceable dashboard tuned for automotive viewing distances
and touch accuracy.

The minimum supported platform is Android 13 (Application Programming
Interface (API) level 33). The launcher is built for multi-region
distribution: no single market is privileged, locale-specific behaviour
(language, units, font fallback, regulation) is parameterised, and the
strictest applicable rule wins when markets diverge.

## Features

The home screen is a fixed dashboard rather than a scrolling grid of apps.

- **Map panel** — a live vector map rendered by MapLibre GL JS inside a
  WebView, with OpenStreetMap tiles served by the keyless OpenFreeMap
  service. The view is heading-up (the map rotates so the travel
  direction points up) and offers an optional three-dimensional terrain
  relief layer (Mapterhorn elevation tiles). Head units whose WebView
  cannot sustain WebGL can switch to a Snapshot mode that rasterises the
  same vector map off-screen. No API key is required in either mode.
- **Trip overlay** — current speed, trip distance, and average speed
  derived from the Global Positioning System (GPS), plus the
  reverse-geocoded address of the current position.
- **Weather card** — current conditions, apparent ("feels-like")
  temperature, wind, humidity, and an hourly forecast from the Open-Meteo
  service.
- **Calendar card** — a six-day strip and the upcoming events read from the
  device calendar.
- **Now-playing card** — the active media session (title, artist, source
  app, and transport controls), read through a notification-listener
  service.
- **Clock overlay** — a self-updating clock that honours the system
  12/24-hour setting, with an optional seconds display.
- **Status cluster** — graduated Wi-Fi and cellular signal strength,
  Bluetooth connection state, GPS reception, and battery level and charging
  state.
- **App dock and drawer** — an application dock that the user can attach
  to any screen edge (bottom, top, left, or right), and a full app drawer
  with search, a pinned-apps row, and small / medium / large icon-size
  presets.
- **Voice assistant** — a microphone button opens a bottom sheet that
  captures speech in-launcher (`android.speech.SpeechRecognizer`) with a
  live transcript. When no on-device recognizer exists or the microphone
  permission is denied, the sheet degrades to launching the system voice
  assistant, voice command, or voice search.
- **Fonts** — the head-unit system font by default, or any Google Fonts
  family chosen per slot (a Latin face plus a CJK — Chinese, Japanese,
  Korean — fallback face), downloaded on demand and cached on disk. No
  fonts are bundled in the APK and no Play Services are required.
- **Settings** — in-app preferences for theme (follow-system / light /
  dark), accent colour (Material You dynamic colour or a fixed seed
  preset), screen orientation (auto / landscape / portrait), speed and
  temperature units, clock format and seconds, a fullscreen toggle that
  hides the system bars, dock edge, drawer icon size, glass-effect blur
  and opacity for the dashboard overlays, map rendering (Live / Snapshot
  mode, render quality, terrain), location-update tuning, and the font
  pairing — plus shortcuts to the system notification-access and Android
  settings screens, and a one-tap reset to defaults.

Each panel degrades gracefully. A panel whose runtime permission is denied,
or whose data source is unavailable, renders an empty or reduced state
instead of failing. The dashboard renders the same tree regardless of
vehicle motion; distraction responsibility stays with the driver and the
vehicle's own cluster.

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
- **Network:** the map is keyless. Weather (Open-Meteo) and reverse
  geocoding (OpenStreetMap Nominatim) default to public endpoints and
  accept configurable production hosts (see [Configuration](#configuration)).

Project rules, the design system, and coding conventions live in
[`CLAUDE.md`](CLAUDE.md).

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
([`webmap/package.json`](webmap/package.json) for the web page); see
[`CLAUDE.md#tech-stack`](CLAUDE.md#tech-stack) for the resolved list.

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
│       ├── fontpicker/   # per-slot Google Fonts picker
│       ├── locale/       # unit and locale formatting
│       └── theme/        # FemtoTheme, design tokens, previews
└── res/                  # themes, strings (per-locale variants)
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
landscape profile) and launch the app as the home activity.

## Configuration

The launcher runs with no configuration: `./gradlew assembleDebug`
produces a working build, and the map needs no API key.

Two network services default to shared public endpoints that are suitable
for a proof of concept but are rate-limited and unsuitable for production
traffic. Override them for a release build through the git-ignored
`local.properties` file, which feeds `BuildConfig` at build time:

| Property | Purpose | Default |
| --- | --- | --- |
| `WEATHER_BASE_URL` | Open-Meteo host | public Open-Meteo endpoint |
| `WEATHER_API_KEY` | Open-Meteo key (keyed/self-hosted) | empty |
| `GEOCODER_BASE_URL` | Nominatim reverse-geocoding host | public Nominatim endpoint |
| `GEOCODER_API_KEY` | Geocoder key (when the host requires one) | empty |

When a key is absent, the corresponding service falls back to its public
endpoint.

## Continuous integration

The [`CI`](.github/workflows/ci.yml) workflow runs on every pull request
and on pushes to `main`:

- **Validate** (all triggers): runs
  `./gradlew spotlessCheck test lint assembleDebug` on a Temurin JDK 21
  runner.
- **Publish nightly** (`main` pushes and manual `workflow_dispatch` only):
  builds a release-signed APK and republishes a rolling prerelease.

### Nightly build

On every merge to `main`, CI builds a release-signed APK and republishes it
as a rolling prerelease tagged `nightly`. The tag is re-pointed to the built
commit each run, so the release always reflects the latest `main`. Download
the latest APK from the [`nightly` release](../../releases/tag/nightly).

The nightly APK is **release-signed**. Local and contributor
`./gradlew assembleRelease` builds stay **unsigned**, because no keystore
environment variables are present. The signing config is registered only
when `RELEASE_KEYSTORE_PATH` is set, so local release builds keep working.

### Signing secrets

A maintainer must add four repository secrets (Settings -> Secrets and
variables -> Actions) before the nightly job can sign:

| Secret | Contents |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | Base64 of the upload keystore (`.jks`) |
| `RELEASE_KEYSTORE_PASSWORD` | Keystore (store) password |
| `RELEASE_KEY_ALIAS` | Key alias inside the keystore |
| `RELEASE_KEY_PASSWORD` | Password for that key |

Generate an upload keystore once:

```bash
keytool -genkeypair -v \
  -keystore release.jks \
  -alias femto-upload \
  -keyalg RSA -keysize 2048 -validity 10000
```

Base64-encode it for the `RELEASE_KEYSTORE_BASE64` secret:

```bash
# macOS
base64 -i release.jks | pbcopy
# Linux
base64 -w0 release.jks
```

Keep `release.jks` out of version control. If `RELEASE_KEYSTORE_BASE64` is
missing, the nightly job fails with an explicit message instead of
publishing an unsigned APK.

## Contributing

Project rules, code style, and design-system policies live in
[`CLAUDE.md`](CLAUDE.md); read it before contributing. Key sections:
[design system](CLAUDE.md#design-system),
[Compose architecture](CLAUDE.md#compose-architecture),
[Kotlin style](CLAUDE.md#kotlin-style), and [testing](CLAUDE.md#testing).
