---
name: lint
description: Manual entry point that runs Android Lint and reports new findings. Invoke as /lint or /lint :app:lintRelease for a specific variant.
disable-model-invocation: true
argument-hint: "[lint-task]"
allowed-tools:
  - Bash
  - Read
---

# Lint

Default task is `lint`. Override with `$ARGUMENTS`.

```!
./gradlew ${ARGUMENTS:-lint}
```

After the run, read the lint XML / HTML output (lives at
`app/build/reports/lint-results-*.{xml,html}`) and summarise:

- New errors (treat as blocking).
- New warnings, grouped by category.
- Any baseline regressions.

Do not auto-suppress findings. Fix the underlying issue or surface
it to the user for a decision.
