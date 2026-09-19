# Security Policy

## Supported versions

Femto Car Launcher ships as a rolling `nightly` prerelease built from
every merge to `main` (see [Installation](README.md#installation)).
No versioned release exists yet, so the latest nightly is the only
supported build. A fix reaches users in the next nightly.

| Build | Supported |
| --- | --- |
| Latest `nightly` | Yes |
| Older nightlies | No — install the latest nightly first |

## Report a vulnerability

Report vulnerabilities through GitHub's private vulnerability
reporting:
<https://github.com/seijikohara/femto-car-launcher/security/advisories/new>.
Do not open a public issue for a vulnerability.

Include in the report:

- The build identifier (`nightly-<run>-<sha>`), shown in Settings →
  System → Diagnostics.
- The device class (aftermarket CarPlay / Android Auto AI box,
  built-in Android head unit, phone mount, or emulator) and the
  Android version.
- Steps to reproduce, and the impact you observed or expect.

The project is a hobby project with a single maintainer and no
service-level commitment ([`TERMS.md`](TERMS.md)). The maintainer
acknowledges reports and works on fixes on a best-effort basis. The
disclosure flow is: the maintainer confirms the report, merges a fix to
`main`, waits for the nightly that carries the fix, and then publishes
the advisory with credit to the reporter unless the reporter asks
otherwise.

## Scope

In scope:

- The Android app under `app/`.
- The bundled map page under `webmap/`, including its bridge to the
  app.
- The CI and release workflows under `.github/workflows/`, and the
  signed `nightly` artifact they publish.

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
follow the weekly schedule in [`renovate.json5`](renovate.json5). Both
kinds ship in the next nightly after they merge.
