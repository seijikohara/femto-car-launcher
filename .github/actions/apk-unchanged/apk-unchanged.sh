#!/usr/bin/env bash
# Write unchanged=true to $GITHUB_OUTPUT when HEAD, built with the version of
# the channel's previous release, reproduces that release's APK; otherwise
# write unchanged=false. The composite action beside this script runs it.
#
# "Reproduces" means the same zip entries with the same bytes, ignoring only
#   - META-INF/version-control-info.textproto, where AGP records the commit
#     the APK was built from, so it differs on every push by design;
#   - JAR signature files, since the comparison build is unsigned. CI signs
#     with the v2+ APK signature schemes only (minSdk 33), whose signature is
#     not a zip entry, so these match nothing today; ignoring them keeps a
#     future v1 signer from turning every build into a change.
# The version itself is not ignored but reproduced: versionCode and
# versionName reach AndroidManifest.xml, the dex (BuildConfig and the strings
# that inline it) and, through the dex checksum, the baseline profile.
#
# Environment: CHANNEL (stable or nightly), GITHUB_REPOSITORY, GITHUB_OUTPUT,
# ANDROID_HOME, and GH_TOKEN (or a gh login); RUNNER_TEMP is optional. Run it
# from the repository root, since it builds with ./gradlew.
set -euo pipefail

case "${CHANNEL:-}" in
    stable) task=assembleStableRelease ;;
    nightly) task=assembleNightlyRelease ;;
    *)
        echo "::error::CHANNEL must be stable or nightly (got '${CHANNEL:-}')"
        exit 1
        ;;
esac

# Every exit from here on runs report(), and the verdict stays "changed"
# unless the comparison proves a match. A check that fails, even in a way
# this script does not anticipate, therefore publishes exactly as the job did
# before the check existed, and the step itself never fails.
unchanged=false
explained=false
work=""
report() {
    if [ "$explained" != true ]; then
        echo "::warning::The unchanged-APK check stopped unexpectedly; publishing as a changed build"
    fi
    echo "unchanged=$unchanged" >> "$GITHUB_OUTPUT"
    [ -z "$work" ] || rm -rf "$work"
    exit 0
}
trap report EXIT

# Stop checking and publish as a changed build, saying why.
give_up() {
    echo "::warning::$1; publishing as a changed build"
    explained=true
    exit 0
}

work="$(mktemp -d "${RUNNER_TEMP:-${TMPDIR:-/tmp}}/apk-unchanged.XXXXXX")"
repo="repos/$GITHUB_REPOSITORY"

case "$CHANNEL" in
    stable)
        # The newest published non-prerelease, which is also what
        # /releases/latest serves the install guide.
        release="$(gh api "$repo/releases/latest")" ||
            give_up "Found no published stable release to compare with"
        ;;
    nightly)
        # Found through the release list, as the publish step in ci.yml finds
        # it for deletion: a release whose tag association is corrupted stays
        # listed but vanishes from a tag lookup. Two listed nightlies, or one
        # its tag cannot reach, are what that step repairs, so both count as
        # changed instead of blocking the repair.
        release="$(gh api --paginate "$repo/releases?per_page=100" --jq '.[] | select(.tag_name == "nightly")')" ||
            give_up "Could not list the releases"
        case "$release" in
            "") give_up "Found no nightly release to compare with" ;;
            *$'\n'*) give_up "Found more than one nightly release" ;;
        esac
        gh api "$repo/releases/tags/nightly" > /dev/null ||
            give_up "The nightly release is listed but its tag does not resolve to it"
        ;;
esac

# The release title ("v2026.09.25-1", "Nightly 1311") names it in the log; the
# nightly tag alone would say nothing about which build it is.
label="$(jq -r '.name // .tag_name' <<< "$release")"
asset_id="$(jq -r '[.assets[] | select(.name | endswith(".apk"))] | if length == 1 then .[0].id else "" end' <<< "$release")"
[ -n "$asset_id" ] || give_up "$label has no single APK asset to compare with"
previous="$work/previous.apk"
gh api -H 'Accept: application/octet-stream' "$repo/releases/assets/$asset_id" > "$previous" ||
    give_up "Could not download the APK of $label"

[ -n "${ANDROID_HOME:-}" ] || give_up "ANDROID_HOME is not set, so aapt2 cannot read the version of $label"
aapt2="$(printf '%s\n' "$ANDROID_HOME"/build-tools/*/aapt2 | sort -V | tail -n 1)"
[ -x "$aapt2" ] || give_up "Found no aapt2 under $ANDROID_HOME/build-tools"
badging="$("$aapt2" dump badging "$previous")" || give_up "aapt2 could not read the APK of $label"
version_code="$(sed -n "1s/.* versionCode='\([0-9][0-9]*\)'.*/\1/p" <<< "$badging")"
version_name="$(sed -n "1s/.* versionName='\([^']*\)'.*/\1/p" <<< "$badging")"
if [ -z "$version_code" ] || [ -z "$version_name" ]; then
    give_up "Could not read the version of $label"
fi

echo "Building HEAD as $label (versionCode $version_code, versionName $version_name) to compare"
# The build runs third-party code (Gradle plugins, npm packages), and only
# the API calls above need the token, so the build never sees it.
env -u GH_TOKEN VERSION_CODE="$version_code" VERSION_NAME="$version_name" ./gradlew "$task" --stacktrace ||
    give_up "Building HEAD with the version of $label failed"
# output-metadata.json names the APK, whose file name depends on whether the
# build was signed.
outputs="app/build/outputs/apk/$CHANNEL/release"
apk_name="$(jq -r '.elements[0].outputFile' "$outputs/output-metadata.json")" ||
    give_up "The build left no output-metadata.json in $outputs"
candidate="$outputs/$apk_name"
[ -f "$candidate" ] || give_up "The build left no APK at $candidate"

# Extract an APK for diff -r, refusing an extraction that lost entries: a
# case-insensitive file system folds AGP's shortened resource names (such as
# res/1n.xml and res/1N.xml) into one file, and duplicate entries would fold
# the same way; either could hide a difference.
extract() {
    unzip -qq -o "$1" -d "$2" || return 1
    [ "$(unzip -Z1 "$1" | awk '!/\/$/ { n++ } END { print n + 0 }')" = \
        "$(find "$2" -type f | awk 'END { print NR }')" ] || return 1
    rm -f "$2/META-INF/version-control-info.textproto"
    if [ -d "$2/META-INF" ]; then
        find "$2/META-INF" -maxdepth 1 -type f \( -name MANIFEST.MF -o -name '*.SF' -o -name '*.RSA' \
            -o -name '*.DSA' -o -name '*.EC' -o -name 'SIG-*' \) -delete
    fi
}
extract "$previous" "$work/previous" || give_up "Could not extract every entry of the APK of $label"
extract "$candidate" "$work/candidate" || give_up "Could not extract every entry of the APK built from HEAD"

status=0
(cd "$work" && diff -rq previous candidate) > "$work/diff.txt" || status=$?
case "$status" in
    0)
        echo "::notice::APK unchanged since $label; not publishing"
        explained=true
        unchanged=true
        ;;
    1)
        echo "APK changed since $label; publishing. Differences (at most 50 shown):"
        head -n 50 "$work/diff.txt"
        explained=true
        ;;
    *) give_up "diff could not compare the APKs" ;;
esac
