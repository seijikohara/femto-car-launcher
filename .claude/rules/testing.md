---
paths:
  - "app/src/test/**"
  - "app/src/androidTest/**"
---

# Testing

Testing rules for femto-car-launcher. Authoritative external
reference: <https://developer.android.com/training/testing>.

- JVM unit tests in `app/src/test/...`: JUnit 4 today (room to
  migrate to Kotest later). Async code uses `runTest` from
  `kotlinx-coroutines-test`; dispatcher control varies by need — an
  ambient `Dispatchers.setMain(StandardTestDispatcher())`, an
  injected `TestScope` / `CoroutineScope`, or neither when Turbine
  and the default `runTest` scheduler already serialise the
  assertions.
- Compose UI tests in `app/src/androidTest/...`: use
  `createComposeRule()`. Wrap content in `FemtoTheme { ... }` —
  never an ad-hoc `MaterialTheme`.
- The SSOT for test fixtures and helpers is a single
  `testfixtures/` package per source set
  (`app/src/test/.../testfixtures/`,
  `app/src/androidTest/.../testfixtures/`). Builders / factories
  are the SSOT for test data; the `data class FakeFoo(...)`
  literal in a single test file is a finding the second time it
  appears. Repeat-yourself in test setup is the same kind of debt
  as in production.
- One assertion focus per test; descriptive names (`returns_x_when_y`).
- Parameterised tests for repeated cases.
- ViewModels expose `StateFlow`; tests drive an action then either
  collect with `viewModel.uiState.test { ... }` (Turbine) or call
  `advanceUntilIdle()` and assert directly on the resulting state —
  pick whichever reads more clearly for the assertion at hand.
