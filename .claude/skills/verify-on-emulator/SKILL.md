---
name: verify-on-emulator
description: "Boot the TBox-Mock AVD, install the debug APK, drive the launcher, and capture screenshots to verify UI changes on the head-unit geometry."
when_to_use: "Verifying a UI change visually; \"check it on the emulator\", \"screenshot the dashboard\", \"does it look right on the head unit\"."
argument-hint: "[screen-or-area]"
allowed-tools:
  - Bash
  - Read
---

# Verifying on the TBox-Mock emulator

Visual verification on the head-unit geometry. `$ARGUMENTS` names the
screen or area to drive to and screenshot; with no argument, verify
the home dashboard.

## Environment

- **Tools**: resolve `adb` / `emulator` from `PATH` first
  (`command -v adb`; the Bash shell initializes from your profile —
  the committed allowlist covers bare invocations). If not on `PATH`,
  use `$ANDROID_HOME/{platform-tools,emulator}`, else the
  conventional SDK root (`~/Library/Android/sdk` on macOS,
  `~/Android/Sdk` on Linux); absolute-path invocations prompt unless
  mirrored in `.claude/settings.local.json`. `local.properties` and
  `~/Library` may be Read-denied — use `ls`/adb, not `cat`. Gradle
  finds the SDK via `local.properties` regardless.
- **AVD**: `TBox-Mock` — head-unit profile, **800x480 / 150 dpi**
  (matches the real target).
- **App id**: `io.github.seijikohara.femto` (no debug suffix).

## Procedure

1. **Build the debug APK** via the
   [`verify-android-build`](../verify-android-build/SKILL.md) skill
   (the verification-procedure SSOT). The APK lands at
   `app/build/outputs/apk/debug/app-debug.apk`.

2. **Boot the emulator** headless:

   ```bash
   emulator -avd TBox-Mock -no-window -no-audio -gpu swiftshader_indirect
   ```

   If one is already running, a second launch exits 1 harmlessly
   (`adb devices` still shows `emulator-5556`). Wait for
   `adb devices` to report the device before installing.

3. **Install the APK**:

   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

   Note: `connectedAndroidTest` **uninstalls the app afterward** —
   run `:app:installDebug` (or `adb install -r`) again before manual
   screenshots.

4. **Pre-grant runtime permissions** — the startup permission dialog
   blocks the dashboard (`MainActivity.requestRuntimePermissions()`
   pops a location dialog on first run; uiautomator shows
   `com.google.android.permissioncontroller`). Pre-grant to skip it:

   ```bash
   adb shell pm grant io.github.seijikohara.femto android.permission.{ACCESS_FINE_LOCATION,ACCESS_COARSE_LOCATION,READ_CALENDAR,READ_PHONE_STATE,BLUETOOTH_CONNECT}
   ```

   then `am force-stop` + restart.

5. **Drive the UI**: `uiautomator dump /sdcard/ui.xml` + `adb pull`,
   grep `content-desc="Settings"` for the footer button bounds, then
   `input tap`. The settings ModalBottomSheet opens **partially
   expanded** — `input swipe` up to fully expand, then swipe to
   scroll.

6. **Capture and inspect**: Compose row text often does not appear as
   discrete `text=` nodes (merged semantics), so confirm with a
   screenshot — `screencap -p` + pull + Read the image:

   ```bash
   adb shell screencap -p /sdcard/shot.png && adb pull /sdcard/shot.png
   ```

## Known limitation

The emulator cannot present the native LIVE map GL — see the map
memory note (`memory/map_maplibre_openfreemap.md` under the project
memory directory) for the details and the SNAPSHOT floor; do not
treat a blank LIVE map on the emulator as a regression.

## Report

Report explicitly what was run and what was observed:

- "Ran `adb install -r app-debug.apk` — Success."
- "Pre-granted permissions via `adb shell pm grant ...`."
- "Captured `shot.png` — the calendar card renders the 6-day strip."

Do not claim the UI "looks right" generically; cite the commands and
attach or describe the screenshots.
