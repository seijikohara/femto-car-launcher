# Settings Defaults & Glass Blur — Design

## Overview

Two related changes, shipped in one PR as separate commits:

- **A. Default value changes** — revise five `DisplaySettings` defaults.
- **B. Glass blur** — consolidate the duplicated Haze glass effect into one
  `Modifier`, and expose its strength (blur radius + tint scale) as two Settings
  sliders.

## A. Default value changes

Five defaults change. Each value has **two SSOT spots that must agree**:
`DisplaySettings.Default` and the read fallback in `DisplayPreferences.settings`
(Reset clears all keys, so the read fallback is what a reset restores).

| Setting | Old | New | `Default` value | Read fallback |
| --- | --- | --- | --- | --- |
| `fullscreen` | OFF | **ON** | `FullscreenSetting.ON` | `.toEnumOr(FullscreenSetting.ON)` |
| `mapRenderMode` | SNAPSHOT | **LIVE** | `MapRenderMode.LIVE` | `.toMapRenderModeOr(MapRenderMode.LIVE)` |
| `map3dBuildings` | false | **true** | `true` | `?: true` |
| `mapTerrain` | false | **true** | `true` | `?: true` |
| `showClockSeconds` | true | **false** | `false` | `?: false` |

Defaulting `mapRenderMode` to LIVE is a deliberate reversal of the prior
"SNAPSHOT is the universal robust floor" decision (it accepts WebGL-on-some-devices
instability) so that 3D buildings + terrain — both LIVE-only — show by default.
The user opted into this trade-off explicitly.

No new keys; only default literals change. Existing persisted values are
untouched.

## B. Glass blur consolidation + settings

### Current state

`SpeedOverlay.kt` and `ClockOverlay.kt` each write the same `hazeEffect` lambda:

```kotlin
.hazeEffect(state = hazeState) {
    backgroundColor = surfaceColor
    tints = listOf(HazeTint(surfaceColor.copy(alpha = glassAlpha)))
    blurRadius = FemtoDimens.GlassBlurRadius   // 24.dp
}
```

`glassAlpha` is `GlassBgAlphaLight` (0.6) or `GlassBgAlphaDark` (0.42). The
values are already constants in `FemtoDimens`; only the lambda is duplicated, and
nothing is configurable.

### Consolidation

New `Modifier.glassEffect(...)` in a small file `ui/home/components/GlassOverlay.kt`:

```kotlin
@Composable
internal fun Modifier.glassEffect(
    hazeState: HazeState,
    blurRadius: Dp,
    tintScale: Int,
): Modifier {
    val baseAlpha = if (isSystemInDarkTheme()) FemtoDimens.GlassBgAlphaDark else FemtoDimens.GlassBgAlphaLight
    val tintAlpha = (baseAlpha * tintScale / 100f).coerceIn(0f, 1f)
    val surfaceColor = MaterialTheme.colorScheme.surface
    return this.hazeEffect(state = hazeState) {
        backgroundColor = surfaceColor
        tints = listOf(HazeTint(surfaceColor.copy(alpha = tintAlpha)))
        this.blurRadius = blurRadius
    }
}
```

`SpeedOverlay` / `ClockOverlay` replace their `hazeEffect { … }` with
`.glassEffect(hazeState, blurRadius, tintScale)`. The light/dark base-alpha
selection moves inside `glassEffect`, so the call sites stop repeating it.

### Settings (two sliders)

`DisplaySettings` adds two fields:

- `glassBlurRadius: Int` (dp) — default **24**, range **0–40**. `0` = blur off
  (also a cheap path for low-end head units).
- `glassTintScale: Int` (%) — default **100**, range **0–150**. Scales the
  light/dark base alphas: `alpha = base × scale/100`. So **100% reproduces the
  current look exactly** (0.6 / 0.42 preserved per context), `0%` = no tint
  (blur only), `150%` = denser.

Defaults live as `DEFAULT_GLASS_BLUR_DP = 24` and `DEFAULT_GLASS_TINT_SCALE = 100`
constants in `DisplayPreferences.kt` (mirroring `DEFAULT_MAP_*`), shared by
`DisplaySettings.Default` and the read fallback so a single source backs both.

### UI

`SettingsScreen` Display section gains a `Glass` sub-header (the existing
`SettingsSubheader`) followed by two `SliderRow`s — Blur radius (0–40 dp) and
Glass opacity (0–150%). Placed after the `Screen` group, before `Fonts`.

### Data flow

`DisplaySettings` → `MainActivity` (collect) → `HomeRoute` → `DashboardScaffold`
→ `SpeedOverlay` / `ClockOverlay`. The two values travel together as a new
`GlassConfig(blurRadius: Dp, tintScale: Int)`, alongside the existing `MapConfig`
/ `PanelVisibility`, so the overlay call sites take one config object.

## Detailed changes by file

| File | Change |
| --- | --- |
| `data/DisplayPreferences.kt` | Change the five default literals (both `Default` and read fallback). Add `glassBlurRadius` / `glassTintScale` fields, `DEFAULT_GLASS_*` constants, keys, read fallback, setters. |
| `ui/settings/SettingsUiState.kt` | Add `glassBlurRadius` / `glassTintScale` to state + `SetGlassBlurRadius` / `SetGlassTintScale` actions. |
| `ui/settings/SettingsViewModel.kt` | Map + handle the two new actions. |
| `ui/settings/SettingsScreen.kt` | Add the `Glass` sub-header + two `SliderRow`s; new `MIN/MAX_GLASS_*` consts. |
| `ui/home/components/GlassOverlay.kt` (new) | `Modifier.glassEffect(...)` + `GlassConfig`. |
| `ui/home/components/SpeedOverlay.kt` | Replace `hazeEffect{}` with `glassEffect(...)`; take `GlassConfig` (or the two values). |
| `ui/home/components/ClockOverlay.kt` | Same. |
| `ui/home/components/DashboardScaffold.kt` | Thread `GlassConfig` down to the overlays. |
| `ui/home/HomeRoute.kt` / `HomeScreen.kt` | Pass `GlassConfig` through. |
| `MainActivity.kt` | Build `GlassConfig` from `display.glassBlurRadius`/`glassTintScale`. |
| `ui/theme/FemtoDimens.kt` | `GlassBlurRadius` becomes the default source (24.dp); `GlassBgAlpha*` stay as the base alphas the scale multiplies. |
| `res/values/strings.xml` | `settings_subheader_glass`, `settings_group_glass_blur`, `settings_group_glass_opacity`, value formats. |

All literals keep their SSOT: dimensions in `FemtoDimens`, the new defaults in
`DEFAULT_GLASS_*`, colors from `MaterialTheme.colorScheme`.

## Testing

Per `CLAUDE.md#testing`:

- **ViewModel (JVM, `FakeDisplaySettingsStore`)**: `glassBlurRadius` default 24 +
  `SetGlassBlurRadius`; `glassTintScale` default 100 + `SetGlassTintScale`. The
  five changed defaults are covered by the existing `ResetToDefaults` test, which
  compares the whole `DisplaySettings.Default` — extend it to also move the new
  fields off their defaults before reset.
- **Compose UI (`createComposeRule` v2 + `FemtoTheme`)**: the two Glass sliders
  render in the Display section.
- **`glassEffect`**: visual / manual on the emulator (Haze output is not
  unit-assertable); confirm overlays still blur and that Blur=0 / Glass=0 behave.

## Non-goals

- Per-overlay blur (one global glass setting drives both overlays).
- Animating blur/opacity changes as the slider moves.
- Separate light/dark tint sliders (the single `tintScale` preserves the
  per-context ratio).

## Open items (resolve in planning)

- Slider granularity — reuse `SliderRow`'s integer stepping (1 dp / 1%).
- Final wording for the Glass strings.
