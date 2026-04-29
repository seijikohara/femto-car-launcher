---
name: format
description: Manual entry point that runs Spotless (`./gradlew spotlessApply`) and reports which files changed. Invoke as /format. For check-only mode, run `./gradlew spotlessCheck` directly via the build skill.
disable-model-invocation: true
allowed-tools:
  - Bash
---

# Format

Run Spotless across the repository and surface what it changed.

```!
./gradlew spotlessApply
```

```!
git status --short
```

Report:

- Whether the run succeeded.
- The list of files Spotless modified (from `git status --short`).
- Any files that Spotless rejected outright (lint errors that block
  auto-fix). Cite `file:line` for each.

If Spotless fails on a lint error, do not silently suppress it.
Surface the error to the user; the fix usually belongs in source,
not in `.editorconfig`.
