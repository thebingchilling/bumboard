#!/usr/bin/env bash
# Fetches Lychee's handwriting recognisers into the APK's assets (lychee/ocr): PaddleOCR's
# PP-OCRv5 mobile text recognition models (Apache-2.0, by PaddlePaddle), as ONNX:
#   zh: Simplified and Traditional Chinese, Japanese (kana and kanji), English
#   ko: Korean
# with their character lists (one character per line, in the models' class order).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/data/out/assets/lychee/ocr"
mkdir -p "$OUT"

fetch() {  # url sha256 dest
    if [ -f "$3" ] && echo "$2  $3" | sha256sum -c --quiet - 2>/dev/null; then return; fi
    curl -fsSL -o "$3.part" "$1"
    echo "$2  $3.part" | sha256sum -c --quiet - || { echo "::error::checksum mismatch for $1"; exit 1; }
    mv "$3.part" "$3"
}

ZH="https://huggingface.co/nakamura196/ppocrv5-mobile-web-onnx/resolve/main"
KO="https://huggingface.co/monkt/paddleocr-onnx/resolve/main/languages/korean"
fetch "$ZH/PP-OCRv5_mobile_rec.onnx" 1205c2e14cc3ecaf7a57fe14736521ded32489603c40d98e36fe4212a70d8be5 "$OUT/zh.onnx"
fetch "$ZH/PP-OCRv5_mobile_rec.dict.json" 02c0a0121c577dfa211f11de21084e3abd23478a77ea4fbdf71ee7a3e990a717 "$OUT/zh.dict.json"
fetch "$KO/rec.onnx" 322f140154c820fcb83c3d24cfe42c9ec70dd1a1834163306a7338136e4f1eaa "$OUT/ko.onnx"
fetch "$KO/dict.txt" a88071c68c01707489baa79ebe0405b7beb5cca229f4fc94cc3ef992328802d7 "$OUT/ko.txt"
# the Chinese list as text too, one character per line like the Korean one
python3 -c "import json,sys; open(sys.argv[2],'w',encoding='utf-8').write('\n'.join(json.load(open(sys.argv[1],encoding='utf-8')))+'\n')" \
    "$OUT/zh.dict.json" "$OUT/zh.txt"
rm -f "$OUT/zh.dict.json"
echo "handwriting models: $(du -sh "$OUT" | cut -f1)"
