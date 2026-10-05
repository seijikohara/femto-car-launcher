# Security Policy

## Supported versions

Femto Car Launcher ships two channels (see [Installation](README.md#installation)): a dated stable release and a rolling `nightly` prerelease. The supported build is the latest stable release. The latest nightly build is also supported, as an early build of the next stable release. Older builds of either channel are not supported.

| Build | Supported |
| --- | --- |
| Latest stable release | Yes |
| Latest nightly build | Yes — as an early build of the next stable release |
| Older builds (either channel) | No — install the latest stable release or nightly build |

## Report a vulnerability

Report vulnerabilities through GitHub's private vulnerability reporting: <https://github.com/seijikohara/femto-car-launcher/security/advisories/new>. Do not open a public issue for a vulnerability.

Include in the report:

- The version name, shown in **Settings → System → Diagnostics**. The nightly build's version name carries a `-nightly` suffix, so the version name alone identifies the channel.
- The device class (aftermarket CarPlay / Android Auto AI box, Android head unit, car-mounted phone, or emulator) and the Android version.
- Steps to reproduce, and the impact you observed or expect.

The project is a hobby project with a single maintainer and no service-level commitment ([Terms of Service](TERMS.md)). The maintainer acknowledges reports and works on fixes on a best-effort basis. For a fix that changes the Android package (APK), the disclosure flow is:

1. The maintainer confirms the report.
2. The maintainer lands the fix on `develop` and waits for the nightly build that carries the fix.
3. The maintainer merges `develop` into `main`, so the release job publishes the fix in the next stable build.
4. The maintainer publishes the advisory, with credit to the reporter unless the reporter asks otherwise.

A fix confined to the continuous integration (CI) and release workflows changes no APK and publishes no build: the fix takes effect as it merges, and the advisory follows once the fix reaches `main`.

## Scope

In scope:

- The Android app under `app/`.
- The bundled map page under `webmap/`, including its bridge to the app.
- The CI and release workflows under `.github/workflows/`, and the signed stable and nightly artifacts they publish.

Out of scope:

- The third-party services the app contacts: map tile and terrain hosts, the weather service, and Google Maps Platform. Report those to the service operator. The [Privacy Policy](PRIVACY.md) lists the endpoints.
- The device's Android System WebView, which the app uses but does not ship.
- A vulnerable dependency with no reachable code path in the app. Dependency updates follow the process below.

## Dependency updates

Renovate opens a pull request for each vulnerability alert outside the weekly schedule and labels it `security`; routine dependency updates follow the weekly schedule in [`renovate.json5`](renovate.json5), targeting `develop`. An update of either kind that changes the APK ships in the next nightly build after it merges, and reaches the stable channel with the next release.
