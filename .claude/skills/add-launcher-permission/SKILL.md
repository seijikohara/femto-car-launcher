---
name: add-launcher-permission
description: Use when adding a new <uses-permission> to AndroidManifest.xml. Triggers on "add SYSTEM_ALERT_WINDOW", "request notifications", "need to query installed apps". This skill is the per-permission procedure SSOT; the project rule (justify in commit body, update audit log) lives at CLAUDE.md#permissions.
argument-hint: "[android.permission.NAME]"
allowed-tools:
  - Read
  - Edit
  - Grep
paths:
  - app/src/main/AndroidManifest.xml
---

# Adding a launcher permission

Rules: see `CLAUDE.md#permissions`. The permission audit log table
in `CLAUDE.md` is the SSOT for which permissions the app declares;
keep it in sync with `AndroidManifest.xml`.

## Procedure

1. **State the use case.** Write the one-line "why" that goes into
   the commit body and into the audit log. Format:

   ```
   <PERMISSION>: needed to <verb> for <feature>.
   ```

2. **Pick the protection level** for the permission:
   - **Normal** (e.g. `INTERNET`, `WAKE_LOCK`) — declare in manifest;
     auto-granted at install.
   - **Dangerous** (e.g. `ACCESS_FINE_LOCATION`, `READ_CONTACTS`) —
     declare in manifest **and** request at runtime via
     `ActivityResultContracts.RequestPermission()`. Never assume
     grant.
   - **Special** (e.g. `SYSTEM_ALERT_WINDOW`,
     `MANAGE_EXTERNAL_STORAGE`) — declare in manifest **and** route
     the user through `Settings.ACTION_MANAGE_OVERLAY_PERMISSION`
     (or equivalent) Intent.
   - **Signature / system** — generally **off-limits** without root
     or system signing. Stop and discuss before adding.

3. **Edit `app/src/main/AndroidManifest.xml`.** Place
   `<uses-permission>` tags before `<application>`, grouped
   logically.

4. **Wire runtime requests** for dangerous / special permissions
   in the appropriate `ViewModel` / Composable. Never call dangerous
   APIs without checking `ContextCompat.checkSelfPermission(...)`
   first.

5. **Update the audit log** in `CLAUDE.md#permissions`. Alphabetised
   by permission name.

6. **Verify** with the
   [`verify-android-build`](../verify-android-build/SKILL.md) skill.

## Per-permission cheat sheet

This table is launcher-specific and lives here (not in CLAUDE.md)
because it captures *how* to handle each common case, which is
procedural detail.

| Permission | Use case | Caveats |
| --- | --- | --- |
| `QUERY_ALL_PACKAGES` | Show installed apps in the launcher's app list | Play Store policy: requires justification at submission. Prefer `<queries>` with specific intents when feasible. |
| `SYSTEM_ALERT_WINDOW` | Map / music PiP overlays | User-grantable but visually scary; explain in onboarding. |
| `POST_NOTIFICATIONS` | Driving-mode reminders, quick replies | API 33+; runtime grant required. |
| `BIND_NOTIFICATION_LISTENER_SERVICE` | Read music MediaSession metadata | User must enable via Settings → Notifications → Notification access. |
| `READ_PHONE_STATE` | Detect calls to mute media | Dangerous; consider whether `TelephonyCallback` without this permission suffices. |
| `ACCESS_FINE_LOCATION` | Driving-state speed gating (see `CLAUDE.md#driving-lockout`) | Dangerous; needed for the lockout flag. Justify carefully. |

## Skill-specific anti-patterns

- Declaring `QUERY_ALL_PACKAGES` when a `<queries>` element with
  specific intents would satisfy the use case.
- Calling a dangerous API directly without `checkSelfPermission`.
- Adding a permission to "future-proof" a feature that does not
  yet exist.
- Requesting permission in `MainActivity#onCreate` without context
  (no rationale UI). Explain why before asking.
