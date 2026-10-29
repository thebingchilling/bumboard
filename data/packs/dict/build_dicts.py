#!/usr/bin/env python3
"""Builds Lychee's dictionary data packs.

  build_dicts.py --out DIR [--only ko,strokes,examples]

  lychee-dict-ko.zip    Korean dictionary: meanings, Hanja and part of speech for Korean words
                        (and their conjugated forms), from Wiktionary via kaikki.org (CC BY-SA).
  lychee-strokes.zip    Stroke order and character breakdown for ~9,500 Chinese characters,
                        from Make Me a Hanzi (graphics: Arphic Public License; data: LGPL).
  lychee-examples.zip   Example sentences with English translations in Cantonese, Mandarin,
                        Japanese and Korean, from Tatoeba (CC BY 2.0 FR).

Each zip holds pack.json, data/<name>.db (SQLite, read in place by the app) and the licences.
"""

from __future__ import annotations

import argparse
import bz2
import json
import shutil
import sqlite3
import subprocess
import sys
import tarfile
import urllib.request
import zipfile
from collections import defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from english_lookup import write_english_schema  # noqa: E402

HERE = Path(__file__).resolve().parent
RIME = HERE.parent.parent / "rime"
KO_WORDS = "https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/ko/ko_50k.txt"
KAIKKI_KO = "https://kaikki.org/dictionary/Korean/kaikki.org-dictionary-Korean.jsonl"
HANZI = "https://raw.githubusercontent.com/skishore/makemeahanzi/bddc96d41bef78427ed0e034e9f7e31d71fd1b92/{}"
TATOEBA = "https://downloads.tatoeba.org/exports/per_language/{0}/{1}"
PACK_VERSION = "1"


def fetch(url: str, cache: Path, name: str | None = None) -> Path:
    dest = cache / (name or url.rsplit("/", 1)[1])
    if not dest.exists():
        print(f"downloading {url}", file=sys.stderr)
        tmp = dest.with_suffix(dest.suffix + ".part")
        with urllib.request.urlopen(url) as r, open(tmp, "wb") as f:
            while chunk := r.read(1 << 20):
                f.write(chunk)
        tmp.rename(dest)
    return dest


def new_db(path: Path, schema: str) -> sqlite3.Connection:
    path.unlink(missing_ok=True)
    db = sqlite3.connect(path)
    db.executescript("PRAGMA page_size=4096;\n" + schema)
    return db


def finish(db: sqlite3.Connection):
    db.commit()
    db.execute("VACUUM")
    db.close()


def write_pack(out: Path, file: str, pack_id: str, name: str, db: Path, licences: dict[str, str],
               extra: dict[str, Path] | None = None):
    pack = out / file
    pack.unlink(missing_ok=True)
    with zipfile.ZipFile(pack, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("pack.json", json.dumps({"id": pack_id, "name": name, "version": PACK_VERSION}))
        z.write(db, f"data/{db.name}")
        for arc, f in (extra or {}).items():
            z.write(f, arc)
        for n, text in licences.items():
            z.writestr(n, text)
    print(f"{pack}: {pack.stat().st_size >> 20} MB", file=sys.stderr)


# ---------------------------------------------------------------- Korean

KO_POS = {"noun": "noun", "verb": "verb", "adj": "adjective", "adv": "adverb", "pron": "pronoun",
          "num": "number", "particle": "particle", "intj": "interjection", "det": "determiner",
          "suffix": "suffix", "prefix": "prefix", "phrase": "phrase", "proverb": "proverb",
          "counter": "counter", "name": "name"}

# word lookups (Japanese and Korean packs): every form a candidate may take -> its entry
WORD_SCHEMA = """
CREATE TABLE word(form TEXT NOT NULL, lemma TEXT NOT NULL, reading TEXT, hanja TEXT, pos TEXT,
                  gloss TEXT NOT NULL, rank INTEGER NOT NULL);
"""


def build_ko(out: Path, cache: Path, work: Path):
    src = fetch(KAIKKI_KO, cache)
    db_path = work / "ko.db"
    db = new_db(db_path, WORD_SCHEMA)
    rows = []
    english = []
    rank = 0
    with open(src, encoding="utf-8") as f:
        for line in f:
            e = json.loads(line)
            word, pos = e.get("word", ""), e.get("pos", "")
            if not word or pos in ("character", "syllable", "romanization", "symbol") or e.get("lang_code") not in (None, "ko"):
                continue
            if not any("가" <= c <= "힣" for c in word):
                continue
            glosses = []
            for s in e.get("senses", []):
                g = "; ".join(s.get("glosses", [])[:1]).strip()
                if g and "form of" not in g and g not in glosses:
                    glosses.append(g)
            if not glosses:
                continue
            forms = e.get("forms", [])
            roman = next((x["form"] for x in forms if "romanization" in x.get("tags", [])), None)
            hanja = next((x["form"] for x in forms if "hanja" in x.get("tags", [])), None)
            gloss = "\n".join(glosses[:8])
            rank += 1
            rows.append((word, word, roman, hanja, KO_POS.get(pos, pos), gloss, rank))
            # the English lookup leaves out vulgar, offensive and old meanings
            clean = []
            for sense in e.get("senses", []):
                if BAD_TAGS & set(sense.get("tags", [])):
                    continue
                g = "; ".join(sense.get("glosses", [])[:1]).strip()
                if g and "form of" not in g and g not in clean:
                    clean.append(g)
            if clean and pos not in ("suffix", "prefix", "name", "proverb"):
                conjugated = [x.get("form", "") for x in e.get("forms", []) if x.get("source") == "conjugation"]
                english.append((word, clean[:8], 1.0, conjugated))
            # conjugated forms find their dictionary form (먹어요 -> 먹다)
            seen = {word}
            for x in forms:
                form = x.get("form", "")
                if x.get("source") == "conjugation" and form not in seen and all("가" <= c <= "힣" for c in form):
                    seen.add(form)
                    rows.append((form, word, x.get("roman"), hanja, KO_POS.get(pos, pos), gloss, rank))
    db.executemany("INSERT INTO word VALUES(?,?,?,?,?,?,?)", rows)
    db.execute("CREATE INDEX word_form ON word(form)")
    finish(db)
    rime = english_to_korean(english, cache, work)
    write_pack(out, "lychee-dict-ko.zip", "dict_ko", "Korean dictionary", db_path, extra=rime, licences={
        "LICENSE.txt": "Korean dictionary data from Wiktionary (https://en.wiktionary.org), extracted by "
                       "kaikki.org (Tatu Ylonen, Wiktextract). Licensed CC BY-SA 4.0 and GFDL; this pack "
                       "is shared under CC BY-SA 4.0: https://creativecommons.org/licenses/by-sa/4.0/\n"
                       "Changes: entries were reduced to their first meanings, Hanja and conjugated forms, "
                       "and converted to SQLite.\n"
                       "The English lookup (rime/lychee_en_ko) is made from the same meanings, ordered by "
                       "FrequencyWords (Hermit Dave, from OpenSubtitles 2018), CC BY-SA 4.0.\n",
    })


BAD_TAGS = {"vulgar", "offensive", "derogatory", "slur", "obsolete", "archaic", "dialectal", "rare"}


def english_to_korean(english, cache: Path, work: Path) -> dict[str, Path]:
    """The English -> Korean Rime schema (영 on the Korean keyboard), compiled: zip path -> file.
    How common a word is comes from FrequencyWords (OpenSubtitles 2018, CC BY-SA 4.0), with
    its conjugated forms counted too (subtitles say 먹어, rarely 먹다)."""
    counts: dict[str, int] = {}
    for line in fetch(KO_WORDS, cache).read_text(encoding="utf-8").splitlines():
        parts = line.split()
        if len(parts) == 2 and parts[1].isdigit():
            counts[parts[0]] = int(parts[1])
    freq: dict[str, int] = {}
    for word, _, _, conjugated in english:
        freq[word] = max(freq.get(word, 0), counts.get(word, 0) + sum(counts.get(f, 0) for f in set(conjugated)))
    english = [(w, g, s) for w, g, s, _ in english]
    shared = work / "rime-ko"
    shutil.rmtree(shared, ignore_errors=True)
    shared.mkdir(parents=True)
    shutil.copy(RIME / "lychee_common.yaml", shared)
    (shared / "default.yaml").write_text('config_version: "lychee.ko.1"\nschema_list:\n  - schema: lychee_en_ko\n',
                                         encoding="utf-8")
    n = write_english_schema(shared, "lychee_en_ko", "영→한", "Korean", english, lambda t: freq.get(t, 0) / 10)
    print(f"lychee_en_ko: {n} entries", file=sys.stderr)
    user = work / "rime-ko-user"
    shutil.rmtree(user, ignore_errors=True)
    user.mkdir()
    build = shared / "build"
    subprocess.run(["rime_deployer", "--build", str(user), str(shared), str(build)], check=True)
    files = {f"rime/{n}": shared / n for n in ("lychee_en_ko.schema.yaml", "lychee_en_ko.dict.yaml")}
    for f in sorted(build.iterdir()):
        if f.name.startswith("lychee_en_ko."):
            files[f"rime/build/{f.name}"] = f
    return files


# ---------------------------------------------------------------- stroke order

STROKES_SCHEMA = """
CREATE TABLE char(ch TEXT PRIMARY KEY, strokes TEXT NOT NULL, medians TEXT NOT NULL,
                  decomposition TEXT, radical TEXT, definition TEXT, pinyin TEXT, etymology TEXT)
                  WITHOUT ROWID;
"""


def build_strokes(out: Path, cache: Path, work: Path):
    graphics = fetch(HANZI.format("graphics.txt"), cache, "hanzi-graphics.txt")
    dictionary = fetch(HANZI.format("dictionary.txt"), cache, "hanzi-dictionary.txt")
    apl = fetch(HANZI.format("APL/english/ARPHICPL.TXT"), cache, "hanzi-APL.txt")
    copying = fetch(HANZI.format("COPYING"), cache, "hanzi-COPYING.txt")
    lgpl = fetch(HANZI.format("LGPL"), cache, "hanzi-LGPL.txt")
    info = {}
    for line in dictionary.read_text(encoding="utf-8").splitlines():
        if line.strip():
            d = json.loads(line)
            info[d["character"]] = d
    db_path = work / "strokes.db"
    db = new_db(db_path, STROKES_SCHEMA)
    rows = []
    for line in graphics.read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        g = json.loads(line)
        d = info.get(g["character"], {})
        ety = d.get("etymology")
        rows.append((g["character"], json.dumps(g["strokes"], separators=(",", ":")),
                     json.dumps(g["medians"], separators=(",", ":")), d.get("decomposition"),
                     d.get("radical"), d.get("definition"), ", ".join(d.get("pinyin", [])) or None,
                     json.dumps(ety, ensure_ascii=False) if ety else None))
    db.executemany("INSERT INTO char VALUES(?,?,?,?,?,?,?,?)", rows)
    finish(db)
    print(f"strokes: {len(rows)} characters", file=sys.stderr)
    write_pack(out, "lychee-strokes.zip", "strokes_zh", "Stroke order", db_path, {
        "LICENSE.txt": "Stroke order and character data from Make Me a Hanzi by Shaunak Kishore "
                       "(https://github.com/skishore/makemeahanzi).\n"
                       "graphics (stroke shapes): derived from fonts by Arphic Technology, under the "
                       "Arphic Public License (APL.txt).\n"
                       "dictionary (breakdown, radical, etymology): GNU LGPL 3 or later (LGPL.txt), "
                       "see COPYING.txt.\n"
                       "Changes: converted to SQLite; nothing else.\n",
        "APL.txt": apl.read_text(encoding="utf-8", errors="replace"),
        "COPYING.txt": copying.read_text(encoding="utf-8", errors="replace"),
        "LGPL.txt": lgpl.read_text(encoding="utf-8", errors="replace"),
    })


# ---------------------------------------------------------------- example sentences

EXAMPLES_SCHEMA = """
CREATE TABLE ex(lang TEXT NOT NULL, text TEXT NOT NULL, en TEXT NOT NULL);
"""
EXAMPLE_LANGS = {"yue": "yue", "cmn": "cmn", "jpn": "ja", "kor": "ko"}


def tatoeba_sentences(lang: str, cache: Path) -> dict[str, str]:
    path = fetch(TATOEBA.format(lang, f"{lang}_sentences.tsv.bz2"), cache)
    out = {}
    with bz2.open(path, "rt", encoding="utf-8") as f:
        for line in f:
            p = line.rstrip("\n").split("\t")
            if len(p) >= 3:
                out[p[0]] = p[2]
    return out


def build_examples(out: Path, cache: Path, work: Path, per_lang: int = 60000):
    english = tatoeba_sentences("eng", cache)
    db_path = work / "examples.db"
    db = new_db(db_path, EXAMPLES_SCHEMA)
    for lang, code in EXAMPLE_LANGS.items():
        sentences = tatoeba_sentences(lang, cache)
        links = fetch(TATOEBA.format(lang, f"{lang}-eng_links.tsv.bz2"), cache)
        pairs: dict[str, str] = {}
        with bz2.open(links, "rt", encoding="utf-8") as f:
            for line in f:
                a, b = line.split()[:2]
                if a in sentences and b in english and a not in pairs:
                    pairs[a] = english[b]
        # short sentences first: they make the best examples
        rows = sorted(((code, sentences[a], en) for a, en in pairs.items() if len(sentences[a]) <= 40),
                      key=lambda r: (len(r[1]), r[1]))[:per_lang]
        db.executemany("INSERT INTO ex VALUES(?,?,?)", rows)
        print(f"examples {code}: {len(rows)}", file=sys.stderr)
    db.execute("CREATE INDEX ex_lang ON ex(lang)")
    finish(db)
    write_pack(out, "lychee-examples.zip", "examples", "Example sentences", db_path, {
        "LICENSE.txt": "Example sentences and their English translations from Tatoeba "
                       "(https://tatoeba.org), licensed CC BY 2.0 FR "
                       "(https://creativecommons.org/licenses/by/2.0/fr/). Each sentence was written "
                       "by a Tatoeba contributor; see tatoeba.org for authors.\n"
                       "Changes: sentences up to 40 characters with an English translation were kept, "
                       "and converted to SQLite.\n",
    })


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--cache", type=Path, default=HERE / ".cache")
    ap.add_argument("--only", default="ko,strokes,examples")
    args = ap.parse_args()
    args.cache.mkdir(parents=True, exist_ok=True)
    work = args.out / "work-dicts"
    work.mkdir(parents=True, exist_ok=True)
    only = set(args.only.split(","))
    if "ko" in only:
        build_ko(args.out, args.cache, work)
    if "strokes" in only:
        build_strokes(args.out, args.cache, work)
    if "examples" in only:
        build_examples(args.out, args.cache, work)


if __name__ == "__main__":
    main()
