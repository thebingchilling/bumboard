#!/usr/bin/env bash
# Builds the harness and checks cases.txt against lychee/data/out/assets/rime.
# Usage: run.sh            check all cases
#        run.sh -d schema input [opts]   dump candidates
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
data="$here/../out/assets/rime"
work="${TMPDIR:-/tmp}/lychee-rime-test"
mkdir -p "$work"
cc -O1 -o "$work/rime_cases" "$here/rime_cases.c" -lrime
# shared dir = shipped data + host OpenCC dictionaries (not shipped: the app links its own)
rm -rf "$work/shared" "$work/user"
mkdir -p "$work/shared" "$work/user"
for f in "$data"/*; do ln -s "$f" "$work/shared/"; done
ln -s /usr/share/opencc "$work/shared/opencc"
if [ "${1:-}" = "-d" ]; then
  shift
  exec "$work/rime_cases" "$work/shared" "$work/user" -d "$@"
fi
exec "$work/rime_cases" "$work/shared" "$work/user" "$here/cases.txt"
