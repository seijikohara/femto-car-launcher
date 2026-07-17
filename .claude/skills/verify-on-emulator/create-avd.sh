#!/usr/bin/env bash
# Recreate the TBox-Mock-Play AVD from the committed definition
# (tbox-mock-play.config.ini beside this script).
#
# Portable by design: the AVD registry files are written directly, so no
# avdmanager / cmdline-tools install is required (Android Studio's SDK
# Manager does not ship them by default). The only prerequisite is the
# Android 33 Google Play system image for the host architecture; Google
# sign-in inside the booted emulator remains a one-time manual step per
# machine (account state cannot be committed).
set -euo pipefail

AVD_NAME="${AVD_NAME:-TBox-Mock-Play}"
API="${API:-33}"
TAG="google_apis_playstore"
case "$(uname -m)" in
  arm64 | aarch64) ABI="arm64-v8a" ;;
  *) ABI="x86_64" ;;
esac

# Pick the first SDK root that actually holds the required system image —
# ANDROID_HOME can point at a toolchain-manager shim (e.g. mise) that has no
# images while the real Studio-managed SDK lives at the conventional path.
SYSDIR="system-images/android-$API/$TAG/$ABI"
SDK=""
for candidate in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do
  if [ -n "$candidate" ] && [ -d "$candidate/$SYSDIR" ]; then
    SDK="$candidate"
    break
  fi
done
if [ -z "$SDK" ]; then
  echo "No Android SDK with the required system image was found." >&2
  echo "Looked for <sdk-root>/$SYSDIR in: ANDROID_HOME, ANDROID_SDK_ROOT, and the conventional roots." >&2
  echo "Install the image via Android Studio's SDK Manager, or if sdkmanager exists:" >&2
  echo "  sdkmanager --install \"system-images;android-$API;$TAG;$ABI\"" >&2
  exit 1
fi

AVD_HOME="${ANDROID_AVD_HOME:-$HOME/.android/avd}"
AVD_DIR="$AVD_HOME/$AVD_NAME.avd"
if [ -e "$AVD_DIR" ] || [ -e "$AVD_HOME/$AVD_NAME.ini" ]; then
  echo "$AVD_NAME already exists under $AVD_HOME - delete it first to recreate." >&2
  exit 1
fi
mkdir -p "$AVD_DIR"

cat > "$AVD_HOME/$AVD_NAME.ini" <<EOF
avd.ini.encoding=UTF-8
path=$AVD_DIR
path.rel=avd/$AVD_NAME.avd
target=android-$API
EOF

# Committed hardware definition first, then the machine/name-dependent lines
# the definition deliberately omits.
{
  cat "$(dirname "$0")/tbox-mock-play.config.ini"
  printf 'AvdId=%s\n' "$AVD_NAME"
  printf 'avd.ini.displayname=%s\n' "$AVD_NAME"
  printf 'abi.type=%s\n' "$ABI"
  printf 'hw.cpu.arch=%s\n' "${ABI%%-*}"
  printf 'image.sysdir.1=%s/\n' "$SYSDIR"
  printf 'target=android-%s\n' "$API"
} > "$AVD_DIR/config.ini"

echo "Created $AVD_NAME (android-$API $TAG $ABI) at $AVD_DIR"
echo "Boot it with: emulator -avd $AVD_NAME -no-audio -gpu host"
