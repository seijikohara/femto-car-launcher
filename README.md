# Femto Car Launcher

Android home launcher for car head units (OTTOCAST / Carlinkit / built-in Android units).
MVP targets Android 13 (API 33). Reference product: [LecoAuto](https://lecoauto.com/?lang=ja).

## Overview

Femto Car Launcher is a home-screen replacement designed for aftermarket head units.
It targets multi-region distribution: no single market is privileged in design, code, or
documentation — locale-specific behaviour is parameterised and the strictest applicable rule wins
when markets diverge. Project rules and conventions live in [`CLAUDE.md`](CLAUDE.md).

## Tech stack

- Kotlin, Jetpack Compose (Material 3), AGP 9
- Gradle 9, JDK 21 toolchain, Java 11 source/target
- `minSdk = 33`, `targetSdk = 36`

See [`CLAUDE.md#tech-stack`](CLAUDE.md#tech-stack) for the full dependency list.

## Developer setup

**Prerequisites:** JDK 21, Android Studio (latest stable), an Android 13+ device or AVD.

```bash
./gradlew assembleDebug    # build debug APK
./gradlew test             # JVM unit tests
./gradlew spotlessCheck    # format / lint check
```

### Map tiles

The launcher renders OpenStreetMap vector tiles on the home dashboard
through [MapLibre](https://maplibre.org/), served by the free, keyless
[OpenFreeMap](https://openfreemap.org/) service. There is **no API key
to configure** — `./gradlew assembleDebug` produces a build with a
working map out of the box. The pane shows a heading-up view (the map
rotates so the travel direction points up) and falls back to a static
placeholder until a location fix is available. Light mode uses the
Positron style; dark mode uses a bundled style under
`app/src/main/assets/map/`.

## Conventions

Project rules, code style, and design system policies live in
[`CLAUDE.md`](CLAUDE.md). Read it before contributing. Key sections:
[design system](CLAUDE.md#design-system), [Kotlin style](CLAUDE.md#kotlin-style),
[testing](CLAUDE.md#testing).
