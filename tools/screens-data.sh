#!/usr/bin/env bash
# The real data for the screenshots (CLAUDE.md): built here exactly as build.yml builds it for
# the app (the readings dictionary, emoji, fonts, the packs that come with Lychee), plus the
# Japanese and Korean dictionaries the app downloads, every pack unpacked into
# app/latinime/build/screens-data/<pack>/ where the snapshot tests read them. Run once per fresh
# session after screens-setup.sh (a few minutes); screens.sh runs it by itself when it's missing.
set -euo pipefail
REPO="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO/app"
OUT="$REPO/app/latinime/build/screens-data"
pip install --quiet opencc fonttools 2>/dev/null || true
scripts/fetch-prebuilt.sh
# Rime's own compiler isn't needed for pictures: the readings dictionary is built before it
python3 data/build_data.py --opencc prebuilt/opencc/data || test -s data/out/assets/lychee/readings.db
python3 data/build_emoji.py
scripts/fetch-fonts.sh
python3 scripts/bundle-packs.py data/out/assets/lychee/bundled
rm -rf "$OUT" && mkdir -p "$OUT"
for z in data/out/assets/lychee/bundled/*.zip; do
  id="$(basename "$z" .zip)"; mkdir -p "$OUT/$id"; (cd "$OUT/$id" && python3 -m zipfile -e "$REPO/app/$z" .)
done
# the downloads: the Japanese and Korean dictionaries, from the public packs release
RELEASE="https://github.com/lychee-keyboard/lychee/releases/download/packs"
curl -fsSL "$RELEASE/manifest.json" -o "$OUT/manifest.json"
for id in rime_ja dict_ko; do
  file="$(python3 -c "import json,sys; p=json.load(open('$OUT/manifest.json'))['packs'].get('$id',{}); print(p.get('download_file') or p.get('file') or '')")"
  [ -n "$file" ] || { echo "no $id in the manifest"; continue; }
  curl -fsSL "$RELEASE/$file" -o "$OUT/$id.zip" && mkdir -p "$OUT/$id" && (cd "$OUT/$id" && python3 -m zipfile -e "$OUT/$id.zip" .) && rm "$OUT/$id.zip"
done
cp data/out/assets/lychee/readings.db "$OUT/readings.db"
find "$OUT" -name "*.db" | sed "s|$OUT/||"
