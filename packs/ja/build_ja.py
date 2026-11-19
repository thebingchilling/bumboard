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
import os
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
RIME = HERE.parent.parent / "app" / "data" / "rime"
MOZC = "https://raw.githubusercontent.com/google/mozc/master/src/data/dictionary_oss/dictionary{:02d}.txt"
JMDICT = "http://ftp.edrdg.org/pub/Nihongo/JMdict_e.gz"
# KANJIDIC2 (EDRDG, CC BY-SA 4.0): each kanji's readings, meanings, school grade, strokes
KANJIDIC = "http://ftp.edrdg.org/pub/Nihongo/kanjidic2.xml.gz"
# JLPT levels: Jonathan Waller's lists (tanos.co.uk, CC BY), as compiled with JMdict ids by
# stephenmk/yomitan-jlpt-vocab (CC BY-SA 4.0); one CSV per level: jmdict_seq,kana,kanji,definition
JLPT = "https://raw.githubusercontent.com/stephenmk/yomitan-jlpt-vocab/main/original_data/n{}.csv"
# pitch accent: Kanjium (mifunetoshiro/kanjium, CC BY-SA 4.0; accents by Uros O.):
# word \t reading \t accent ("0", "2", "0,2", "(副)0,(名)3")
ACCENTS = "https://raw.githubusercontent.com/mifunetoshiro/kanjium/master/data/source_files/raw/accents.txt"
# KRADFILE (EDRDG, CC BY-SA 4.0): each kanji's parts, "<kanji> : <part> <part> ...", EUC-JP
KRADFILE = "http://ftp.edrdg.org/pub/Nihongo/kradfile.gz"
PACK_VERSION = "3"

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
                          gloss TEXT NOT NULL, rank INTEGER NOT NULL, vclass TEXT);
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
            senses, pos, vclass = [], None, None
            kana_usually = any(m.text and "usually written using kana" in m.text
                               for m in e.iter("misc"))
            for s in e.findall("sense"):
                gl = [g.text for g in s.findall("gloss") if g.text][:3]
                if gl:
                    senses.append("; ".join(gl))
                if pos is None and s.find("pos") is not None:
                    pos = short_pos(s.findtext("pos") or "")
                if vclass is None:
                    vclass = next((c for c in (verb_class(p.text or "") for p in s.findall("pos")) if c), None)
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
                rows.append((form, lemma, kana[0], None, pos, gloss, r, vclass))
    db.executemany("INSERT INTO word VALUES(?,?,?,?,?,?,?,?)", rows)
    db.execute("CREATE INDEX word_form ON word(form)")
    add_kanji(cache, db)
    add_learner(cache, db, {r[0] for r in rows})
    db.commit()
    db.execute("VACUUM")
    db.close()
    return len(rows)


def add_kanji(cache: Path, db: sqlite3.Connection):
    """KANJIDIC2 into ja.db's kanji table, kept short for the card: up to four on and kun
    readings, three meanings, the school grade (1-6, 8 for the rest of the jōyō kanji) and the
    stroke count."""
    src = cache / "kanjidic2.xml.gz"
    if not src.exists():
        urllib.request.urlretrieve(KANJIDIC, src)
    db.execute("CREATE TABLE kanji(ch TEXT PRIMARY KEY, onyomi TEXT, kunyomi TEXT, meanings TEXT, "
               "grade INTEGER, strokes INTEGER, jlpt INTEGER, freq INTEGER, parts TEXT) WITHOUT ROWID")
    parts = kanji_parts(cache)
    rows = []
    with gzip.open(src) as f:
        for _, e in ET.iterparse(f, events=("end",)):
            if e.tag != "character":
                continue
            ch = e.findtext("literal")
            on = [r.text for r in e.iter("reading") if r.get("r_type") == "ja_on" and r.text][:4]
            kun = [r.text for r in e.iter("reading") if r.get("r_type") == "ja_kun" and r.text][:4]
            # English meanings only (the others carry m_lang)
            meanings = [m.text for m in e.iter("meaning") if m.get("m_lang") is None and m.text][:3]
            grade = e.findtext("misc/grade")
            strokes = e.findtext("misc/stroke_count")
            # KANJIDIC2's (old, four-level) JLPT level and its newspaper frequency rank (1-2500)
            jlpt = e.findtext("misc/jlpt")
            freq = e.findtext("misc/freq")
            e.clear()
            if ch and (on or kun or meanings):
                num = lambda v: int(v) if v and v.isdigit() else None  # noqa: E731
                rows.append((ch, "、".join(on), "、".join(kun), "; ".join(meanings), num(grade), num(strokes),
                             num(jlpt), num(freq), " ".join(p for p in parts.get(ch, []) if p != ch) or None))
    db.executemany("INSERT OR IGNORE INTO kanji VALUES(?,?,?,?,?,?,?,?,?)", rows)
    print(f"ja.db: {len(rows)} kanji from KANJIDIC2", file=sys.stderr)


def jmdict_date(cache: Path) -> str | None:
    """JMdict's release date from its header ("<!-- JMdict created: 2026-11-18 -->"), as
    "2026.11.18"."""
    import re
    src = cache / "JMdict_e.gz"
    if not src.exists():
        return None
    with gzip.open(src) as f:
        head = f.read(1 << 20).decode("utf-8", "ignore")
    m = re.search(r"JMdict created: (\d{4})-(\d\d)-(\d\d)", head)
    return ".".join(m.groups()) if m else None


def verb_class(p: str) -> str | None:
    """JMdict's part-of-speech text -> the conjugation class Lychee's tables use: v1 (ichidan),
    v5u v5k v5g v5s v5t v5n v5b v5m v5r (godan, by ending), v5k-s (行く), v5r-i (ある), v5aru
    (いらっしゃる, ください...), v5u-s (問う), vk (来る), vs (する and suru verbs), vs-i (する),
    adj-i, adj-ix (いい/よい), adj-na; None for anything else."""
    q = p.lower()
    if "ichidan verb" in q:
        return "v1"
    if "kuru verb" in q:
        return "vk"
    if "iku/yuku special class" in q:
        return "v5k-s"
    if "-aru special class" in q:
        return "v5aru"
    if "'ru' ending (irregular" in q:
        return "v5r-i"
    if "'u' ending (special class)" in q:
        return "v5u-s"
    if "godan verb with '" in q:
        end = q.split("godan verb with '", 1)[1].split("'", 1)[0]
        return {"u": "v5u", "ku": "v5k", "gu": "v5g", "su": "v5s", "tsu": "v5t", "nu": "v5n",
                "bu": "v5b", "mu": "v5m", "ru": "v5r"}.get(end)
    if "suru verb - included" in q or "suru verb - special class" in q:
        return "vs-i"
    if "takes the aux. verb suru" in q:
        return "vs"
    if "yoi/ii class" in q:
        return "adj-ix"
    if "adjective (keiyoushi)" in q:
        return "adj-i"
    if "adjectival nouns or quasi-adjectives" in q:
        return "adj-na"
    return None


def fetch_retrying(url: str, dest: Path, tries: int = 4) -> bool:
    """Downloads [url] to [dest], trying again after a pause; whether it arrived."""
    import time
    for attempt in range(tries):
        try:
            with urllib.request.urlopen(url, timeout=60) as r:
                data = r.read()
            dest.write_bytes(data)
            return True
        except OSError as e:
            print(f"{url}: {e} (attempt {attempt + 1} of {tries})", file=sys.stderr)
            time.sleep(10 * (attempt + 1))
    return False


def kanji_parts(cache: Path) -> dict[str, list[str]]:
    """KRADFILE: each kanji -> the parts it is drawn from."""
    src = cache / "kradfile.gz"
    if not src.exists() and not fetch_retrying(KRADFILE, src):
        # EDRDG's server is sometimes unreachable from the build machines: the pack is still
        # built, its kanji just have no parts this time
        print("kradfile: unreachable, kanji parts left out", file=sys.stderr)
        return {}
    out: dict[str, list[str]] = {}
    with gzip.open(src) as f:
        for line in f.read().decode("euc_jp", errors="replace").splitlines():
            if line.startswith("#") or " : " not in line:
                continue
            ch, rest = line.split(" : ", 1)
            out[ch.strip()] = rest.split()
    print(f"kradfile: {len(out)} kanji", file=sys.stderr)
    return out


def add_learner(cache: Path, db: sqlite3.Connection, forms: set[str]):
    """The learner's extras: each word's JLPT level (5 = N5 ... 1 = N1) and its pitch accent
    (the mora after which the pitch drops, 0 for none), for the words the dictionary has."""
    db.execute("CREATE TABLE jlpt(form TEXT NOT NULL, reading TEXT NOT NULL, level INTEGER NOT NULL)")
    rows = []
    for n in range(1, 6):
        src = cache / f"jlpt_n{n}.csv"
        if not src.exists():
            urllib.request.urlretrieve(JLPT.format(n), src)
        import csv
        with open(src, encoding="utf-8") as f:
            for r in csv.DictReader(f):
                kana, kanji = (r.get("kana") or "").strip(), (r.get("kanji") or "").strip()
                if kana:
                    rows.append((kanji or kana, kana, n))
    # a word on two lists keeps the easier level (the higher number)
    best: dict[tuple[str, str], int] = {}
    for form, kana, n in rows:
        best[(form, kana)] = max(best.get((form, kana), 0), n)
    db.executemany("INSERT INTO jlpt VALUES(?,?,?)", [(f, k, n) for (f, k), n in best.items()])
    db.execute("CREATE INDEX jlpt_form ON jlpt(form)")
    print(f"ja.db: {len(best)} JLPT words", file=sys.stderr)

    db.execute("CREATE TABLE accent(form TEXT NOT NULL, reading TEXT NOT NULL, accent TEXT NOT NULL)")
    src = cache / "accents.txt"
    if not src.exists():
        urllib.request.urlretrieve(ACCENTS, src)
    acc = []
    with open(src, encoding="utf-8") as f:
        for line in f:
            p = line.rstrip("\n").split("\t")
            if len(p) >= 3 and (p[0] in forms or p[1] in forms):
                acc.append((p[0], p[1], p[2]))
    db.executemany("INSERT INTO accent VALUES(?,?,?)", acc)
    db.execute("CREATE INDEX accent_form ON accent(form)")
    print(f"ja.db: {len(acc)} pitch accents", file=sys.stderr)


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
    # Rime records its sources' modification times in what it builds: a fixed time, so the same
    # data builds the same files and the pack isn't offered as an update when nothing changed
    for f in shared.rglob("*"):
        if f.is_file():
            os.utime(f, (946684800, 946684800))
    subprocess.run(["rime_deployer", "--build", str(user), str(shared), str(build)], check=True)
    for p in build.glob("*.userdb*"):
        shutil.rmtree(p, ignore_errors=True) if p.is_dir() else p.unlink()

    pack = args.out / "lychee-rime-ja.zip"
    pack.unlink(missing_ok=True)
    with zipfile.ZipFile(pack, "w", zipfile.ZIP_DEFLATED) as z:
        # the version: JMdict's own release date (the dictionary everything else is joined to)
        z.writestr("pack.json", json.dumps({"id": "rime_ja", "name": "Japanese dictionary",
                                            "version": jmdict_date(args.cache) or PACK_VERSION}))
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
                   "sense and converted to SQLite.\n"
                   "Kanji readings, meanings, grades, stroke counts, JLPT levels and frequencies (the "
                   "kanji table) from KANJIDIC2, and each kanji's parts from KRADFILE, both the "
                   "property of the EDRDG, used under CC BY-SA 4.0. Changes: up to four readings and "
                   "three meanings each, converted to SQLite.\n"
                   "JLPT word levels (the jlpt table) from Jonathan Waller's JLPT Resources "
                   "(http://www.tanos.co.uk/jlpt/, CC BY), as compiled by stephenmk/yomitan-jlpt-vocab "
                   "(CC BY-SA 4.0). Changes: reduced to word, reading and level.\n"
                   "Pitch accents (the accent table) from Kanjium "
                   "(https://github.com/mifunetoshiro/kanjium), CC BY-SA 4.0, with accent data by "
                   "Uros O. Changes: reduced to the words in this dictionary.\n")
    print(f"{pack}: {len(lines)} entries, {pack.stat().st_size >> 20} MB", file=sys.stderr)


if __name__ == "__main__":
    main()
