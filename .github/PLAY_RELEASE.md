# Play Store release checklist (manual Console steps)

This file records the Play Console declarations that cannot live in the repo.
Code-side release wiring is in `app/build.gradle.kts` and `.github/RELEASING.md`.

## Status (free-launch audit, 2026-06-17)

**No hard code/policy blocker remains.** Code-side is ready: `targetSdk 36`
(clears the API-35 floor and the Aug-2026 API-36 deadline), signed **AAB**
built by the tag-driven [`release.yml`](workflows/release.yml), manifest hygiene
(no `QUERY_ALL_PACKAGES`, no `ACCESS_BACKGROUND_LOCATION`, FGS `location` type
declared, all components `exported`-explicit, location prefs excluded from
backup), all external resources free + ToS-compliant (MET Norway / OpenFreeMap /
Google Fonts / on-device geocoder), privacy policy shipped, adaptive + 512px
icons present.

The remaining gates are all in this file (Console paperwork) plus, for a **new
personal developer account**, the closed-testing requirement below.

## Privacy policy

- URL: <https://github.com/seijikohara/femto-car-launcher/blob/main/PRIVACY.md>
- Set this in **Play Console → App content → Privacy policy**.
- The same URL is linked in-app under **Settings → System → Privacy policy**.

## Data safety form

Declare the following in **Play Console → App content → Data safety**. The app
has no analytics/ads/crash SDKs and collects no advertising or device IDs.

| Data type | Collected | Shared (3rd party) | Purpose | Notes |
| --- | --- | --- | --- | --- |
| Precise location | Yes | Yes — weather (MET Norway), map-tile hosts, on-device geocoder backend (Google on GMS) | App functionality | Sent as coordinates; not stored by the app |
| Approximate location | Yes | Yes — same recipients | App functionality | Coarse-grant path |
| Audio (voice) | Yes (transient) | Via the device speech recognizer (may be Google on GMS) | App functionality | App stores/transmits no audio itself |
| Calendar events | Yes (read) | No | App functionality | In-memory only |
| Installed apps | Yes (read) | No | App functionality (launcher) | Uses `<queries>`, not QUERY_ALL_PACKAGES |
| Phone/cellular state | Yes (read) | No | App functionality | Signal level only, no identifiers |
| Music/media metadata | Yes (read) | No | App functionality | In-memory only |
| App settings | Yes | Yes — Google (Auto Backup) | App functionality | Location settings excluded from backup |
| Analytics / crash data | No | No | — | No SDK present |
| Device / advertising IDs | No | No | — | None collected |

Location and microphone must be marked **shared with third parties** — they cannot
be declared "not shared."

## Foreground service (location) declaration

`TripTrackingService` uses `foregroundServiceType="location"`.

- Complete **Play Console → App content → Foreground service permissions** for the
  `location` type: justify the use case (opt-in trip distance/average that keeps
  accruing while another app is foreground) and provide the in-app prominent
  disclosure + consent flow (the service is opt-in, default off, via Settings).
- A short screen recording of the disclosure/consent flow is typically requested.

## Sensitive permission declarations

- **Location** (`ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`): map, address,
  weather, trip. No `ACCESS_BACKGROUND_LOCATION`, so the heavyweight background-
  location Permissions Declaration Form is NOT required — only the foreground
  prominent disclosure + the FGS declaration above.
- **Microphone** (`RECORD_AUDIO`): voice assistant + music spectrum; runtime-requested
  on user action. No declaration form, but needs a prominent disclosure.
- **`READ_PHONE_STATE`**: cellular signal level only (not Call Log / SMS group).
- **Notification listener** (`BIND_NOTIFICATION_LISTENER_SERVICE`,
  `MusicSessionListenerService`): reads the active media session for the
  now-playing card. No formal declaration form, but notification access draws
  review scrutiny — keep the in-app prominent disclosure + affirmative consent
  before requesting access, and justify the media-control use case.

## Content rating, account, and testing

- **Content rating**: complete the IARC questionnaire at **App content → Content
  rating**.
- **Account deletion**: N/A — the app has no user accounts / sign-in.
- **Closed-testing requirement (new personal accounts only)**: a personal
  developer account created after 2023-11-13 must run a **closed test with ≥12
  testers opted-in continuously for ≥14 days** before applying for production
  access. Recruit via friends / mutual-testing communities (real people on real
  devices — bot/fake testers risk account termination). An **organization**
  account is exempt (requires a D-U-N-S number). Plan ~3 weeks lead time
  (14-day test + ~7-day production-access review).
- One-time account setup: **$25** registration + identity verification.

## Release artifact

- Cut the build by pushing a `vMAJOR.MINOR.PATCH` tag → [`release.yml`](workflows/release.yml)
  produces the **signed AAB** (and an APK) with a production version derived from
  the tag, and attaches them to the GitHub release. Upload the `.aab` to the Play
  Console. See `.github/RELEASING.md`.
- Upload an **App Bundle (.aab)**, not an APK.
- Store assets: the **512×512 icon** lives in `art/play/`; the **feature graphic
  (1024×500)** and **screenshots** (landscape head-unit + portrait phone) also
  live in `art/play/`. Short/long descriptions are authored in the Console.
