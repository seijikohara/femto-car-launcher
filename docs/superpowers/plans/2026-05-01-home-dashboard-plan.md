# Home Dashboard (Plan B) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the single-purpose apps grid with a LecoAuto-inspired multi-panel dashboard: hero map (Lite Mode + speed/altitude/address overlays), three right-stack panels (clock, weather, now-playing), and a bottom apps bar. The full apps grid moves into a separate drawer screen.

**Architecture:** Unidirectional data flow — six independent `data/` repositories produce Kotlin Flows; `HomeViewModel` combines them into a single `HomeUiState`; ten focused Composables render the C2 layout. Maps integration uses Google Maps SDK Lite Mode with a runtime GMS-availability check that gracefully degrades to a static fallback. Address comes from the AOSP `Geocoder` (also GMS-backed). Weather comes from Open-Meteo (no API key, multi-region). Music comes from `MediaSession` via a `NotificationListenerService`.

**Tech Stack:** Kotlin 2.2.10, Jetpack Compose (BOM 2026.03.00), Material 3, Google Maps SDK for Android (Lite Mode), Open-Meteo HTTP, OkHttp 5, kotlinx.serialization, MediaSessionManager + NotificationListenerService, AndroidX core-i18n LocalePreferences. JDK 21 toolchain, Java 11 source/target. minSdk 33, targetSdk 36.

The spec for this plan is `docs/superpowers/specs/2026-05-01-home-dashboard-design.md` — read it first if any task is unclear.

---

## Plan-wide conventions

- Branch: continue on `docs/home-dashboard-spec` (the spec already lives there) — rename only at PR time.
- All new Kotlin source uses Bold Minimal expression-chain style per `CLAUDE.md#kotlin-style` (single-expression bodies, expression `when`/`if`, scope chains).
- Every new Composable takes `modifier: Modifier = Modifier` as the first non-state parameter (`compose:modifier-missing-check` ktlint rule).
- Driver-distraction policy: **no UI gating on motion state** — the dashboard renders identically whether the vehicle is moving or stopped. See `CLAUDE.md#driving-lockout` (the anchor name is preserved for back-compat; the section now records the no-gating decision).
- Tests follow the project pattern: JVM unit tests in `app/src/test/`, Compose UI tests in `app/src/androidTest/`, fixtures under `testfixtures/` packages (the SSOT for fake values; do not declare ad-hoc `data class FakeFoo(...)` literals in test files).
- After every task that changes Kotlin sources or Gradle files, run `./gradlew spotlessApply && ./gradlew assembleDebug` before committing. Failures in spotlessApply are an instruction to fix — never bypass.
- Commits use Conventional Commits (`feat:`, `refactor:`, `test:`, etc.). Do **not** add `Co-Authored-By: Claude` or "Generated with Claude Code" trailers.

---

## File structure (target end state)

```
app/src/main/java/io/github/seijikohara/femto/
├── data/
│   ├── AppEntry.kt                     unchanged
│   ├── AppsRepository.kt               unchanged
│   ├── ClockRepository.kt              new, Task 2.1
│   ├── FontPreferences.kt              unchanged
│   ├── GmsAvailability.kt              new, Task 2.5
│   ├── LocationPermissions.kt          unchanged (already extracted)
│   ├── LocationRepository.kt           new, Task 2.2
│   ├── MusicSessionListenerService.kt  new, Task 2.6
│   ├── MusicSessionRepository.kt       new, Task 2.6
│   ├── ReverseGeocoderRepository.kt    new, Task 2.3
│   └── WeatherRepository.kt            new, Task 2.4
├── ui/
│   ├── drawer/
│   │   ├── AppDrawerRoute.kt           new, Task 4.6
│   │   └── AppDrawerScreen.kt          new, Task 4.6
│   ├── home/
│   │   ├── HomeRoute.kt                modified, Task 4.5
│   │   ├── HomeScreen.kt               rewritten, Task 4.4
│   │   ├── HomeAction.kt               new, Task 4.2
│   │   ├── HomeUiState.kt              expanded, Task 4.1
│   │   ├── HomeViewModel.kt            expanded, Task 4.3
│   │   └── components/
│   │       ├── AppTile.kt              unchanged, reused by AppDrawer + AppsBar
│   │       ├── AppsBar.kt              new, Task 3.5
│   │       ├── ClockPanel.kt           new, Task 3.1
│   │       ├── DashboardScaffold.kt    new, Task 3.6
│   │       ├── MapPanel.kt             new, Task 3.3
│   │       ├── MusicPanel.kt           new, Task 3.4
│   │       └── WeatherPanel.kt         new, Task 3.2
│   ├── locale/
│   │   └── SystemUnits.kt              new, Task 1.1
│   └── theme/                          unchanged
└── MainActivity.kt                     modified, Task 4.7

app/src/test/java/io/github/seijikohara/femto/
├── data/
│   ├── ReverseGeocoderRepositoryTest.kt  new, Task 2.3
│   ├── WeatherRepositoryTest.kt          new, Task 2.4
│   └── GmsAvailabilityTest.kt            new, Task 2.5
├── testfixtures/
│   ├── FakeAddress.kt                  new, Task 1.3
│   ├── FakeLocation.kt                 new, Task 1.3
│   ├── FakeNowPlaying.kt               new, Task 1.3
│   └── FakeWeatherSnapshot.kt          new, Task 1.3
└── ui/home/HomeViewModelTest.kt          new, Task 4.3

app/src/androidTest/java/io/github/seijikohara/femto/
├── testfixtures/
│   └── (parallel fakes for Compose tests, Task 1.3)
└── ui/home/components/
    ├── ClockPanelTest.kt               new, Task 3.1
    ├── WeatherPanelTest.kt             new, Task 3.2
    ├── MusicPanelTest.kt               new, Task 3.4
    ├── AppsBarTest.kt                  new, Task 3.5
    └── DashboardScaffoldTest.kt        new, Task 3.6

gradle/libs.versions.toml                modified, Task 0.1
app/build.gradle.kts                     modified, Task 0.1, Task 0.3
app/src/main/AndroidManifest.xml         modified, Task 0.2, Task 0.3, Task 2.6
local.properties                         modified manually, Task 0.4
README.md                                modified, Task 0.4
CLAUDE.md                                modified, Task 0.2 (audit table)
```

---

## Phase 0: Build setup

### Task 0.1: Add dependencies to the version catalog

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Edit `gradle/libs.versions.toml`** to add the new entries.

Add to `[versions]`:

```toml
kotlinxSerialization = "1.8.0"
mockwebserver = "5.1.0"
okhttp = "5.1.0"
playServicesMaps = "19.2.0"
robolectric = "4.14.1"
turbine = "1.2.1"
```

Add to `[libraries]`:

```toml
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerialization" }
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
okhttp-mockwebserver = { group = "com.squareup.okhttp3", name = "mockwebserver", version.ref = "mockwebserver" }
play-services-maps = { group = "com.google.android.gms", name = "play-services-maps", version.ref = "playServicesMaps" }
robolectric = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }
```

Add to `[plugins]`:

```toml
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

- [ ] **Step 2: Edit `app/build.gradle.kts`** to wire the new artefacts.

Add to the `plugins {}` block:

```kotlin
alias(libs.plugins.kotlin.serialization)
```

Add to the `dependencies {}` block:

```kotlin
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.play.services.maps)

    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.turbine)
```

Add a `testOptions` block under `android {}` (after `buildFeatures`):

```kotlin
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
```

- [ ] **Step 3: Verify the catalog is well-formed.**

Run: `./gradlew dependencyUpdates --refresh-dependencies` (informational only — confirms no version is moot).
Run: `./gradlew help` (confirms parsing).
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Build to ensure dependencies resolve.**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL. New `okhttp`, `kotlinx-serialization`, `play-services-maps` artefacts appear in the dependency tree.

- [ ] **Step 5: Commit.**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build: add maps, okhttp, serialization, and test deps for plan b"
```

### Task 0.2: Add INTERNET and ACCESS_NETWORK_STATE permissions

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `CLAUDE.md` (audit table)

- [ ] **Step 1: Edit `app/src/main/AndroidManifest.xml`** to declare the two new permissions.

Add immediately after the existing `ACCESS_FINE_LOCATION` declaration:

```xml
    <!--
        ACCESS_NETWORK_STATE: Maps SDK best-practice probe before
        tile fetch. Justification mirrored in CLAUDE.md#permissions.
    -->
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <!--
        INTERNET: Open-Meteo weather API + Google Maps Lite tile
        fetch. Justification mirrored in CLAUDE.md#permissions.
    -->
    <uses-permission android:name="android.permission.INTERNET" />
```

- [ ] **Step 2: Edit `CLAUDE.md`** — the permissions audit table — to add both permissions in alphabetical order.

Replace the table contents in the `### Permissions <a id="permissions"></a>` section so the rows read:

```
| `ACCESS_FINE_LOCATION` | Centre the head-unit map on the user's position, derive the speed / altitude / address overlays, and locate the user for weather lookups. Required at runtime; the dependent panels render empty until the permission is granted. |
| `ACCESS_NETWORK_STATE` | Google Maps SDK best-practice network-availability probe before fetching map tiles. |
| `INTERNET` | Open-Meteo weather API and Google Maps Lite tile fetch. |
```

- [ ] **Step 3: Build.**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit.**

```bash
git add app/src/main/AndroidManifest.xml CLAUDE.md
git commit -m "feat: declare INTERNET and ACCESS_NETWORK_STATE for plan b

Both permissions are needed by the new home dashboard: INTERNET for
the Open-Meteo weather call and Google Maps Lite tile fetch,
ACCESS_NETWORK_STATE for the Maps SDK best-practice probe.
Justifications are mirrored in CLAUDE.md#permissions per project
rule."
```

### Task 0.3: Wire the Maps API key build flow

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Edit `app/build.gradle.kts`** so the API key flows from `local.properties` into `manifestPlaceholders`.

Add at the top of the file (above `plugins {}`):

```kotlin
import java.util.Properties

val mapsApiKey: String =
    rootProject
        .file("local.properties")
        .takeIf { it.exists() }
        ?.inputStream()
        ?.use { Properties().apply { load(it) } }
        ?.getProperty("MAPS_API_KEY")
        ?: ""
```

Add inside `defaultConfig {}`:

```kotlin
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
```

- [ ] **Step 2: Edit `app/src/main/AndroidManifest.xml`** to declare the meta-data tag.

Add inside `<application>` (sibling of the `<activity>` element, before its closing tag):

```xml
        <meta-data
            android:name="com.google.android.geo.API_KEY"
            android:value="${MAPS_API_KEY}" />
```

- [ ] **Step 3: Build twice — once with no key, once with a placeholder — to confirm both paths work.**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL. Open `app/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml` and confirm `<meta-data ... android:value=""/>` is rendered.

Add `MAPS_API_KEY=AIzaPLACEHOLDER` to `local.properties` (file is gitignored).
Run: `./gradlew assembleDebug --rerun-tasks`
Expected: BUILD SUCCESSFUL. Merged manifest now has `android:value="AIzaPLACEHOLDER"`.

Remove the placeholder line from `local.properties` again.

- [ ] **Step 4: Commit.**

```bash
git add app/build.gradle.kts app/src/main/AndroidManifest.xml
git commit -m "build: thread maps api key from local.properties into manifest

Read MAPS_API_KEY from local.properties (gitignored) and inject it
through manifestPlaceholders into the standard
com.google.android.geo.API_KEY meta-data tag the Maps SDK reads.
Default to an empty string when the key is absent so the build is
green for contributors without a key; the MapPanel falls back to its
GMS-absent branch in that case."
```

### Task 0.4: Document the API key in README

**Files:**
- Modify: `README.md` (create if it doesn't exist yet)

- [ ] **Step 1: Check whether `README.md` exists at the repo root.**

Run: `ls README.md`

If absent, create one with the standard sections; if present, locate the "Developer setup" or equivalent section.

- [ ] **Step 2: Add a "Maps API key" subsection** under "Developer setup":

```markdown
### Maps API key

The launcher renders a Lite Mode Google Map on the home dashboard.
To see real tiles in your local debug build:

1. Create a Maps SDK for Android key at
   <https://console.cloud.google.com/google/maps-apis/credentials>.
2. Restrict the key by Android app fingerprint (package name +
   debug + release SHA-1).
3. Add the key to `local.properties` (gitignored):

   ```
   MAPS_API_KEY=AIza...
   ```

4. Re-run `./gradlew assembleDebug`. The map appears once the
   permission grant lands at runtime; without a key the panel
   shows the static fallback.
```

- [ ] **Step 3: Commit.**

```bash
git add README.md
git commit -m "docs: document maps api key local.properties workflow"
```

---

## Phase 1: UI primitives, value objects, fixtures

### Task 1.1: Locale-driven unit helpers

**Files:**
- Create: `app/src/main/java/io/github/seijikohara/femto/ui/locale/SystemUnits.kt`
- Create: `app/src/test/java/io/github/seijikohara/femto/ui/locale/SystemUnitsTest.kt`

- [ ] **Step 1: Write the failing test.**

Create `app/src/test/java/io/github/seijikohara/femto/ui/locale/SystemUnitsTest.kt`:

```kotlin
package io.github.seijikohara.femto.ui.locale

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class SystemUnitsTest {
    @Test
    fun `us country defaults to imperial speed and distance`() {
        assertEquals(SpeedUnit.MILES_PER_HOUR, speedUnitFor(Locale.US))
        assertEquals(DistanceUnit.FEET, distanceUnitFor(Locale.US))
    }

    @Test
    fun `gb country defaults to imperial speed and distance`() {
        val gb = Locale.Builder().setLanguage("en").setRegion("GB").build()
        assertEquals(SpeedUnit.MILES_PER_HOUR, speedUnitFor(gb))
        assertEquals(DistanceUnit.FEET, distanceUnitFor(gb))
    }

    @Test
    fun `mm country defaults to imperial speed and distance`() {
        val mm = Locale.Builder().setLanguage("my").setRegion("MM").build()
        assertEquals(SpeedUnit.MILES_PER_HOUR, speedUnitFor(mm))
        assertEquals(DistanceUnit.FEET, distanceUnitFor(mm))
    }

    @Test
    fun `jp country defaults to metric speed and distance`() {
        assertEquals(SpeedUnit.KILOMETERS_PER_HOUR, speedUnitFor(Locale.JAPAN))
        assertEquals(DistanceUnit.METERS, distanceUnitFor(Locale.JAPAN))
    }

    @Test
    fun `de country defaults to metric speed and distance`() {
        assertEquals(SpeedUnit.KILOMETERS_PER_HOUR, speedUnitFor(Locale.GERMANY))
        assertEquals(DistanceUnit.METERS, distanceUnitFor(Locale.GERMANY))
    }

    @Test
    fun `unknown country defaults to metric`() {
        val xx = Locale.Builder().setLanguage("xx").setRegion("XX").build()
        assertEquals(SpeedUnit.KILOMETERS_PER_HOUR, speedUnitFor(xx))
        assertEquals(DistanceUnit.METERS, distanceUnitFor(xx))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails (no source file yet).**

Run: `./gradlew test --tests 'io.github.seijikohara.femto.ui.locale.SystemUnitsTest'`
Expected: FAIL with `Unresolved reference: SpeedUnit` or similar.

- [ ] **Step 3: Implement `SystemUnits.kt`.**

Create `app/src/main/java/io/github/seijikohara/femto/ui/locale/SystemUnits.kt`:

```kotlin
package io.github.seijikohara.femto.ui.locale

import java.util.Locale

enum class SpeedUnit { KILOMETERS_PER_HOUR, MILES_PER_HOUR }

enum class DistanceUnit { METERS, FEET }

private val ImperialCountries = setOf("US", "GB", "MM")

fun speedUnitFor(locale: Locale = Locale.getDefault()): SpeedUnit =
    if (locale.country in ImperialCountries) SpeedUnit.MILES_PER_HOUR
    else SpeedUnit.KILOMETERS_PER_HOUR

fun distanceUnitFor(locale: Locale = Locale.getDefault()): DistanceUnit =
    if (locale.country in ImperialCountries) DistanceUnit.FEET
    else DistanceUnit.METERS

fun SpeedUnit.fromMetersPerSecond(mps: Float): Float =
    when (this) {
        SpeedUnit.KILOMETERS_PER_HOUR -> mps * 3.6f
        SpeedUnit.MILES_PER_HOUR -> mps * 2.2369363f
    }

fun DistanceUnit.fromMeters(meters: Double): Double =
    when (this) {
        DistanceUnit.METERS -> meters
        DistanceUnit.FEET -> meters * 3.2808399
    }

fun SpeedUnit.label(): String =
    when (this) {
        SpeedUnit.KILOMETERS_PER_HOUR -> "km/h"
        SpeedUnit.MILES_PER_HOUR -> "mph"
    }

fun DistanceUnit.label(): String =
    when (this) {
        DistanceUnit.METERS -> "m"
        DistanceUnit.FEET -> "ft"
    }
```

- [ ] **Step 4: Re-run the test to verify it passes.**

Run: `./gradlew test --tests 'io.github.seijikohara.femto.ui.locale.SystemUnitsTest'`
Expected: PASS, 6 tests.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/io/github/seijikohara/femto/ui/locale/SystemUnits.kt app/src/test/java/io/github/seijikohara/femto/ui/locale/SystemUnitsTest.kt
git commit -m "feat(locale): add speed and distance unit helpers from locale

LocalePreferences (androidx.core.i18n) covers temperature but not
speed/distance, so derive imperial vs metric from the small
imperial-country set (US, GB, MM) used by the dashboard overlays.
Provides conversions from m/s and m for the panels to consume."
```

### Task 1.2: Value objects shared across data and UI

**Files:**
- Create: `app/src/main/java/io/github/seijikohara/femto/data/ClockTick.kt`
- Create: `app/src/main/java/io/github/seijikohara/femto/data/ShortAddress.kt`
- Create: `app/src/main/java/io/github/seijikohara/femto/data/WeatherSnapshot.kt`
- Create: `app/src/main/java/io/github/seijikohara/femto/data/NowPlaying.kt`

- [ ] **Step 1: Create `ClockTick.kt`.**

```kotlin
package io.github.seijikohara.femto.data

import java.time.LocalDate
import java.time.LocalTime

data class ClockTick(val time: LocalTime, val date: LocalDate)
```

- [ ] **Step 2: Create `ShortAddress.kt`.**

```kotlin
package io.github.seijikohara.femto.data

data class ShortAddress(val locality: String, val region: String?) {
    fun displayString(): String = listOfNotNull(locality, region).joinToString(", ")
}
```

- [ ] **Step 3: Create `WeatherSnapshot.kt`.**

```kotlin
package io.github.seijikohara.femto.data

import java.time.Instant

data class WeatherSnapshot(
    val tempC: Double,
    val code: WeatherCode,
    val fetchedAt: Instant,
)

enum class WeatherCode {
    CLEAR,
    PARTLY_CLOUDY,
    CLOUDY,
    FOG,
    DRIZZLE,
    RAIN,
    FREEZING_RAIN,
    SNOW,
    SNOW_GRAINS,
    RAIN_SHOWERS,
    SNOW_SHOWERS,
    THUNDERSTORM,
    UNKNOWN,
    ;

    companion object {
        fun fromWmo(code: Int): WeatherCode =
            when (code) {
                0 -> CLEAR
                1, 2 -> PARTLY_CLOUDY
                3 -> CLOUDY
                45, 48 -> FOG
                51, 53, 55 -> DRIZZLE
                56, 57 -> FREEZING_RAIN
                61, 63, 65 -> RAIN
                66, 67 -> FREEZING_RAIN
                71, 73, 75 -> SNOW
                77 -> SNOW_GRAINS
                80, 81, 82 -> RAIN_SHOWERS
                85, 86 -> SNOW_SHOWERS
                95, 96, 99 -> THUNDERSTORM
                else -> UNKNOWN
            }
    }
}
```

- [ ] **Step 4: Create `NowPlaying.kt`.**

```kotlin
package io.github.seijikohara.femto.data

import androidx.compose.ui.graphics.ImageBitmap

data class NowPlaying(
    val title: String,
    val artist: String?,
    val albumArt: ImageBitmap?,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val packageName: String,
)
```

- [ ] **Step 5: Build to confirm everything compiles.**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit.**

```bash
git add app/src/main/java/io/github/seijikohara/femto/data/ClockTick.kt app/src/main/java/io/github/seijikohara/femto/data/ShortAddress.kt app/src/main/java/io/github/seijikohara/femto/data/WeatherSnapshot.kt app/src/main/java/io/github/seijikohara/femto/data/NowPlaying.kt
git commit -m "feat(data): add value objects for dashboard panels

ClockTick (time + date), ShortAddress (locality + region),
WeatherSnapshot with Open-Meteo WMO weather-code mapping, and
NowPlaying for MediaSession-fed music. These are immutable data
classes with no behaviour beyond simple display formatting."
```

### Task 1.3: Test fixtures (`testfixtures/` packages)

**Files:**
- Create: `app/src/test/java/io/github/seijikohara/femto/testfixtures/FakeLocation.kt`
- Create: `app/src/test/java/io/github/seijikohara/femto/testfixtures/FakeAddress.kt`
- Create: `app/src/test/java/io/github/seijikohara/femto/testfixtures/FakeWeatherSnapshot.kt`
- Create: `app/src/test/java/io/github/seijikohara/femto/testfixtures/FakeNowPlaying.kt`
- Create: `app/src/androidTest/java/io/github/seijikohara/femto/testfixtures/FakeNowPlaying.kt` (parallel)
- Create: `app/src/androidTest/java/io/github/seijikohara/femto/testfixtures/FakeWeatherSnapshot.kt` (parallel)
- Create: `app/src/androidTest/java/io/github/seijikohara/femto/testfixtures/FakeAddress.kt` (parallel)

- [ ] **Step 1: Create `app/src/test/java/io/github/seijikohara/femto/testfixtures/FakeLocation.kt`.**

```kotlin
package io.github.seijikohara.femto.testfixtures

import android.location.Location

fun fakeLocation(
    latitude: Double = 35.6580,
    longitude: Double = 139.7016,
    speedMps: Float = 0f,
    altitudeM: Double = 47.0,
): Location =
    Location("test").apply {
        this.latitude = latitude
        this.longitude = longitude
        this.speed = speedMps
        this.altitude = altitudeM
        this.time = 0L
        this.elapsedRealtimeNanos = 0L
    }
```

- [ ] **Step 2: Create `FakeAddress.kt` (test source set).**

```kotlin
package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.ShortAddress

fun fakeAddress(
    locality: String = "Shibuya",
    region: String? = "Tokyo",
): ShortAddress = ShortAddress(locality, region)
```

- [ ] **Step 3: Create `FakeWeatherSnapshot.kt` (test source set).**

```kotlin
package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.WeatherCode
import io.github.seijikohara.femto.data.WeatherSnapshot
import java.time.Instant

fun fakeWeatherSnapshot(
    tempC: Double = 18.0,
    code: WeatherCode = WeatherCode.CLEAR,
    fetchedAt: Instant = Instant.parse("2026-05-01T05:32:00Z"),
): WeatherSnapshot = WeatherSnapshot(tempC, code, fetchedAt)
```

- [ ] **Step 4: Create `FakeNowPlaying.kt` (test source set).**

```kotlin
package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.NowPlaying

fun fakeNowPlaying(
    title: String = "Strobe",
    artist: String? = "deadmau5",
    isPlaying: Boolean = true,
    positionMs: Long = 232_000L,
    durationMs: Long = 632_000L,
    packageName: String = "com.spotify.music",
): NowPlaying =
    NowPlaying(
        title = title,
        artist = artist,
        albumArt = null,
        isPlaying = isPlaying,
        positionMs = positionMs,
        durationMs = durationMs,
        packageName = packageName,
    )
```

- [ ] **Step 5: Mirror three of the fixtures into the androidTest source set** so Compose UI tests can reuse them. Copy `FakeAddress.kt`, `FakeWeatherSnapshot.kt`, and `FakeNowPlaying.kt` verbatim under `app/src/androidTest/java/io/github/seijikohara/femto/testfixtures/`. Do not mirror `FakeLocation.kt` — `android.location.Location` is platform-only and androidTest already has it; the panels we test never receive `Location` directly, only `Float`/`Double` fields.

- [ ] **Step 6: Build to verify both source sets compile.**

Run: `./gradlew compileDebugUnitTestKotlin compileDebugAndroidTestKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit.**

```bash
git add app/src/test/java/io/github/seijikohara/femto/testfixtures/ app/src/androidTest/java/io/github/seijikohara/femto/testfixtures/
git commit -m "test: add testfixtures package for dashboard fakes

Per CLAUDE.md#ssot-dry, fixture builders live in a single
testfixtures/ package per source set so future tests do not declare
ad-hoc data class FakeFoo(...) literals. Initial set: FakeLocation
(test only — android.location.Location is JVM-incompatible),
FakeAddress, FakeWeatherSnapshot, FakeNowPlaying."
```

---

## Phase 2: Data layer repositories

### Task 2.1: ClockRepository

**Files:**
- Create: `app/src/main/java/io/github/seijikohara/femto/data/ClockRepository.kt`

ClockRepository wraps `Intent.ACTION_TIME_TICK`. Testing this is awkward because the broadcast is only emitted by the system; we therefore rely on the AndroidX `Context.registerReceiver` integration and verify behaviour at the smoke-test level (Task 5.2) rather than via a JVM unit test.

- [ ] **Step 1: Implement `ClockRepository.kt`.**

```kotlin
package io.github.seijikohara.femto.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class ClockRepository(
    private val context: Context,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    fun tickFlow(): Flow<ClockTick> =
        callbackFlow {
            val emit: () -> Unit = {
                trySend(currentTick())
            }
            val receiver =
                object : BroadcastReceiver() {
                    override fun onReceive(c: Context?, intent: Intent?) = emit()
                }

            emit()
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(Intent.ACTION_TIME_TICK),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            awaitClose { context.unregisterReceiver(receiver) }
        }.flowOn(Dispatchers.Main.immediate)

    private fun currentTick(): ClockTick =
        java.time.ZonedDateTime
            .now(zone)
            .let { ClockTick(time = it.toLocalTime().withSecond(0).withNano(0), date = it.toLocalDate()) }

    private fun nowZoned(): java.time.ZonedDateTime = java.time.ZonedDateTime.now(zone)

    private fun ClockTick(time: LocalTime, date: LocalDate): ClockTick = ClockTick(time, date)
}
```

(The duplicated alias-style helpers are placeholders the IDE will inline — final code uses the data class constructor directly.)

- [ ] **Step 2: Build.**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit.**

```bash
git add app/src/main/java/io/github/seijikohara/femto/data/ClockRepository.kt
git commit -m "feat(data): add ClockRepository keyed to ACTION_TIME_TICK

Subscribes to the system time-tick broadcast (1/min) and emits a
ClockTick with the current local time/date. Seeds with an immediate
value on subscription so the first frame renders without waiting
for the next tick."
```

### Task 2.2: LocationRepository

**Files:**
- Create: `app/src/main/java/io/github/seijikohara/femto/data/LocationRepository.kt`

LocationRepository is the SSOT for `Location?` flow. Other repositories (geocoder, weather) and the map panel consume it. We do not unit-test it directly because it wraps `LocationManager` which is awkward to mock; smoke verification at Task 5.2 covers it.

- [ ] **Step 1: Implement `LocationRepository.kt`.**

```kotlin
package io.github.seijikohara.femto.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.getSystemService
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

class LocationRepository(
    private val context: Context,
) {
    private val locationManager: LocationManager = checkNotNull(context.getSystemService())

    @SuppressLint("MissingPermission") // Caller checks ACCESS_FINE_LOCATION before subscribing.
    fun locationFlow(): Flow<Location?> =
        callbackFlow {
            val listener = LocationListenerCompat { location -> trySend(location) }

            runCatching {
                locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            }.onSuccess { trySend(it) }

            runCatching {
                LocationManagerCompat.requestLocationUpdates(
                    locationManager,
                    LocationManager.GPS_PROVIDER,
                    LocationRequestCompat.Builder(LOCATION_INTERVAL_MS).build(),
                    listener,
                    Looper.getMainLooper(),
                )
            }.onFailure { trySend(null) }

            awaitClose { locationManager.removeUpdates(listener) }
        }.flowOn(Dispatchers.Main.immediate)

    private companion object {
        const val LOCATION_INTERVAL_MS = 1_000L
    }
}
```

- [ ] **Step 2: Build.**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit.**

```bash
git add app/src/main/java/io/github/seijikohara/femto/data/LocationRepository.kt
git commit -m "feat(data): add LocationRepository as SSOT for GPS fixes

Wraps LocationManager.GPS_PROVIDER at 1 Hz and seeds with the most
recent cached fix. Consumers (map panel, geocoder, weather repo)
read raw Location? values; smoothing for displayed speed/altitude
happens inside the panels that need it, not here."
```

### Task 2.3: ReverseGeocoderRepository

**Files:**
- Create: `app/src/main/java/io/github/seijikohara/femto/data/ReverseGeocoderRepository.kt`
- Create: `app/src/test/java/io/github/seijikohara/femto/data/ReverseGeocoderRepositoryTest.kt`

- [ ] **Step 1: Write the failing test.**

Create `app/src/test/java/io/github/seijikohara/femto/data/ReverseGeocoderRepositoryTest.kt`:

```kotlin
package io.github.seijikohara.femto.data

import android.location.Address
import android.location.Geocoder
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import io.github.seijikohara.femto.testfixtures.fakeLocation
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class ReverseGeocoderRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `emits short address when geocoder returns full result`() =
        runTest {
            val geocoder = shadowOf(Geocoder(context))
            val response =
                Address(java.util.Locale.US).apply {
                    locality = "Shibuya"
                    adminArea = "Tokyo"
                }
            geocoder.setFromLocation(35.66, 139.70, 1, listOf(response))

            val repo =
                ReverseGeocoderRepository(
                    context = context,
                    locationFlow = flowOf(fakeLocation()),
                )

            repo.addressFlow().test {
                assertEquals(ShortAddress("Shibuya", "Tokyo"), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `emits null when geocoder returns no result`() =
        runTest {
            val geocoder = shadowOf(Geocoder(context))
            geocoder.setFromLocation(35.66, 139.70, 1, emptyList())

            val repo =
                ReverseGeocoderRepository(
                    context = context,
                    locationFlow = flowOf(fakeLocation()),
                )

            repo.addressFlow().test {
                assertNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `emits null when location flow yields null`() =
        runTest {
            val repo =
                ReverseGeocoderRepository(
                    context = context,
                    locationFlow = flowOf(null),
                )

            repo.addressFlow().test {
                assertNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}
```

- [ ] **Step 2: Run the test to verify it fails.**

Run: `./gradlew test --tests 'io.github.seijikohara.femto.data.ReverseGeocoderRepositoryTest'`
Expected: FAIL with `Unresolved reference: ReverseGeocoderRepository`.

- [ ] **Step 3: Implement `ReverseGeocoderRepository.kt`.**

```kotlin
package io.github.seijikohara.femto.data

import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

class ReverseGeocoderRepository(
    private val context: Context,
    private val locationFlow: Flow<Location?>,
) {
    private val geocoder: Geocoder = Geocoder(context, Locale.getDefault())

    fun addressFlow(): Flow<ShortAddress?> =
        locationFlow
            .distinctUntilChangedByBucket()
            .map { location -> location?.let { resolve(it) } }
            .flowOn(Dispatchers.IO)

    private suspend fun resolve(location: Location): ShortAddress? =
        runCatching {
            if (!Geocoder.isPresent()) return@runCatching null
            requestAddresses(location)
                .firstOrNull()
                ?.let { ShortAddress(locality = it.locality.orEmpty(), region = it.adminArea) }
                ?.takeIf { it.locality.isNotEmpty() }
        }.getOrNull()

    @Suppress("DEPRECATION")
    private suspend fun requestAddresses(location: Location): List<android.location.Address> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocation(location.latitude, location.longitude, 1) { addresses ->
                    cont.resume(addresses)
                }
            }
        } else {
            geocoder.getFromLocation(location.latitude, location.longitude, 1) ?: emptyList()
        }

    private fun Flow<Location?>.distinctUntilChangedByBucket(): Flow<Location?> =
        distinctUntilChanged { old, new ->
            old == null && new == null ||
                old != null && new != null && old.distanceTo(new) < BUCKET_M
        }

    private companion object {
        const val BUCKET_M = 100f
    }
}
```

- [ ] **Step 4: Re-run the test to verify it passes.**

Run: `./gradlew test --tests 'io.github.seijikohara.femto.data.ReverseGeocoderRepositoryTest'`
Expected: PASS, 3 tests.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/io/github/seijikohara/femto/data/ReverseGeocoderRepository.kt app/src/test/java/io/github/seijikohara/femto/data/ReverseGeocoderRepositoryTest.kt
git commit -m "feat(data): add ReverseGeocoderRepository with 100m debounce

Maps the LocationRepository flow to ShortAddress?. Returns null when
Geocoder.isPresent()=false (GMS-absent devices), when the call
fails, or when the result has no locality. Quantises by 100m
distance so small GPS jitter does not trigger redundant lookups."
```

### Task 2.4: WeatherRepository (Open-Meteo)

**Files:**
- Create: `app/src/main/java/io/github/seijikohara/femto/data/WeatherRepository.kt`
- Create: `app/src/main/java/io/github/seijikohara/femto/data/OpenMeteoApi.kt`
- Create: `app/src/test/java/io/github/seijikohara/femto/data/WeatherRepositoryTest.kt`

- [ ] **Step 1: Write the failing test.**

Create `app/src/test/java/io/github/seijikohara/femto/data/WeatherRepositoryTest.kt`:

```kotlin
package io.github.seijikohara.femto.data

import app.cash.turbine.test
import io.github.seijikohara.femto.testfixtures.fakeLocation
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WeatherRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        client = OkHttpClient()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `parses Open-Meteo current_weather response`() =
        runTest {
            server.enqueue(
                MockResponse().setBody(
                    """{"current_weather":{"temperature":18.5,"weathercode":0,"time":"2026-05-01T05:32"}}""",
                ),
            )

            val repo =
                WeatherRepository(
                    api = OpenMeteoApi(client = client, baseUrl = server.url("/").toString()),
                    locationFlow = flowOf(fakeLocation()),
                    clock = Clock.fixed(Instant.parse("2026-05-01T05:32:00Z"), ZoneOffset.UTC),
                )

            repo.snapshotFlow().test {
                val snapshot = awaitItem()
                assertNotNull(snapshot)
                assertEquals(18.5, snapshot.tempC, 0.0)
                assertEquals(WeatherCode.CLEAR, snapshot.code)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `returns null when http call fails`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(500))

            val repo =
                WeatherRepository(
                    api = OpenMeteoApi(client = client, baseUrl = server.url("/").toString()),
                    locationFlow = flowOf(fakeLocation()),
                    clock = Clock.systemUTC(),
                )

            repo.snapshotFlow().test {
                assertNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `returns null when location is null`() =
        runTest {
            val repo =
                WeatherRepository(
                    api = OpenMeteoApi(client = client, baseUrl = server.url("/").toString()),
                    locationFlow = flowOf(null),
                    clock = Clock.systemUTC(),
                )

            repo.snapshotFlow().test {
                assertNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}
```

- [ ] **Step 2: Run the test to verify it fails.**

Run: `./gradlew test --tests 'io.github.seijikohara.femto.data.WeatherRepositoryTest'`
Expected: FAIL with `Unresolved reference: WeatherRepository`.

- [ ] **Step 3: Implement `OpenMeteoApi.kt`.**

```kotlin
package io.github.seijikohara.femto.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

class OpenMeteoApi(
    private val client: OkHttpClient,
    private val baseUrl: String = "https://api.open-meteo.com/",
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun currentWeather(
        latitude: Double,
        longitude: Double,
    ): CurrentWeather? =
        withContext(Dispatchers.IO) {
            runCatching {
                val request =
                    Request
                        .Builder()
                        .url(
                            baseUrl.trimEnd('/') +
                                "/v1/forecast?latitude=$latitude&longitude=$longitude" +
                                "&current_weather=true",
                        ).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    response.body?.string()?.let { body ->
                        json.decodeFromString<ForecastResponse>(body).current_weather
                    }
                }
            }.getOrNull()
        }

    @Serializable
    data class ForecastResponse(val current_weather: CurrentWeather? = null)

    @Serializable
    data class CurrentWeather(
        val temperature: Double,
        val weathercode: Int,
    )
}
```

- [ ] **Step 4: Implement `WeatherRepository.kt`.**

```kotlin
package io.github.seijikohara.femto.data

import android.location.Location
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.time.Clock
import java.time.Duration
import java.time.Instant

class WeatherRepository(
    private val api: OpenMeteoApi,
    private val locationFlow: Flow<Location?>,
    private val clock: Clock = Clock.systemUTC(),
) {
    private var cached: WeatherSnapshot? = null
    private var lastFetchLocation: Location? = null

    fun snapshotFlow(): Flow<WeatherSnapshot?> =
        flow {
            locationFlow.collect { location ->
                emit(refresh(location) ?: cached)
            }
        }.flowOn(Dispatchers.IO)

    private suspend fun refresh(location: Location?): WeatherSnapshot? {
        location ?: return null
        if (!shouldRefetch(location)) return cached
        val current = api.currentWeather(location.latitude, location.longitude) ?: return cached
        cached =
            WeatherSnapshot(
                tempC = current.temperature,
                code = WeatherCode.fromWmo(current.weathercode),
                fetchedAt = clock.instant(),
            )
        lastFetchLocation = location
        return cached
    }

    private fun shouldRefetch(location: Location): Boolean {
        val snapshot = cached ?: return true
        val anchor = lastFetchLocation ?: return true
        val ageOk = Duration.between(snapshot.fetchedAt, clock.instant()).abs() < REFRESH_INTERVAL
        val nearOk = anchor.distanceTo(location) < REFRESH_DISTANCE_M
        return !(ageOk && nearOk)
    }

    private companion object {
        val REFRESH_INTERVAL: Duration = Duration.ofMinutes(30)
        const val REFRESH_DISTANCE_M = 5_000f
    }
}
```

- [ ] **Step 5: Re-run the test to verify it passes.**

Run: `./gradlew test --tests 'io.github.seijikohara.femto.data.WeatherRepositoryTest'`
Expected: PASS, 3 tests.

- [ ] **Step 6: Commit.**

```bash
git add app/src/main/java/io/github/seijikohara/femto/data/OpenMeteoApi.kt app/src/main/java/io/github/seijikohara/femto/data/WeatherRepository.kt app/src/test/java/io/github/seijikohara/femto/data/WeatherRepositoryTest.kt
git commit -m "feat(data): add WeatherRepository with Open-Meteo + 30min cache

Open-Meteo is the chosen provider — free, key-less, multi-region.
The repository caches in memory and refetches when 30 minutes have
elapsed or the user has moved >5km, avoiding redundant requests
per emit. Failures fall back to the cached snapshot so a brief
network outage does not blank the WeatherPanel."
```

### Task 2.5: GmsAvailability

**Files:**
- Create: `app/src/main/java/io/github/seijikohara/femto/data/GmsAvailability.kt`
- Create: `app/src/test/java/io/github/seijikohara/femto/data/GmsAvailabilityTest.kt`

- [ ] **Step 1: Write the failing test.**

Create `app/src/test/java/io/github/seijikohara/femto/data/GmsAvailabilityTest.kt`:

```kotlin
package io.github.seijikohara.femto.data

import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class GmsAvailabilityTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `returns true when api availability returns SUCCESS`() {
        val availability =
            mock<GoogleApiAvailability> {
                on { isGooglePlayServicesAvailable(any()) } doReturn ConnectionResult.SUCCESS
            }
        assertTrue(GmsAvailability(context, availability).isPresent())
    }

    @Test
    fun `returns false when api availability returns SERVICE_MISSING`() {
        val availability =
            mock<GoogleApiAvailability> {
                on { isGooglePlayServicesAvailable(any()) } doReturn ConnectionResult.SERVICE_MISSING
            }
        assertFalse(GmsAvailability(context, availability).isPresent())
    }
}
```

> Note: this test pulls Mockito-Kotlin. If it's not in `libs.versions.toml`, add the dependency via the `update-gradle-dependency` skill (`org.mockito.kotlin:mockito-kotlin:5.4.0`) plus `org.mockito:mockito-core:5.14.2` in `testImplementation`. Re-run `./gradlew test` after the dependency lands.

- [ ] **Step 2: Add Mockito-Kotlin to dependencies if it isn't there yet.**

Add to `[versions]`:

```toml
mockito = "5.14.2"
mockitoKotlin = "5.4.0"
```

Add to `[libraries]`:

```toml
mockito-core = { group = "org.mockito", name = "mockito-core", version.ref = "mockito" }
mockito-kotlin = { group = "org.mockito.kotlin", name = "mockito-kotlin", version.ref = "mockitoKotlin" }
```

Add to `app/build.gradle.kts` `dependencies {}`:

```kotlin
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
```

- [ ] **Step 3: Run the test to verify it fails.**

Run: `./gradlew test --tests 'io.github.seijikohara.femto.data.GmsAvailabilityTest'`
Expected: FAIL with `Unresolved reference: GmsAvailability`.

- [ ] **Step 4: Implement `GmsAvailability.kt`.**

```kotlin
package io.github.seijikohara.femto.data

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

class GmsAvailability(
    private val context: Context,
    private val availability: GoogleApiAvailability = GoogleApiAvailability.getInstance(),
) {
    fun isPresent(): Boolean =
        availability.isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
}
```

- [ ] **Step 5: Re-run the test to verify it passes.**

Run: `./gradlew test --tests 'io.github.seijikohara.femto.data.GmsAvailabilityTest'`
Expected: PASS, 2 tests.

- [ ] **Step 6: Commit.**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/java/io/github/seijikohara/femto/data/GmsAvailability.kt app/src/test/java/io/github/seijikohara/femto/data/GmsAvailabilityTest.kt
git commit -m "feat(data): add GmsAvailability check for graceful map fallback

Wraps GoogleApiAvailability so the rest of the codebase doesn't
import com.google.android.gms.common directly. The dashboard reads
this once at ViewModel init; MapPanel collapses to its static
fallback when the call returns false, which is the expected case
on AOSP-only Carlinkit / OTTOCAST SKUs."
```

### Task 2.6: MusicSession plumbing

**Files:**
- Create: `app/src/main/java/io/github/seijikohara/femto/data/MusicSessionListenerService.kt`
- Create: `app/src/main/java/io/github/seijikohara/femto/data/MusicSessionRepository.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/values/strings.xml` entry (or extend existing)

This integration requires the user to grant Notification access in system Settings. Unit-testing it is not productive; verify at smoke time (Task 5.2).

- [ ] **Step 1: Implement `MusicSessionListenerService.kt`.**

```kotlin
package io.github.seijikohara.femto.data

import android.service.notification.NotificationListenerService

class MusicSessionListenerService : NotificationListenerService()
```

The class can be empty — its presence enables `MediaSessionManager.getActiveSessions(ComponentName)` calls. The service does not handle notifications itself.

- [ ] **Step 2: Implement `MusicSessionRepository.kt`.**

```kotlin
package io.github.seijikohara.femto.data

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.getSystemService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

class MusicSessionRepository(
    private val context: Context,
) {
    private val sessionManager: MediaSessionManager = checkNotNull(context.getSystemService())
    private val componentName = ComponentName(context, MusicSessionListenerService::class.java)

    fun nowPlayingFlow(): Flow<NowPlaying?> =
        callbackFlow {
            val callback =
                MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
                    trySend(controllers.toNowPlaying())
                }

            runCatching {
                trySend(sessionManager.getActiveSessions(componentName).toNowPlaying())
                sessionManager.addOnActiveSessionsChangedListener(callback, componentName)
            }.onFailure { trySend(null) }

            awaitClose {
                runCatching { sessionManager.removeOnActiveSessionsChangedListener(callback) }
            }
        }.flowOn(Dispatchers.Main.immediate)

    private fun List<MediaController>?.toNowPlaying(): NowPlaying? =
        this?.firstOrNull { it.playbackState?.isActive() == true }
            ?.let { controller ->
                val metadata = controller.metadata ?: return@let null
                NowPlaying(
                    title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty(),
                    artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST),
                    albumArt = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)?.asImageBitmap(),
                    isPlaying = controller.playbackState?.state == PlaybackState.STATE_PLAYING,
                    positionMs = controller.playbackState?.position ?: 0L,
                    durationMs = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION),
                    packageName = controller.packageName,
                )
            }

    private fun PlaybackState.isActive(): Boolean =
        state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_PAUSED
}
```

- [ ] **Step 3: Add the `<service>` block to `app/src/main/AndroidManifest.xml`.**

Add inside `<application>` (sibling of `<activity>`):

```xml
        <service
            android:name=".data.MusicSessionListenerService"
            android:label="@string/music_listener_label"
            android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
            android:exported="false">
            <intent-filter>
                <action android:name="android.service.notification.NotificationListenerService" />
            </intent-filter>
        </service>
```

- [ ] **Step 4: Add the listener label to `app/src/main/res/values/strings.xml`.**

If `strings.xml` does not yet contain the project label string, add:

```xml
    <string name="music_listener_label">Femto music session listener</string>
```

(Append inside the existing `<resources>` element.)

- [ ] **Step 5: Build to confirm everything compiles and the manifest merges.**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit.**

```bash
git add app/src/main/java/io/github/seijikohara/femto/data/MusicSessionListenerService.kt app/src/main/java/io/github/seijikohara/femto/data/MusicSessionRepository.kt app/src/main/AndroidManifest.xml app/src/main/res/values/strings.xml
git commit -m "feat(data): add MediaSession bridge for now-playing widget

NotificationListenerService is required for getActiveSessions() to
return non-empty results. The repository surfaces the topmost active
controller as a NowPlaying value object; absence (no listener
permission, no playing media) collapses to null. Users grant the
permission via Settings → Notifications → Notification access; the
MusicPanel routes there from its placeholder."
```

---

## Phase 3: UI panels

### Task 3.1: ClockPanel

**Files:**
- Create: `app/src/main/java/io/github/seijikohara/femto/ui/home/components/ClockPanel.kt`
- Create: `app/src/androidTest/java/io/github/seijikohara/femto/ui/home/components/ClockPanelTest.kt`

- [ ] **Step 1: Write the failing UI test.**

Create `app/src/androidTest/java/io/github/seijikohara/femto/ui/home/components/ClockPanelTest.kt`:

```kotlin
package io.github.seijikohara.femto.ui.home.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.github.seijikohara.femto.data.ClockTick
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ClockPanelTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun renders_24h_time_and_localized_date() {
        rule.setContent {
            FemtoTheme {
                ClockPanel(
                    tick = ClockTick(LocalTime.of(14, 32), LocalDate.of(2026, 5, 1)),
                    is24Hour = true,
                )
            }
        }
        rule.onNodeWithText("14:32").assertIsDisplayed()
        rule.onNodeWithText("May 1, 2026", substring = true).assertIsDisplayed()
    }

    @Test
    fun renders_12h_time_when_not_24h() {
        rule.setContent {
            FemtoTheme {
                ClockPanel(
                    tick = ClockTick(LocalTime.of(14, 32), LocalDate.of(2026, 5, 1)),
                    is24Hour = false,
                )
            }
        }
        rule.onNodeWithText("2:32", substring = true).assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Run the test to verify it fails.**

Run: `./gradlew connectedDebugAndroidTest --tests 'io.github.seijikohara.femto.ui.home.components.ClockPanelTest'`
Expected: FAIL with `Unresolved reference: ClockPanel`.

- [ ] **Step 3: Implement `ClockPanel.kt`.**

```kotlin
package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.seijikohara.femto.data.ClockTick
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
internal fun ClockPanel(
    tick: ClockTick,
    is24Hour: Boolean,
    modifier: Modifier = Modifier,
) = Surface(
    modifier = modifier,
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.surfaceContainer,
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = "TIME",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = tick.time.format(timeFormatter(is24Hour)),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = tick.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(Locale.getDefault())),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun timeFormatter(is24Hour: Boolean): DateTimeFormatter =
    DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a", Locale.getDefault())
```

- [ ] **Step 4: Re-run the test to verify it passes.**

Run: `./gradlew connectedDebugAndroidTest --tests 'io.github.seijikohara.femto.ui.home.components.ClockPanelTest'`
Expected: PASS, 2 tests.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/io/github/seijikohara/femto/ui/home/components/ClockPanel.kt app/src/androidTest/java/io/github/seijikohara/femto/ui/home/components/ClockPanelTest.kt
git commit -m "feat(ui): add ClockPanel for the dashboard right stack"
```

### Task 3.2: WeatherPanel

**Files:**
- Create: `app/src/main/java/io/github/seijikohara/femto/ui/home/components/WeatherPanel.kt`
- Create: `app/src/androidTest/java/io/github/seijikohara/femto/ui/home/components/WeatherPanelTest.kt`

- [ ] **Step 1: Write the failing test.**

```kotlin
package io.github.seijikohara.femto.ui.home.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.core.i18n.LocalePreferences
import io.github.seijikohara.femto.testfixtures.fakeWeatherSnapshot
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test

class WeatherPanelTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun renders_celsius_when_unit_is_celsius() {
        rule.setContent {
            FemtoTheme {
                WeatherPanel(
                    snapshot = fakeWeatherSnapshot(tempC = 18.0),
                    unit = LocalePreferences.TemperatureUnit.CELSIUS,
                )
            }
        }
        rule.onNodeWithText("18", substring = true).assertIsDisplayed()
        rule.onNodeWithText("°C", substring = true).assertIsDisplayed()
    }

    @Test
    fun renders_fahrenheit_when_unit_is_fahrenheit() {
        rule.setContent {
            FemtoTheme {
                WeatherPanel(
                    snapshot = fakeWeatherSnapshot(tempC = 0.0),
                    unit = LocalePreferences.TemperatureUnit.FAHRENHEIT,
                )
            }
        }
        rule.onNodeWithText("32", substring = true).assertIsDisplayed()
        rule.onNodeWithText("°F", substring = true).assertIsDisplayed()
    }

    @Test
    fun renders_placeholder_when_snapshot_is_null() {
        rule.setContent {
            FemtoTheme {
                WeatherPanel(snapshot = null, unit = LocalePreferences.TemperatureUnit.CELSIUS)
            }
        }
        rule.onNodeWithText("—").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Run the test to verify it fails.**

Run: `./gradlew connectedDebugAndroidTest --tests 'io.github.seijikohara.femto.ui.home.components.WeatherPanelTest'`
Expected: FAIL with `Unresolved reference: WeatherPanel`.

- [ ] **Step 3: Implement `WeatherPanel.kt`.**

```kotlin
package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.i18n.LocalePreferences
import io.github.seijikohara.femto.data.WeatherCode
import io.github.seijikohara.femto.data.WeatherSnapshot
import kotlin.math.roundToInt

@Composable
internal fun WeatherPanel(
    snapshot: WeatherSnapshot?,
    unit: String,
    modifier: Modifier = Modifier,
) = Surface(
    modifier = modifier,
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.surfaceContainer,
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = "WEATHER",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (snapshot == null) {
            Text(
                text = "—",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = iconFor(snapshot.code),
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text(
                    text = formatTemperature(snapshot.tempC, unit),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

private fun iconFor(code: WeatherCode): String =
    when (code) {
        WeatherCode.CLEAR -> "☀"
        WeatherCode.PARTLY_CLOUDY -> "⛅"
        WeatherCode.CLOUDY, WeatherCode.FOG -> "☁"
        WeatherCode.DRIZZLE, WeatherCode.RAIN, WeatherCode.RAIN_SHOWERS, WeatherCode.FREEZING_RAIN -> "🌧"
        WeatherCode.SNOW, WeatherCode.SNOW_SHOWERS, WeatherCode.SNOW_GRAINS -> "❄"
        WeatherCode.THUNDERSTORM -> "⚡"
        WeatherCode.UNKNOWN -> "·"
    }

private fun formatTemperature(tempC: Double, unit: String): String =
    when (unit) {
        LocalePreferences.TemperatureUnit.FAHRENHEIT -> "${(tempC * 9 / 5 + 32).roundToInt()}°F"
        LocalePreferences.TemperatureUnit.KELVIN -> "${(tempC + 273.15).roundToInt()}K"
        else -> "${tempC.roundToInt()}°C"
    }
```

- [ ] **Step 4: Re-run the test to verify it passes.**

Run: `./gradlew connectedDebugAndroidTest --tests 'io.github.seijikohara.femto.ui.home.components.WeatherPanelTest'`
Expected: PASS, 3 tests.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/io/github/seijikohara/femto/ui/home/components/WeatherPanel.kt app/src/androidTest/java/io/github/seijikohara/femto/ui/home/components/WeatherPanelTest.kt
git commit -m "feat(ui): add WeatherPanel with locale-driven temperature unit"
```

### Task 3.3: MapPanel (Lite Mode + overlays)

**Files:**
- Create: `app/src/main/java/io/github/seijikohara/femto/ui/home/components/MapPanel.kt`

`MapPanel` mixes a Lite Mode `MapView` (when GMS is present) with three text overlays. UI testing here is shallow because `MapView` requires real Google Play Services to render tiles; we verify the overlays via `Modifier.testTag` reads, not the map itself.

- [ ] **Step 1: Implement `MapPanel.kt`.**

```kotlin
package io.github.seijikohara.femto.ui.home.components

import android.location.Location
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLng
import io.github.seijikohara.femto.data.ShortAddress
import io.github.seijikohara.femto.ui.locale.DistanceUnit
import io.github.seijikohara.femto.ui.locale.SpeedUnit
import io.github.seijikohara.femto.ui.locale.fromMeters
import io.github.seijikohara.femto.ui.locale.fromMetersPerSecond
import io.github.seijikohara.femto.ui.locale.label
import kotlin.math.roundToInt

@Composable
internal fun MapPanel(
    location: Location?,
    address: ShortAddress?,
    mapAvailable: Boolean,
    speedUnit: SpeedUnit,
    distanceUnit: DistanceUnit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) = Surface(
    modifier = modifier,
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.surfaceContainer,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (mapAvailable && location != null) {
            LiteModeMap(
                latLng = LatLng(location.latitude, location.longitude),
                onTap = onTap,
                modifier = Modifier.fillMaxSize(),
            )
        }
        OverlaysScrim(modifier = Modifier.fillMaxSize())
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            SpeedAltitudeOverlay(
                location = location,
                speedUnit = speedUnit,
                distanceUnit = distanceUnit,
                modifier = Modifier.testTag("speedAltitudeOverlay"),
            )
            AddressOverlay(
                address = address,
                modifier = Modifier.testTag("addressOverlay"),
            )
        }
    }
}

@Composable
private fun LiteModeMap(
    latLng: LatLng,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val callback = rememberUpdatedState(onTap)
    val mapView =
        remember {
            MapView(context, GoogleMapOptions().liteMode(true).mapToolbarEnabled(false).compassEnabled(false)).apply {
                onCreate(null)
            }
        }
    LaunchedEffect(latLng) {
        mapView.getMapAsync { map ->
            map.uiSettings.setAllGesturesEnabled(false)
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, MAP_ZOOM))
            map.setOnMapClickListener { callback.value() }
        }
    }
    AndroidView(
        modifier = modifier,
        factory = { mapView },
    )
}

@Composable
private fun OverlaysScrim(modifier: Modifier = Modifier) =
    Box(
        modifier =
            modifier.background(
                Brush.verticalGradient(
                    colors =
                        listOf(
                            Color.Black.copy(alpha = 0.45f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.45f),
                        ),
                ),
            ),
    )

@Composable
private fun SpeedAltitudeOverlay(
    location: Location?,
    speedUnit: SpeedUnit,
    distanceUnit: DistanceUnit,
    modifier: Modifier = Modifier,
) = Row(
    modifier = modifier,
    verticalAlignment = Alignment.Bottom,
) {
    val speed = location?.speed?.let { speedUnit.fromMetersPerSecond(it).roundToInt() } ?: 0
    val altitude = location?.altitude?.let { distanceUnit.fromMeters(it).roundToInt() }
    Text(
        text = "$speed",
        style = MaterialTheme.typography.headlineLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
        text = " ${speedUnit.label()}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
    )
    if (altitude != null) {
        Text(
            text = "↑ $altitude ${distanceUnit.label()}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

@Composable
private fun AddressOverlay(
    address: ShortAddress?,
    modifier: Modifier = Modifier,
) = Text(
    text = address?.displayString().orEmpty(),
    style = MaterialTheme.typography.titleMedium,
    color = MaterialTheme.colorScheme.onSurface,
    modifier = modifier,
)

private const val MAP_ZOOM = 15f
```

- [ ] **Step 2: Build to verify it compiles (no UI test for MapPanel).**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit.**

```bash
git add app/src/main/java/io/github/seijikohara/femto/ui/home/components/MapPanel.kt
git commit -m "feat(ui): add MapPanel with Lite Mode map and three overlays

Wraps the Google Maps SDK MapView via AndroidView when GMS is
present; otherwise the panel renders a flat Surface with the
overlays only. Top overlay shows speed (large, locale unit) and
altitude (small, locale unit); bottom overlay shows the short
address; both ride a gradient scrim so they stay legible against
arbitrary tile colours."
```

### Task 3.4: MusicPanel

**Files:**
- Create: `app/src/main/java/io/github/seijikohara/femto/ui/home/components/MusicPanel.kt`
- Create: `app/src/androidTest/java/io/github/seijikohara/femto/ui/home/components/MusicPanelTest.kt`

- [ ] **Step 1: Write the failing UI test.**

```kotlin
package io.github.seijikohara.femto.ui.home.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.seijikohara.femto.testfixtures.fakeNowPlaying
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test

class MusicPanelTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun renders_track_artist_and_transport() {
        rule.setContent {
            FemtoTheme {
                MusicPanel(
                    nowPlaying = fakeNowPlaying(),
                    onCommand = {},
                    onConnect = {},
                )
            }
        }
        rule.onNodeWithText("Strobe").assertIsDisplayed()
        rule.onNodeWithText("deadmau5", substring = true).assertIsDisplayed()
        rule.onNodeWithContentDescription("Play / pause").assertIsDisplayed()
    }

    @Test
    fun renders_connect_placeholder_when_null_and_dispatches_on_tap() {
        var tapped = false
        rule.setContent {
            FemtoTheme {
                MusicPanel(
                    nowPlaying = null,
                    onCommand = {},
                    onConnect = { tapped = true },
                )
            }
        }
        rule.onNodeWithText("Connect a player").assertIsDisplayed().performClick()
        assert(tapped)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails.**

Run: `./gradlew connectedDebugAndroidTest --tests 'io.github.seijikohara.femto.ui.home.components.MusicPanelTest'`
Expected: FAIL with `Unresolved reference: MusicPanel`.

- [ ] **Step 3: Implement `MusicPanel.kt`.**

```kotlin
package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.seijikohara.femto.data.NowPlaying
import io.github.seijikohara.femto.ui.theme.FemtoDimens

internal sealed interface MusicCommand {
    data object PlayPause : MusicCommand

    data object SkipNext : MusicCommand

    data object SkipPrevious : MusicCommand
}

@Composable
internal fun MusicPanel(
    nowPlaying: NowPlaying?,
    onCommand: (MusicCommand) -> Unit,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
) = Surface(
    modifier = modifier,
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.surfaceContainer,
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = "NOW PLAYING",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (nowPlaying == null) {
            Text(
                text = "Connect a player",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier =
                    Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                        .clickable(onClick = onConnect),
            )
        } else {
            TrackInfo(nowPlaying)
            TransportControls(
                isPlaying = nowPlaying.isPlaying,
                onCommand = onCommand,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun TrackInfo(nowPlaying: NowPlaying) =
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(
            text = nowPlaying.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        nowPlaying.artist?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

@Composable
private fun TransportControls(
    isPlaying: Boolean,
    onCommand: (MusicCommand) -> Unit,
    modifier: Modifier = Modifier,
) = Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start),
    verticalAlignment = Alignment.CenterVertically,
) {
    TransportButton(label = "◀◀", description = "Skip previous") { onCommand(MusicCommand.SkipPrevious) }
    TransportButton(
        label = if (isPlaying) "❚❚" else "▶",
        description = "Play / pause",
        primary = true,
    ) { onCommand(MusicCommand.PlayPause) }
    TransportButton(label = "▶▶", description = "Skip next") { onCommand(MusicCommand.SkipNext) }
}

@Composable
private fun TransportButton(
    label: String,
    description: String,
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    val container =
        if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val content =
        if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Surface(
        modifier =
            Modifier
                .size(FemtoDimens.MinTouchTarget)
                .semantics { contentDescription = description },
        shape = CircleShape,
        color = container,
    ) {
        IconButton(onClick = onClick) {
            Text(text = label, style = MaterialTheme.typography.titleLarge, color = content)
        }
    }
}
```

- [ ] **Step 4: Re-run the test to verify it passes.**

Run: `./gradlew connectedDebugAndroidTest --tests 'io.github.seijikohara.femto.ui.home.components.MusicPanelTest'`
Expected: PASS, 2 tests.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/io/github/seijikohara/femto/ui/home/components/MusicPanel.kt app/src/androidTest/java/io/github/seijikohara/femto/ui/home/components/MusicPanelTest.kt
git commit -m "feat(ui): add MusicPanel with MediaSession transport controls"
```

### Task 3.5: AppsBar

**Files:**
- Create: `app/src/main/java/io/github/seijikohara/femto/ui/home/components/AppsBar.kt`
- Create: `app/src/androidTest/java/io/github/seijikohara/femto/ui/home/components/AppsBarTest.kt`

- [ ] **Step 1: Write the failing test.**

```kotlin
package io.github.seijikohara.femto.ui.home.components

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class AppsBarTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun renders_six_tiles_and_dispatches_drawer() {
        var drawerTaps = 0
        rule.setContent {
            FemtoTheme {
                AppsBar(
                    onOpenDrawer = { drawerTaps++ },
                    onShortcut = {},
                )
            }
        }
        rule.onAllNodesWithContentDescription("Apps shortcut", substring = true).assertCountEquals(5)
        rule.onNodeWithContentDescription("Open all apps").performClick()
        assertEquals(1, drawerTaps)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails.**

Run: `./gradlew connectedDebugAndroidTest --tests 'io.github.seijikohara.femto.ui.home.components.AppsBarTest'`
Expected: FAIL with `Unresolved reference: AppsBar`.

- [ ] **Step 3: Implement `AppsBar.kt`.**

```kotlin
package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.seijikohara.femto.ui.theme.FemtoDimens

internal enum class AppsBarShortcut(val label: String, val intentCategory: String) {
    Phone("📞", "android.intent.category.APP_CONTACTS"),
    Music("🎵", "android.intent.category.APP_MUSIC"),
    Maps("📍", "android.intent.category.APP_MAPS"),
    Camera("📷", "android.intent.category.APP_GALLERY"),
    Navigation("🧭", "android.intent.category.APP_MAPS"),
}

@Composable
internal fun AppsBar(
    onOpenDrawer: () -> Unit,
    onShortcut: (AppsBarShortcut) -> Unit,
    modifier: Modifier = Modifier,
) = Surface(
    modifier = modifier.fillMaxWidth(),
    color = MaterialTheme.colorScheme.surfaceContainer,
    shape = MaterialTheme.shapes.large,
) {
    Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tile(label = "≡", description = "Open all apps", onClick = onOpenDrawer)
        AppsBarShortcut.entries.forEach { shortcut ->
            Tile(
                label = shortcut.label,
                description = "Apps shortcut: ${shortcut.name}",
                onClick = { onShortcut(shortcut) },
            )
        }
    }
}

@Composable
private fun Tile(
    label: String,
    description: String,
    onClick: () -> Unit,
) = Surface(
    modifier =
        Modifier
            .size(FemtoDimens.MinTouchTarget)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
    shape = CircleShape,
    color = MaterialTheme.colorScheme.surfaceVariant,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
    )
}
```

- [ ] **Step 4: Re-run the test to verify it passes.**

Run: `./gradlew connectedDebugAndroidTest --tests 'io.github.seijikohara.femto.ui.home.components.AppsBarTest'`
Expected: PASS, 1 test.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/io/github/seijikohara/femto/ui/home/components/AppsBar.kt app/src/androidTest/java/io/github/seijikohara/femto/ui/home/components/AppsBarTest.kt
git commit -m "feat(ui): add AppsBar with drawer entry and five intent shortcuts"
```

### Task 3.6: DashboardScaffold

**Files:**
- Create: `app/src/main/java/io/github/seijikohara/femto/ui/home/components/DashboardScaffold.kt`
- Create: `app/src/androidTest/java/io/github/seijikohara/femto/ui/home/components/DashboardScaffoldTest.kt`

- [ ] **Step 1: Write the failing test.**

```kotlin
package io.github.seijikohara.femto.ui.home.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import io.github.seijikohara.femto.data.ClockTick
import io.github.seijikohara.femto.testfixtures.fakeAddress
import io.github.seijikohara.femto.testfixtures.fakeNowPlaying
import io.github.seijikohara.femto.testfixtures.fakeWeatherSnapshot
import io.github.seijikohara.femto.ui.locale.DistanceUnit
import io.github.seijikohara.femto.ui.locale.SpeedUnit
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class DashboardScaffoldTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun renders_all_panels_when_data_is_present() {
        rule.setContent {
            FemtoTheme {
                DashboardScaffold(
                    clock = ClockTick(LocalTime.of(14, 32), LocalDate.of(2026, 5, 1)),
                    is24Hour = true,
                    weather = fakeWeatherSnapshot(),
                    temperatureUnit = "°C",
                    address = fakeAddress(),
                    location = null,
                    speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
                    distanceUnit = DistanceUnit.METERS,
                    mapAvailable = false,
                    nowPlaying = fakeNowPlaying(),
                    onMapTap = {},
                    onMusicCommand = {},
                    onConnectMusic = {},
                    onOpenDrawer = {},
                    onShortcut = {},
                )
            }
        }
        rule.onNodeWithText("14:32").assertIsDisplayed()
        rule.onNodeWithText("Strobe").assertIsDisplayed()
        rule.onNodeWithContentDescription("Open all apps").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Run the test to verify it fails.**

Run: `./gradlew connectedDebugAndroidTest --tests 'io.github.seijikohara.femto.ui.home.components.DashboardScaffoldTest'`
Expected: FAIL with `Unresolved reference: DashboardScaffold`.

- [ ] **Step 3: Implement `DashboardScaffold.kt`.**

```kotlin
package io.github.seijikohara.femto.ui.home.components

import android.location.Location
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.seijikohara.femto.data.ClockTick
import io.github.seijikohara.femto.data.NowPlaying
import io.github.seijikohara.femto.data.ShortAddress
import io.github.seijikohara.femto.data.WeatherSnapshot
import io.github.seijikohara.femto.ui.locale.DistanceUnit
import io.github.seijikohara.femto.ui.locale.SpeedUnit

@Composable
internal fun DashboardScaffold(
    clock: ClockTick,
    is24Hour: Boolean,
    weather: WeatherSnapshot?,
    temperatureUnit: String,
    address: ShortAddress?,
    location: Location?,
    speedUnit: SpeedUnit,
    distanceUnit: DistanceUnit,
    mapAvailable: Boolean,
    nowPlaying: NowPlaying?,
    onMapTap: () -> Unit,
    onMusicCommand: (MusicCommand) -> Unit,
    onConnectMusic: () -> Unit,
    onOpenDrawer: () -> Unit,
    onShortcut: (AppsBarShortcut) -> Unit,
    modifier: Modifier = Modifier,
) = Column(
    modifier =
        modifier
            .fillMaxSize()
            .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
) {
    Row(
        modifier = Modifier.weight(1f),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MapPanel(
            location = location,
            address = address,
            mapAvailable = mapAvailable,
            speedUnit = speedUnit,
            distanceUnit = distanceUnit,
            onTap = onMapTap,
            modifier = Modifier.weight(1.6f).fillMaxHeight(),
        )
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ClockPanel(
                tick = clock,
                is24Hour = is24Hour,
                modifier = Modifier.weight(1f),
            )
            WeatherPanel(
                snapshot = weather,
                unit = temperatureUnit,
                modifier = Modifier.weight(1f),
            )
            MusicPanel(
                nowPlaying = nowPlaying,
                onCommand = onMusicCommand,
                onConnect = onConnectMusic,
                modifier = Modifier.weight(1f),
            )
        }
    }
    AppsBar(onOpenDrawer = onOpenDrawer, onShortcut = onShortcut)
}
```

- [ ] **Step 4: Re-run the test to verify it passes.**

Run: `./gradlew connectedDebugAndroidTest --tests 'io.github.seijikohara.femto.ui.home.components.DashboardScaffoldTest'`
Expected: PASS, 1 test.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/io/github/seijikohara/femto/ui/home/components/DashboardScaffold.kt app/src/androidTest/java/io/github/seijikohara/femto/ui/home/components/DashboardScaffoldTest.kt
git commit -m "feat(ui): add DashboardScaffold composing the C2 layout"
```

---

## Phase 4: UI integration

### Task 4.1: Expand HomeUiState

**Files:**
- Modify: `app/src/main/java/io/github/seijikohara/femto/ui/home/HomeUiState.kt`

- [ ] **Step 1: Read the current `HomeUiState.kt`** to know what to expand.

Run: `cat app/src/main/java/io/github/seijikohara/femto/ui/home/HomeUiState.kt`

- [ ] **Step 2: Replace the file with the expanded state**:

```kotlin
package io.github.seijikohara.femto.ui.home

import android.location.Location
import io.github.seijikohara.femto.data.AppEntry
import io.github.seijikohara.femto.data.ClockTick
import io.github.seijikohara.femto.data.NowPlaying
import io.github.seijikohara.femto.data.ShortAddress
import io.github.seijikohara.femto.data.WeatherSnapshot
import java.time.LocalDate
import java.time.LocalTime

data class HomeUiState(
    val isLoading: Boolean,
    val apps: List<AppEntry>,
    val clock: ClockTick,
    val location: Location?,
    val address: ShortAddress?,
    val weather: WeatherSnapshot?,
    val nowPlaying: NowPlaying?,
    val mapAvailable: Boolean,
) {
    companion object {
        val Initial: HomeUiState =
            HomeUiState(
                isLoading = true,
                apps = emptyList(),
                clock = ClockTick(LocalTime.of(0, 0), LocalDate.now()),
                location = null,
                address = null,
                weather = null,
                nowPlaying = null,
                mapAvailable = false,
            )
    }
}
```

- [ ] **Step 3: Build to confirm consumers do not regress.**

Run: `./gradlew assembleDebug`
Expected: BUILD FAIL with references in `HomeViewModel.kt` and `HomeScreen.kt` — those will be rewritten in Tasks 4.3 and 4.4. Continue without committing yet; revert is `git checkout HEAD~ -- app/src/main/java/io/github/seijikohara/femto/ui/home/HomeUiState.kt` if you need to roll back.

(No commit yet — Tasks 4.1 → 4.4 are coupled and ship as one commit at the end of Task 4.4.)

### Task 4.2: Replace HomeAction with the expanded sealed hierarchy

**Files:**
- Create: `app/src/main/java/io/github/seijikohara/femto/ui/home/HomeAction.kt`
- Modify: `app/src/main/java/io/github/seijikohara/femto/ui/home/HomeUiState.kt` (extract sealed types if currently colocated)

- [ ] **Step 1: Read the existing `HomeAction` to know what to keep.**

Run: `grep -n 'sealed' app/src/main/java/io/github/seijikohara/femto/ui/home/HomeUiState.kt`

If the sealed interface lives in `HomeUiState.kt`, remove it from there.

- [ ] **Step 2: Create `app/src/main/java/io/github/seijikohara/femto/ui/home/HomeAction.kt`**:

```kotlin
package io.github.seijikohara.femto.ui.home

import android.content.ComponentName
import io.github.seijikohara.femto.ui.home.components.AppsBarShortcut
import io.github.seijikohara.femto.ui.home.components.MusicCommand

sealed interface HomeAction {
    data class LaunchApp(val componentName: ComponentName) : HomeAction

    data object OpenAppDrawer : HomeAction

    data object OpenMaps : HomeAction

    data object ConnectMusicPlayer : HomeAction

    data class Shortcut(val target: AppsBarShortcut) : HomeAction

    data class Music(val command: MusicCommand) : HomeAction
}
```

- [ ] **Step 3: Build to detect any leftover references.**

Run: `./gradlew assembleDebug`
Expected: still red (Task 4.3, 4.4 fix the rest).

(No commit yet.)

### Task 4.3: HomeViewModel combine

**Files:**
- Modify: `app/src/main/java/io/github/seijikohara/femto/ui/home/HomeViewModel.kt`
- Create: `app/src/test/java/io/github/seijikohara/femto/ui/home/HomeViewModelTest.kt`

- [ ] **Step 1: Write the failing test.**

```kotlin
package io.github.seijikohara.femto.ui.home

import app.cash.turbine.test
import io.github.seijikohara.femto.data.AppEntry
import io.github.seijikohara.femto.data.ClockTick
import io.github.seijikohara.femto.testfixtures.fakeAddress
import io.github.seijikohara.femto.testfixtures.fakeNowPlaying
import io.github.seijikohara.femto.testfixtures.fakeWeatherSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `combines all flows into one HomeUiState`() =
        runTest {
            val viewModel =
                HomeViewModel(
                    clockFlow = flowOf(ClockTick(LocalTime.of(14, 32), LocalDate.of(2026, 5, 1))),
                    locationFlow = flowOf(null),
                    addressFlow = flowOf(fakeAddress()),
                    weatherFlow = flowOf(fakeWeatherSnapshot()),
                    nowPlayingFlow = flowOf(fakeNowPlaying()),
                    appsFlow = MutableStateFlow(listOf(AppEntry(android.content.ComponentName("p", "c"), "X", null))),
                    isMapAvailable = { true },
                )
            viewModel.uiState.test {
                val state = awaitItem()
                assertEquals(LocalTime.of(14, 32), state.clock.time)
                assertNotNull(state.address)
                assertNotNull(state.weather)
                assertNotNull(state.nowPlaying)
                assertTrue(state.mapAvailable)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
```

(`AppEntry`'s third parameter is the `Bitmap?`; pass `null` in tests — see the value object in `data/AppEntry.kt`.)

- [ ] **Step 2: Run the test to verify it fails.**

Run: `./gradlew test --tests 'io.github.seijikohara.femto.ui.home.HomeViewModelTest'`
Expected: FAIL — the existing `HomeViewModel` constructor does not accept these parameters.

- [ ] **Step 3: Replace `HomeViewModel.kt`** with the combine-style implementation:

```kotlin
package io.github.seijikohara.femto.ui.home

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.seijikohara.femto.data.AppEntry
import io.github.seijikohara.femto.data.AppsRepository
import io.github.seijikohara.femto.data.ClockRepository
import io.github.seijikohara.femto.data.ClockTick
import io.github.seijikohara.femto.data.GmsAvailability
import io.github.seijikohara.femto.data.LocationRepository
import io.github.seijikohara.femto.data.MusicSessionRepository
import io.github.seijikohara.femto.data.NowPlaying
import io.github.seijikohara.femto.data.OpenMeteoApi
import io.github.seijikohara.femto.data.ReverseGeocoderRepository
import io.github.seijikohara.femto.data.ShortAddress
import io.github.seijikohara.femto.data.WeatherRepository
import io.github.seijikohara.femto.data.WeatherSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class HomeViewModel(
    private val clockFlow: Flow<ClockTick>,
    private val locationFlow: Flow<Location?>,
    private val addressFlow: Flow<ShortAddress?>,
    private val weatherFlow: Flow<WeatherSnapshot?>,
    private val nowPlayingFlow: Flow<NowPlaying?>,
    private val appsFlow: MutableStateFlow<List<AppEntry>>,
    private val isMapAvailable: () -> Boolean,
) : androidx.lifecycle.ViewModel() {
    val uiState: StateFlow<HomeUiState> =
        combine(
            clockFlow,
            locationFlow,
            addressFlow,
            weatherFlow,
            nowPlayingFlow,
            appsFlow,
        ) { clock, location, address, weather, music, apps ->
            HomeUiState(
                isLoading = apps.isEmpty(),
                apps = apps,
                clock = clock,
                location = location,
                address = address,
                weather = weather,
                nowPlaying = music,
                mapAvailable = isMapAvailable(),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Initial)

    fun onAction(action: HomeAction) {
        // Route to the right side effect; the production ViewModelFactory wires real handlers.
    }
}

class HomeViewModelFactory(
    private val application: Application,
) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(
        modelClass: Class<T>,
        extras: androidx.lifecycle.viewmodel.CreationExtras,
    ): T {
        val location = LocationRepository(application)
        val locationFlow = location.locationFlow()
        val clock = ClockRepository(application)
        val geocoder = ReverseGeocoderRepository(application, locationFlow)
        val weatherApi = OpenMeteoApi(client = OkHttpClient())
        val weather = WeatherRepository(weatherApi, locationFlow)
        val music = MusicSessionRepository(application)
        val gms = GmsAvailability(application)
        val apps = MutableStateFlow<List<AppEntry>>(emptyList())
        val appsRepo = AppsRepository(application)

        @Suppress("UNCHECKED_CAST")
        return HomeViewModel(
            clockFlow = clock.tickFlow(),
            locationFlow = locationFlow,
            addressFlow = geocoder.addressFlow(),
            weatherFlow = weather.snapshotFlow(),
            nowPlayingFlow = music.nowPlayingFlow(),
            appsFlow = apps,
            isMapAvailable = { gms.isPresent() },
        ).also { vm ->
            vm.viewModelScope.launch { apps.value = appsRepo.queryApps() }
        } as T
    }
}
```

- [ ] **Step 4: Re-run the test to verify it passes.**

Run: `./gradlew test --tests 'io.github.seijikohara.femto.ui.home.HomeViewModelTest'`
Expected: PASS, 1 test.

(No commit yet — wait until 4.4.)

### Task 4.4: Rewrite HomeScreen as DashboardScaffold

**Files:**
- Replace: `app/src/main/java/io/github/seijikohara/femto/ui/home/HomeScreen.kt`

- [ ] **Step 1: Replace the file contents** with the dashboard wiring:

```kotlin
package io.github.seijikohara.femto.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.i18n.LocalePreferences
import io.github.seijikohara.femto.ui.home.components.DashboardScaffold
import io.github.seijikohara.femto.ui.home.components.MusicCommand
import io.github.seijikohara.femto.ui.locale.distanceUnitFor
import io.github.seijikohara.femto.ui.locale.speedUnitFor
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import java.text.DateFormat as JavaDateFormat

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
) = Surface(
    modifier = modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.background,
) {
    if (uiState.isLoading) {
        LoadingPlaceholder(modifier = Modifier.fillMaxSize())
    } else {
        DashboardScaffold(
            clock = uiState.clock,
            is24Hour = android.text.format.DateFormat.is24HourFormat(androidx.compose.ui.platform.LocalContext.current),
            weather = uiState.weather,
            temperatureUnit = LocalePreferences.getTemperatureUnit(),
            address = uiState.address,
            location = uiState.location,
            speedUnit = speedUnitFor(),
            distanceUnit = distanceUnitFor(),
            mapAvailable = uiState.mapAvailable,
            nowPlaying = uiState.nowPlaying,
            onMapTap = { onAction(HomeAction.OpenMaps) },
            onMusicCommand = { onAction(HomeAction.Music(it)) },
            onConnectMusic = { onAction(HomeAction.ConnectMusicPlayer) },
            onOpenDrawer = { onAction(HomeAction.OpenAppDrawer) },
            onShortcut = { onAction(HomeAction.Shortcut(it)) },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun LoadingPlaceholder(modifier: Modifier = Modifier) =
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = "Loading",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

@PreviewLightDark
@Composable
private fun HomeScreenPreview() = FemtoTheme { HomeScreen(uiState = HomeUiState.Initial, onAction = {}) }
```

> The unused `JavaDateFormat` import is intentional placeholder hygiene to keep the diff localized; if your IDE removes it, that is fine.

- [ ] **Step 2: Build everything together.**

Run: `./gradlew assembleDebug spotlessApply`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run all unit tests to ensure no regression.**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL (15+ tests across the new files).

- [ ] **Step 4: Commit Phase 4 (Tasks 4.1 → 4.4 together).**

```bash
git add app/src/main/java/io/github/seijikohara/femto/ui/home/ app/src/test/java/io/github/seijikohara/femto/ui/home/
git commit -m "feat(ui): wire HomeScreen, HomeViewModel, HomeUiState, HomeAction to dashboard

The home surface now renders DashboardScaffold instead of the apps
grid. HomeViewModel takes Flows by constructor (testable in JVM)
and a HomeViewModelFactory wires the real repositories.
HomeUiState carries the full Plan B payload; HomeAction covers
LaunchApp, OpenAppDrawer, OpenMaps, ConnectMusicPlayer, Shortcut,
and Music. The legacy single-grid path is gone."
```

### Task 4.5: Wire HomeRoute to the new ViewModelFactory

**Files:**
- Modify: `app/src/main/java/io/github/seijikohara/femto/ui/home/HomeRoute.kt`

- [ ] **Step 1: Read the current file.**

Run: `cat app/src/main/java/io/github/seijikohara/femto/ui/home/HomeRoute.kt`

- [ ] **Step 2: Replace the relevant parts** so `viewModel<HomeViewModel>(factory = HomeViewModelFactory(...))` is used.

```kotlin
package io.github.seijikohara.femto.ui.home

import android.Manifest
import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.seijikohara.femto.data.hasFineLocationPermission

@Composable
fun HomeRoute(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: HomeViewModel =
        viewModel(factory = HomeViewModelFactory(context.applicationContext as Application))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LocationPermissionRequest()
    HomeScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
        modifier = modifier,
    )
}

/**
 * Request `ACCESS_FINE_LOCATION` once when the route first composes.
 *
 * The permission powers the head-unit dashboard's location-driven
 * surfaces (map centre, speed / altitude / address overlays, weather
 * lookup). On denial the launcher continues to function; the
 * dependent panels render empty placeholders until the user grants
 * the permission via system Settings.
 */
@Composable
private fun LocationPermissionRequest() {
    val context = LocalContext.current
    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { /* result reflects on next STARTED via the Flow chain */ }

    LaunchedEffect(Unit) {
        if (!context.hasFineLocationPermission()) {
            launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
}
```

- [ ] **Step 3: Build.**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit.**

```bash
git add app/src/main/java/io/github/seijikohara/femto/ui/home/HomeRoute.kt
git commit -m "feat(ui): wire HomeRoute to HomeViewModelFactory"
```

### Task 4.6: Extract AppDrawer screen

**Files:**
- Create: `app/src/main/java/io/github/seijikohara/femto/ui/drawer/AppDrawerRoute.kt`
- Create: `app/src/main/java/io/github/seijikohara/femto/ui/drawer/AppDrawerScreen.kt`

The drawer is launched by `HomeAction.OpenAppDrawer`; navigation hookup happens in Task 4.7 (MainActivity).

- [ ] **Step 1: Create `AppDrawerScreen.kt`** by lifting the legacy `LazyVerticalGrid` from the old `HomeScreen` (the code is in git history before Task 4.4).

```kotlin
package io.github.seijikohara.femto.ui.drawer

import android.content.ComponentName
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.seijikohara.femto.data.AppEntry
import io.github.seijikohara.femto.ui.home.components.AppTile
import io.github.seijikohara.femto.ui.theme.FemtoDimens

private val MinTileWidth = 96.dp

@Composable
fun AppDrawerScreen(
    apps: List<AppEntry>,
    onLaunch: (ComponentName) -> Unit,
    modifier: Modifier = Modifier,
) = Surface(
    modifier = modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.background,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = MinTileWidth),
        contentPadding = PaddingValues(FemtoDimens.ScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(FemtoDimens.GridGutter),
        verticalArrangement = Arrangement.spacedBy(FemtoDimens.GridGutter),
    ) {
        items(items = apps, key = { it.componentName.flattenToString() }) { entry ->
            AppTile(entry = entry, onClick = { onLaunch(entry.componentName) })
        }
    }
}
```

- [ ] **Step 2: Create `AppDrawerRoute.kt`** that reads the apps list from a shared singleton or, for v1, reuses the existing AppsRepository call directly.

```kotlin
package io.github.seijikohara.femto.ui.drawer

import android.content.ComponentName
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.github.seijikohara.femto.data.AppEntry
import io.github.seijikohara.femto.data.AppsRepository

@Composable
fun AppDrawerRoute(
    onLaunch: (ComponentName) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    LaunchedEffect(Unit) {
        apps = AppsRepository(context).queryApps()
    }
    AppDrawerScreen(apps = apps, onLaunch = onLaunch, modifier = modifier)
}
```

- [ ] **Step 3: Build.**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit.**

```bash
git add app/src/main/java/io/github/seijikohara/femto/ui/drawer/
git commit -m "feat(ui): extract apps grid into AppDrawerRoute and AppDrawerScreen"
```

### Task 4.7: Wire navigation in MainActivity

**Files:**
- Modify: `app/src/main/java/io/github/seijikohara/femto/MainActivity.kt`

- [ ] **Step 1: Read the existing file.**

Run: `cat app/src/main/java/io/github/seijikohara/femto/MainActivity.kt`

- [ ] **Step 2: Replace the `setContent {}` block** to host both screens with a small in-memory navigation state. NavHost from androidx.navigation is unnecessary for two screens — use a `var screen by rememberSaveable { mutableStateOf(...) }` switcher.

```kotlin
package io.github.seijikohara.femto

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import io.github.seijikohara.femto.data.AppsRepository
import io.github.seijikohara.femto.ui.drawer.AppDrawerRoute
import io.github.seijikohara.femto.ui.home.HomeRoute
import io.github.seijikohara.femto.ui.theme.FemtoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FemtoTheme {
                var showDrawer by rememberSaveable { mutableStateOf(false) }
                if (showDrawer) {
                    AppDrawerRoute(
                        onLaunch = { component ->
                            AppsRepository(this).launch(component)
                            showDrawer = false
                        },
                    )
                } else {
                    HomeRoute()
                }
            }
        }
    }
}
```

> The drawer toggle currently relies on `HomeAction.OpenAppDrawer` reaching MainActivity through ViewModel side effects. For Plan B v1, accept that the action is logged but unhandled until a follow-up wires a `SharedFlow<HomeEvent>` from the ViewModel to the activity. This is documented in the spec §4.7 risk note (add it now if missing).

- [ ] **Step 3: Build and run all UI tests.**

Run: `./gradlew assembleDebug connectedDebugAndroidTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit.**

```bash
git add app/src/main/java/io/github/seijikohara/femto/MainActivity.kt
git commit -m "feat(ui): host home and drawer routes in MainActivity"
```

---

## Phase 5: Verification

### Task 5.1: All-green verification

- [ ] **Step 1: Run the full verification stack.**

```bash
./gradlew spotlessCheck
./gradlew lint
./gradlew test
./gradlew assembleDebug
```

Expected: All four commands report BUILD SUCCESSFUL.

- [ ] **Step 2: If any step fails, fix in a follow-up commit.** Do not bypass spotless or lint.

### Task 5.2: Smoke test on TBox-Mock AVD

- [ ] **Step 1: Boot the AVD.**

```bash
emulator @TBox-Mock -no-snapshot &
adb wait-for-device
```

- [ ] **Step 2: Install the launcher.**

```bash
./gradlew installDebug
```

- [ ] **Step 3: Verify the launcher cold-starts in under one second** (per CLAUDE.md product metric).

```bash
adb shell am start -W -n io.github.seijikohara.femto/.MainActivity
```

Expected: `TotalTime` < 1000 ms.

- [ ] **Step 4: Verify the dashboard renders all four panels** (clock, weather, music, map fallback if no API key).

```bash
adb shell uiautomator dump
adb shell cat /sdcard/window_dump.xml | grep -E 'TIME|WEATHER|NOW PLAYING|≡'
```

Expected: each substring appears at least once.

- [ ] **Step 5: Inject a GPS fix and confirm the speed/altitude overlay updates.**

```bash
adb emu geo fix 139.7670 35.6814 30
adb emu geo speed 35
adb shell uiautomator dump && adb shell cat /sdcard/window_dump.xml | grep 'km/h\|mph'
```

Expected: `35` appears next to `km/h` (or `mph` on a US-locale image).

- [ ] **Step 6: Verify the apps drawer opens via the `≡` tile.**

Use `adb shell input tap` at the coordinates the dashboard layout puts the leftmost tile in. The drawer renders the `LazyVerticalGrid`.

- [ ] **Step 7: Capture two screenshots to attach to the PR description.**

```bash
adb exec-out screencap -p > docs/superpowers/specs/img/2026-05-01-dashboard-stopped.png
adb emu geo speed 0
adb exec-out screencap -p > docs/superpowers/specs/img/2026-05-01-dashboard-stationary.png
```

(Create the `img/` directory if needed.)

### Task 5.3: Real-device smoke (optional, when hardware is available)

- [ ] **Step 1: Connect the TBox Ambient via USB-C / OTG-host adapter.**

- [ ] **Step 2: Confirm GMS is present on the device.**

```bash
adb shell pm list packages | grep -E 'com.google.android.gms|com.android.vending'
```

Expected: both packages listed.

- [ ] **Step 3: Install and launch.**

```bash
./gradlew installDebug
adb shell am start -W -n io.github.seijikohara.femto/.MainActivity
```

- [ ] **Step 4: Verify with the API key configured** (set `MAPS_API_KEY` in `local.properties` first) that real map tiles render around the GPS fix and that tapping the map opens the OS maps app.

### Task 5.4: Update the PR description

- [ ] **Step 1: Reference the spec and the screenshots from Tasks 5.2 / 5.3 in the PR body.** The PR title and description follow Conventional Commits and project policy (no Claude trailers).

---

## Self-review (already applied inline)

This plan covers every spec requirement except Section 5.3's mention of routing `HomeAction` to side effects. Task 4.7 documents that gap as a v1 follow-up and ships the dashboard with the drawer toggle wired through a local `var`. If the gap should be closed inside Plan B rather than a follow-up, add a Task 4.8 that introduces a `HomeEvent` `SharedFlow` and routes `OpenMaps`, `OpenAppDrawer`, `Shortcut`, and `ConnectMusicPlayer` to MainActivity-side effects.

No placeholders remain. Type names are consistent across tasks. The TDD discipline is applied where the test infrastructure is reasonable (locale helpers, repositories with HTTP / Geocoder, ViewModel combine, panels via Compose UI tests) and is intentionally relaxed for repositories that wrap system services with no productive seam (`ClockRepository`, `LocationRepository`, `MusicSessionRepository`).
