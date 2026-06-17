# Release signing

The nightly job in [`ci.yml`](workflows/ci.yml) signs the release
artifacts with an upload keystore supplied through repository secrets.
It builds both an **APK** (`assembleRelease`, attached to the GitHub
nightly release for direct sideload onto AI boxes / head units) and an
**App Bundle** (`bundleRelease`, `femto-car-launcher-nightly.aab`) — the
**AAB is the format Google Play requires** for new apps; upload it under
Play Console -> Testing/Production -> Create release. Local
`./gradlew assembleRelease` / `bundleRelease` builds stay unsigned: the
signing config is registered only when `RELEASE_KEYSTORE_PATH` is set, so
contributor builds keep working with no keystore.

## Production release (tag-driven)

Cut a production build by pushing a semver tag, which triggers
[`release.yml`](workflows/release.yml):

```bash
git tag v1.0.0
git push origin v1.0.0
```

The workflow derives the version from the tag — `versionName` is the tag
without the `v` (e.g. `1.0.0`), and `versionCode` is packed as
`major*10000 + minor*100 + patch` (so `v1.0.0` → `10000`, monotonic with
semver; minor and patch must each be `< 100`). It builds the **signed AAB
and APK**, then attaches both to a GitHub release for that tag. Download
the `.aab` and upload it under **Play Console → Testing/Production →
Create release**. (`workflow_dispatch` with a `version` input is the
manual fallback when you'd rather not push a tag.)

The nightly job uses the same signing secrets but stamps a
`nightly-<run>-<sha>` version; only tagged builds carry a clean
production version.

## Signing secrets

A maintainer must add four repository secrets (Settings -> Secrets and
variables -> Actions) before the nightly job can sign:

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

Keep `release.jks` out of version control. If `RELEASE_KEYSTORE_BASE64`
is missing, the nightly job fails with an explicit message instead of
publishing an unsigned APK.
