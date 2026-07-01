---
name: add-launcher-permission
description: Procedure for adding a new <uses-permission> to AndroidManifest.xml. This skill is the per-permission procedure SSOT; the project rule (justify in commit body, update audit log) lives at .claude/rules/permissions.md.
when_to_use: Any new <uses-permission> — "add SYSTEM_ALERT_WINDOW", "request notifications", "need to query installed apps".
argument-hint: "[android.permission.NAME]"
allowed-tools:
  - Read
  - Edit
  - Write
  - Grep
  - Bash
  - Skill
paths:
  - app/src/main/AndroidManifest.xml
---

# Adding a launcher permission

Rules: see `.claude/rules/permissions.md`. Its audit log table is
the SSOT for which permissions the app declares; keep it in sync
with `AndroidManifest.xml`.

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

3. **Edit `app/src/main/AndroidManifest.xml`.** Add the
   `<uses-permission>` tag to the alphabetised block before
   `<application>`. Do **not** write a per-permission comment — the
   block-header comment already points at the audit log, and the
   audit log is the justification SSOT. Manifest comments are
   reserved for manifest mechanics (e.g. the optional
   `<uses-feature>` declarations).

4. **Wire runtime requests** for dangerous / special permissions
   in the appropriate `ViewModel` / Composable. Never call dangerous
   APIs without checking `ContextCompat.checkSelfPermission(...)`
   first. Prefer requesting at the interaction point over startup —
   the launcher's sanctioned startup exception is the dashboard's
   core location set (`MainActivity.requestRuntimePermissions()`),
   which gates most of the home surface; everything else (e.g.
   `RECORD_AUDIO` on mic tap) requests on interaction. Design the
   denied-state degradation first either way.

5. **Update the audit log** in `.claude/rules/permissions.md`.
   Alphabetised by permission name.

6. **Verify** with the
   [`verify-android-build`](../verify-android-build/SKILL.md) skill.

## Common not-yet-declared cases

This table is launcher-specific and lives here (not in the
audit-log home, `.claude/rules/permissions.md`) because it captures
*how* to handle each common case, which is procedural detail.
Already-declared permissions are NOT listed here — their use case
and degradation behaviour live in the audit log at
`.claude/rules/permissions.md`.

| Permission | Use case | Caveats |
| --- | --- | --- |
| `QUERY_ALL_PACKAGES` | Show installed apps in the launcher's app list | Play Store policy: requires justification at submission. Prefer `<queries>` with specific intents when feasible. |
| `SYSTEM_ALERT_WINDOW` | Map / music PiP overlays | User-grantable but visually scary; explain in onboarding. |

## Skill-specific anti-patterns

- Declaring `QUERY_ALL_PACKAGES` when a `<queries>` element with
  specific intents would satisfy the use case.
- Calling a dangerous API directly without `checkSelfPermission`.
- Adding a permission to "future-proof" a feature that does not
  yet exist.
- Requesting permission in `MainActivity#onCreate` without context
  (no rationale UI). Explain why before asking.
