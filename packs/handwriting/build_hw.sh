#!/usr/bin/env bash
# Builds Lychee's handwriting packs (Zinnia models) into $1:
#   lychee-hw-ja.zip, and lychee-hw-zh.zip (the Traditional and Simplified models together)
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
declare -A TOMOE=([ja]=handwriting-ja [zh-hans]=handwriting-zh_CN)

tomoe_ready=0
tomoe() {
  [ "$tomoe_ready" = 1 ] && return 0
  curl -fsSL -o tomoe.tar.bz2 \
    "https://sourceforge.net/projects/zinnia/files/zinnia-tomoe/0.6.0/zinnia-tomoe-0.6.0-20080911.tar.bz2/download" \
    && tar xjf tomoe.tar.bz2 && tomoe_ready=1
}

# the model file for $1, or empty
find_model() {
  local lang="$1" model=""
  if apt-get download "${DEBS[$lang]}" >/dev/null 2>&1; then
    dpkg -x ./"${DEBS[$lang]}"_*.deb "deb-$lang"
    model=$(find "deb-$lang" -name '*.model' | head -1)
  fi
  if [ -z "$model" ] && [ -n "${TOMOE[$lang]:-}" ] && tomoe; then
    src=$(find . -name "${TOMOE[$lang]}.s" | head -1)
    if [ -n "$src" ] && command -v zinnia_convert >/dev/null; then
      zinnia_convert "$src" "${TOMOE[$lang]}.model" >&2 && model="${TOMOE[$lang]}.model"
    fi
  fi
  echo "$model"
}

licence() {
  printf 'Zinnia handwriting models from the Tegaki / Tomoe projects, LGPL-2.1.\nSource: https://github.com/tegaki and https://taku910.github.io/zinnia/\n' > "$1/LICENSE.txt"
}

# Japanese
model=$(find_model ja)
if [ -n "$model" ]; then
  mkdir -p pack-ja && cp "$model" pack-ja/handwriting.model && licence pack-ja
  printf '{"id": "hw_ja", "name": "Handwriting: Japanese", "version": "1"}' > pack-ja/pack.json
  (cd pack-ja && zip -q -X -r "$OUT/lychee-hw-ja.zip" pack.json handwriting.model LICENSE.txt)
  echo "built lychee-hw-ja.zip ($(du -h "$OUT/lychee-hw-ja.zip" | cut -f1))"
else
  echo "::warning::no handwriting model for ja"
fi

# Chinese: one pack with both the Traditional and the Simplified model (the app asks both)
hant=$(find_model zh-hant)
hans=$(find_model zh-hans)
if [ -n "$hant" ] || [ -n "$hans" ]; then
  mkdir -p pack-zh && licence pack-zh
  files="pack.json LICENSE.txt"
  [ -n "$hant" ] && cp "$hant" pack-zh/hant.model && files="$files hant.model"
  [ -n "$hans" ] && cp "$hans" pack-zh/hans.model && files="$files hans.model"
  printf '{"id": "hw_zh", "name": "Handwriting: Chinese", "version": "2"}' > pack-zh/pack.json
  (cd pack-zh && zip -q -X -r "$OUT/lychee-hw-zh.zip" $files)
  echo "built lychee-hw-zh.zip ($(du -h "$OUT/lychee-hw-zh.zip" | cut -f1)): $files"
else
  echo "::warning::no handwriting model for Chinese"
fi
