# Privacy Policy

**Effective date: 2026-06-22**

Femto Car Launcher ("the app") is an Android home launcher. This policy explains
what data the app accesses, why, and who it is shared with. The app does **not**
contain advertising or analytics SDKs, and does **not** sell personal data.

## Data the app accesses

| Data | Why | Leaves the device? |
| --- | --- | --- |
| **Precise / approximate location** | Show the map, the current address, local weather, and trip distance | Yes — see "Third parties" below |
| **Calendar events** (read only) | Show upcoming events on the dashboard | No |
| **Microphone** | Voice assistant input and the optional music spectrum visualization | The app does not store or transmit audio; voice input is handled by the device's speech recognizer (see below) |
| **Installed apps** (launcher app list) | Show and launch installed apps | No |
| **Phone/cellular state** | Show the signal-strength indicator | No |
| **Media playback metadata** | Show the now-playing card | No |
| **App settings** | Remember your preferences | Device backup only (see "Backup") |

The app stores location, trip, calendar, music, and voice data only in memory
while running. None of it is written to disk or transmitted, except the location
coordinates sent to the third-party services below to render the map, address,
and weather.

## Third parties

To provide map, address, and weather features the app sends your location
coordinates to the following services, governed by their own privacy policies:

- **Weather** — MET Norway (the Norwegian Meteorological Institute, `api.met.no`).
- **Map tiles (default / OSM backend)** — OpenFreeMap and Mapterhorn (OpenStreetMap-based map data).
- **Map tiles (optional Mapbox backend)** — if you enter your own Mapbox access token in
  Settings to enable the Mapbox map backend, location coordinates are sent to Mapbox
  (`api.mapbox.com`) to render map tiles, styles, satellite imagery, and traffic. Your
  Mapbox access token is stored on-device only and is not transmitted by the launcher;
  Mapbox GL JS in the WebView uses it to fetch tiles directly from Mapbox. This data is
  governed by the [Mapbox privacy policy](https://www.mapbox.com/legal/privacy).
- **Map tiles (optional Google Maps backend)** — if you enter your own Google Maps
  Platform API key in Settings to enable the Google Maps map backend, location
  coordinates are sent to Google (`maps.googleapis.com`) to render the map, satellite
  imagery, and traffic via the Google Maps JavaScript API. Your API key is stored
  on-device only; the Google Maps JS API loaded in the WebView uses it to fetch map
  data directly from Google. This data is governed by the
  [Google privacy policy](https://policies.google.com/privacy), and your use of your
  own key is subject to the
  [Google Maps Platform Terms of Service](https://cloud.google.com/maps-platform/terms).
- **Reverse geocoding (address)** — by default the app uses the **on-device**
  Android geocoder. On devices with Google services, that geocoder is provided by
  Google and may process the coordinates. If you configure a self-hosted geocoding
  server, coordinates are sent there instead.
- **Fonts** — if you choose a custom font, it is downloaded from Google Fonts
  (`fonts.gstatic.com`). No personal data is sent.

Voice input uses the device's built-in speech recognizer. On devices with Google
services this may transmit audio to Google for recognition, outside the app's
control.

## What the app does NOT do

- No advertising, no analytics, no crash-reporting SDKs — except the optional
  Mapbox and Google Maps map backends' usage data collection, disclosed below.
- No sale or sharing of personal data for advertising.
- No collection of device or advertising identifiers — **except** when you enable
  the optional Mapbox map backend by entering your own access token: Mapbox GL JS
  sends usage telemetry to Mapbox as part of its standard operation, and the app
  cannot fully disable this. The telemetry is governed by the
  [Mapbox privacy policy](https://www.mapbox.com/legal/privacy). Likewise, the
  optional Google Maps backend, when enabled with your own API key, sends usage
  data to Google as part of the Google Maps JavaScript API's standard operation,
  governed by the [Google privacy policy](https://policies.google.com/privacy).
  The default OSM backend does not send any analytics or telemetry.
- No background location collection. The optional trip-tracking foreground
  service runs only while you enable it and only while the app would otherwise
  lose the location stream; it does not use background-location access.

## Backup

Android Auto Backup may copy app settings to your Google account. Location-related
settings are **excluded** from backup and device transfer.

## Children

The app is not directed at children and does not knowingly collect data from
children.

## Changes

This policy may be updated; the effective date above will change accordingly.

## Contact

Questions: open an issue at
<https://github.com/seijikohara/femto-car-launcher/issues>.
