---
paths:
  - "app/src/main/AndroidManifest.xml"
---

# Permissions

Permission discipline for femto-car-launcher's `AndroidManifest.xml`.

- Every `<uses-permission>` is added through the
  [`add-launcher-permission`](../skills/add-launcher-permission/SKILL.md)
  skill.
- Adding any permission requires a one-line justification in the
  commit message body.
- The audit log below lists every declared permission with its
  one-line justification, alphabetised. Keep this table in sync with
  `AndroidManifest.xml` — this file is the audit-log SSOT.

| Permission | Justification |
| --- | --- |
| `ACCESS_COARSE_LOCATION` | Paired with `ACCESS_FINE_LOCATION` per the Android 12+ runtime model — users may grant only coarse. The dashboard panels accept either precision and render with degraded precision when only coarse is granted. |
| `ACCESS_FINE_LOCATION` | Centre the head-unit map on the user's position, derive the speed / altitude / address overlays, and locate the user for weather lookups. Required at runtime; the dependent panels render empty until the permission is granted. |
| `ACCESS_NETWORK_STATE` | MapLibre connectivity probe before fetching OpenFreeMap vector map tiles. |
| `ACCESS_WIFI_STATE` | Read Wi-Fi transport / validation state so the footer status cluster reports a live Wi-Fi indicator. Normal protection; auto-granted at install. |
| `BLUETOOTH_CONNECT` | Read the set of currently-connected Bluetooth devices (HEADSET / A2DP / GATT) so the footer status cluster reflects head-unit pairing state. Dangerous on Android 12+; runtime grant. When denied, the connected-device APIs are unreadable, so the BT indicator falls back to the adapter power state (on/off) rather than a misleading "disconnected"; the rest of the launcher remains functional. |
| `INTERNET` | Open-Meteo weather API, OpenFreeMap vector map tile fetch (MapLibre), Nominatim/OSM reverse geocoding, the optional live-map terrain layer (Mapterhorn DEM tiles), and the on-demand Google Fonts catalog + TTF download (`fonts.google.com` / `fonts.gstatic.com`, no API key, no Play Services). |
| `READ_CALENDAR` | Query `CalendarContract.Instances` for the dashboard's Calendar card — the 6-day strip dots and the upcoming-event list. Dangerous; runtime grant. The card falls back to "today's date only" when denied. |
| `READ_PHONE_STATE` | Read the cellular `SignalStrength.level` via `TelephonyCallback` so the footer status cluster shows graduated cellular signal bars. Dangerous; runtime grant. The cellular indicator degrades to the binary connected/disconnected icon when denied. |
| `RECORD_AUDIO` | Capture speech for the in-launcher voice assistant (`android.speech.SpeechRecognizer`) so the user talks to the assistant without leaving the launcher. Dangerous; requested at runtime only when the user taps the mic, never at startup. When denied — or when no on-device recognizer is available — the assistant sheet degrades to the system-intent delegation rows. |
