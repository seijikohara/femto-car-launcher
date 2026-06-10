# App Drawer Revamp — Design

Date: 2026-06-10
Status: Approved scope — pinned dock, icon size presets, search polish

## Goal

Improve the app drawer bottom sheet so that pinned apps are always one
tap away, icon size adapts to user preference, and search reaches the
target app with minimal input. Scope decisions follow a competitive
study of comparable car launchers and general-purpose Android
launchers (see Research notes below).

## Scope

In scope:

1. Pinned dock fixed at the bottom of the drawer sheet.
2. Icon size presets (Small / Medium / Large) configurable in
   Settings.
3. Search polish: prefix-priority result ordering and automatic
   list-layout while a query is active.

Out of scope (deferred to future work):

- "Recent apps" section (requires the `PACKAGE_USAGE_STATS` special
  permission and a grant-flow design).
- Drag-and-drop reordering of pinned apps.
- Customisable footer quick-access shortcuts (touches the home screen
  surface, separate feature).

## Design

### 1. Pinned dock

Restructure `AppDrawerScreen` content as a `Column`:

```
ModalBottomSheet (height fraction 0.72, unchanged)
└─ Column
   ├─ Search field + toolbar (layout toggle)   — unchanged
   ├─ App grid or list                          — weight(1f), scrolls
   └─ PinnedDock                                — fixed row
```

- `PinnedDock` is a `LazyRow`. Each tile shows the app icon plus a
  one-line label (ellipsised) at `FemtoDimens.MinBodyTextSize`,
  matching `AppTile` typography. Tile touch target stays at or above
  `FemtoDimens.MinTouchTarget` (64 dp); tile width is approximately
  96 dp so the label remains readable.
- The dock sits on `surfaceContainerHigh` with a `HorizontalDivider`
  above it to visually separate the fixed region from the scrolling
  grid.
- When no apps are pinned, the dock is not composed (no empty fixed
  row).
- The current "Pinned" section at the top of the grid/list is
  removed. The grid/list shows **all** apps, including pinned ones,
  so every app stays findable in one place; pinned apps carry a small
  pin badge.
- Long-press menu on grid/list tiles keeps Pin / Unpin. Dock tiles
  gain a long-press Unpin action.

### 2. Pin order persistence

- Pinned apps render in the order they were pinned. The existing
  DataStore value `drawer_pinned: Set<String>` cannot express order,
  so persistence moves to an ordered representation under a new key
  (`drawer_pinned_order: String`, flattened component names joined
  with `\n`, which cannot appear in a flattened `ComponentName`).
- Migration: on first read, if the new key is absent and the legacy
  set is present, seed the order from the legacy set sorted by label
  order (alphabetical as a deterministic fallback), then clear the
  legacy key.

### 3. Icon size presets

- New enum `DrawerIconSize { SMALL, MEDIUM, LARGE }` persisted in
  `DrawerPreferences` (key `drawer_icon_size`), default `MEDIUM`
  (matches current dimensions).

| Preset | Grid tile min width | Grid icon | List icon |
| --- | --- | --- | --- |
| SMALL | 96 dp | 48 dp | 32 dp |
| MEDIUM (current) | 120 dp | 64 dp | 40 dp |
| LARGE | 160 dp | 88 dp | 56 dp |

- Tile width and list row height never drop below
  `FemtoDimens.MinTouchTarget` (64 dp) at any preset.
- Settings gains an "App drawer icon size" row using an M3
  `SegmentedButton` group (three options, applied immediately).
- The dock tile size is independent of the preset (fixed) to keep the
  dock height stable.

### 4. Search polish

- **Prefix-priority ordering**: filter results sort `startsWith`
  matches (case-insensitive) before `contains` matches; ties keep
  label order. Typing "M" surfaces Maps / Music first.
- **Automatic list layout**: while the query is non-blank the drawer
  renders the list layout regardless of the persisted preference,
  because labels are the primary signal when searching. Clearing the
  query restores the persisted layout. The persisted value is never
  written by this behaviour.
- Both behaviours live in pure functions so they are unit-testable
  without Compose.

## Components touched

| File | Change |
| --- | --- |
| `data/DrawerPreferences.kt` | `DrawerIconSize` enum, ordered pin persistence + migration |
| `ui/drawer/AppDrawerScreen.kt` | Column restructure, dock slot, search ordering, auto-list |
| `ui/drawer/components/PinnedDock.kt` (new) | Fixed dock row |
| `ui/home/components/AppTile.kt` | Size parameterisation, pin badge |
| `ui/home/components/AppListRow.kt` | Size parameterisation, pin badge |
| `ui/settings/...` | Icon size segmented row |
| `app/src/test/...` | Preferences round-trip + migration, search ordering, auto-list selection |

## Error handling

- Pinned component names that no longer resolve to an installed app
  are skipped at render time and pruned from the persisted order on
  the next pin/unpin write (same behaviour as the current set-based
  implementation).
- DataStore read failures fall back to defaults (`MEDIUM`, empty pin
  order), matching the existing `DrawerPreferences` posture.

## Testing

- JVM unit tests: ordered pin persistence (round-trip, append order,
  unpin removal, legacy-set migration), `DrawerIconSize` round-trip,
  prefix-priority sort, auto-list layout selection.
- Compose previews: `@PreviewLightDark` for the dock and for each
  icon-size preset.
- Manual verification on the TBox-Mock AVD (800x480 @ 150 dpi) for
  dock reachability and LARGE-preset label wrapping.

## Research notes

Competitive study (2026-06-10, `similar-app-researcher`): the
bottom-sheet drawer with pinned-priority access is already the
strongest pattern for head units versus full-screen drawers
(context loss over the map) and favourites-only models (poor full-app
reachability). Differentiators adopted from the study: three-step icon
size presets (one-tap selection beats continuous sliders for in-car
touch), prefix-priority search, and automatic list layout during
search (no comparable launcher ships either search behaviour).
