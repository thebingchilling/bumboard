#!/usr/bin/env bash
# Fetches Lychee's bundled fonts into the APK's assets:
#   Google Sans Flex (SIL Open Font License 1.1), the interface font, from google/fonts;
#   LXGW WenKai (SIL Open Font License 1.1), the default Chinese font (it also covers kana and
#   Hangul), from its pinned release;
#   the symbols Google Sans Flex lacks (⅙ ∞ ★ ✓ ‰ ← ♥ ...), cut from Google's Noto Sans, Noto Sans
#   Math and Noto Sans Symbols 2 (SIL Open Font License 1.1) by scripts/subset-symbols.py.
# Without them Lychee falls back to the system's fonts.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/data/out/assets/lychee"
mkdir -p "$OUT"

if [ ! -f "$OUT/GoogleSansFlex.ttf" ]; then
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
fi

WENKAI_VERSION="v1.530"
if [ ! -f "$OUT/LXGWWenKai-Regular.ttf" ]; then
    curl -fsSL -o "$OUT/LXGWWenKai-Regular.ttf.part" \
        "https://github.com/lxgw/LxgwWenKai/releases/download/$WENKAI_VERSION/LXGWWenKai-Regular.ttf"
    mv "$OUT/LXGWWenKai-Regular.ttf.part" "$OUT/LXGWWenKai-Regular.ttf"
    echo "lxgw wenkai: $WENKAI_VERSION"
fi

if [ ! -f "$OUT/LycheeSymbols0.ttf" ]; then
    TMP2="$(mktemp -d)"
    git clone -q --depth 1 --filter=blob:none --sparse https://github.com/google/fonts "$TMP2/fonts"
    git -C "$TMP2/fonts" sparse-checkout set ofl/notosans ofl/notosansmath ofl/notosanssymbols2
    sans="$(find "$TMP2/fonts/ofl/notosans" -maxdepth 1 -name 'NotoSans*.ttf' ! -iname '*italic*' | sort | head -1)"
    python3 "$ROOT/scripts/subset-symbols.py" "$OUT/GoogleSansFlex.ttf" "$OUT" "$ROOT/latinime/java/res/xml" \
        "$sans" "$TMP2/fonts/ofl/notosansmath/NotoSansMath-Regular.ttf" \
        "$TMP2/fonts/ofl/notosanssymbols2/NotoSansSymbols2-Regular.ttf"
    rm -rf "$TMP2"
fi
