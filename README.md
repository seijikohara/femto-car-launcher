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

## Continuous integration

The [`CI`](.github/workflows/ci.yml) workflow runs on every pull request and on
pushes to `main`:

- **Validate** (all triggers): runs
  `./gradlew spotlessCheck test lint assembleDebug` on a Temurin JDK 21 runner.
- **Publish nightly** (`main` pushes and manual `workflow_dispatch` only): builds a
  release-signed APK and republishes a rolling prerelease.

### Nightly build

On every merge to `main`, CI builds a release-signed APK and republishes it as a
rolling prerelease tagged `nightly`. The tag is re-pointed to the built commit each
run, so the release always reflects the latest `main`. Download the latest APK from
the [`nightly` release](../../releases/tag/nightly).

The nightly APK is **release-signed**; local and contributor `./gradlew
assembleRelease` builds stay **unsigned** because no keystore environment variables
are present. The signing config is registered only when `RELEASE_KEYSTORE_PATH` is
set, so local release builds keep working.

### Signing secrets

A maintainer must add four repository secrets (Settings -> Secrets and variables ->
Actions) before the nightly job can sign:

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

Keep `release.jks` out of version control. If `RELEASE_KEYSTORE_BASE64` is missing,
the nightly job fails with an explicit message instead of publishing an unsigned APK.

## Conventions

Project rules, code style, and design system policies live in
[`CLAUDE.md`](CLAUDE.md). Read it before contributing. Key sections:
[design system](CLAUDE.md#design-system), [Kotlin style](CLAUDE.md#kotlin-style),
[testing](CLAUDE.md#testing).
