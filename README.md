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

### Maps API key

The launcher renders a Lite Mode Google Map on the home dashboard.
To see real tiles in your local debug build:

1. Create a Maps SDK for Android key at
   <https://console.cloud.google.com/google/maps-apis/credentials>.
2. Restrict the key by Android app fingerprint (package name +
   debug + release SHA-1).
3. Add the key to `local.properties` (gitignored):

   ```
   MAPS_API_KEY=AIza...
   ```

4. Re-run `./gradlew assembleDebug`. The map appears once the
   permission grant lands at runtime; without a key the panel
   shows the static fallback.

## Conventions

Project rules, code style, and design system policies live in
[`CLAUDE.md`](CLAUDE.md). Read it before contributing. Key sections:
[design system](CLAUDE.md#design-system), [Kotlin style](CLAUDE.md#kotlin-style),
[testing](CLAUDE.md#testing).
