---
paths:
  - "gradle/libs.versions.toml"
  - "gradle/wrapper/gradle-wrapper.properties"
  - "**/build.gradle.kts"
  - "settings.gradle.kts"
  - "webmap/package.json"
  - "webmap/pnpm-lock.yaml"
---

# Dependencies

Dependency and build-file discipline for femto-car-launcher.

- All dependencies and plugins are declared in
  `gradle/libs.versions.toml` first, then referenced via `libs.*`
  aliases. No raw `implementation("...")` strings in module
  `build.gradle.kts` files.
- `gradle/libs.versions.toml` and
  `gradle/wrapper/gradle-wrapper.properties` are the version SSOT —
  never restate version numbers in docs or comments; cite the
  catalog.
- Compose dependencies go through the BOM. Overriding a single
  Compose artifact's version requires a justification in the commit
  body.
- The Kotlin version and the
  `org.jetbrains.kotlin.plugin.compose` plugin version move in
  lock-step: since Kotlin 2.0 the Compose compiler ships with
  Kotlin; `kotlin-compose` shares `version.ref = "kotlin"` in the
  catalog and cannot diverge through it.
- See the
  [`update-gradle-dependency`](../skills/update-gradle-dependency/SKILL.md)
  skill for the procedure.

Build-time endpoints: `GEOCODER_BASE_URL` / `GEOCODER_API_KEY`,
`WEATHER_BASE_URL` / `WEATHER_API_KEY`, and `FONTS_METADATA_BASE_URL`
are `BuildConfig` fields fed from the gitignored `local.properties`,
falling back to the public hosts. The mechanism (and the per-field
comments) in `app/build.gradle.kts` is the wiring SSOT.
