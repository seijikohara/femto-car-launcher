---
name: build
description: Manual entry point that runs ./gradlew assembleDebug (or another Gradle task supplied as an argument) and reports the result. Invoke as /build, /build assembleRelease, /build :app:lintDebug.
disable-model-invocation: true
argument-hint: "[gradle-task]"
allowed-tools:
  - Bash
---

# Build

Default task is `assembleDebug`. Override with `$ARGUMENTS`.

```!
./gradlew ${ARGUMENTS:-assembleDebug}
```

Report:

- Whether the build succeeded.
- Any new warnings introduced by the change.
- Where the resulting APK landed (for `assemble*` tasks).

If the build fails, report the **first** error in full and stop.
Do not attempt a fix without the user's approval.
