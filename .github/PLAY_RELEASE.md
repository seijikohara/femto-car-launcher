# Play Store release checklist (manual Console steps)

This file records the Play Console declarations that cannot live in the repo.
Code-side release wiring is in `app/build.gradle.kts` and `.github/RELEASING.md`.

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
  weather, trip. No `ACCESS_BACKGROUND_LOCATION`.
- **Microphone** (`RECORD_AUDIO`): voice assistant + music spectrum; runtime-requested
  on user action.
- **`READ_PHONE_STATE`**: cellular signal level only (not Call Log / SMS group).

## Release artifact

- Upload an **App Bundle (.aab)**, not an APK (see the AAB CI work / `bundleRelease`).
- Use a release `versionCode` / `versionName` distinct from the nightly channel.
- Prepare store assets the repo cannot hold: 512×512 hi-res icon (export
  `logo.svg`), feature graphic, and screenshots (landscape head-unit + portrait
  phone).
