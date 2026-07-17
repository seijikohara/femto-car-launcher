---
paths:
  - "app/src/main/AndroidManifest.xml"
---

# Permissions

Permission discipline for femto-car-launcher's `AndroidManifest.xml`.

- Every `<uses-permission>` follows the procedure at the end of this
  file (this rule is both the procedure and the audit-log SSOT, and
  auto-loads whenever the manifest is touched).
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
| `ACCESS_WIFI_STATE` | Read Wi-Fi transport / validation state so the dock status cluster reports a live Wi-Fi indicator. Normal protection; auto-granted at install. |
| `BLUETOOTH_CONNECT` | Read the set of currently-connected Bluetooth devices (HEADSET / A2DP / GATT) so the dock status cluster reflects head-unit pairing state. Dangerous on Android 12+; runtime grant. When denied, the connected-device APIs are unreadable, so the BT indicator falls back to the adapter power state (on/off) rather than a misleading "disconnected"; the rest of the launcher remains functional. |
| `FOREGROUND_SERVICE` | Run `TripTrackingService` so the trip distance / average keep accruing while the launcher is backgrounded (e.g. a navigation app is in front). Normal protection; auto-granted at install. Used only when the user opts into background ranging in Settings. |
| `FOREGROUND_SERVICE_LOCATION` | The `location` foreground-service type for `TripTrackingService`, required on Android 14+ to keep receiving GPS fixes while backgrounded. The service starts only from the foreground, so the "while in use" location grant suffices — `ACCESS_BACKGROUND_LOCATION` is deliberately **not** declared. Normal protection; auto-granted at install. |
| `INTERNET` | MET Norway weather API (`api.met.no`), OpenFreeMap vector map tile fetch (MapLibre), optional self-hosted Nominatim reverse geocoding (the default geocoder is on-device and needs no network), the optional live-map terrain layer (Mapterhorn DEM tiles), the optional BYO Mapbox (`mapbox.html`, `api.mapbox.com`) and Google Maps (`googlemaps.html`, `maps.googleapis.com`) map backends — network-active only once the user supplies their own token/key — and the on-demand Google Fonts catalog + TTF download (`fonts.google.com` / `fonts.gstatic.com`, no API key, no Play Services). |
| `POST_NOTIFICATIONS` | Show the ongoing background-ranging notification raised by `TripTrackingService` (live speed / distance / average). Dangerous on Android 13+; requested at runtime only when the user enables background ranging, never at startup. When denied, the service still runs and keeps accruing the trip — only the notification refresh is suppressed. |
| `READ_CALENDAR` | Query `CalendarContract.Instances` for the dashboard's Calendar card — the 6-day strip dots and the upcoming-event list. Dangerous; runtime grant. The card falls back to "today's date only" when denied. |
| `READ_PHONE_STATE` | Read the cellular `SignalStrength.level` via `TelephonyCallback` so the dock status cluster shows graduated cellular signal bars. Dangerous; runtime grant. The cellular indicator degrades to the binary connected/disconnected icon when denied. |
| `RECORD_AUDIO` | Capture speech for the in-launcher voice assistant (`android.speech.SpeechRecognizer`) so the user talks to the assistant without leaving the launcher, and attach the music card's spectrum `Visualizer` to the audio output mix (session 0 capture requires this permission by platform contract; the mic hardware is never read). Dangerous; requested at runtime only on user action — tapping the mic or enabling the spectrum setting — never at startup. When denied, the assistant sheet degrades to the system-intent delegation rows and the spectrum renders flat. |

## Adding a new permission (procedure)

1. **State the use case** — the one-line "why" that goes into the
   commit body and the audit log above:
   `<PERMISSION>: needed to <verb> for <feature>.`
2. **Pick the protection level**:
   - **Normal** (`INTERNET`, `WAKE_LOCK`) — declare in the manifest;
     auto-granted at install.
   - **Dangerous** (`ACCESS_FINE_LOCATION`, `READ_CONTACTS`) —
     declare **and** request at runtime via
     `ActivityResultContracts.RequestPermission()`. Never assume the
     grant.
   - **Special** (`SYSTEM_ALERT_WINDOW`, `MANAGE_EXTERNAL_STORAGE`) —
     declare **and** route the user through the matching
     `Settings.ACTION_*` Intent.
   - **Signature / system** — off-limits without system signing; stop
     and discuss before adding.
3. **Edit `app/src/main/AndroidManifest.xml`** — add the tag to the
   alphabetised block before `<application>`. No per-permission
   comment: the block header already points here, and this audit log
   is the justification SSOT.
4. **Wire runtime requests** for dangerous / special permissions.
   Never call a dangerous API without
   `ContextCompat.checkSelfPermission(...)`. Request at the
   interaction point, not startup — the one sanctioned startup
   request is the dashboard's core location set
   (`MainActivity.requestRuntimePermissions()`); design the
   denied-state degradation first either way.
5. **Update the audit log above** (alphabetised).
6. **Verify** with the
   [`verify-android-build`](../skills/verify-android-build/SKILL.md)
   skill.

## Common not-yet-declared cases

Already-declared permissions are NOT listed here — their use case and
degradation behaviour live in the audit log above.

| Permission | Use case | Caveats |
| --- | --- | --- |
| `QUERY_ALL_PACKAGES` | Show installed apps in the launcher's app list | Play Store policy: requires justification at submission. Prefer `<queries>` with specific intents when feasible. |
| `SYSTEM_ALERT_WINDOW` | Map / music PiP overlays | User-grantable but visually scary; explain in onboarding. |

## Anti-patterns

- Declaring `QUERY_ALL_PACKAGES` when a `<queries>` element with
  specific intents would satisfy the use case.
- Calling a dangerous API directly without `checkSelfPermission`.
- Adding a permission to "future-proof" a feature that does not yet
  exist.
- Requesting a permission in `MainActivity#onCreate` without context
  (no rationale UI) — explain why before asking.
