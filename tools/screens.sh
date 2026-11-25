#!/usr/bin/env bash
# Draws Lychee's screens with Paparazzi (no emulator or phone) and copies them to screens/ at
# the repository's root (git-ignored), for looking at before committing UI work (CLAUDE.md).
# Run tools/screens-setup.sh once per fresh session first. Optional argument: a test filter,
# e.g. tools/screens.sh '*CardScreens*'.
set -euo pipefail
REPO="$(cd "$(dirname "$0")/.." && pwd)"
export ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
cd "$REPO/app"
if [ $# -gt 0 ]; then
  ./gradlew --no-daemon -q :latinime:recordPaparazziDebug -PlycheeScreens --tests "$1"
else
  ./gradlew --no-daemon -q :latinime:recordPaparazziDebug -PlycheeScreens
fi
mkdir -p "$REPO/screens"
cp latinime/src/test/snapshots/images/*.png "$REPO/screens/"
ls -1 "$REPO/screens"
