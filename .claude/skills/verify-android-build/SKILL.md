---
name: verify-android-build
description: "Run the canonical verification pipeline (spotlessCheck → assembleDebug → lint → test), parse the results, and refuse success claims on a red build. The project's verification-procedure SSOT."
when_to_use: "Before declaring any non-trivial change complete; on 'is the build green?' or 'let's verify'; implicitly after edits to Kotlin sources, manifest, themes, res/, webmap TypeScript, or Gradle files."
argument-hint: "[gradle-task]"
allowed-tools:
  - Bash
  - Read
paths:
  - app/src/main/java/**/*.kt
  - app/src/main/AndroidManifest.xml
  - app/build.gradle.kts
  - build.gradle.kts
  - settings.gradle.kts
  - gradle/libs.versions.toml
  - app/src/main/res/**
  - webmap/**
---

# Verifying an Android build

A change that compiles only in the editor still requires a real
build before it ships. Use this skill before declaring a change
"done", "ready", or "looks good" after any edit to the paths
above. This skill is the verification-procedure SSOT; other skills
cite it rather than describing the verification themselves. The
manual entry points `/build`, `/lint`, and `/format` are thin
wrappers over individual steps of this procedure; this skill is the
composite SSOT, they are conveniences.

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

   Interpret the report per the `/lint` skill (its report-parsing
   section is the SSOT for lint-report interpretation); new errors
   are blocking.

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

6. **On red, fix the root cause** per `CLAUDE.md#no-suppress`, and
   do not skip hooks. If a Compose API is experimental, opt in at
   file level — never at module level.

CI parity: `.github/workflows/ci.yml` runs the same set
(`spotlessCheck test lint assembleDebug`). If this step list
changes, change `ci.yml` in the same commit.

## Common failure modes and fixes

| Failure | Likely cause | Fix |
| --- | --- | --- |
| `This API is experimental and is likely to change` | Variable-font `FontVariation` use | Add `@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)` at the top of the file (see `.claude/rules/fonts.md`) |
| `Unresolved reference: libs.<x>` | Forgot to add the alias in `libs.versions.toml` | Add to `[libraries]` (and `[plugins]` for plugins), then re-sync. See `.claude/rules/dependencies.md`. |
| `Theme.Material3.DayNight.NoActionBar` not found | `com.google.android.material:material` removed | Re-add it in `libs.versions.toml` and `app/build.gradle.kts` |
| `:app:buildWebMap` fails | TypeScript / Vite error under `webmap/` | Fix the webmap source; Gradle provisions Node and pnpm itself. Keep Vite's `build.target` at the WebView floor (`CLAUDE.md#tech-stack`) |
| Compose Compiler version mismatch | The catalog was bypassed — `kotlin-compose` shares `version.ref = "kotlin"` in `gradle/libs.versions.toml` and cannot diverge through it (`.claude/rules/dependencies.md`) | Restore the shared `version.ref` |
