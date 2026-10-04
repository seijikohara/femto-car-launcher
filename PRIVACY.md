# Privacy Policy

**Effective date: 2026-10-04**

Femto Car Launcher ("the app") is an Android home launcher. This policy explains
what data the app accesses, why, and who it is shared with. The app does **not**
contain advertising or analytics SDKs, and does **not** sell personal data.

## Data the app accesses

| Data | Why | Leaves the device? |
| --- | --- | --- |
| **Precise / approximate location** | Show the map, the current address, local weather, and trip distance | Yes — see "Third parties" below |
| **Recorded trip track** (position, speed, bearing, altitude) | Draw the trip visualization and export a GPX file when you ask for one | No — stored on the device only, and excluded from backup (see "Backup") |
| **Saved places** (a name with a search text or a position) | Hand a destination to your navigation app in one tap | No — stored on the device only, and excluded from backup (see "Backup") |
| **Calendar events** (read only) | Show upcoming events on the dashboard | No |
| **Microphone** | Voice assistant input, dictating a destination in the destination panel, and the optional music spectrum visualization | The app does not store or transmit audio; voice input is handled by the device's speech recognizer (see below) |
| **Installed apps** (launcher app list) | Show and launch installed apps | No |
| **Phone/cellular state** | Show the signal-strength indicator | No |
| **Media playback metadata** | Show the now-playing card | No |
| **Video files you pick** | Play a file in the dashboard's video window | No — the file is read where it is, and only a reference to it is stored, excluded from backup (see "Backup") |
| **App settings** | Remember your preferences | Device backup only (see "Backup") |

Calendar, music, and voice data are held only in memory while the app runs; none
of it is written to disk or transmitted. Location coordinates are transmitted,
to the third-party services listed below, to render the map, the address, and
the weather. Weather responses, and responses from a self-hosted geocoding host
if you configure one, also sit briefly in an on-device HTTP cache that is never
backed up; weather request URLs round your coordinates to about four decimal
places, geocoding ones carry the exact fix.

Besides your settings, the app writes three kinds of personal data to disk: the
recorded trip track, the places you save in the destination panel, and, if you
use the video window, a reference to the video file you picked. While trip
recording is on — it is on by default and can be turned off in **Settings →
Location** — the app stores each position fix, with its speed, bearing, and
altitude, in a database on the device. That history is kept for 90 days by
default; **Settings → Location** offers 30 days, 90 days, a year, or no limit,
and older points are deleted automatically. The history never leaves the device
on its own: it is excluded from backup and device transfer, and it is sent
nowhere. Exporting a trip as a GPX file writes it to the location you choose.

Places you save in the destination panel (a name with a search text or a
position) are stored on the device only, excluded from backup and device
transfer, and passed only to the navigation app you hand one to.

The video window plays a file you pick with the system file picker, from the
device or from storage attached to it. The app reads the file where it is; it
neither copies nor uploads it, and nothing about it leaves the device. To play
the file again after a restart, the app keeps a reference to it (a content URI)
and Android's permission to read it. That reference is excluded from backup and
device transfer, and picking another file releases the permission on the old
one.

## Third parties

The app contacts the following services, each governed by its own privacy
policy. Map, weather, and address requests carry your location coordinates;
font downloads and update checks carry none:

- **Weather** — MET Norway (the Norwegian Meteorological Institute, `api.met.no`).
- **Map tiles (default / OSM backend)** — OpenFreeMap and Mapterhorn (OpenStreetMap-based map data).
- **Map tiles (custom style URL)** — if you enter your own style URL in Settings
  (Appearance → Map color → Custom style URL), the map fetches that style and
  then its tiles, sprites and glyphs from whichever hosts the style names, and
  your location determines which tiles are requested. Those hosts are your
  choice and governed by their own policies; the URL, which may carry a key you
  hold with that provider, is stored with the app settings and included in your
  own account's backup and device transfer like the Google Maps key below.
- **Map tiles (optional Google Maps backend)** — if you enter your own Google Maps
  Platform API key in Settings to enable the Google Maps map backend, location
  coordinates are sent to Google (`maps.googleapis.com`) to render the map, satellite
  imagery, and traffic via the Google Maps JavaScript API. Your API key is stored
  in the app's settings on the device and, like the rest of those settings, is
  included in Android's backup and device transfer for your own account (see
  "Backup"); it is sent to no one but Google. The Google Maps JS API loaded in the WebView uses it to fetch
  map data directly from Google. This data is governed by the
  [Google privacy policy](https://policies.google.com/privacy), and your use of your
  own key is subject to the
  [Google Maps Platform Terms of Service](https://cloud.google.com/maps-platform/terms).
- **Reverse geocoding (address)** — by default the app uses the **on-device**
  Android geocoder. On devices with Google services, that geocoder is provided by
  Google and may process the coordinates. If you configure a self-hosted geocoding
  server, coordinates are sent there instead.
- **Fonts** — if you choose a Google Fonts family, it is downloaded from
  Google Fonts (`fonts.gstatic.com`). If you choose a font already installed
  on the device, it is read locally and nothing is sent over the network. No
  personal data is sent.
- **App updates** — the app checks GitHub (`github.com`) for a small
  update-availability file published beside each release; GitHub redirects
  the actual file to `release-assets.githubusercontent.com`. This check runs
  automatically at most once a day while the app is running — turn it off
  in **Settings → Updates** — and again whenever you tap "Check for updates",
  "Update to …" or "Retry" there, or "Update" on the dashboard's update
  prompt. Each check sends only the request information any web request
  carries: the device's IP address and a User-Agent naming the app and its
  version. The update file itself, about 45 MB, downloads only when you tap
  "Update to …", "Retry", or the prompt's "Update".

Voice input uses the device's built-in speech recognizer. On devices with Google
services this may transmit audio to Google for recognition, outside the app's
control.

## What the app does NOT do

- No advertising, no analytics, no crash-reporting SDKs — except the optional
  Google Maps map backend's usage data collection, disclosed below.
- No sale or sharing of personal data for advertising.
- No collection of device or advertising identifiers — **except** when you enable
  the optional Google Maps backend with your own API key: the Google Maps
  JavaScript API sends usage data to Google as part of its standard operation,
  governed by the [Google privacy policy](https://policies.google.com/privacy).
  The default OSM backend does not send any analytics or telemetry.
- No background location collection. The optional trip-tracking foreground
  service runs only while you enable it and only while the app would otherwise
  lose the location stream; it does not use background-location access.

## Backup

Android Auto Backup may copy app settings to your Google account. Location-related
settings, the recorded trip track, and the places saved in the destination panel
are **excluded** from backup and device transfer, so none of your position
history, your location settings, or your saved places is copied off the device.
The video window's settings, including the reference to the file you picked, are
excluded too: the permission to read that file belongs to this installation, and
a new installation starts with the picture hidden while driving.

Your Google Maps Platform API key, if you enter one, lives in the app settings and
is therefore **included** in both that backup and a device-to-device transfer.
This is deliberate: it is a client-side key designed to live in distributed
applications, it is your key in your own account's backup, and keeping it there
means a replacement head unit restores the map without re-entry. If you would
rather it were not copied, clear it in **Settings → Map** before a backup or a
transfer runs, or turn off backup for the app in Android's settings.

The same applies to a custom map style URL (**Settings → Appearance → Map
color**), including any provider key it carries: clear it there before a backup
or transfer if it must not be copied.

## Children

The app is not directed at children and does not knowingly collect data from
children.

## Changes

This policy may be updated; the effective date above will change accordingly.

## Contact

Questions: open an issue at
<https://github.com/seijikohara/femto-car-launcher/issues>.
