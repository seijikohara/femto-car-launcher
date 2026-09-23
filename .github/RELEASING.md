# Releasing

Femto Car Launcher publishes two channels, both built and published by
[`ci.yml`](workflows/ci.yml). Nobody runs a release command by hand: CI
computes every version, creates every tag, and signs every APK it
publishes.

## Channels

| Channel | Publishes on | What ships | Application id |
| --- | --- | --- | --- |
| `nightly` | every push to `develop` | a rolling `nightly` prerelease (replaces the previous one), asset `femto-car-launcher-nightly.apk` | `io.github.seijikohara.femto.nightly` |
| `stable` | every push to `main` | a new dated GitHub release, asset `femto-car-launcher-v<version>.apk` | `io.github.seijikohara.femto` |

Both are release-signed APKs, not an Android App Bundle: sideloading
onto AI boxes and head units off the GitHub release is the only
distribution channel, and an app bundle defers APK generation and
signing to Google Play, which no on-device installer can open. The two
application ids mean both channels install at once — see
[`AGENTS.md`, Git conventions](../AGENTS.md#git-conventions).

## Cutting a stable release

Merge `develop` into `main` when the build is ready — that push is the
entire procedure. The `release` job computes the version, builds
`assembleStableRelease`, tags the commit, and publishes the GitHub
release; nobody pushes a version tag by hand.
`.github/actions/app-version` computes `versionName` and `versionCode`
for both channels from the date — see
[`AGENTS.md`, Git conventions](../AGENTS.md#git-conventions) for the
scheme — and the nightly channel keeps one rolling `nightly` tag
instead of a tag per build.

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
