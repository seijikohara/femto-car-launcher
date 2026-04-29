---
name: update-gradle-dependency
description: Use when adding or upgrading any Gradle dependency or plugin in femto-car-launcher. Triggers on "add Coil", "upgrade Compose BOM", "bump kotlin to X". This skill is the version-catalog procedure SSOT; the project rule (catalog first, BOM precedence, Kotlin/Compose-Compiler lock-step) lives at CLAUDE.md#dependencies.
argument-hint: "[group:artifact:version | bom-update]"
allowed-tools:
  - Read
  - Edit
  - Bash
paths:
  - gradle/libs.versions.toml
  - app/build.gradle.kts
  - build.gradle.kts
---

# Updating a Gradle dependency

Rules: see `CLAUDE.md#dependencies`.

## Procedure

1. **Decide the shape of the change.**
   - **New library** — add to `[versions]`, `[libraries]`, then
     reference via `libs.<alias>` in `app/build.gradle.kts`.
   - **Version bump** — edit the `[versions]` entry only.
   - **New plugin** — add to `[versions]`, `[plugins]`, then apply
     via `alias(libs.plugins.<alias>)` in the relevant
     `build.gradle.kts`.

2. **Edit `gradle/libs.versions.toml`** in this order:
   - `[versions]`: alphabetical by key, lowercase camelCase.
   - `[libraries]`: hyphenated keys (`androidx-core-ktx`).
   - `[plugins]`: hyphenated keys, version refs.

3. **Reference from `app/build.gradle.kts`** using the alias:

   ```kotlin
   implementation(libs.androidx.compose.material3)
   ```

   Hyphens in the catalog key become dots on the alias side.

4. **Sanity-check what resolved:**

   ```bash
   ./gradlew app:dependencies --configuration debugRuntimeClasspath \
     | grep -iE 'kotlin-stdlib|<your-artifact>' | head
   ```

5. **Verify** with the
   [`verify-android-build`](../verify-android-build/SKILL.md) skill.

## Bulk-updating to latest versions

For routine version maintenance across the whole catalog, the
project ships two cooperating plugins:

- `com.github.ben-manes.versions` exposes `./gradlew dependencyUpdates`
  to report outdated artefacts.
- `nl.littlerobots.version-catalog-update` exposes
  `./gradlew versionCatalogUpdate` to rewrite
  `gradle/libs.versions.toml` in place using the report from
  the ben-manes plugin.

Workflow:

```bash
./gradlew dependencyUpdates       # inspect what would change
./gradlew versionCatalogUpdate    # apply the update to libs.versions.toml
./gradlew spotlessApply           # tidy formatting
./gradlew assembleDebug           # verify
```

Review every diff before committing. Major-version bumps still
need the per-bump justification rule from
`CLAUDE.md#dependencies`.

6. **Document in the commit body** when:
   - Bumping a major version of any library.
   - Overriding a Compose BOM-managed artifact.
   - Adding an annotation processor / KSP plugin.
   - Bumping Kotlin (must include the matching Compose Compiler
     plugin bump).

## Skill-specific anti-patterns

- `implementation("io.coil-kt:coil-compose:2.6.0")` directly in
  `app/build.gradle.kts` — go through the catalog.
- Hardcoding a version inside `[libraries]` instead of using
  `version.ref`.
- Mixing the Compose BOM with explicit version pins on Compose
  artifacts without a justification line in the commit body.
- Adding a transitive dependency to silence a warning — investigate
  the root cause first.
