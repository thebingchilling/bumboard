#!/usr/bin/env bash
# Builds BumBoard's handwriting packs (Zinnia models) into $1:
#   bumboard-hw-ja.zip, bumboard-hw-zh-hans.zip, bumboard-hw-zh-hant.zip
# Models come from Ubuntu's tegaki-zinnia-* packages (LGPL-2.1, from the Tegaki project), or
# else from zinnia-tomoe (LGPL-2.1, Tomoe project) converted with zinnia_convert. A pack whose
# model can't be found is skipped with a warning.
set -uo pipefail
OUT="$(realpath -m "$1")"
mkdir -p "$OUT"
WORK="$(mktemp -d)"
cd "$WORK"

declare -A DEBS=(
  [ja]=tegaki-zinnia-japanese
  [zh-hans]=tegaki-zinnia-simplified-chinese
  [zh-hant]=tegaki-zinnia-traditional-chinese
)
declare -A IDS=([ja]=hw_ja [zh-hans]=hw_zh_hans [zh-hant]=hw_zh_hant)
declare -A NAMES=([ja]="Handwriting: Japanese" [zh-hans]="Handwriting: Simplified Chinese" [zh-hant]="Handwriting: Traditional Chinese")
declare -A TOMOE=([ja]=handwriting-ja [zh-hans]=handwriting-zh_CN)

tomoe_ready=0
tomoe() {
  [ "$tomoe_ready" = 1 ] && return 0
  curl -fsSL -o tomoe.tar.bz2 \
    "https://sourceforge.net/projects/zinnia/files/zinnia-tomoe/0.6.0/zinnia-tomoe-0.6.0-20080911.tar.bz2/download" \
    && tar xjf tomoe.tar.bz2 && tomoe_ready=1
}

for lang in ja zh-hans zh-hant; do
  model=""
  if apt-get download "${DEBS[$lang]}" >/dev/null 2>&1; then
    dpkg -x ./"${DEBS[$lang]}"_*.deb "deb-$lang"
    model=$(find "deb-$lang" -name '*.model' | head -1)
  fi
  if [ -z "$model" ] && [ -n "${TOMOE[$lang]:-}" ] && tomoe; then
    src=$(find . -name "${TOMOE[$lang]}.s" | head -1)
    if [ -n "$src" ] && command -v zinnia_convert >/dev/null; then
      zinnia_convert "$src" "${TOMOE[$lang]}.model" && model="${TOMOE[$lang]}.model"
    fi
  fi
  if [ -z "$model" ]; then
    echo "::warning::no handwriting model for $lang"
    continue
  fi
  dir="pack-$lang"
  mkdir -p "$dir"
  cp "$model" "$dir/handwriting.model"
  printf '{"id": "%s", "name": "%s", "version": "1"}' "${IDS[$lang]}" "${NAMES[$lang]}" > "$dir/pack.json"
  printf 'Zinnia handwriting model from the Tegaki / Tomoe projects, LGPL-2.1.\nSource: https://github.com/tegaki and https://taku910.github.io/zinnia/\n' > "$dir/LICENSE.txt"
  (cd "$dir" && zip -q -X -r "$OUT/bumboard-hw-$lang.zip" pack.json handwriting.model LICENSE.txt)
  echo "built bumboard-hw-$lang.zip ($(du -h "$OUT/bumboard-hw-$lang.zip" | cut -f1))"
done
