#!/usr/bin/env python3
"""Builds BumBoard's Japanese dictionary pack from Mozc's open-source dictionary.

  build_ja.py --out DIR [--max 200000]

Downloads src/data/dictionary_oss/dictionary0[0-9].txt from google/mozc (BSD-3-Clause; the
entries come from IPAdic and other permissively licensed sources), turns each reading into
romaji syllables (Hepburn; っ doubles the next consonant), keeps the most common entries,
adds every reading in hiragana and katakana, compiles the bum_ja schema with rime_deployer
and zips it as bumboard-rime-ja.zip with pack.json. The app copies rime/ into Rime's data.
"""

from __future__ import annotations

import argparse
import json
import math
import shutil
import subprocess
import sys
import urllib.request
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
RIME = HERE.parent.parent / "rime"
MOZC = "https://raw.githubusercontent.com/google/mozc/master/src/data/dictionary_oss/dictionary{:02d}.txt"
PACK_VERSION = "1"

# hiragana -> Hepburn romaji
BASE = {
    "あ": "a", "い": "i", "う": "u", "え": "e", "お": "o",
    "か": "ka", "き": "ki", "く": "ku", "け": "ke", "こ": "ko",
    "が": "ga", "ぎ": "gi", "ぐ": "gu", "げ": "ge", "ご": "go",
    "さ": "sa", "し": "shi", "す": "su", "せ": "se", "そ": "so",
    "ざ": "za", "じ": "ji", "ず": "zu", "ぜ": "ze", "ぞ": "zo",
    "た": "ta", "ち": "chi", "つ": "tsu", "て": "te", "と": "to",
    "だ": "da", "ぢ": "ji", "づ": "zu", "で": "de", "ど": "do",
    "な": "na", "に": "ni", "ぬ": "nu", "ね": "ne", "の": "no",
    "は": "ha", "ひ": "hi", "ふ": "fu", "へ": "he", "ほ": "ho",
    "ば": "ba", "び": "bi", "ぶ": "bu", "べ": "be", "ぼ": "bo",
    "ぱ": "pa", "ぴ": "pi", "ぷ": "pu", "ぺ": "pe", "ぽ": "po",
    "ま": "ma", "み": "mi", "む": "mu", "め": "me", "も": "mo",
    "や": "ya", "ゆ": "yu", "よ": "yo",
    "ら": "ra", "り": "ri", "る": "ru", "れ": "re", "ろ": "ro",
    "わ": "wa", "ゐ": "i", "ゑ": "e", "を": "wo", "ん": "n",
    "ゔ": "vu", "ー": "-",
    "ぁ": "xa", "ぃ": "xi", "ぅ": "xu", "ぇ": "xe", "ぉ": "xo",
    "ゃ": "xya", "ゅ": "xyu", "ょ": "xyo", "ゎ": "xwa", "っ": "xtsu",
}
# consonant stems for kana + small ya/yu/yo (きゃ -> kya, しゃ -> sha)
YOON = {
    "き": "ky", "ぎ": "gy", "し": "sh", "じ": "j", "ち": "ch", "ぢ": "j", "に": "ny",
    "ひ": "hy", "び": "by", "ぴ": "py", "み": "my", "り": "ry",
}
SMALL_Y = {"ゃ": "a", "ゅ": "u", "ょ": "o"}
# foreign-sound pairs (ファ -> fa, ティ -> ti ...)
EXTRA = {
    "ふぁ": "fa", "ふぃ": "fi", "ふぇ": "fe", "ふぉ": "fo", "てぃ": "ti", "でぃ": "di",
    "とぅ": "tu", "どぅ": "du", "うぃ": "wi", "うぇ": "we", "うぉ": "wo", "ゔぁ": "va",
    "ゔぃ": "vi", "ゔぇ": "ve", "ゔぉ": "vo", "しぇ": "she", "じぇ": "je", "ちぇ": "che",
    "つぁ": "tsa", "つぃ": "tsi", "つぇ": "tse", "つぉ": "tso", "いぇ": "ye",
}


def kata_to_hira(s: str) -> str:
    return "".join(chr(ord(c) - 0x60) if "ァ" <= c <= "ヶ" else c for c in s)


def hira_to_kata(s: str) -> str:
    return "".join(chr(ord(c) + 0x60) if "ぁ" <= c <= "ゖ" else c for c in s)


def syllables(reading: str) -> list[str] | None:
    """Romaji syllables for a hiragana reading, or None when it has other characters."""
    out: list[str] = []
    i = 0
    double = False
    while i < len(reading):
        pair = reading[i:i + 2]
        if pair in EXTRA:
            syl, i = EXTRA[pair], i + 2
        elif len(pair) == 2 and pair[0] in YOON and pair[1] in SMALL_Y:
            syl, i = YOON[pair[0]] + SMALL_Y[pair[1]], i + 2
        elif reading[i] == "っ":
            i += 1
            if i < len(reading) and reading[i] not in "あいうえおんっー":
                double = True
                continue
            syl = "xtsu"
        elif reading[i] in BASE:
            syl, i = BASE[reading[i]], i + 1
        else:
            return None
        if double:
            syl = ("t" + syl) if syl.startswith("ch") else (syl[0] + syl)
            double = False
        out.append(syl)
    return out or None


def schema_with_kana_preedit() -> str:
    """The schema with a preedit_format that shows typed romaji as hiragana."""
    pairs: dict[str, str] = {}
    for kana, rom in BASE.items():
        if rom != "-":
            pairs.setdefault(rom, kana)
    for kana, stem in YOON.items():
        for small, vowel in SMALL_Y.items():
            pairs.setdefault(stem + vowel, kana + small)
    for kana, rom in EXTRA.items():
        pairs.setdefault(rom, kana)
    # the other spellings the speller accepts
    alias = {"si": "し", "ti": "ち", "tu": "つ", "hu": "ふ", "zi": "じ", "nn": "ん",
             "sya": "しゃ", "syu": "しゅ", "syo": "しょ", "tya": "ちゃ", "tyu": "ちゅ", "tyo": "ちょ",
             "cya": "ちゃ", "cyu": "ちゅ", "cyo": "ちょ", "zya": "じゃ", "zyu": "じゅ", "zyo": "じょ",
             "jya": "じゃ", "jyu": "じゅ", "jyo": "じょ"}
    for rom, kana in alias.items():
        pairs.setdefault(rom, kana)
    rules = ["xform/n'/ん/", "xform/'//", "xform/ //",
             "xform/([kstcpgzdbhfjmrwyv])\\1/っ$1/", "xform/tch/っch/"]
    for rom in sorted(pairs, key=lambda r: (-len(r), r)):
        if rom == "n":
            continue
        rules.append(f"xform/{rom}/{pairs[rom]}/")
    # a lone n last, so it doesn't eat the n of na, ni...
    rules += ["xform/n/ん/", "xform/-/ー/"]
    text = (HERE / "bum_ja.schema.yaml").read_text(encoding="utf-8")
    quote = "'"
    block = "  preedit_format:\n" + "".join(f"    - {quote}{r.replace(quote, quote * 2)}{quote}\n" for r in rules)
    return text.replace("  spelling_hints: 0\n", "  spelling_hints: 0\n" + block, 1)


def download(n: int, cache: Path) -> Path:
    f = cache / f"dictionary{n:02d}.txt"
    if not f.exists():
        urllib.request.urlretrieve(MOZC.format(n), f)
    return f


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--cache", type=Path, default=HERE / ".cache")
    ap.add_argument("--max", type=int, default=200_000)
    args = ap.parse_args()
    args.cache.mkdir(parents=True, exist_ok=True)

    best: dict[tuple[str, str], int] = {}
    for n in range(10):
        with open(download(n, args.cache), encoding="utf-8") as f:
            for line in f:
                parts = line.rstrip("\n").split("\t")
                if len(parts) < 5:
                    continue
                reading, cost, word = kata_to_hira(parts[0]), int(parts[3]), parts[4]
                key = (word, reading)
                if key not in best or cost < best[key]:
                    best[key] = cost
    entries = sorted(best.items(), key=lambda kv: kv[1])[: args.max]

    lines: list[str] = []
    readings: dict[str, int] = {}
    for (word, reading), cost in entries:
        syl = syllables(reading)
        if not syl:
            continue
        code = " ".join(syl)
        weight = max(1, int(1e6 * math.exp(-cost / 700.0)))
        lines.append(f"{word}\t{code}\t{weight}")
        readings[code] = max(readings.get(code, 0), weight)
    # every reading also as itself, in hiragana and katakana, below the words
    for (word, reading), _ in entries:
        syl = syllables(reading)
        if not syl:
            continue
        code = " ".join(syl)
        w = max(1, readings[code] // 20)
        lines.append(f"{reading}\t{code}\t{w}")
        lines.append(f"{hira_to_kata(reading)}\t{code}\t{max(1, w // 4)}")
    # single kana, so anything can be typed
    for kana, rom in BASE.items():
        if rom.startswith("x") or rom == "-":
            continue
        lines.append(f"{kana}\t{rom}\t1")
        lines.append(f"{hira_to_kata(kana)}\t{rom}\t1")
    lines = list(dict.fromkeys(lines))

    work = args.out / "work"
    shutil.rmtree(work, ignore_errors=True)
    shared = work / "shared"
    shared.mkdir(parents=True)
    (shared / "bum_ja.schema.yaml").write_text(schema_with_kana_preedit(), encoding="utf-8")
    shutil.copy(RIME / "bum_common.yaml", shared)
    (shared / "default.yaml").write_text('config_version: "bumboard.ja.1"\nschema_list:\n  - schema: bum_ja\n', encoding="utf-8")
    with open(shared / "bum_ja.dict.yaml", "w", encoding="utf-8") as f:
        f.write("# Rime dictionary: Japanese, from Mozc's open-source dictionary (BSD-3-Clause; IPAdic licence).\n")
        f.write(f"---\nname: bum_ja\nversion: \"{PACK_VERSION}\"\nsort: by_weight\nuse_preset_vocabulary: false\n...\n")
        f.write("\n".join(lines) + "\n")
    user = work / "user"
    user.mkdir()
    build = shared / "build"
    subprocess.run(["rime_deployer", "--build", str(user), str(shared), str(build)], check=True)
    for p in build.glob("*.userdb*"):
        shutil.rmtree(p, ignore_errors=True) if p.is_dir() else p.unlink()

    pack = args.out / "bumboard-rime-ja.zip"
    pack.unlink(missing_ok=True)
    with zipfile.ZipFile(pack, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("pack.json", json.dumps({"id": "rime_ja", "name": "Japanese dictionary", "version": PACK_VERSION}))
        for name in ["bum_ja.schema.yaml", "bum_ja.dict.yaml"]:
            z.write(shared / name, f"rime/{name}")
        for f in sorted(build.iterdir()):
            if f.name.startswith("bum_ja."):
                z.write(f, f"rime/build/{f.name}")
        z.writestr("LICENSE.txt", "Japanese dictionary from Mozc (https://github.com/google/mozc), "
                   "src/data/dictionary_oss: BSD-3-Clause, with entries from IPAdic (see Mozc's "
                   "dictionary_oss/README.txt for the full terms).\n")
    print(f"{pack}: {len(lines)} entries, {pack.stat().st_size >> 20} MB", file=sys.stderr)


if __name__ == "__main__":
    main()
