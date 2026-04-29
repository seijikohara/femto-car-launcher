#!/usr/bin/env bash
# Download a variable-axis TTF from a GitHub repo into res/font/ and
# its license text into assets/licenses/, with name-validation that
# matches Android resource rules.
#
# Usage:
#   download-variable-font.sh <font-key> <owner/repo> <branch> <ttf-path> <license-path>
#
# Example:
#   download-variable-font.sh outfit googlefonts/Outfit main \
#       'fonts/variable/Outfit%5Bwght%5D.ttf' OFL.txt

set -euo pipefail

if [[ $# -ne 5 ]]; then
    echo "usage: $0 <font-key> <owner/repo> <branch> <ttf-path> <license-path>" >&2
    exit 64
fi

FONT_KEY="$1"
REPO="$2"
BRANCH="$3"
TTF_PATH="$4"
LICENSE_PATH="$5"

if [[ ! "$FONT_KEY" =~ ^[a-z0-9_]+$ ]]; then
    echo "font-key '$FONT_KEY' must match [a-z0-9_]+ (Android resource rules)" >&2
    exit 65
fi

# Locate project root by walking up from this script.
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &>/dev/null && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../../.." && pwd)"
FONT_DIR="${PROJECT_ROOT}/app/src/main/res/font"
LICENSE_DIR="${PROJECT_ROOT}/app/src/main/assets/licenses"

mkdir -p "${FONT_DIR}" "${LICENSE_DIR}"

FONT_OUT="${FONT_DIR}/${FONT_KEY}_variable.ttf"
LICENSE_OUT="${LICENSE_DIR}/${FONT_KEY}-OFL.txt"

curl -fsSL -o "${FONT_OUT}" \
    "https://github.com/${REPO}/raw/${BRANCH}/${TTF_PATH}"

if ! file "${FONT_OUT}" | grep -q 'TrueType Font data'; then
    echo "downloaded file is not a TrueType Font: ${FONT_OUT}" >&2
    file "${FONT_OUT}" >&2
    rm -f "${FONT_OUT}"
    exit 66
fi

curl -fsSL -o "${LICENSE_OUT}" \
    "https://github.com/${REPO}/raw/${BRANCH}/${LICENSE_PATH}"

echo "font:    ${FONT_OUT} ($(wc -c <"${FONT_OUT}") bytes)"
echo "license: ${LICENSE_OUT}"
