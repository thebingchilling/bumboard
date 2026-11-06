#!/usr/bin/env python3
"""Builds Lychee's dictionary data packs.

  build_dicts.py --out DIR [--only ko,strokes,examples]

  lychee-dict-ko.zip    Korean dictionary: meanings, Hanja and part of speech for Korean words
                        (and their conjugated forms), from Wiktionary via kaikki.org (CC BY-SA).
  lychee-dict-en.zip    English dictionary: definitions, examples and pronunciation for English
                        words, from Open English WordNet (CC BY 4.0).
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

sys.path.insert(0, str(Path(__file__).resolve().parent))
import strokes_local  # noqa: E402

HERE = Path(__file__).resolve().parent
RIME = HERE.parent.parent / "app" / "data" / "rime"
KO_WORDS = "https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/ko/ko_50k.txt"
KAIKKI_KO = "https://kaikki.org/dictionary/Korean/kaikki.org-dictionary-Korean.jsonl"
HANZI = "https://raw.githubusercontent.com/skishore/makemeahanzi/bddc96d41bef78427ed0e034e9f7e31d71fd1b92/{}"
TATOEBA = "https://downloads.tatoeba.org/exports/per_language/{0}/{1}"
OEWN = "https://github.com/globalwordnet/english-wordnet/releases/download/2025-edition/english-wordnet-2025.xml.gz"
# English Wiktionary (kaikki.org, CC BY-SA): the words WordNet leaves out (the, I, of, with,
# because, don't ...); streamed, never cached (it is half a gigabyte)
KAIKKI_EN = "https://kaikki.org/dictionary/English/kaikki.org-dictionary-English.jsonl.gz"
EN_WORDS = "https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/en/en_50k.txt"
KANJIVG = "https://github.com/KanjiVG/kanjivg/releases/download/r20260714/kanjivg-20260714-main.zip"
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


# ---------------------------------------------------------------- English

EN_POS = {"n": "noun", "v": "verb", "a": "adjective", "s": "adjective", "r": "adverb"}


def build_en(out: Path, cache: Path, work: Path):
    """English definitions (the word card on the English keyboards) from Open English WordNet:
    each word's senses, most common first, with an example and its pronunciation (IPA);
    irregular forms (went, mice) find their word. Ordered by FrequencyWords."""
    import gzip
    import xml.etree.ElementTree as ET
    src = fetch(OEWN, cache, "english-wordnet.xml.gz")
    order: dict[str, int] = {}
    for i, line in enumerate(fetch(EN_WORDS, cache, "en_50k.txt").read_text(encoding="utf-8").splitlines()):
        w = line.split(" ")[0]
        order.setdefault(w, i)
    entries = []  # (lemma, pos, ipa, forms, synsets)
    synsets: dict[str, tuple[str, str | None]] = {}
    with gzip.open(src) as f:
        for _, e in ET.iterparse(f, events=("end",)):
            if e.tag == "LexicalEntry":
                lemma_el = e.find("Lemma")
                lemma = lemma_el.get("writtenForm", "")
                if lemma and " " not in lemma:
                    pron = {}
                    for p in lemma_el.findall("Pronunciation"):
                        pron.setdefault(p.get("variety", ""), p.text or "")
                    ipa = None
                    if pron:
                        if "GB" in pron and "US" in pron and pron["GB"] != pron["US"]:
                            ipa = f"/{pron['GB']}/ (UK) · /{pron['US']}/ (US)"
                        else:
                            ipa = "/" + next(iter(pron.values())) + "/"
                    forms = [x.get("writtenForm") for x in e.findall("Form") if x.get("writtenForm")]
                    senses = [x.get("synset") for x in e.findall("Sense")]
                    entries.append((lemma, EN_POS.get(lemma_el.get("partOfSpeech"), None), ipa, forms, senses))
                e.clear()
            elif e.tag == "Synset":
                definition = e.findtext("Definition") or ""
                example = e.findtext("Example")
                synsets[e.get("id")] = (definition, example)
                e.clear()
    db_path = work / "en.db"
    db = new_db(db_path, WORD_SCHEMA)
    rows = []
    covered: dict[str, set] = {}  # form -> parts of speech WordNet has for it
    for lemma, pos, ipa, forms, senses in entries:
        glosses = []
        for sid in senses[:8]:
            d, ex = synsets.get(sid, ("", None))
            if d:
                glosses.append(f"{d} — “{ex}”" if ex else d)
        if not glosses:
            continue
        key = lemma.lower()
        rank = order.get(key, 1_000_000)
        gloss = "\n".join(glosses)
        rows.append((key, lemma, ipa, None, pos, gloss, rank))
        covered.setdefault(key, set()).add(pos)
        for form in forms:
            if form.lower() != key:
                rows.append((form.lower(), lemma, ipa, None, pos, gloss, rank))
                covered.setdefault(form.lower(), set())
    rows += wiktionary_en(order, covered)
    db.executemany("INSERT INTO word VALUES(?,?,?,?,?,?,?)", rows)
    db.execute("CREATE INDEX word_form ON word(form)")
    finish(db)
    print(f"en.db: {len(rows)} forms", file=sys.stderr)
    write_pack(out, "lychee-dict-en.zip", "dict_en", "English dictionary", db_path, {
        "LICENSE.txt": "English definitions from Open English WordNet (https://en-word.net), "
                       "CC BY 4.0: https://creativecommons.org/licenses/by/4.0/\n"
                       "Words WordNet lacks (the, of, because, don't ...) from English Wiktionary "
                       "(https://en.wiktionary.org), extracted by kaikki.org (Tatu Ylonen, Wiktextract), "
                       "CC BY-SA 4.0 and GFDL.\n"
                       "Word order from FrequencyWords (Hermit Dave, OpenSubtitles 2018), CC BY-SA 4.0.\n"
                       "Changes: single words only, up to eight senses each, converted to SQLite.\n",
    })


WIKT_POS = {
    "pron": "pronoun", "prep": "preposition", "conj": "conjunction", "det": "determiner",
    "article": "article", "intj": "interjection", "particle": "particle", "contraction": "contraction",
    "num": "number", "noun": "noun", "verb": "verb", "adj": "adjective", "adv": "adverb",
    "name": "proper noun", "prep_phrase": "phrase", "phrase": "phrase", "postp": "postposition",
}
WIKT_SKIP_TAGS = {"obsolete", "archaic", "rare", "dated", "nonstandard", "misspelling", "dialectal"}


def wiktionary_en(order: dict[str, int], covered: dict[str, set]) -> list[tuple]:
    """English Wiktionary entries for the words people type (FrequencyWords' 50,000) that
    WordNet lacks, or lacks in that part of speech (function words: the, of, because; like as
    a preposition; contractions: don't, I'm)."""
    import gzip
    import json
    wanted = {w for w in order if w}
    wanted |= {w.replace("'", "’") for w in wanted if "'" in w}
    rows = []
    seen: set[tuple[str, str]] = set()
    print(f"streaming {KAIKKI_EN}", file=sys.stderr)
    with urllib.request.urlopen(KAIKKI_EN) as r, gzip.GzipFile(fileobj=r) as f:
        for raw in f:
            # most lines are words no one types: skip them before parsing the JSON
            head = raw[:200]
            i = head.find(b'"word": "')
            if i >= 0:
                j = head.find(b'"', i + 9)
                if j > 0 and head[i + 9:j].decode("utf-8", "ignore").lower() not in wanted:
                    continue
            e = json.loads(raw)
            if e.get("lang_code") != "en":
                continue
            word = e.get("word", "")
            key = word.lower().replace("’", "'")
            if key not in order:
                continue
            pos = WIKT_POS.get(e.get("pos", ""))
            if pos is None:
                continue
            # WordNet's own entry is better where it has one in this part of speech
            have = covered.get(key)
            if have is not None and (pos in have or (pos == "proper noun" and have)):
                continue
            if (key, pos) in seen:
                continue
            glosses = []
            for sense in e.get("senses", []):
                if WIKT_SKIP_TAGS & set(sense.get("tags", [])):
                    continue
                g = (sense.get("glosses") or [""])[-1].strip()
                if not g or g in glosses:
                    continue
                ex = next((x.get("text") for x in sense.get("examples", [])
                           if x.get("text") and len(x["text"]) < 120), None)
                glosses.append(f"{g} — “{ex}”" if ex else g)
                if len(glosses) >= 6:
                    break
            if not glosses:
                continue
            uk = us = other = None
            for snd in e.get("sounds", []):
                ipa = snd.get("ipa")
                if not ipa:
                    continue
                tags = set(snd.get("tags", []))
                if uk is None and ({"UK", "Received-Pronunciation"} & tags):
                    uk = ipa
                elif us is None and ({"US", "General-American"} & tags):
                    us = ipa
                elif other is None:
                    other = ipa
            if uk and us and uk != us:
                ipa = f"{uk} (UK) · {us} (US)"
            else:
                ipa = uk or us or other
            seen.add((key, pos))
            rows.append((key, word, ipa, None, pos, "\n".join(glosses), order[key]))
    print(f"wiktionary: {len(rows)} entries WordNet lacks", file=sys.stderr)
    return rows


# ---------------------------------------------------------------- stroke order

STROKES_SCHEMA = """
CREATE TABLE char(ch TEXT PRIMARY KEY, strokes TEXT NOT NULL, medians TEXT NOT NULL,
                  decomposition TEXT, radical TEXT, definition TEXT, pinyin TEXT, etymology TEXT)
                  WITHOUT ROWID;
CREATE TABLE char_ja(ch TEXT PRIMARY KEY, medians TEXT NOT NULL) WITHOUT ROWID;
CREATE TABLE char_ko(ch TEXT PRIMARY KEY, medians TEXT NOT NULL, decomposition TEXT) WITHOUT ROWID;
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
    # Japanese (kana and kanji, KanjiVG) and Korean (every syllable): centre lines only
    import zipfile as _zip
    kvg_zip = fetch(KANJIVG, cache, "kanjivg-main.zip")
    kvg = work / "kanjivg"
    shutil.rmtree(kvg, ignore_errors=True)
    with _zip.ZipFile(kvg_zip) as z:
        z.extractall(kvg)
    ja = [(c, json.dumps(m, separators=(",", ":"))) for c, m, _ in strokes_local.japanese(kvg / "kanji")]
    db.executemany("INSERT INTO char_ja VALUES(?,?)", ja)
    ko = [(c, json.dumps(m, separators=(",", ":")), d) for c, m, d in strokes_local.korean()]
    db.executemany("INSERT INTO char_ko VALUES(?,?,?)", ko)
    finish(db)
    print(f"strokes: {len(rows)} characters, {len(ja)} Japanese, {len(ko)} Korean", file=sys.stderr)
    write_pack(out, "lychee-strokes.zip", "strokes_zh", "Stroke order", db_path, {
        "LICENSE.txt": "Stroke order and character data from Make Me a Hanzi by Shaunak Kishore "
                       "(https://github.com/skishore/makemeahanzi).\n"
                       "graphics (stroke shapes): derived from fonts by Arphic Technology, under the "
                       "Arphic Public License (APL.txt).\n"
                       "dictionary (breakdown, radical, etymology): GNU LGPL 3 or later (LGPL.txt), "
                       "see COPYING.txt.\n"
                       "Changes: converted to SQLite; nothing else.\n"
                       "Japanese stroke order (char_ja): from KanjiVG by Ulrich Apel "
                       "(https://kanjivg.tagaini.net), CC BY-SA 3.0; reduced to each stroke's centre line "
                       "and shared under the same licence.\n"
                       "Korean stroke order (char_ko): made by Lychee from each letter's standard stroke "
                       "order.\n",
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
    ap.add_argument("--only", default="ko,en,strokes,examples")
    args = ap.parse_args()
    args.cache.mkdir(parents=True, exist_ok=True)
    work = args.out / "work-dicts"
    work.mkdir(parents=True, exist_ok=True)
    only = set(args.only.split(","))
    if "ko" in only:
        build_ko(args.out, args.cache, work)
    if "en" in only:
        build_en(args.out, args.cache, work)
    if "strokes" in only:
        build_strokes(args.out, args.cache, work)
    if "examples" in only:
        build_examples(args.out, args.cache, work)


if __name__ == "__main__":
    main()
