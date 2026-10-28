#!/usr/bin/env bash
# Fetches Google Sans Flex (SIL Open Font License 1.1), BumBoard's interface font, from the
# google/fonts repository into the APK's assets. Without it BumBoard falls back to the system font.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/data/out/assets/bumboard"
if [ -f "$OUT/GoogleSansFlex.ttf" ]; then
    exit 0
fi
mkdir -p "$OUT"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
git clone -q --depth 1 --filter=blob:none --sparse https://github.com/google/fonts "$TMP/fonts"
git -C "$TMP/fonts" sparse-checkout set ofl/googlesansflex
font="$(find "$TMP/fonts/ofl/googlesansflex" -maxdepth 1 -name '*.ttf' ! -iname '*italic*' 2>/dev/null | sort | head -1)"
if [ -z "$font" ]; then
    echo "::error::Google Sans Flex not found in google/fonts (ofl/googlesansflex)"
    exit 1
fi
cp "$font" "$OUT/GoogleSansFlex.ttf"
echo "google sans flex: $(basename "$font") @ $(git -C "$TMP/fonts" rev-parse HEAD)"
