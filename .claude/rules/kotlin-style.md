---
paths:
  - "app/src/**/*.kt"
---

# Kotlin style

The latest official Kotlin conventions are the authoritative
external SSOT; the bullets below capture project-specific
extensions. Where they differ, the project convention wins.

- Authoritative reference: <https://kotlinlang.org/docs/coding-conventions.html>.
- **Prefer expression chains over statement blocks.** Use
  single-expression function bodies (`fun foo() = bar()`),
  expression `when` and `if` (consume the result rather than
  branching as a statement), and chained calls (`?.`, `?:`,
  `let` / `run` / `also` / `apply`, `runCatching {}.onFailure {}`,
  collection chains) in preference to intermediate `val`
  declarations or imperative blocks. Reach for a block body only
  when the function genuinely runs unrelated side effects in
  sequence (`onCreate`, `init`, lifecycle callbacks). Stable
  Kotlin 2.x features this rule sanctions: guard conditions in
  `when`-with-subject (`branch if condition ->`, Stable 2.2) to
  keep `when` an expression where a nested `if` would force a
  statement; non-local `break` / `continue` (Stable 2.2);
  multi-dollar string interpolation (Stable 2.2).
- When every branch of a `when` is a single expression, omit the
  `{}`: `branch -> doIt()`. ktlint enforces a consistency rule that
  wraps **every** branch of a `when` in `{}` as soon as one branch
  spans multiple lines (e.g. a function call with named arguments
  on separate lines). When that happens, accept the block form
  rather than fighting the formatter — flatten the offending call
  to a single line if the goal is the unwrapped style. The
  `multiline-expression-wrapping` ktlint rule is **disabled** in
  `.editorconfig` so single-line branches stay unwrapped where
  ktlint's consistency rule does not interfere.
- Trailing commas in multi-line argument and parameter lists, in
  `when` branches, and in collection literals.
- `enum.entries` in preference to `.values()` (`values()` is not
  deprecated; `entries` returns one cached `EnumEntries` list
  where `values()` allocates a new array per call).
- `internal` for module-internal API; `private` for class- or
  file-internal API. Public API of `app` is intentionally narrow —
  prefer `internal` until export is needed.
- Default arguments over overloads; named arguments at call sites
  for booleans and numeric flags.
- Scope functions (`let`, `also`, `apply`, `run`, `with`) are used
  for readability, not brevity. If two readers disagree on which
  scope function fits, prefer an explicit local `val`.
- File-level `@OptIn(ExperimentalXxx::class)` for experimental
  APIs. Never module-level opt-in via `freeCompilerArgs`.
- Context parameters are Stable in the pinned Kotlin (the pin
  lives in `gradle/libs.versions.toml`) — adopt deliberately,
  never by default; their sub-features (explicit context
  arguments, context-parameter callable references) remain
  Experimental: do not use.
- Collection literals and other Experimental language features
  stay out entirely — no `-X` flags in `freeCompilerArgs`,
  consistent with the file-level `@OptIn` rule.
- Top-level `private val Foo = ...` for file-private constants.
  `UPPER_SNAKE_CASE` only for `const val` or `companion object`
  public constants.
- Sealed hierarchies for closed Action / Event types; data classes
  for value containers; `data object` for stateless markers.
- Immutability: prefer `val` and read-only collections (`List<T>`,
  `Map<K, V>`) over mutable variants in public API.
- Nullable lookups are named `<noun>OrNull` after the stdlib
  vocabulary (`firstOrNull`, `getOrNull`) — e.g. `cachedFontOrNull`,
  `cachedAddressOrNull`. No Java-flavored `get` prefix, and no
  past-participle names (`cached()`) that read as state predicates.

## Formatting wiring

Spotless is configured at the project root (`build.gradle.kts`)
for `*.gradle.kts` and `*.md` and at `:app`
(`app/build.gradle.kts`) for `*.kt`. ktlint runs with the official
code style plus the `io.nlopez.compose.rules:ktlint` Compose rule
set. The `.editorconfig` at the project root is the configuration
SSOT for both ktlint and Android Studio.
