#!/usr/bin/env bash
# Fetches the prebuilt static librime (BSD-3-Clause) and its dependencies from
# fcitx5-android/prebuilt into bumboard/prebuilt, at a pinned commit.
set -euo pipefail

COMMIT=3f987c60ae1485a7b2a2cf7db301726af4700f29
DEST="$(cd "$(dirname "$0")/.." && pwd)/prebuilt"
PARTS=(librime glog leveldb lua marisa opencc yaml-cpp)

if [ -f "$DEST/.commit" ] && [ "$(cat "$DEST/.commit")" = "$COMMIT" ]; then
    exit 0
fi
rm -rf "$DEST"
mkdir -p "$DEST"
cd "$DEST"
git init -q
git remote add origin https://github.com/fcitx5-android/prebuilt
git sparse-checkout set "${PARTS[@]}"
git fetch -q --depth 1 --filter=blob:none origin "$COMMIT"
git checkout -q FETCH_HEAD
echo "$COMMIT" > .commit
