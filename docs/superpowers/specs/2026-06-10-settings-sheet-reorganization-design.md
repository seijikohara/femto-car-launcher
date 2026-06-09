# Settings Bottom Sheet Reorganization — Design

## Overview

The settings bottom sheet has grown to ~23 rows in five flat sections.
Some rows silently depend on other settings, and the relationship is not
visible. This design reorganizes the sheet around two ideas: **group
related rows under sub-headings** so a setting's location is
predictable, and **hide rows that cannot apply** (disclosure) so the
sheet only ever shows controls that currently matter. It also adds a
`Keep screen on` toggle and clarifies the two font rows.

Scope follows the agreed priority: dependency visibility first, the font
fixes second, overall length reduction as a side effect.

## Problem

1. **Hidden dependencies.** `Map style` (Auto / Light / Dark) silently
   decides whether `Light scheme` or `Dark scheme` is used
   (`WebMapView.kt:62-67`), yet both rows are always shown. `Map
   rendering` (Snapshot / Live) already swaps `Sharpness` for `3D
   buildings` + `Terrain` (`SettingsScreen.kt:256-277`), but the swap
   reads as rows randomly appearing rather than as a parent-child
   relationship.
2. **Length and grouping.** The `Map` section alone holds 7 base rows
   plus the mode-dependent rows — over half the sheet — in one flat
   list, so locating a control means scanning.
3. **Font rows are unclear.** The CJK row label over-specifies:
   `settings_group_font_cjk = "Japanese / CJK fallback"`
   (`strings.xml:115`). The picker lists families in popularity order
   (`GoogleFontsApi.kt:34`, `.sortedBy { it.popularity }`) but never
   labels that order, so it reads as arbitrary.
4. **New requirement.** The launcher should be able to keep the screen
   awake while it is in the foreground.

## Goals

- Make each setting's dependency visible by hiding rows that cannot
  apply, placing every dependent row directly under its driver.
- Group related rows under sub-headings so location is predictable.
- Add a `Keep screen on` toggle (default on).
- Clarify the two font rows: simplify the CJK label and surface the
  picker's sort order.

## Non-goals

- Font picker A–Z / category sorting. This design only **labels** the
  existing popularity order; a sort switch is future work.
- Sub-grouping the `Units`, `Panels`, or `System` sections — they stay
  flat.
- Advanced keep-awake policies (charge-only, timeout, motion).
- Changing persisted enum types or DataStore keys for existing
  settings. Only additive changes.

## Design

### Section structure (after)

Sub-headings (the `[bracketed]` rows) are visual groupings inside a
section card, not new sections.

```
Display
  Theme
  Accent color
  [Screen]
    Fullscreen
    Keep screen on            ← new, default ON
  [Fonts]
    Latin font
    CJK fallback              ← label simplified
Units                          (unchanged, flat)
  Speed & distance
  Temperature
  Clock
  Show seconds
Map
  [Rendering]
    Map rendering             (Snapshot / Live)
      Sharpness               ← shown only when Snapshot
      3D buildings            ← shown only when Live
      Terrain                 ← shown only when Live
  [Appearance]
    Map style                 (Auto / Light / Dark)
      Light scheme            ← shown when Auto or Light
      Dark scheme             ← shown when Auto or Dark
  [Camera]
    Tilt
    Zoom
    Marker position
Panels                         (unchanged, flat)
  Calendar
  Weather
  Music
System                         (unchanged, flat)
  Notification access
  System settings
  Reset to defaults
```

### Dependency disclosure

There are two kinds of dependency. Both hide the inactive rows; both
keep the dependent rows **directly under their driver** and animate them
in and out so the change reads as a consequence of the parent.

| Driver | Dependent rows | Visible when |
| --- | --- | --- |
| `Map rendering` | Sharpness | `Snapshot` |
| `Map rendering` | 3D buildings, Terrain | `Live` |
| `Map style` | Light scheme | `Auto` or `Light` |
| `Map style` | Dark scheme | `Auto` or `Dark` |

- **Conditional rows** (`rendering` → sharpness / 3D / terrain) are
  fully inert in the other mode. This is the existing behaviour
  (`SettingsScreen.kt:256-277`); the change is to nest them visually
  under `Map rendering` and animate the swap.
- **Active-scheme rows** (`style` → schemes) carry a meaningful value in
  every mode, but only the matching scheme is used at a time. `Auto` can
  use either depending on the system theme, so `Auto` shows **both**
  scheme rows; `Light` and `Dark` show only the matching one. This row
  was previously always shown — making it disclosure is the core fix.

Both swaps use `AnimatedVisibility` with a slide so a row entering or
leaving reads as "I just changed the parent," not "a row flickered."

### Keep screen on (new)

- A boolean setting, default **on** (the head unit runs on vehicle
  power, so keeping the screen awake while the launcher is foreground is
  the expected default).
- Applied by setting `FLAG_KEEP_SCREEN_ON` on the Activity window,
  driven from the persisted value through the **same path
  `Fullscreen` already uses** in `MainActivity`. The exact mechanism is
  confirmed during planning.
- **No permission required.** `FLAG_KEEP_SCREEN_ON` needs none;
  `WAKE_LOCK` is not used. The `add-launcher-permission` skill and the
  `CLAUDE.md#permissions` audit table are therefore untouched.
- Lives in `Display ▸ Screen`, next to `Fullscreen`, rendered with the
  existing `SwitchRow`.

Modelled as `Boolean` rather than a `FullscreenSetting`-style enum: the
sheet's other on/off rows (`showClockSeconds`, `map3dBuildings`,
`mapTerrain`, `showCalendar`, …) are already `Boolean`, so `Boolean` is
the majority convention; `FullscreenSetting` is the outlier.

### Font adjustments

1. **CJK label.** Change the value of `settings_group_font_cjk` from
   `"Japanese / CJK fallback"` to `"CJK fallback"`. The key is unchanged,
   so every reference (`SettingsScreen.kt:128`) keeps working. No
   locale-specific override exists today, so only the base string
   changes.
2. **Picker sort order.** Surface a short, non-interactive label in the
   font picker (`ui/fontpicker/`) that names the existing order
   (popularity, most popular first). Behaviour is unchanged; the label
   only removes the "why is it in this order?" confusion. A real sort
   switch (A–Z, by category) is out of scope (see Non-goals).

## Detailed changes by file

| File | Change |
| --- | --- |
| `ui/settings/SettingsScreen.kt` | Add a private `SettingsSubheader` composable. Re-lay `Display` (Screen / Fonts groups, `Keep screen on` row) and `Map` (Rendering / Appearance / Camera groups). Wrap conditional rows and each scheme row in `AnimatedVisibility`; pick scheme visibility with `when (mapStyle)`. |
| `ui/settings/SettingsUiState.kt` | Add `keepScreenOn: Boolean` to `SettingsUiState`; add `SetKeepScreenOn(value: Boolean)` to `SettingsAction`. |
| `ui/settings/SettingsViewModel.kt` | Map `keepScreenOn` from the store into `SettingsUiState`; handle `SetKeepScreenOn`; include it in the `ResetToDefaults` path. |
| `data/DisplayPreferences.kt` | Add `KEEP_SCREEN_ON_KEY` (`booleanPreferencesKey("keep_screen_on")`, default `true`); add `keepScreenOn` to `DisplaySettings`; read/write/reset it. |
| `MainActivity.kt` | Collect `keepScreenOn` and add/clear `FLAG_KEEP_SCREEN_ON` through the same window-flag path as `Fullscreen`. |
| `res/values/strings.xml` | Edit `settings_group_font_cjk`. Add `settings_keep_screen_on` and the sub-heading strings (`settings_subgroup_screen`, `settings_subgroup_fonts`, `settings_group_map_rendering`, `settings_group_map_appearance`, `settings_group_map_camera`). |
| `ui/fontpicker/` (picker screen) | Render the popularity-order label above the list. |

All literals stay in their SSOT: dimensions from `FemtoDimens`, colors
from `MaterialTheme.colorScheme.*`, type from `MaterialTheme.typography`
(`CLAUDE.md#design-system`). Sub-headings reuse the existing section
heading style one notch down rather than introducing a new token;
confirmed against `Type.kt` during implementation.

## Behaviour specifics

- **Scheme visibility:** `Auto` → both scheme rows; `Light` → Light
  scheme only; `Dark` → Dark scheme only. The hidden scheme keeps its
  persisted value; switching back reveals it unchanged.
- **Reset to defaults** sets `keepScreenOn = true` alongside the
  existing resets.
- **Disclosure is presentation only.** Hiding a row never clears its
  stored value; it only removes it from the layout while inactive.

## Testing

Following `CLAUDE.md#testing`:

- **ViewModel (JVM, `app/src/test`):** with `FakeDisplaySettingsStore`
  (the interface fake, never a real DataStore under `runTest`), assert
  `keepScreenOn` default is `true`, `SetKeepScreenOn` flips it, and
  `ResetToDefaults` restores `true`.
- **Compose UI (`app/src/androidTest`, `createComposeRule`, wrapped in
  `FemtoTheme`):** assert that switching `Map style` shows/hides the
  scheme rows (Auto → both, Light → Light only), that switching `Map
  rendering` swaps Sharpness ↔ 3D/Terrain, and that the `Keep screen on`
  row renders in the Screen group.

The window-flag effect itself is left to manual on-device verification;
the unit/UI tests cover state and layout.

## Open items to confirm in review

- Sub-heading typography — the intent is "existing section heading, one
  notch lighter/smaller." Final style is matched to `Type.kt` during
  implementation, not invented.
- Font picker order label wording (e.g. "Popular first" vs "Sorted by
  popularity").
