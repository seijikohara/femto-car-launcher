# Settings Defaults & Glass Blur Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Change five `DisplaySettings` defaults and add a consolidated, settings-driven glass blur (blur radius + tint scale) for the map overlays.

**Architecture:** A. flip five default literals in the two SSOT spots. B. a new `Modifier.glassEffect` + `GlassConfig` carry blur/tint from `DisplaySettings` through the existing `MapConfig` data path (`MainActivity → HomeRoute → HomeScreen → DashboardScaffold → MapPane → Clock/SpeedOverlay`). The light/dark base-alpha pick and the percent scale collapse into one alpha inside `glassEffect`.

**Tech Stack:** Kotlin, Jetpack Compose, Haze 1.7.2, DataStore, JUnit4 + Robolectric, `createComposeRule` (v2).

Spec: `docs/superpowers/specs/2026-06-10-settings-defaults-and-glass-blur-design.md`.

---

## Task 1: Change five defaults (A)

**Files:**
- Modify: `app/src/main/java/io/github/seijikohara/femto/data/DisplayPreferences.kt`
- Test: `app/src/test/java/io/github/seijikohara/femto/ui/settings/SettingsViewModelTest.kt`

- [ ] **Step 1: Failing test for the new defaults**

Add to `SettingsViewModelTest`:

```kotlin
@Test
fun `defaults reflect the revised values`() =
    runTest(dispatcher) {
        val d = DisplaySettings.Default
        assertEquals(FullscreenSetting.ON, d.fullscreen)
        assertEquals(MapRenderMode.LIVE, d.mapRenderMode)
        assertEquals(true, d.map3dBuildings)
        assertEquals(true, d.mapTerrain)
        assertEquals(false, d.showClockSeconds)
    }
```

(Add the `MapRenderMode` import if missing.)

- [ ] **Step 2: Run — expect FAIL**

Run: `./gradlew :app:testDebugUnitTest --tests "*SettingsViewModelTest*"`
Expected: FAIL (current defaults are OFF/SNAPSHOT/false/false/true).

- [ ] **Step 3: Flip the defaults in both SSOT spots**

In `DisplaySettings.Default`: `fullscreen = FullscreenSetting.ON`, `mapRenderMode = MapRenderMode.LIVE`, `map3dBuildings = true`, `mapTerrain = true`, `showClockSeconds = false`.

In the `settings` read map:
```kotlin
showClockSeconds = prefs[SHOW_CLOCK_SECONDS_KEY] ?: false,
fullscreen = prefs[FULLSCREEN_KEY].toEnumOr(FullscreenSetting.ON),
...
mapRenderMode = prefs[MAP_RENDER_MODE_KEY].toMapRenderModeOr(MapRenderMode.LIVE),
...
map3dBuildings = prefs[MAP_3D_BUILDINGS_KEY] ?: true,
mapTerrain = prefs[MAP_TERRAIN_KEY] ?: true,
```

- [ ] **Step 4: Run — expect PASS**

Run: `./gradlew :app:testDebugUnitTest --tests "*SettingsViewModelTest*"`
Expected: PASS (the existing `ResetToDefaults` test still passes — it compares the whole `Default`).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/io/github/seijikohara/femto/data/DisplayPreferences.kt \
        app/src/test/java/io/github/seijikohara/femto/ui/settings/SettingsViewModelTest.kt
git commit -m "feat(settings): revise five defaults (fullscreen, live map, 3D, terrain, seconds)

Default to fullscreen on, the live map with 3D buildings + terrain, and
the minute clock. Live becomes the default backend so 3D/terrain (live-only)
show out of the box, accepting the WebGL-on-some-devices instability."
```

---

## Task 2: `glassEffect` + `GlassConfig` + `glassTintAlpha` (B, pure parts)

**Files:**
- Create: `app/src/main/java/io/github/seijikohara/femto/ui/home/components/GlassOverlay.kt`
- Test: `app/src/test/java/io/github/seijikohara/femto/ui/home/components/GlassOverlayTest.kt`

- [ ] **Step 1: Failing test for the pure scale fn**

Create `GlassOverlayTest.kt`:

```kotlin
package io.github.seijikohara.femto.ui.home.components

import org.junit.Test
import kotlin.test.assertEquals

class GlassOverlayTest {
    @Test
    fun `glassTintAlpha scales base alpha by percent and clamps`() {
        assertEquals(0.6f, glassTintAlpha(0.6f, 100), 0.0001f)
        assertEquals(0.3f, glassTintAlpha(0.6f, 50), 0.0001f)
        assertEquals(0f, glassTintAlpha(0.6f, 0), 0.0001f)
        assertEquals(1f, glassTintAlpha(0.6f, 300), 0.0001f) // clamped to 1.0
    }
}
```

- [ ] **Step 2: Run — expect FAIL (unresolved)**

Run: `./gradlew :app:testDebugUnitTest --tests "*GlassOverlayTest*"`
Expected: FAIL to compile (`glassTintAlpha` undefined).

- [ ] **Step 3: Create `GlassOverlay.kt`**

```kotlin
package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import io.github.seijikohara.femto.ui.theme.FemtoDimens

// Glass blur strength, threaded from DisplaySettings down to the map overlays
// like MapConfig. Defaults reproduce the pre-settings look (24 dp, 100% tint).
internal data class GlassConfig(
    val blurRadius: Dp = FemtoDimens.GlassBlurRadius,
    val tintScale: Int = 100,
)

// Shared frosted-glass backdrop for the map overlays (clock / speed): blur the
// map captured via [hazeState] and lay the surface tint over it. The light/dark
// base alpha pick and the user's percent scale collapse into one alpha here, so
// SpeedOverlay / ClockOverlay stop repeating the hazeEffect lambda.
@Composable
internal fun Modifier.glassEffect(
    hazeState: HazeState,
    blurRadius: Dp,
    tintScale: Int,
): Modifier {
    val baseAlpha = if (isSystemInDarkTheme()) FemtoDimens.GlassBgAlphaDark else FemtoDimens.GlassBgAlphaLight
    val tintAlpha = glassTintAlpha(baseAlpha, tintScale)
    val surfaceColor = MaterialTheme.colorScheme.surface
    return hazeEffect(state = hazeState) {
        backgroundColor = surfaceColor
        tints = listOf(HazeTint(surfaceColor.copy(alpha = tintAlpha)))
        this.blurRadius = blurRadius
    }
}

// Resolve the effective tint alpha from a base alpha and the user's percent
// scale (100 = unchanged), clamped to a valid alpha. Pure, so it is unit-testable
// without Compose.
internal fun glassTintAlpha(
    baseAlpha: Float,
    tintScale: Int,
): Float = (baseAlpha * tintScale / 100f).coerceIn(0f, 1f)
```

- [ ] **Step 4: Run — expect PASS**

Run: `./gradlew :app:testDebugUnitTest --tests "*GlassOverlayTest*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/io/github/seijikohara/femto/ui/home/components/GlassOverlay.kt \
        app/src/test/java/io/github/seijikohara/femto/ui/home/components/GlassOverlayTest.kt
git commit -m "feat(home): add glassEffect modifier + GlassConfig for the map overlays

One shared hazeEffect wrapper; the light/dark base alpha and a percent tint
scale collapse into one alpha. Pure glassTintAlpha is unit-tested."
```

---

## Task 3: `glassBlurRadius` / `glassTintScale` in settings state (B)

**Files:**
- Modify: `DisplayPreferences.kt`, `SettingsUiState.kt`, `SettingsViewModel.kt`,
  `app/src/test/.../testfixtures/FakeDisplaySettingsStore.kt`
- Test: `SettingsViewModelTest.kt`

- [ ] **Step 1: Failing VM test**

```kotlin
@Test
fun `glass defaults and setters write to the store`() =
    runTest(dispatcher) {
        val vm = viewModel()
        assertEquals(24, vm.uiState.value.glassBlurRadius)
        assertEquals(100, vm.uiState.value.glassTintScale)
        vm.onAction(SettingsAction.SetGlassBlurRadius(12))
        vm.onAction(SettingsAction.SetGlassTintScale(60))
        advanceUntilIdle()
        assertEquals(12, store.settings.first().glassBlurRadius)
        assertEquals(60, store.settings.first().glassTintScale)
    }
```

- [ ] **Step 2: Run — expect FAIL (unresolved)**

Run: `./gradlew :app:testDebugUnitTest --tests "*SettingsViewModelTest*"`
Expected: FAIL to compile.

- [ ] **Step 3: Data layer** — in `DisplayPreferences.kt`

- Constants near `DEFAULT_MAP_MARKER_POS`:
  ```kotlin
  internal const val DEFAULT_GLASS_BLUR_DP = 24
  internal const val DEFAULT_GLASS_TINT_SCALE = 100
  ```
- `DisplaySettings` fields (near the map fields): `val glassBlurRadius: Int`, `val glassTintScale: Int`.
- `Default`: `glassBlurRadius = DEFAULT_GLASS_BLUR_DP`, `glassTintScale = DEFAULT_GLASS_TINT_SCALE`.
- read map: `glassBlurRadius = prefs[GLASS_BLUR_KEY] ?: DEFAULT_GLASS_BLUR_DP`, `glassTintScale = prefs[GLASS_TINT_KEY] ?: DEFAULT_GLASS_TINT_SCALE`.
- interface: `suspend fun setGlassBlurRadius(value: Int)`, `suspend fun setGlassTintScale(value: Int)`.
- impl:
  ```kotlin
  override suspend fun setGlassBlurRadius(value: Int) { context.displayDataStore.edit { it[GLASS_BLUR_KEY] = value } }
  override suspend fun setGlassTintScale(value: Int) { context.displayDataStore.edit { it[GLASS_TINT_KEY] = value } }
  ```
- keys: `val GLASS_BLUR_KEY = intPreferencesKey("glass_blur_radius")`, `val GLASS_TINT_KEY = intPreferencesKey("glass_tint_scale")`.

- [ ] **Step 4: State + VM + fake**

- `SettingsUiState`: add `glassBlurRadius: Int`, `glassTintScale: Int`; `Initial` seeds from `DisplaySettings.Default`. Add actions `SetGlassBlurRadius(Int)`, `SetGlassTintScale(Int)`.
- `SettingsViewModel`: map both in `combine`; handle both actions (`displayPreferences.setGlassBlurRadius(action.value)` etc.).
- `FakeDisplaySettingsStore`: `override suspend fun setGlassBlurRadius(value: Int) = state.update { it.copy(glassBlurRadius = value) }` and the tint twin.

- [ ] **Step 5: Run — expect PASS**

Run: `./gradlew :app:testDebugUnitTest --tests "*SettingsViewModelTest*"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/io/github/seijikohara/femto/data/DisplayPreferences.kt \
        app/src/main/java/io/github/seijikohara/femto/ui/settings/SettingsUiState.kt \
        app/src/main/java/io/github/seijikohara/femto/ui/settings/SettingsViewModel.kt \
        app/src/test/java/io/github/seijikohara/femto/testfixtures/FakeDisplaySettingsStore.kt \
        app/src/test/java/io/github/seijikohara/femto/ui/settings/SettingsViewModelTest.kt
git commit -m "feat(settings): persist glass blur radius + tint scale"
```

---

## Task 4: Use `glassEffect` + thread `GlassConfig` through the tree (B)

**Files:** `SpeedOverlay.kt`, `ClockOverlay.kt`, `DashboardScaffold.kt`, `HomeScreen.kt`, `HomeRoute.kt`, `MainActivity.kt`

- [ ] **Step 1: Overlays use `glassEffect`**

In `SpeedOverlay`: add param `glassConfig: GlassConfig = GlassConfig()`; delete the `glassAlpha` (line ~125) and `surfaceColor` (line ~128) locals; replace the `.hazeEffect(state = hazeState) { … }` block with:
```kotlin
.glassEffect(hazeState, glassConfig.blurRadius, glassConfig.tintScale)
```
Remove now-unused imports (`HazeTint`, `hazeEffect`, `isSystemInDarkTheme`, `FemtoDimens.GlassBgAlpha*` usages). Keep `HazeState`, `rememberHazeState`. Same edit in `ClockOverlay`.

- [ ] **Step 2: Thread through the layout**

- `MapPane` (in `DashboardScaffold.kt`): add `glassConfig: GlassConfig`, pass to `ClockOverlay`/`SpeedOverlay`.
- `DashboardScaffold`: add `glassConfig: GlassConfig` param (no default — matches `mapConfig`), pass to both `MapPane` call sites; add `glassConfig = GlassConfig()` to the 5 previews.
- `HomeScreen` + `HomeRoute`: add `glassConfig: GlassConfig` param, pass down; add `glassConfig = GlassConfig()` to `HomeScreenPreview`.

- [ ] **Step 3: Build `GlassConfig` in `MainActivity`**

Add `import androidx.compose.ui.unit.dp` and `import io.github.seijikohara.femto.ui.home.components.GlassConfig`. In the `HomeRoute(...)` call, add:
```kotlin
glassConfig = GlassConfig(blurRadius = display.glassBlurRadius.dp, tintScale = display.glassTintScale),
```

- [ ] **Step 4: Build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/io/github/seijikohara/femto/ui/home/ app/src/main/java/io/github/seijikohara/femto/MainActivity.kt
git commit -m "feat(home): drive overlay glass blur from settings via GlassConfig"
```

---

## Task 5: Settings UI — Glass sub-group + two sliders (B)

**Files:** `SettingsScreen.kt`, `res/values/strings.xml`, `SettingsScreenTest.kt`

- [ ] **Step 1: Strings**

Add near the Display strings:
```xml
<string name="settings_subheader_glass">Glass</string>
<string name="settings_group_glass_blur">Blur radius</string>
<string name="settings_group_glass_opacity">Glass opacity</string>
<string name="settings_glass_blur_value">%1$d dp</string>
<string name="settings_glass_opacity_value">%1$d%%</string>
```

- [ ] **Step 2: Sliders in the Display section**

After the `Screen` group (the `Keep screen on` row), before the `Fonts` sub-header, add:
```kotlin
SettingsSubheader(stringResource(R.string.settings_subheader_glass))
SliderRow(
    title = stringResource(R.string.settings_group_glass_blur),
    valueLabel = stringResource(R.string.settings_glass_blur_value, uiState.glassBlurRadius),
    value = uiState.glassBlurRadius,
    range = MIN_GLASS_BLUR..MAX_GLASS_BLUR,
    onValueChange = { onAction(SettingsAction.SetGlassBlurRadius(it)) },
)
SliderRow(
    title = stringResource(R.string.settings_group_glass_opacity),
    valueLabel = stringResource(R.string.settings_glass_opacity_value, uiState.glassTintScale),
    value = uiState.glassTintScale,
    range = MIN_GLASS_OPACITY..MAX_GLASS_OPACITY,
    onValueChange = { onAction(SettingsAction.SetGlassTintScale(it)) },
)
```
Add consts near `MIN_MAP_QUALITY`:
```kotlin
private const val MIN_GLASS_BLUR = 0
private const val MAX_GLASS_BLUR = 40
private const val MIN_GLASS_OPACITY = 0
private const val MAX_GLASS_OPACITY = 150
```

- [ ] **Step 3: UI test**

In `SettingsScreenTest`, add a label `private val glassBlurLabel = context.getString(R.string.settings_group_glass_blur)` and:
```kotlin
@Test
fun glass_blur_row_is_shown() {
    setScreen()
    rule.onNodeWithText(glassBlurLabel).performScrollTo().assertIsDisplayed()
}
```

- [ ] **Step 4: Build + (the UI test runs in Task 6 on the emulator)**

Run: `./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/io/github/seijikohara/femto/ui/settings/SettingsScreen.kt \
        app/src/main/res/values/strings.xml \
        app/src/androidTest/java/io/github/seijikohara/femto/ui/settings/SettingsScreenTest.kt
git commit -m "feat(settings): add Glass sub-group with blur + opacity sliders"
```

---

## Task 6: Verify, emulator, PR, merge

- [ ] **Step 1:** `./gradlew spotlessApply` then review the diff.
- [ ] **Step 2:** `./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest spotlessCheck` — all green, zero warnings.
- [ ] **Step 3:** Boot the `TBox-Mock` AVD (see memory `emulator-tbox-mock-verification`), `:app:installDebug`, pre-grant permissions.
- [ ] **Step 4:** `./gradlew :app:connectedDebugAndroidTest` — all pass.
- [ ] **Step 5:** Manual on device: Settings → Display shows the `Glass` group with two sliders; dragging Blur and Glass opacity changes the clock/speed overlays; the new defaults take effect (live map + 3D/terrain, fullscreen, minute clock). Screenshot.
- [ ] **Step 6:** `git push -u origin feat/settings-defaults-and-glass-blur`; `gh pr create --base main --fill`.
- [ ] **Step 7:** Merge once CI is green and the manual check is clean.

---

## Self-review notes
- **Spec coverage:** A defaults (Task 1), glassEffect consolidation (Task 2 + 4), blur/tint persistence (Task 3), data flow (Task 4), UI sliders (Task 5), tests (Tasks 1–3, 5–6) — all mapped.
- **Type consistency:** `glassBlurRadius: Int` (dp) and `glassTintScale: Int` (%) used identically across `DisplaySettings`, store, `SettingsUiState`, actions, VM, fake, and the screen. `GlassConfig(blurRadius: Dp, tintScale: Int)` — `MainActivity` converts `Int` dp → `Dp` via `.dp`. `glassEffect(hazeState, blurRadius: Dp, tintScale: Int)` matches its call sites.
- **Open items:** Glass strings wording; slider integer stepping (reuses `SliderRow`).
