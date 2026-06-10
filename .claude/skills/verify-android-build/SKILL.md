---
name: verify-android-build
description: Use before declaring any non-trivial code change complete in femto-car-launcher. Runs assembleDebug + lint, parses the result, and refuses to claim success on a red build. Triggers on "is the build green?", "let's verify", or implicitly after edits to Kotlin sources, manifest, themes, webmap TypeScript, or Gradle files. This skill is the project's verification SSOT.
argument-hint: "[gradle-task]"
allowed-tools:
  - Bash
  - Read
paths:
  - app/src/main/java/**/*.kt
  - app/src/main/AndroidManifest.xml
  - app/build.gradle.kts
  - gradle/libs.versions.toml
  - app/src/main/res/values/themes.xml
  - app/src/main/res/values-night/themes.xml
  - webmap/**
---

# Verifying an Android build

A change that compiles only in the editor still requires a real
build before it ships. Use this skill before declaring a change
"done", "ready", or "looks good" after any edit to the paths
above. This skill is the verification-procedure SSOT; other skills
cite it rather than describing the verification themselves.

When invoked manually, `$ARGUMENTS` overrides the default Gradle
task. Default is `assembleDebug`.

## Procedure

1. **Check format and code style.**

   ```bash
   ./gradlew spotlessCheck
   ```

   On failure, run `./gradlew spotlessApply` (or invoke `/format`)
   to auto-fix. ktlint findings that Spotless cannot auto-fix
   (e.g. `compose:modifier-missing-check`) need a manual edit; do
   not bypass with `suppressLintsFor`.

2. **Build a debug APK.**

   ```bash
   ./gradlew assembleDebug
   ```

   Expect `BUILD SUCCESSFUL`. APK at
   `app/build/outputs/apk/debug/app-debug.apk`. `assembleDebug`
   also builds the LIVE map web payload (`:app:buildWebMap` runs
   pnpm + Vite over `webmap/`), so webmap regressions surface here.

3. **Run lint** for any change touching the manifest, themes, or
   resources:

   ```bash
   ./gradlew lint
   ```

   New errors are blocking. New warnings are findings to discuss
   before merging.

4. **Run unit tests** if you touched code that has corresponding
   tests:

   ```bash
   ./gradlew test
   ```

5. **Report** explicitly what was run and what passed:

   - "Ran `./gradlew spotlessCheck` — passed."
   - "Ran `./gradlew assembleDebug` — BUILD SUCCESSFUL."
   - "Ran `./gradlew lint` — no new findings."

   Do not claim success generically; cite the commands.

6. **On red, fix the root cause.** Do not silence warnings, do not
   add `@Suppress` to make red go green, and do not skip hooks. If
   a Compose API is experimental, opt in at file level — never at
   module level.

## Common failure modes and fixes

| Failure | Likely cause | Fix |
| --- | --- | --- |
| `This API is experimental and is likely to change` | Variable-font `FontVariation` use | Add `@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)` at the top of the file (see `CLAUDE.md#fonts`) |
| `Unresolved reference: libs.<x>` | Forgot to add the alias in `libs.versions.toml` | Add to `[libraries]` (and `[plugins]` for plugins), then re-sync. See `CLAUDE.md#dependencies`. |
| `Theme.Material3.DayNight.NoActionBar` not found | `com.google.android.material:material` removed | Re-add it in `libs.versions.toml` and `app/build.gradle.kts` |
| `:app:buildWebMap` fails | TypeScript / Vite error under `webmap/` | Fix the webmap source; Gradle provisions Node and pnpm itself. Keep Vite's `build.target` at `chrome109` (CLAUDE.md, Tech stack) |
| Compose Compiler version mismatch | Kotlin updated but `kotlin-compose` plugin pinned | Bump `kotlin` and `compose-compiler` plugin in lock-step (see `CLAUDE.md#dependencies`) |
