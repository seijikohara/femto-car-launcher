---
name: review
description: Manual entry point that runs the compose-launcher-reviewer agent on the current Git diff (or a supplied ref range) and reports findings grouped by severity. Invoke as /review, /review main..HEAD, /review --staged.
disable-model-invocation: true
argument-hint: "[git-ref-range | --staged | --working]"
context: fork
agent: compose-launcher-reviewer
---

# Review

Current working-tree state:

```!
git status --short
```

Scope: $ARGUMENTS — resolve per your Scope section.

Report findings preserving the **Blocking / Suggestion / Praise**
grouping. Do not summarise away findings. If there are no findings,
say "Looks good, no findings" directly.
