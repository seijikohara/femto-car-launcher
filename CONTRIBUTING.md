# Contributing to Femto Car Launcher

Femto Car Launcher is a hobby project with a single maintainer. The
maintainer sets the roadmap and decides what ships. Bug reports and
proposals are welcome, and so are pull requests that follow this guide.

This guide covers how to report, propose, and submit. The coding rules
live in [`AGENTS.md`](AGENTS.md) and the path-scoped rule files under
`.claude/rules/`; this guide cites them and does not restate them.

## Report a bug

1. Install the latest `nightly` build (see
   [Installation](README.md#installation)) and confirm that the bug
   still occurs. Every merge to `develop` ships as a new nightly, so
   an older build may already be fixed.
2. Search the [open and closed issues](https://github.com/seijikohara/femto-car-launcher/issues?q=is%3Aissue)
   for the same symptom.
3. Open a [bug report](https://github.com/seijikohara/femto-car-launcher/issues/new?template=bug_report.yml).
   The form asks for the build identifier, the device class, and the
   in-app diagnostics report.

The diagnostics report is the fastest way to give the maintainer the
facts a fix needs. In the app, open Settings → System → Diagnostics and
tap **Copy report**. The report lists device and runtime facts, the
build identifier (its version name), the WebView version, and
recent warnings. It masks API keys, but review it before you post it.

When a fix merges, the maintainer posts a closing comment on the issue
naming the first nightly that carries the fix.

Do not report security vulnerabilities in a public issue. Follow
[`SECURITY.md`](SECURITY.md) instead.

## Propose a feature

Open a [feature request](https://github.com/seijikohara/femto-car-launcher/issues/new?template=feature_request.yml)
before you write code. Describe the problem, the device class where
you meet it, and the change you have in mind. The maintainer replies
with one of three outcomes: accepted (with a plan in the issue),
declined (with the reason), or deferred.

Do not open a pull request for a new feature or a design change
without an accepted issue. A pull request for a bug fix, a typo, a
documentation correction, or a dependency bump needs no issue.

## Submit a change

### Set up

The build prerequisites and commands are in
[Building from source](README.md#building-from-source). The Gradle
build provisions the Node.js toolchain for the bundled map page on its
own.

### Follow the project rules

Read [`AGENTS.md`](AGENTS.md) before you edit anything. Its
[path-scoped rules index](AGENTS.md#path-scoped-rules-index) names the
rule file to read for each part of the tree. The rules that most often
surprise a first-time contributor:

- The automotive floors for tap targets and body text
  ([`AGENTS.md#automotive-overrides`](AGENTS.md#automotive-overrides)).
- The permission procedure for any `<uses-permission>` change
  ([`.claude/rules/permissions.md`](.claude/rules/permissions.md)).
- The suppression policy: fix warnings at the source, never
  `@Suppress` or baseline them
  ([`AGENTS.md#no-suppress`](AGENTS.md#no-suppress)).
- One home per fact: cite the single source of truth (SSOT) instead of
  restating it ([`AGENTS.md#ssot-dry`](AGENTS.md#ssot-dry)).

### Verify

Run the verification pipeline in
[`.claude/skills/verify-android-build/SKILL.md`](.claude/skills/verify-android-build/SKILL.md)
before you open a pull request. `./gradlew spotlessApply` fixes
formatting in place. Continuous integration (CI) runs the same tasks
plus the screenshot tests (`verifyRoborazziStableDebug`, whose goldens are
recorded on the CI runner) and reports them as the single `Validate`
status check, which every merge requires.

### Open a pull request

- Branch from `develop`.
- Write commit messages and the pull request title in
  [Conventional Commits](https://www.conventionalcommits.org/) form.
  The pull request title becomes the subject of the squashed commit
  ([`AGENTS.md`, Git conventions](AGENTS.md#git-conventions)).
- Write code comments, commit messages, and pull request text in
  English ([`AGENTS.md#code-style`](AGENTS.md#code-style)).
- Do not add AI-attribution trailers or footers to commits or pull
  requests ([`AGENTS.md#code-style`](AGENTS.md#code-style)).
- Link the issue the change resolves.
- Fill in the pull request template. Its checklist mirrors this
  section.

The maintainer squashes the pull request into `develop`, and later
promotes `develop` to `main` as a single squash per release, which is
why `main` stays linear. Force-push is denied; update a stale branch
with `gh pr update-branch`.

## License of contributions

Femto Car Launcher is licensed under the Apache License, Version 2.0
([`LICENSE`](LICENSE)). By submitting a contribution, you agree that
the project may distribute it under that license, as Section 5 of the
license states. The project has no separate contributor license
agreement.

## Code of conduct

[`CODE_OF_CONDUCT.md`](CODE_OF_CONDUCT.md) applies to every project
space, including issues and pull requests.
