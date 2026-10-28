#!/usr/bin/env bash
# Fetches Zinnia (BSD-3-Clause), Taku Kudo's handwriting recogniser, into prebuilt/zinnia.
# Only its library sources are compiled (latinime/bumboard/cpp/CMakeLists.txt).
set -euo pipefail

DEST="$(cd "$(dirname "$0")/.." && pwd)/prebuilt/zinnia"
if [ -f "$DEST/zinnia/recognizer.cpp" ]; then
    exit 0
fi
rm -rf "$DEST"
git clone -q --depth 1 https://github.com/taku910/zinnia "$DEST"
git -C "$DEST" rev-parse HEAD > "$DEST/.commit"
echo "zinnia $(cat "$DEST/.commit")"
