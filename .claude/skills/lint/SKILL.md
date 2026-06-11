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

Run `./gradlew $ARGUMENTS` via Bash. If no argument was given, run
`./gradlew lint`.

After the run, read the lint XML / HTML output (lives at
`app/build/reports/lint-results-*.{xml,html}`) and summarise:

- New errors (treat as blocking).
- New warnings, grouped by category.
- Any baseline regressions.

This section is the SSOT for lint-report interpretation;
verify-android-build step 3 cites it.

Never suppress; fix at source (CLAUDE.md#no-suppress) or surface
the finding to the user for a decision.
