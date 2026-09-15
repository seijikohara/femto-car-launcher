---
paths:
  - "gradle/libs.versions.toml"
  - "gradle/wrapper/gradle-wrapper.properties"
  - "**/build.gradle.kts"
  - "settings.gradle.kts"
  - "webmap/package.json"
  - "webmap/pnpm-lock.yaml"
  - "webmap/pnpm-workspace.yaml"
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
`WEATHER_BASE_URL`, `FONTS_METADATA_BASE_URL`, `MAP_TILE_HOST`, and
`MAP_TERRAIN_TILEJSON_URL` are `BuildConfig` fields fed from the
gitignored `local.properties`. `WEATHER_BASE_URL`,
`FONTS_METADATA_BASE_URL`, `MAP_TILE_HOST` and
`MAP_TERRAIN_TILEJSON_URL` fall back to public hosts;
`GEOCODER_BASE_URL` / `GEOCODER_API_KEY` fall back to empty, which
makes the launcher use the on-device platform geocoder instead of a
network host. `MAP_TILE_HOST` alone also has a user-facing override
(Settings → Map → Tile host), because the keyless default is a
volunteer service with no availability commitment and a mirror must
be reachable without a new build; the map page re-points every
request under the upstream origin at the effective host and rotates
back to the default on a failed load (`WebMapView.mapTileHosts`). The
mechanism (and the per-field comments) in `app/build.gradle.kts` is
the wiring SSOT.
