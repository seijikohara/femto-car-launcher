# Security Policy

## Supported versions

Femto Car Launcher ships two channels (see
[Installation](README.md#installation)): a versioned stable release
and a rolling `nightly` prerelease. The supported build is the latest
stable release; the rolling nightly is supported as a preview of the
next one; older builds of either channel are not supported.

| Build | Supported |
| --- | --- |
| Latest stable release | Yes |
| Latest `nightly` | Yes — as a preview of the next stable release |
| Older builds (either channel) | No — install the latest stable release or nightly |

## Report a vulnerability

Report vulnerabilities through GitHub's private vulnerability
reporting:
<https://github.com/seijikohara/femto-car-launcher/security/advisories/new>.
Do not open a public issue for a vulnerability.

Include in the report:

- The app version, shown in Settings → System → Diagnostics. The
  nightly build's version name carries a `-nightly` suffix, so it
  identifies the channel on its own.
- The device class (aftermarket CarPlay / Android Auto AI box,
  built-in Android head unit, phone mount, or emulator) and the
  Android version.
- Steps to reproduce, and the impact you observed or expect.

The project is a hobby project with a single maintainer and no
service-level commitment ([`TERMS.md`](TERMS.md)). The maintainer
acknowledges reports and works on fixes on a best-effort basis. The
disclosure flow is: the maintainer confirms the report, lands a fix on
`develop`, waits for the nightly that carries it, merges `develop`
into `main` so the release job publishes it in the next stable build,
and then publishes the advisory with credit to the reporter unless the
reporter asks otherwise.

## Scope

In scope:

- The Android app under `app/`.
- The bundled map page under `webmap/`, including its bridge to the
  app.
- The CI and release workflows under `.github/workflows/`, and the
  signed stable and nightly artifacts they publish.

Out of scope:

- The third-party services the app talks to: map tile and terrain
  hosts, the weather service, and Google Maps Platform. Report those to
  the service operator. [`PRIVACY.md`](PRIVACY.md) lists the endpoints.
- The device's Android System WebView, which the app uses but does not
  ship.
- A vulnerable dependency with no reachable code path in the app.
  Dependency updates follow the process below.

## Dependency updates

Renovate opens a pull request for every vulnerability alert as soon as
the alert appears and labels it `security`; routine dependency updates
follow the weekly schedule in [`renovate.json5`](renovate.json5),
targeting `develop`. An update of either kind that changes the APK
ships in the next nightly after it merges, and reaches the stable
channel with the next release.
