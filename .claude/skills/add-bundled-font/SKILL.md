---
name: add-bundled-font
description: Use when bundling a new typeface into femto-car-launcher (e.g. wiring up Outfit, swapping the JP fallback, adding a display face). Triggers on "add Outfit", "bundle Inter", "wire up Zen Kaku Gothic New". Covers download, license placement, FontTheme enum extension, and FontFamily wiring; the rules for fonts live in CLAUDE.md.
argument-hint: "[font-key] [github-owner/repo] [variable-ttf-path]"
allowed-tools:
  - Read
  - Write
  - Edit
  - Bash
  - Glob
paths:
  - app/src/main/res/font/**
  - app/src/main/assets/licenses/**
  - app/src/main/java/io/github/seijikohara/femto/ui/theme/FontTheme.kt
  - app/src/main/java/io/github/seijikohara/femto/ui/theme/Type.kt
---

# Adding a bundled font

Rules: see `CLAUDE.md#fonts`. This skill is the **procedure SSOT**;
the rules SSOT is `CLAUDE.md`. Do not restate rules here.

The download helper SSOT is
[`scripts/download-variable-font.sh`](scripts/download-variable-font.sh).
Use it for the file-fetch step; perform the wiring steps in code
yourself.

## Procedure

1. **Find the upstream variable TTF.** Use the official repo (e.g.
   `vercel/geist-font`, `coz-m/MPLUS_FONTS`, `googlefonts/Outfit`):

   ```bash
   curl -fsSL "https://api.github.com/repos/<owner>/<repo>/git/trees/<branch>?recursive=1" \
     | grep -iE 'variable.*\.ttf|wght.*\.ttf'
   ```

2. **Download** font + license:

   ```bash
   "${CLAUDE_SKILL_DIR}/scripts/download-variable-font.sh" \
       outfit \
       googlefonts/Outfit \
       main \
       'fonts/variable/Outfit%5Bwght%5D.ttf' \
       OFL.txt
   ```

   The script verifies the file is real TTF (`file` command),
   enforces the lowercase-snake-case Android resource filename, and
   places the artefacts under `app/src/main/res/font/` and
   `app/src/main/assets/licenses/`.

3. **Wire the `FontFamily`** in
   `app/src/main/java/io/github/seijikohara/femto/ui/theme/Type.kt`:
   - Add a member to `FemtoFonts` — `mixedFamily(...)` for a Latin
     face that needs JP glyph fallback, or
     `FontFamily(WeightAxis.map { variableFont(R.font.<key>, it) })`
     for a self-sufficient face.

4. **Extend `FontTheme`** in `ui/theme/FontTheme.kt`:
   - Add a new enum entry.
   - Add the matching branch in `fontPairOf(theme)`.
   - The `(latin, jp)` pairing follows the design memory entry at
     `~/.claude/projects/-Users-seiji-git-GitHub-seijikohara-femto-car-launcher/memory/design_direction.md`.

5. **Verify** with the
   [`verify-android-build`](../verify-android-build/SKILL.md) skill.

## Sizing budget

The MVP APK ships ~4.4 MB of fonts. Each additional CJK face adds
~3–6 MB; Latin-only faces are typically 100–300 KB. If a new pair
would push the APK past 25 MB, stop and discuss before bundling.

## Skill-specific anti-patterns

- Filenames with brackets, spaces, or uppercase letters — the
  helper script rejects these, and the constraint applies equally
  when editing by hand.
- Static-weight TTFs when a variable TTF exists upstream.
- Adding a `FontTheme` entry without the matching `fontPairOf`
  branch.
- Bundling a font that the design memory has not approved.
