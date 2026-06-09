# Settings Bottom Sheet Reorganization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reorganize the settings bottom sheet so dependencies are visible (hide rows that cannot apply, nested under their driver), related rows are grouped under sub-headings, a `Keep screen on` toggle exists, and the two font rows are clarified.

**Architecture:** Pure additive changes. `keepScreenOn` flows through the existing DataStore → `DisplaySettings` → `SettingsUiState` → `SettingsViewModel` chain and is applied as a window flag in `MainActivity` exactly like `Fullscreen`. The sheet layout (`SettingsScreen.kt`) gains a `SettingsSubheader` and wraps dependent rows in `AnimatedVisibility`. No persisted key or enum type for an existing setting changes.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), DataStore, JUnit4 + `runTest` + Turbine, Compose `createComposeRule`.

Spec: `docs/superpowers/specs/2026-06-10-settings-sheet-reorganization-design.md`.

---

## File structure

| File | Responsibility | Change |
| --- | --- | --- |
| `data/DisplayPreferences.kt` | Persistence SSOT for display settings | Add `keepScreenOn` field/default/key/setter |
| `ui/settings/SettingsUiState.kt` | Settings state + actions | Add `keepScreenOn` field + `SetKeepScreenOn` |
| `ui/settings/SettingsViewModel.kt` | UDF binding | Map + handle `keepScreenOn` |
| `ui/settings/SettingsScreen.kt` | Sheet UI | `SettingsSubheader`; regroup Display + Map; disclosure |
| `MainActivity.kt` | Window-flag application | Apply `FLAG_KEEP_SCREEN_ON` |
| `ui/fontpicker/FontPickerScreen.kt` | Picker UI | Sort-order label |
| `res/values/strings.xml` | Strings SSOT | CJK label edit + new strings |
| `…/testfixtures/FakeDisplaySettingsStore.kt` | Test fake of the store | Implement `setKeepScreenOn` |
| `…/ui/settings/SettingsViewModelTest.kt` | VM tests | `keepScreenOn` default/toggle/reset |
| `…/ui/settings/SettingsScreenTest.kt` | UI tests | Scheme disclosure + keep-screen-on row |

---

## Task 1: `keepScreenOn` end-to-end (data → state → view-model)

**Files:**
- Modify: `app/src/main/java/io/github/seijikohara/femto/data/DisplayPreferences.kt`
- Modify: `app/src/main/java/io/github/seijikohara/femto/ui/settings/SettingsUiState.kt`
- Modify: `app/src/main/java/io/github/seijikohara/femto/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/test/java/io/github/seijikohara/femto/testfixtures/FakeDisplaySettingsStore.kt`
- Test: `app/src/test/java/io/github/seijikohara/femto/ui/settings/SettingsViewModelTest.kt`

- [ ] **Step 1: Read the existing VM test + fake to match their style**

Read `SettingsViewModelTest.kt` and `FakeDisplaySettingsStore.kt` fully so the new test and the fake setter copy the established pattern (e.g. how an existing boolean like `showClockSeconds` is tested and faked).

- [ ] **Step 2: Write the failing VM test**

Add to `SettingsViewModelTest.kt`, mirroring the existing `showClockSeconds`-style cases (use the same fake-construction + collection helper the file already uses):

```kotlin
@Test
fun keepScreenOn_defaults_to_true() = runTest {
    val viewModel = SettingsViewModel(FakeDisplaySettingsStore(), FakeFontPreferences())
    assertTrue(viewModel.uiState.value.keepScreenOn)
}

@Test
fun setKeepScreenOn_updates_state() = runTest {
    val viewModel = SettingsViewModel(FakeDisplaySettingsStore(), FakeFontPreferences())
    viewModel.onAction(SettingsAction.SetKeepScreenOn(false))
    runCurrent()
    assertFalse(viewModel.uiState.value.keepScreenOn)
}
```

(Match the file's actual VM-construction and dispatcher pattern; the snippet shows intent, not necessarily the exact constructor call. If the file uses Turbine, use the same `.test {}` collection it already uses.)

- [ ] **Step 3: Run the test — expect a compile failure**

Run: `./gradlew :app:testDebugUnitTest --tests "*SettingsViewModelTest*"`
Expected: FAILS to compile — `keepScreenOn` / `SetKeepScreenOn` unresolved.

- [ ] **Step 4: Add the field to `DisplaySettings` + default + key + setter**

In `DisplayPreferences.kt`:
- Add to `DisplaySettings` (next to `fullscreen`, since both are window flags):
  ```kotlin
  // Whether to keep the screen awake while the launcher is foreground. Defaults
  // to true: the head unit runs on vehicle power, so the dashboard should stay lit.
  val keepScreenOn: Boolean,
  ```
- Add to `DisplaySettings.Default`: `keepScreenOn = true,`
- Add to the `settings` read map: `keepScreenOn = prefs[KEEP_SCREEN_ON_KEY] ?: true,`
- Add the interface method to `DisplaySettingsStore`: `suspend fun setKeepScreenOn(value: Boolean)`
- Add the impl:
  ```kotlin
  override suspend fun setKeepScreenOn(value: Boolean) {
      context.displayDataStore.edit { it[KEEP_SCREEN_ON_KEY] = value }
  }
  ```
- Add the key to the companion: `val KEEP_SCREEN_ON_KEY = booleanPreferencesKey("keep_screen_on")`

`resetToDefaults()` already clears all keys, so the read fallback `?: true` restores the default — no extra reset code.

- [ ] **Step 5: Add the field to `SettingsUiState` + the action**

In `SettingsUiState.kt`:
- Add `val keepScreenOn: Boolean,` to the data class (next to `fullscreen`).
- Add to `Initial`: `keepScreenOn = DisplaySettings.Default.keepScreenOn,`
- Add the action:
  ```kotlin
  data class SetKeepScreenOn(
      val value: Boolean,
  ) : SettingsAction
  ```

- [ ] **Step 6: Wire the view-model**

In `SettingsViewModel.kt`:
- In the `combine` mapping add `keepScreenOn = display.keepScreenOn,` (next to `fullscreen`).
- In `onAction`'s `when`, add:
  ```kotlin
  is SettingsAction.SetKeepScreenOn -> {
      displayPreferences.setKeepScreenOn(action.value)
  }
  ```

- [ ] **Step 7: Implement `setKeepScreenOn` in the fake**

In `FakeDisplaySettingsStore.kt`, mirror the existing boolean setter pattern (the fake holds a `MutableStateFlow<DisplaySettings>`; copy how `setShowClockSeconds` updates it):

```kotlin
override suspend fun setKeepScreenOn(value: Boolean) {
    state.update { it.copy(keepScreenOn = value) }
}
```

(Use the fake's actual backing-field name and update idiom.)

- [ ] **Step 8: Run the test — expect PASS**

Run: `./gradlew :app:testDebugUnitTest --tests "*SettingsViewModelTest*"`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/io/github/seijikohara/femto/data/DisplayPreferences.kt \
        app/src/main/java/io/github/seijikohara/femto/ui/settings/SettingsUiState.kt \
        app/src/main/java/io/github/seijikohara/femto/ui/settings/SettingsViewModel.kt \
        app/src/test/java/io/github/seijikohara/femto/testfixtures/FakeDisplaySettingsStore.kt \
        app/src/test/java/io/github/seijikohara/femto/ui/settings/SettingsViewModelTest.kt
git commit -m "feat(settings): add keep-screen-on setting through the state chain

Persist a keepScreenOn flag (default on) and expose it through
SettingsUiState / SettingsAction so the sheet can toggle it. The window
flag itself is applied in MainActivity in a later commit."
```

---

## Task 2: Apply `FLAG_KEEP_SCREEN_ON` in `MainActivity`

**Files:**
- Modify: `app/src/main/java/io/github/seijikohara/femto/MainActivity.kt`

No automated test — window flags need an instrumented host; verified on the emulator in Task 6.

- [ ] **Step 1: Add the import**

Add `import android.view.WindowManager` to the import block.

- [ ] **Step 2: Drive the flag from the persisted value**

In `setContent`, next to the existing `LaunchedEffect(display.fullscreen) { applyFullscreen(display.fullscreen) }`, add:

```kotlin
LaunchedEffect(display.keepScreenOn) {
    applyKeepScreenOn(display.keepScreenOn)
}
```

- [ ] **Step 3: Add the apply helper**

Next to `applyFullscreen`, add:

```kotlin
// Keep the panel lit while the launcher is foreground. Unlike the system bars
// this survives focus changes, so it needs no onWindowFocusChanged re-apply.
private fun applyKeepScreenOn(enabled: Boolean) {
    if (enabled) {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    } else {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
```

- [ ] **Step 4: Build to verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/io/github/seijikohara/femto/MainActivity.kt
git commit -m "feat(settings): keep the screen awake when keepScreenOn is on

Apply FLAG_KEEP_SCREEN_ON from the persisted setting, mirroring how the
fullscreen choice drives the system bars. No permission is required."
```

---

## Task 3: Strings — CJK label, keep-screen-on, sub-headings, picker sort label

**Files:**
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Edit the CJK label (line 115)**

Change:
```xml
<string name="settings_group_font_cjk">Japanese / CJK fallback</string>
```
to:
```xml
<string name="settings_group_font_cjk">CJK fallback</string>
```

- [ ] **Step 2: Add the new strings**

Add in the `<!-- Settings -->` block (keep keys grouped logically — sub-headers near the section strings, the keep-screen-on row near `settings_group_fullscreen`):

```xml
<string name="settings_keep_screen_on">Keep screen on</string>
<string name="settings_subheader_screen">Screen</string>
<string name="settings_subheader_fonts">Fonts</string>
<string name="settings_subheader_map_rendering">Rendering</string>
<string name="settings_subheader_map_appearance">Appearance</string>
<string name="settings_subheader_map_camera">Camera</string>
```

Add near the `font_picker_*` strings:
```xml
<string name="font_picker_sort_popular">Most popular first</string>
```

- [ ] **Step 3: Verify resources compile**

Run: `./gradlew :app:processDebugResources`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/values/strings.xml
git commit -m "feat(settings): add strings for sub-headings, keep-screen-on, sort label

Also simplify the CJK font row label to \"CJK fallback\" (the slot is
already named CJK, so \"Japanese\" over-specified the multibyte fallback)."
```

---

## Task 4: `SettingsScreen` — sub-headings, regrouping, disclosure

**Files:**
- Modify: `app/src/main/java/io/github/seijikohara/femto/ui/settings/SettingsScreen.kt`
- Test: `app/src/androidTest/java/io/github/seijikohara/femto/ui/settings/SettingsScreenTest.kt`

- [ ] **Step 1: Read the existing UI test to match its style**

Read `SettingsScreenTest.kt` fully (how it builds `SettingsScreen` inside `FemtoTheme`, drives `uiState`, and asserts node existence) so the new assertions match.

- [ ] **Step 2: Add a `SettingsSubheader` composable**

Add near `SettingsSection` (verify the style against `Type.kt`: it must read one notch below the `titleSmall` section heading and must NOT be `labelSmall`):

```kotlin
// A sub-group label inside a section card, separating related rows under a
// section. Sits one notch below the section heading (primary, indented to align
// with row content) so a card can carry two or three labelled clusters.
@Composable
private fun SettingsSubheader(
    title: String,
    modifier: Modifier = Modifier,
) = Text(
    text = title,
    style = MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.primary,
    modifier = modifier.padding(start = 20.dp, top = 14.dp, bottom = 2.dp),
)
```

- [ ] **Step 3: Regroup the Display section**

Replace the Display `SettingsSection` body so the order is Theme, Accent, `[Screen]` Fullscreen + Keep screen on, `[Fonts]` Latin + CJK:

```kotlin
SettingsSection(title = stringResource(R.string.settings_section_display)) {
    ChoiceRow(
        title = stringResource(R.string.settings_group_theme),
        options =
            listOf(
                ThemeMode.SYSTEM to stringResource(R.string.settings_option_auto),
                ThemeMode.LIGHT to stringResource(R.string.settings_theme_light),
                ThemeMode.DARK to stringResource(R.string.settings_theme_dark),
            ),
        selected = uiState.themeMode,
        onSelect = { onAction(SettingsAction.SetThemeMode(it)) },
    )
    AccentRow(
        selected = uiState.accentColor,
        onSelect = { onAction(SettingsAction.SetAccentColor(it)) },
    )
    SettingsSubheader(stringResource(R.string.settings_subheader_screen))
    SwitchRow(
        title = stringResource(R.string.settings_group_fullscreen),
        checked = uiState.fullscreen == FullscreenSetting.ON,
        onCheckedChange = { onAction(SettingsAction.SetFullscreen(it.toFullscreen())) },
    )
    SwitchRow(
        title = stringResource(R.string.settings_keep_screen_on),
        checked = uiState.keepScreenOn,
        onCheckedChange = { onAction(SettingsAction.SetKeepScreenOn(it)) },
    )
    SettingsSubheader(stringResource(R.string.settings_subheader_fonts))
    FontRow(
        title = stringResource(R.string.settings_group_font_latin),
        family = uiState.latinFont,
        onClick = { onOpenFontPicker(FontSlot.LATIN) },
    )
    FontRow(
        title = stringResource(R.string.settings_group_font_cjk),
        family = uiState.cjkFont,
        onClick = { onOpenFontPicker(FontSlot.CJK) },
    )
}
```

- [ ] **Step 4: Regroup the Map section with disclosure**

Replace the Map `SettingsSection` body. Add `import androidx.compose.animation.AnimatedVisibility`. Order: `[Rendering]` rendering + conditional rows, `[Appearance]` style + scheme disclosure, `[Camera]` tilt/zoom/marker:

```kotlin
SettingsSection(title = stringResource(R.string.settings_section_map)) {
    SettingsSubheader(stringResource(R.string.settings_subheader_map_rendering))
    ChoiceRow(
        title = stringResource(R.string.settings_group_map_rendering),
        options =
            listOf(
                MapRenderMode.LIVE to stringResource(R.string.settings_map_mode_live),
                MapRenderMode.SNAPSHOT to stringResource(R.string.settings_map_mode_snapshot),
            ),
        selected = uiState.mapRenderMode,
        onSelect = { onAction(SettingsAction.SetMapRenderMode(it)) },
    )
    // Snapshot exposes bitmap sharpness; the live backend exposes 3D + terrain.
    // Hidden rows keep their stored value; only the layout changes.
    AnimatedVisibility(visible = uiState.mapRenderMode == MapRenderMode.SNAPSHOT) {
        SliderRow(
            title = stringResource(R.string.settings_group_map_quality),
            valueLabel = stringResource(R.string.settings_map_quality_value, uiState.mapRenderPercent),
            value = uiState.mapRenderPercent,
            range = MIN_MAP_QUALITY..MAX_MAP_QUALITY,
            onValueChange = { onAction(SettingsAction.SetMapRenderPercent(it)) },
            description = stringResource(R.string.settings_map_quality_desc),
        )
    }
    AnimatedVisibility(visible = uiState.mapRenderMode == MapRenderMode.LIVE) {
        Column {
            SwitchRow(
                title = stringResource(R.string.settings_group_map_3d),
                checked = uiState.map3dBuildings,
                onCheckedChange = { onAction(SettingsAction.SetMap3dBuildings(it)) },
            )
            SwitchRow(
                title = stringResource(R.string.settings_group_map_terrain),
                checked = uiState.mapTerrain,
                onCheckedChange = { onAction(SettingsAction.SetMapTerrain(it)) },
                summary = stringResource(R.string.settings_map_terrain_desc),
            )
        }
    }
    SettingsSubheader(stringResource(R.string.settings_subheader_map_appearance))
    ChoiceRow(
        title = stringResource(R.string.settings_group_map_style),
        options =
            listOf(
                MapStyleSetting.AUTO to stringResource(R.string.settings_option_auto),
                MapStyleSetting.LIGHT to stringResource(R.string.settings_theme_light),
                MapStyleSetting.DARK to stringResource(R.string.settings_theme_dark),
            ),
        selected = uiState.mapStyle,
        onSelect = { onAction(SettingsAction.SetMapStyle(it)) },
    )
    // Light scheme applies for AUTO + LIGHT; Dark scheme for AUTO + DARK. AUTO
    // can use either (system theme decides), so AUTO shows both.
    AnimatedVisibility(visible = uiState.mapStyle != MapStyleSetting.DARK) {
        ChoiceRow(
            title = stringResource(R.string.settings_group_map_scheme_light),
            options =
                listOf(
                    MapColorScheme.ACCENT to stringResource(R.string.settings_map_scheme_accent),
                    MapColorScheme.POSITRON to stringResource(R.string.settings_map_scheme_positron),
                    MapColorScheme.BRIGHT to stringResource(R.string.settings_map_scheme_bright),
                    MapColorScheme.LIBERTY to stringResource(R.string.settings_map_scheme_liberty),
                ),
            selected = uiState.mapSchemeLight,
            onSelect = { onAction(SettingsAction.SetMapSchemeLight(it)) },
        )
    }
    AnimatedVisibility(visible = uiState.mapStyle != MapStyleSetting.LIGHT) {
        ChoiceRow(
            title = stringResource(R.string.settings_group_map_scheme_dark),
            options =
                listOf(
                    MapColorScheme.ACCENT to stringResource(R.string.settings_map_scheme_accent),
                    MapColorScheme.DARK_MATTER to stringResource(R.string.settings_map_scheme_dark_matter),
                    MapColorScheme.DARK to stringResource(R.string.settings_map_scheme_dark),
                    MapColorScheme.FIORD to stringResource(R.string.settings_map_scheme_fiord),
                ),
            selected = uiState.mapSchemeDark,
            onSelect = { onAction(SettingsAction.SetMapSchemeDark(it)) },
        )
    }
    SettingsSubheader(stringResource(R.string.settings_subheader_map_camera))
    SliderRow(
        title = stringResource(R.string.settings_group_map_tilt),
        valueLabel = stringResource(R.string.settings_map_tilt_value, uiState.mapTiltDeg),
        value = uiState.mapTiltDeg,
        range = MIN_MAP_TILT..MAX_MAP_TILT,
        onValueChange = { onAction(SettingsAction.SetMapTilt(it)) },
    )
    SliderRow(
        title = stringResource(R.string.settings_group_map_zoom),
        valueLabel = stringResource(R.string.settings_map_zoom_value, uiState.mapZoom),
        value = uiState.mapZoom,
        range = MIN_MAP_ZOOM..MAX_MAP_ZOOM,
        onValueChange = { onAction(SettingsAction.SetMapZoom(it)) },
    )
    SliderRow(
        title = stringResource(R.string.settings_group_map_marker_pos),
        valueLabel = stringResource(R.string.settings_map_marker_pos_value, uiState.mapMarkerPos),
        value = uiState.mapMarkerPos,
        range = MIN_MAP_MARKER_POS..MAX_MAP_MARKER_POS,
        onValueChange = { onAction(SettingsAction.SetMapMarkerPos(it)) },
    )
}
```

- [ ] **Step 5: Add UI tests for disclosure + the new row**

In `SettingsScreenTest.kt`, following the file's existing pattern, add tests that:
- with `mapStyle = AUTO`, both `Light scheme` and `Dark scheme` rows exist;
- with `mapStyle = LIGHT`, `Dark scheme` does not exist;
- with `mapStyle = DARK`, `Light scheme` does not exist;
- the `Keep screen on` row exists.

Use `SettingsUiState.Initial.copy(mapStyle = …)` and `composeTestRule.onNodeWithText(…).assert{Exists,DoesNotExist}` matching the file's helpers. Example shape:

```kotlin
@Test
fun lightStyle_hides_darkScheme() {
    composeTestRule.setContent {
        FemtoTheme {
            SettingsScreen(
                uiState = SettingsUiState.Initial.copy(mapStyle = MapStyleSetting.LIGHT),
                onAction = {}, onBack = {},
                onOpenNotificationAccess = {}, onOpenSystemSettings = {}, onOpenFontPicker = {},
            )
        }
    }
    composeTestRule.onNodeWithText("Dark scheme").assertDoesNotExist()
    composeTestRule.onNodeWithText("Light scheme").assertExists()
}
```

- [ ] **Step 6: Run the UI tests**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "*SettingsScreenTest*"` (needs the emulator from Task 6; if not yet booted, run after Step in Task 6 and before the PR).
Expected: PASS. If the emulator is not up yet, defer this run to Task 6 and continue.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/io/github/seijikohara/femto/ui/settings/SettingsScreen.kt \
        app/src/androidTest/java/io/github/seijikohara/femto/ui/settings/SettingsScreenTest.kt
git commit -m "feat(settings): group rows under sub-headings and disclose by dependency

Split Display into Screen/Fonts and Map into Rendering/Appearance/Camera
sub-groups, place each dependent row under its driver, and hide rows that
cannot apply (snapshot vs live; the scheme not selected by Map style),
animating them in and out."
```

---

## Task 5: Font picker sort-order label

**Files:**
- Modify: `app/src/main/java/io/github/seijikohara/femto/ui/fontpicker/FontPickerScreen.kt`

- [ ] **Step 1: Add the label between the search field and the list**

After `SearchField(...)` and before `LazyColumn(...)` in `FontPickerScreen`, add:

```kotlin
Text(
    text = stringResource(R.string.font_picker_sort_popular),
    style = MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(horizontal = 4.dp),
)
```

(Use `labelLarge`, not `labelSmall`, per `CLAUDE.md#automotive-overrides`.)

- [ ] **Step 2: Build to verify**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/io/github/seijikohara/femto/ui/fontpicker/FontPickerScreen.kt
git commit -m "feat(fontpicker): label the catalog's popularity ordering

The picker lists families most-popular-first but never said so, making the
order read as arbitrary. Surface a static 'Most popular first' label."
```

---

## Task 6: Verify, run on emulator, PR, merge

- [ ] **Step 1: Format**

Run: `./gradlew spotlessApply` then review the diff.

- [ ] **Step 2: Full verification** (the `verify-android-build` skill)

Run: `./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest spotlessCheck`
Expected: all green. Fix any failure before proceeding.

- [ ] **Step 3: Boot an emulator and install**

Start an AVD, then `./gradlew :app:installDebug` and launch.

- [ ] **Step 4: Run the instrumented UI tests**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "*SettingsScreenTest*"`
Expected: PASS.

- [ ] **Step 5: Manual check on the emulator**

Open Settings and confirm:
- Display shows `Screen` (Fullscreen + Keep screen on) and `Fonts` (Latin + `CJK fallback`) sub-groups.
- Toggling `Keep screen on` off then on does not crash; left on, the screen does not dim while the launcher is foreground.
- Map: switching `Map style` Auto→Light hides `Dark scheme`; Auto→Dark hides `Light scheme`. Switching `Map rendering` swaps Sharpness ↔ 3D/Terrain, each under its parent.
- Font picker shows `Most popular first` above the list.
Capture a screenshot of the reorganized sheet.

- [ ] **Step 6: Push and open the PR**

```bash
git push -u origin feat/settings-sheet-reorganization
gh pr create --fill --base main
```
PR body summarizes the four changes and notes no new permission.

- [ ] **Step 7: Merge once green**

After CI passes and the manual check is clean, merge the PR.

---

## Self-review notes

- **Spec coverage:** disclosure (Task 4), sub-grouping (Task 4 + 3), keep-screen-on (Tasks 1–3), CJK label (Task 3), picker order (Tasks 3 + 5), testing (Tasks 1, 4, 6) — all mapped.
- **Type consistency:** `keepScreenOn: Boolean` and `SetKeepScreenOn(value: Boolean)` used identically across `DisplaySettings`, `DisplaySettingsStore`, `SettingsUiState`, `SettingsAction`, `SettingsViewModel`, the fake, and the screen. Key `keep_screen_on`. String keys `settings_subheader_*`, `settings_keep_screen_on`, `font_picker_sort_popular` used consistently between Task 3 and Tasks 4–5.
- **Open items:** sub-heading typography (`labelLarge` proposed; confirm against `Type.kt` in Task 4 Step 2) and picker label wording (`Most popular first`).
