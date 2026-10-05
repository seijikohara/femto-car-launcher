# Privacy Policy

**Effective date: 2026-10-04**

Femto Car Launcher ("the app") is an Android home launcher. This policy explains what data the app accesses, why, and who receives it. The app does **not** contain advertising or analytics software development kits (SDKs), and does **not** sell personal data.

## Data the app accesses

| Data | Why | Leaves the device? |
| --- | --- | --- |
| **Precise / approximate location** | Show the map, the current address, local weather, and trip distance | Yes — see "Third parties" below |
| **Recorded trip track** (position, speed, bearing, altitude) | Draw the trip flythrough and export a GPS Exchange Format (GPX) file when you ask for one | No — stored on the device only, and excluded from backup (see "Backup") |
| **Saved places** (a name with a search text or a position) | Hand a destination to your navigation app in one tap | No — stored on the device only, and excluded from backup (see "Backup") |
| **Calendar events** (read only) | Show upcoming events on the dashboard | No |
| **Microphone** | Voice assistant input, dictating a destination in the destination panel, and the optional music spectrum visualization | The app does not store or transmit audio; the device's speech recognizer handles voice input (see below) |
| **Installed apps** (launcher app list) | Show and launch installed apps | No |
| **Phone/cellular state** | Show the signal-strength indicator | No |
| **Media playback metadata** | Show the now-playing card | No |
| **Video files you pick** | Play a file in the dashboard's video window | No — the file is read where it is, and only a reference to it is stored, excluded from backup (see "Backup") |
| **App settings** | Remember your preferences | Device backup only (see "Backup") |

The app holds calendar, music, and voice data only in memory while it runs. The app writes none of this data to disk and sends none of it over the network; for voice input, see the speech-recognizer note below. The app sends location coordinates to the third-party services listed below to render the map, the address, and the weather.

The app also writes weather responses, and responses from a self-hosted geocoding host if you configure one, to a small, size-capped on-device HTTP cache. An entry can persist there across restarts until the cache fills and the oldest entries are evicted, or until you clear the app's cache or data. The cache is never backed up. Weather request URLs round your coordinates to about four decimal places; geocoding request URLs carry the exact fix.

Besides your settings, the app writes four kinds of personal data to disk: the recorded trip track, the places you save in the destination panel, the HTTP cache described above, and, if you use the video window, a reference to the video file you picked. While **Settings → Location → Track recording** is on (it is on by default), the app stores each position fix, with its speed, bearing, and altitude, in a database on the device. The app keeps that history for 90 days by default; **Settings → Location → Keep track history** offers 30 days, 90 days, 1 year, or no limit, and the app deletes older points automatically. The app never sends the history anywhere on its own, and the history is excluded from backup and device transfer. Exporting a trip as a GPX file writes it to the location you choose.

Places you save in the destination panel (a name with a search text or a position) are stored on the device only and excluded from backup and device transfer. The app passes a saved place only to the navigation app you hand it to.

The video window plays a file you pick with the system file picker, from the device or from storage attached to it. The app reads the file where it is; it neither copies nor uploads the file, and nothing about the file leaves the device. To play the file again after a restart, the app keeps a reference to it (a content URI) and Android's permission to read it. That reference is excluded from backup and device transfer, and picking another file releases the permission on the old one.

## Third parties

The app contacts the following services, each governed by its own privacy policy. Map, weather, and address requests carry your location coordinates; font downloads and update checks carry none:

- **Weather** — MET Norway (the Norwegian Meteorological Institute, `api.met.no`).
- **Map tiles (default OpenStreetMap provider)** — OpenFreeMap and Mapterhorn (OpenStreetMap-based map data).
- **Map tiles (custom style URL)** — if you enter your own style URL in **Settings → Appearance → Map color → Custom style URL**, the map fetches that style, then its tiles, sprites and glyphs from whichever hosts the style names. Your location determines which tiles the map requests. Those hosts are your choice and governed by their own policies. The URL, which may carry a key you hold with that provider, is stored with the app settings and included in your own account's backup and device transfer, like the Google Maps key below.
- **Map tiles (optional Google Maps provider)** — if you enter your own Google Maps Platform application programming interface (API) key in **Settings → Map** to enable the Google Maps provider, the app sends location coordinates to Google (`maps.googleapis.com`) to render the map, satellite imagery, and traffic through the Google Maps JavaScript API. The app stores your API key in its settings on the device. Like the rest of those settings, the key is included in Android's backup and device transfer for your own account (see "Backup"). The app sends the key to no one but Google: the Google Maps JavaScript API, loaded in the WebView, uses the key to fetch map data directly from Google. The [Google Privacy Policy](https://policies.google.com/privacy) governs this data, and your use of your own key is subject to the [Google Maps Platform Terms of Service](https://cloud.google.com/maps-platform/terms).
- **Reverse geocoding (address)** — by default the app uses the **on-device** Android geocoder. On devices with Google services, Google provides that geocoder, and the geocoder may process the coordinates. If you configure a self-hosted geocoding server, the app sends coordinates there instead.
- **Fonts** — if you choose a Google Fonts family in **Settings → Appearance**, the app downloads it from Google Fonts (`fonts.gstatic.com`). If you choose a font already installed on the device, the app reads it locally and sends nothing over the network. Font downloads send no personal data.
- **App updates** — the app checks GitHub (`github.com`) for a small update-availability file published beside each release; GitHub redirects the actual file to `release-assets.githubusercontent.com`. The automatic check runs at most once a day while the app is running, and only while **Settings → Updates → Check automatically** is on. Tapping **Check for updates**, **Update to …** or **Retry** there, or **Update** on the dashboard's update prompt, checks again right away, regardless of **Check automatically** or the daily limit. **Update to …** and **Retry** also check again before downloading, in case a newer build shipped since the last check. Each check sends only the request information any web request carries: the device's IP address and a User-Agent naming the app and its version. The update file itself, about 45 MB, downloads only when you tap **Update to …**, **Retry**, or the prompt's **Update**.

Voice input uses the device's built-in speech recognizer. On devices with Google services, the recognizer may transmit audio to Google for recognition, outside the app's control.

## What the app does not do

- No advertising, no analytics, no crash-reporting SDKs, except the optional Google Maps provider's usage data collection, disclosed below.
- No sale or sharing of personal data for advertising.
- No collection of device or advertising identifiers, **except** when you enable the optional Google Maps provider with your own API key: the Google Maps JavaScript API sends usage data to Google as part of its standard operation, governed by the [Google Privacy Policy](https://policies.google.com/privacy). The default OpenStreetMap provider does not send any analytics or telemetry.
- No background location collection. The optional trip-tracking foreground service (**Settings → Location → Background ranging**) runs only while you enable it and only while the app would otherwise lose the location stream; the service does not use background-location access.

## Backup

Android Auto Backup may copy app settings to your Google account. Location-related settings, the recorded trip track, and the places saved in the destination panel are **excluded** from backup and device transfer, so none of your position history, your location settings, or your saved places is copied off the device. The video window's settings, including the reference to the file you picked, are excluded too: the permission to read that file belongs to this installation, and a new installation starts with the picture hidden while driving.

If you enter a Google Maps Platform API key, the key lives in the app settings and is therefore **included** in both that backup and a device-to-device transfer. The key is included on purpose. A Google Maps key is a client-side key designed for distributed applications, and the backup belongs to your own account. Keeping the key in the backup lets a replacement device restore the map without re-entry. If you would rather the key were not copied, clear it in **Settings → Map** before a backup or a transfer runs, or turn off backup for the app in Android's settings.

The same applies to a custom map style URL (**Settings → Appearance → Map color → Custom style URL**), including any provider key the URL carries: clear the URL there before a backup or transfer if it must not be copied.

## Children

The app is not directed at children and does not knowingly collect data from children.

## Changes

This policy may be updated; the effective date above will change accordingly.

## Contact

Questions: open an issue at <https://github.com/seijikohara/femto-car-launcher/issues>.
