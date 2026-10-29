#!/usr/bin/env python3
"""Builds Lychee's Japanese dictionary pack from Mozc's open-source dictionary.

  build_ja.py --out DIR [--max 200000]

Downloads src/data/dictionary_oss/dictionary0[0-9].txt from google/mozc (BSD-3-Clause; the
entries come from IPAdic and other permissively licensed sources), turns each reading into
romaji syllables (Hepburn; っ doubles the next consonant), keeps the most common entries,
adds every reading in hiragana and katakana, compiles the lychee_ja schema with rime_deployer
and zips it as lychee-rime-ja.zip with pack.json. The app copies rime/ into Rime's data.

The pack also carries data/ja.db: meanings, part of speech and kana readings for each word
from JMdict (EDRDG, CC BY-SA 4.0), shown under the candidates and in the word card.
"""

from __future__ import annotations

import argparse
import gzip
import json
import sqlite3
import xml.etree.ElementTree as ET
import math
import shutil
import subprocess
import sys
import urllib.request
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from english_lookup import write_english_schema  # noqa: E402

HERE = Path(__file__).resolve().parent
RIME = HERE.parent.parent / "rime"
MOZC = "https://raw.githubusercontent.com/google/mozc/master/src/data/dictionary_oss/dictionary{:02d}.txt"
JMDICT = "http://ftp.edrdg.org/pub/Nihongo/JMdict_e.gz"
PACK_VERSION = "2"

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
    text = (HERE / "lychee_ja.schema.yaml").read_text(encoding="utf-8")
    quote = "'"
    block = "  preedit_format:\n" + "".join(f"    - {quote}{r.replace(quote, quote * 2)}{quote}\n" for r in rules)
    return text.replace("  spelling_hints: 0\n", "  spelling_hints: 0\n" + block, 1)


def build_meanings(cache: Path, path: Path, english: list | None = None) -> int:
    """data/ja.db from JMdict: every kanji and kana form of an entry -> its meanings. [english]
    collects (word, glosses, weight) for the English lookup: the word as it is usually written
    (kana when JMdict says so), common words weighing more."""
    src = cache / "JMdict_e.gz"
    if not src.exists():
        urllib.request.urlretrieve(JMDICT, src)
    path.unlink(missing_ok=True)
    db = sqlite3.connect(path)
    db.executescript("""
        PRAGMA page_size=4096;
        CREATE TABLE word(form TEXT NOT NULL, lemma TEXT NOT NULL, reading TEXT, hanja TEXT, pos TEXT,
                          gloss TEXT NOT NULL, rank INTEGER NOT NULL);
    """)
    rows = []
    rank = 0
    with gzip.open(src) as f:
        for _, e in ET.iterparse(f, events=("end",)):
            if e.tag != "entry":
                continue
            kanji = [k.findtext("keb") for k in e.findall("k_ele")]
            kana = [r.findtext("reb") for r in e.findall("r_ele")]
            # common words (news, ichi, spec lists) rank first
            common = any(p.text and p.text[:4] in ("news", "ichi", "spec")
                         for p in e.iter() if p.tag in ("ke_pri", "re_pri"))
            senses, pos = [], None
            kana_usually = any(m.text and "usually written using kana" in m.text
                               for m in e.iter("misc"))
            for s in e.findall("sense"):
                gl = [g.text for g in s.findall("gloss") if g.text][:3]
                if gl:
                    senses.append("; ".join(gl))
                if pos is None and s.find("pos") is not None:
                    pos = short_pos(s.findtext("pos") or "")
            e.clear()
            if not senses or not kana:
                continue
            rank += 1
            r = rank if common else rank + 1_000_000
            gloss = "\n".join(senses[:8])
            lemma = kanji[0] if kanji else kana[0]
            if english is not None:
                english.append((kana[0] if kana_usually or not kanji else kanji[0], senses[:8], 1.0 if common else 0.4))
            for form in dict.fromkeys(kanji + kana):
                rows.append((form, lemma, kana[0], None, pos, gloss, r))
    db.executemany("INSERT INTO word VALUES(?,?,?,?,?,?,?)", rows)
    db.execute("CREATE INDEX word_form ON word(form)")
    db.commit()
    db.execute("VACUUM")
    db.close()
    return len(rows)


def short_pos(p: str) -> str:
    """JMdict's part-of-speech text ("noun (common) (futsuumeishi)") in a word or two."""
    p = p.lower()
    for key, short in (("noun", "noun"), ("godan verb", "verb"), ("ichidan verb", "verb"),
                       ("suru verb", "verb"), ("kuru verb", "verb"), ("verb", "verb"),
                       ("adjective (keiyoushi)", "i-adjective"), ("adjectival noun", "na-adjective"),
                       ("adjective", "adjective"), ("adverb", "adverb"), ("pronoun", "pronoun"),
                       ("particle", "particle"), ("conjunction", "conjunction"),
                       ("interjection", "interjection"), ("counter", "counter"),
                       ("expression", "expression"), ("prefix", "prefix"), ("suffix", "suffix")):
        if key in p:
            return short
    return p.split("(")[0].strip()


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
    (shared / "lychee_ja.schema.yaml").write_text(schema_with_kana_preedit(), encoding="utf-8")
    shutil.copy(RIME / "lychee_common.yaml", shared)
    (shared / "default.yaml").write_text('config_version: "lychee.ja.1"\nschema_list:\n  - schema: lychee_ja\n'
                                         '  - schema: lychee_en_ja\n', encoding="utf-8")
    with open(shared / "lychee_ja.dict.yaml", "w", encoding="utf-8") as f:
        f.write("# Rime dictionary: Japanese, from Mozc's open-source dictionary (BSD-3-Clause; IPAdic licence).\n")
        f.write(f"---\nname: lychee_ja\nversion: \"{PACK_VERSION}\"\nsort: by_weight\nuse_preset_vocabulary: false\n...\n")
        f.write("\n".join(lines) + "\n")
    meanings = work / "ja.db"
    english: list = []
    n = build_meanings(args.cache, meanings, english)
    print(f"ja.db: {n} forms", file=sys.stderr)
    # English -> Japanese (英 on the Japanese keyboard): how common a word is, from Mozc
    common: dict[str, int] = {}
    for (word, reading), cost in entries:
        w = max(1, int(1e6 * math.exp(-cost / 700.0)))
        common[word] = max(common.get(word, 0), w)
    # words JMdict marks common, or that Mozc knows (rare entries would only bloat the pack)
    english = [e for e in english if e[2] == 1.0 or e[0] in common]
    n = write_english_schema(shared, "lychee_en_ja", "英→日", "Japanese", english, lambda t: common.get(t, 0) / 1000)
    print(f"lychee_en_ja: {n} entries", file=sys.stderr)

    user = work / "user"
    user.mkdir()
    build = shared / "build"
    subprocess.run(["rime_deployer", "--build", str(user), str(shared), str(build)], check=True)
    for p in build.glob("*.userdb*"):
        shutil.rmtree(p, ignore_errors=True) if p.is_dir() else p.unlink()

    pack = args.out / "lychee-rime-ja.zip"
    pack.unlink(missing_ok=True)
    with zipfile.ZipFile(pack, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("pack.json", json.dumps({"id": "rime_ja", "name": "Japanese dictionary", "version": PACK_VERSION}))
        for name in ["lychee_ja.schema.yaml", "lychee_ja.dict.yaml", "lychee_en_ja.schema.yaml", "lychee_en_ja.dict.yaml"]:
            z.write(shared / name, f"rime/{name}")
        for f in sorted(build.iterdir()):
            if f.name.startswith(("lychee_ja.", "lychee_en_ja.")):
                z.write(f, f"rime/build/{f.name}")
        z.write(meanings, "data/ja.db")
        z.writestr("LICENSE.txt", "Japanese dictionary from Mozc (https://github.com/google/mozc), "
                   "src/data/dictionary_oss: BSD-3-Clause, with entries from IPAdic (see Mozc's "
                   "dictionary_oss/README.txt for the full terms).\n"
                   "Meanings, parts of speech and readings (data/ja.db) from JMdict, the property of "
                   "the Electronic Dictionary Research and Development Group "
                   "(https://www.edrdg.org/edrdg/licence.html), used under CC BY-SA 4.0; this file "
                   "is shared under the same licence. Changes: reduced to the first glosses of each "
                   "sense and converted to SQLite.\n")
    print(f"{pack}: {len(lines)} entries, {pack.stat().st_size >> 20} MB", file=sys.stderr)


if __name__ == "__main__":
    main()
