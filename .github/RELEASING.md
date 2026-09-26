# Releasing

Femto Car Launcher publishes two channels, both built and published by
[`ci.yml`](workflows/ci.yml). Nobody runs a release command by hand: CI
computes every version, creates every tag, and signs every APK it
publishes.

## Channels

| Channel | Publishes on | What ships | Application id |
| --- | --- | --- | --- |
| `nightly` | every push to `develop` that changes the APK | a rolling `nightly` prerelease (replaces the previous one), asset `femto-car-launcher-nightly.apk` | `io.github.seijikohara.femto.nightly` |
| `stable` | every push to `main` that changes the APK | a new dated GitHub release, asset `femto-car-launcher-v<version>.apk` | `io.github.seijikohara.femto` |

Both are release-signed APKs, not an Android App Bundle: sideloading
onto AI boxes and head units off the GitHub release is the only
distribution channel, and an app bundle defers APK generation and
signing to Google Play, which no on-device installer can open. The two
application ids mean both channels install at once — see
[`AGENTS.md`, Git conventions](../AGENTS.md#git-conventions).

## Cutting a stable release

Promote `develop` to `main` when the build is ready, through a pull
request from `develop` into `main`. Before opening that promotion pull
request, record `main`'s latest promotion in `develop`. `main`'s
ruleset requires the promotion to be up to date with `main`, and a
squash promotion never reaches `develop`, so every promotion after the
first starts behind. The record is a merge commit whose tree is
`develop`'s own and whose parents are `develop` and `main`. On a branch
cut from `origin/develop`, run
`git merge -s ours -m "chore: record the v<version> promotion in develop" origin/main`
(for a promotion that published no release, write its pull request,
`#<number>`, in place of `v<version>`), push the branch as
`sync/main-into-develop`, open a pull request into `develop`, and merge
that pull request with a merge commit (`gh pr merge <number> --merge`).
Use a merge commit only: never squash or rebase the sync pull request,
since either drops `main` as a parent. The sync is safe because `main`
only ever receives `develop`'s content, so the merge changes no file
and cannot conflict.
[#430](https://github.com/seijikohara/femto-car-launcher/pull/430) is
the first such sync.

Then open the promotion pull request and squash-merge it. Its push to
`main` is the release: the `release` job computes the version, builds
`assembleStableRelease`, tags the commit, and publishes the GitHub
release; nobody pushes a version tag by hand. When the APK is unchanged
since the latest release, the job does none of that (see
[Unchanged APKs](#unchanged-apks)). Merge one at a time: if a
second merge lands before the first release is out, the newer commit is
released and the older run stands down, since it would otherwise publish
a stale build as the latest release.
`.github/actions/app-version` computes `versionName` and `versionCode`
for both channels from the date — see
[`AGENTS.md`, Git conventions](../AGENTS.md#git-conventions) for the
scheme — and the nightly channel keeps one rolling `nightly` tag
instead of a tag per build.

## Unchanged APKs

A push publishes only an APK that differs from the channel's previous
release: the current `nightly` prerelease, or the latest stable
release. Before publishing, the job rebuilds the pushed commit with the
previous release's `versionCode` and `versionName` and compares that
APK with the published one
([`actions/apk-unchanged`](actions/apk-unchanged/apk-unchanged.sh)).
The two count as unchanged when they hold the same zip entries with the
same bytes, except for the entries that the script's header comment
lists as ignored and explains.

An unchanged push publishes nothing: the `nightly` prerelease stays as
it is, and the `release` job computes no version, creates no tag, and
publishes no release. A push that changes only docs, CI, or tests
therefore ships no new build, while almost any edit to the app's
sources does: the release dex keeps line numbers, so even a comment
that moves lines changes the APK. The commits a skipped push carries
still reach the release notes of the next build that ships, which list
everything since the previous release.

The check errs toward publishing. No previous release, no APK asset, a
failed download or build, or any other failure of the check makes the
job publish as before, with a warning in the run log. A `release` run
of a commit that already carries a `v*` tag skips the check: an
earlier run of that commit decided to publish, tagged it, and failed,
so this run finishes that release instead of stranding the tag.

The comparison is a full build of the pushed commit. On an unchanged
push it is the job's only build; on a changed push the signed build
that follows it is incremental.

## Signing secrets

One upload keystore signs both channels. A maintainer must add four
repository secrets (Settings -> Secrets and variables -> Actions)
before either job can sign:

| Secret | Contents |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | Base64 of the upload keystore (`.jks`) |
| `RELEASE_KEYSTORE_PASSWORD` | Keystore (store) password |
| `RELEASE_KEY_ALIAS` | Key alias inside the keystore |
| `RELEASE_KEY_PASSWORD` | Password for that key |

Generate an upload keystore once:

```bash
keytool -genkeypair -v \
  -keystore release.jks \
  -alias femto-upload \
  -keyalg RSA -keysize 2048 -validity 10000
```

Base64-encode it for the `RELEASE_KEYSTORE_BASE64` secret:

```bash
# macOS
base64 -i release.jks | pbcopy
# Linux
base64 -w0 release.jks
```

Keep `release.jks` out of version control. If a secret is missing, the
nightly or release job fails with an explicit message instead of
publishing an unsigned APK. Local `assembleStableRelease` /
`assembleNightlyRelease` builds stay unsigned: the signing config
registers only when `RELEASE_KEYSTORE_PATH` is set, so a contributor
without the keystore keeps building.
