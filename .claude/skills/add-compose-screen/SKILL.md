---
name: add-compose-screen
description: Scaffold a new Jetpack Compose screen for femto-car-launcher from the canonical template. Rules for theming, sizing, and previews live in CLAUDE.md and .claude/rules/design-system.md.
when_to_use: Adding a new Compose screen; "add a screen for X", "create a Settings screen", "new HomeScreen variant".
argument-hint: "[ScreenName] [package-area]"
allowed-tools:
  - Read
  - Write
  - Edit
  - Glob
  - Grep
  - Bash
  - Skill
paths:
  - app/src/main/java/io/github/seijikohara/femto/ui/**/*.kt
---

# Adding a Compose screen

Rules: see `.claude/rules/design-system.md` and
`CLAUDE.md#automotive-overrides`. This skill is the **procedure
SSOT**; the rules SSOT is `CLAUDE.md` and `.claude/rules/`. Do not
restate rules here.

The screen scaffold SSOT is
[references/screen-template.md](references/screen-template.md) —
read it at `${CLAUDE_SKILL_DIR}/references/screen-template.md`.
Copy from it; do not invent a different shape.

When invoked manually as `/add-compose-screen <ScreenName> <area>`,
treat `$0` as the screen name (e.g. `Settings`) and `$1` as the
package area under `ui/` (e.g. `settings`). Without arguments,
prompt the caller for both values.

## Procedure

1. **Pick the package.** `app/src/main/java/io/github/seijikohara/femto/ui/<area>/`.
   One Composable per file; file name matches the Composable name.

2. **Copy the template** from
   `${CLAUDE_SKILL_DIR}/references/screen-template.md`
   ([references/screen-template.md](references/screen-template.md)).
   Replace the `<Name>` and `<area>` placeholders.

3. **Replace the TODO** with real content. Use only:
   - `MaterialTheme.colorScheme.*`
   - `MaterialTheme.typography.*`
   - `FemtoDimens.*`
   - shapes from M3 default `Shapes`
   - the named `Typography` extensions in `ui/theme/Type.kt`
     (`bigNumber`, `sectionLabel`, `cardTitle`, `cardMeta`,
     `cardCta`, `cardCtaHint`)
   - `FemtoDimens.GlanceTextSize` where
     `CLAUDE.md#automotive-overrides` sanctions the glance relaxation
   See `.claude/rules/design-system.md` for the rationale.
   `.claude/rules/design-system.md` and
   `CLAUDE.md#automotive-overrides` are the SSOT for this list; if
   they and this list ever disagree, `CLAUDE.md` and
   `.claude/rules/` win and this list needs updating.

4. **Hoist state.** State lives in a `ViewModel` or hoisted
   parameters. The Composable takes plain values plus callbacks.
   Use `rememberSaveable` for state that must survive
   configuration changes. When the screen needs durable state,
   promote it via the
   [`add-viewmodel`](../add-viewmodel/SKILL.md) skill — that is
   the procedure SSOT for the UDF shape.

5. **User-visible text** goes through `stringResource(R.string.xxx)`.
   English source in `values/strings.xml`; per-locale variants live
   under `values-<locale>/strings.xml` (e.g. `values-ja`, `values-zh`,
   `values-ko`, `values-de`) once each locale is wired up.

6. **Verify** with the
   [`verify-android-build`](../verify-android-build/SKILL.md) skill.

## Skill-specific anti-patterns

- Hand-writing two `@Preview(...)` annotations instead of using
  `@PreviewLightDark` from `ui/theme/PreviewLightDark.kt`.
- Wrapping the screen with `MaterialTheme(...)` directly. Production
  callers wrap once at the `MainActivity` level via `FemtoTheme`;
  the only place `FemtoTheme { ... }` appears in a screen file is
  inside the preview.
- Putting full-screen surfaces under `ui/<area>/components/` —
  components are reusable widgets; full screens live one level up.
