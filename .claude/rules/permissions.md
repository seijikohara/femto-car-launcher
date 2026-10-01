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
| `INTERNET` | MET Norway weather API (`api.met.no`), OpenFreeMap vector map tile fetch (MapLibre), optional self-hosted Nominatim reverse geocoding (the default geocoder is on-device and needs no network), the optional live-map terrain layer (Mapterhorn DEM tiles), the optional BYO Google Maps (`maps.googleapis.com`) map backend — network-active only once the user supplies their own key — the on-demand Google Fonts catalog + TTF download (`fonts.google.com` / `fonts.gstatic.com`, no API key, no Play Services), and the in-app updater's release-manifest check (daily while automatic checks are on, the opt-out default, and whenever the user taps Check for updates; both contact the same hosts) and user-started APK download, from GitHub Releases (`github.com`, which redirects to `release-assets.githubusercontent.com`; builds without a release feed never check). |
| `POST_NOTIFICATIONS` | Show the ongoing background-ranging notification raised by `TripTrackingService` (live speed / distance / average). Dangerous on Android 13+; requested at runtime only when the user enables background ranging, never at startup. When denied, the service still runs and keeps accruing the trip — only the notification refresh is suppressed. |
| `READ_CALENDAR` | Query `CalendarContract.Instances` for the dashboard's Calendar card — the 6-day strip dots and the upcoming-event list. Dangerous; runtime grant. The card falls back to "today's date only" when denied. |
| `READ_PHONE_STATE` | Read the cellular `SignalStrength.level` via `TelephonyCallback` so the dock status cluster shows graduated cellular signal bars. Dangerous; runtime grant. The cellular indicator degrades to the binary connected/disconnected icon when denied. |
| `RECORD_AUDIO` | Capture speech for the in-launcher voice assistant (`android.speech.SpeechRecognizer`) so the user talks to the assistant without leaving the launcher, and attach the music card's spectrum `Visualizer` to the audio output mix (session 0 capture requires this permission by platform contract; the mic hardware is never read). Dangerous; requested at runtime only on user action — tapping the mic or enabling the spectrum setting — never at startup. When denied, the assistant sheet degrades to the system-intent delegation rows and the spectrum renders flat. |
| `REQUEST_DELETE_PACKAGES` | Hand the app drawer's Uninstall action for a user-chosen, non-system app to the system uninstaller (`Intent.ACTION_DELETE` in `AppsRepository.requestUninstall`); the confirmation UI and the deletion are the system's. An app targeting API 28+ needs it for the uninstaller to accept the request (found empirically in #309). Normal protection (`protectionLevel` 0x0 in the framework manifest); auto-granted at install. |
| `REQUEST_INSTALL_PACKAGES` | Hand a downloaded update of the launcher itself, verified against its release manifest's size and SHA-256, to the platform installer through a `PackageInstaller` session (Settings → Updates); the system's confirmation dialog appears for every install. Special: protection level `signature\|appop` (`protectionLevel` 0x42 in the framework manifest; the reference page's "signature" omits the appop flag), and the appop bit is what makes it user-grantable: the user grants it per app through "Install unknown apps" (`Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES`, read with `PackageManager.canRequestPackageInstalls()`), which the app routes to only when the user taps Install, never at startup. When it is denied, or disabled by device policy, nothing is installed: the verified download stays offered, and the user is pointed to the setting or to the release page for a manual install; the rest of the launcher is unaffected. |

A permission a library's manifest merges in is part of the APK too, so
it either joins the audit log or is removed in `AndroidManifest.xml` with
`tools:node="remove"`. Removed today: `WAKE_LOCK` (merged by
`media3-exoplayer` for its `setWakeMode`, which the app never calls).

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
