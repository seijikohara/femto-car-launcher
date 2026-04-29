---
name: review
description: Manual entry point that runs the compose-launcher-reviewer agent on the current Git diff (or a supplied ref range) and reports findings grouped by severity. Invoke as /review, /review main..HEAD, /review --staged.
disable-model-invocation: true
argument-hint: "[git-ref-range | --staged | --working]"
allowed-tools:
  - Bash
---

# Review

Resolve the change scope:

- No argument → `git diff --stat HEAD` plus `git diff HEAD` (working tree
  + staged).
- `--staged` → `git diff --cached`.
- `--working` → `git diff` (unstaged only).
- Anything else → `git diff $ARGUMENTS` (e.g. `main..HEAD`,
  `HEAD~3..HEAD`).

```!
git status --short
```

Then dispatch the **compose-launcher-reviewer** subagent with:

- The list of changed files.
- The diff content. Truncate sensibly when the diff exceeds the
  agent's context budget.
- Explicit instruction to read `CLAUDE.md` and the project memory
  before reviewing.

Relay the agent's report verbatim, preserving the
**Blocking / Suggestion / Praise** grouping. Do not summarise away
findings. If the agent reports "Looks good, no findings", say so
directly.
