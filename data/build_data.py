#!/usr/bin/env python3
"""Build Lychee's dictionary data.

Outputs (in --out, default lychee/data/out; Gradle packages out/assets as APK assets):
  assets/rime/        Rime schemas + dictionaries, precompiled into assets/rime/build;
                      STAMP changes whenever the data does (the app re-installs it then)
  assets/lychee/readings.db
                      readings + meanings for every candidate (packaged as an APK asset)
  assets/lychee/data-licenses.txt

Every input is pinned in sources.json. Needs python3, the `opencc` pip package and
rime_deployer (librime-bin) unless --no-compile is given.
"""

from __future__ import annotations

import argparse
import csv
import gzip
import hashlib
import io
import json
import math
import re
import shutil
import sqlite3
import subprocess
import sys
import unicodedata
import urllib.request
import zipfile
from collections import defaultdict
from pathlib import Path

HERE = Path(__file__).resolve().parent
DATA_VERSION = 1

# ---------------------------------------------------------------- sources


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def fetch_sources(cache: Path) -> dict[str, Path]:
    spec = json.loads((HERE / "sources.json").read_text())
    cache.mkdir(parents=True, exist_ok=True)
    paths: dict[str, Path] = {}
    for name, s in spec.items():
        if name.startswith("_"):
            continue
        if "git" in s:
            dest = cache / f"{name}-{s['commit'][:12]}"
            if not (dest / ".done").exists():
                shutil.rmtree(dest, ignore_errors=True)
                dest.mkdir(parents=True)
                run(["git", "init", "-q"], cwd=dest)
                run(["git", "fetch", "-q", "--depth", "1", s["git"], s["commit"]], cwd=dest)
                run(["git", "checkout", "-q", "FETCH_HEAD"], cwd=dest)
                (dest / ".done").touch()
            paths[name] = dest
        elif "url" in s and not s.get("sha256"):
            # not pinned yet: fetched once, and its sha256 printed so it can be pinned
            dest = cache / Path(s["url"]).name
            if not dest.exists():
                print(f"downloading {s['url']}", file=sys.stderr)
                tmp = dest.with_suffix(".part")
                with urllib.request.urlopen(s["url"]) as r, open(tmp, "wb") as f:
                    shutil.copyfileobj(r, f)
                tmp.rename(dest)
            print(f"warning: {name} is not pinned; sha256 {sha256(dest)}", file=sys.stderr)
            paths[name] = dest
        elif "url" in s:
            dest = cache / Path(s["url"]).name
            if not dest.exists() or sha256(dest) != s["sha256"]:
                print(f"downloading {s['url']}", file=sys.stderr)
                tmp = dest.with_suffix(".part")
                with urllib.request.urlopen(s["url"]) as r, open(tmp, "wb") as f:
                    shutil.copyfileobj(r, f)
                tmp.rename(dest)
            if sha256(dest) != s["sha256"]:
                sys.exit(f"sha256 mismatch for {name}: {dest}")
            paths[name] = dest
        else:
            dest = HERE / s["file"]
            if sha256(dest) != s["sha256"]:
                sys.exit(f"sha256 mismatch for {name}: {dest}")
            paths[name] = dest
    return paths


def run(cmd, cwd=None):
    subprocess.run(cmd, cwd=cwd, check=True)


def zip_text(path: Path, member_suffix: str) -> str:
    with zipfile.ZipFile(path) as z:
        for n in z.namelist():
            if n.endswith(member_suffix):
                return z.read(n).decode("utf-8")
    sys.exit(f"{member_suffix} not found in {path}")


def dict_body(path: Path):
    """Yield the tab-separated rows after the YAML header of a Rime dictionary."""
    in_body = False
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if not in_body:
                if line == "...":
                    in_body = True
                continue
            if not line or line.startswith("#"):
                continue
            yield line.split("\t")


# ---------------------------------------------------------------- romanisation helpers

TONE_MARKS = {
    "ā": ("a", 1), "á": ("a", 2), "ǎ": ("a", 3), "à": ("a", 4),
    "ē": ("e", 1), "é": ("e", 2), "ě": ("e", 3), "è": ("e", 4),
    "ī": ("i", 1), "í": ("i", 2), "ǐ": ("i", 3), "ì": ("i", 4),
    "ō": ("o", 1), "ó": ("o", 2), "ǒ": ("o", 3), "ò": ("o", 4),
    "ū": ("u", 1), "ú": ("u", 2), "ǔ": ("u", 3), "ù": ("u", 4),
    "ǖ": ("ü", 1), "ǘ": ("ü", 2), "ǚ": ("ü", 3), "ǜ": ("ü", 4),
    "ń": ("n", 2), "ň": ("n", 3), "ǹ": ("n", 4), "ḿ": ("m", 2),
}

PINYIN_SYL = re.compile(r"^[a-zü]+[1-5]$")
JYUT_SYL = re.compile(r"^[a-z]+[1-6]$")


def marked_to_numbered(s: str) -> str | None:
    """'xíng' -> 'xing2'; 'fa' -> 'fa5'."""
    s = unicodedata.normalize("NFC", s.strip().lower())
    tone = 5
    out = []
    for ch in s:
        if ch in TONE_MARKS:
            base, tone = TONE_MARKS[ch]
            out.append(base)
        else:
            out.append(ch)
    syl = "".join(out).replace("v", "ü")
    syl = f"{syl}{tone}"
    return syl if PINYIN_SYL.match(syl) else None


def norm_pinyin(raw: str) -> list[str] | None:
    """CEDICT pinyin '[Bei3 jing1]' -> ['bei3', 'jing1'], None if not all syllables."""
    out = []
    for syl in raw.lower().replace("u:", "ü").split():
        if syl == "r5":
            syl = "er5"
        if not PINYIN_SYL.match(syl):
            return None
        out.append(syl)
    return out or None


def norm_jyutping(raw: str) -> list[str] | None:
    raw = raw.strip().lower()
    if not raw:
        return None
    sylls = raw.split() if " " in raw else re.findall(r"[a-z]+[1-6]", raw)
    if "".join(sylls) != raw.replace(" ", "") or not all(JYUT_SYL.match(s) for s in sylls):
        return None
    return sylls


def pinyin_code(sylls: list[str]) -> str:
    """Rime code for Mandarin: ü typed as v, with the tone number (the schema derives the
    toneless spelling, so tones are optional: ni3hao3 narrows what nihao finds)."""
    return " ".join(s.replace("ü", "v") for s in sylls)


def is_han(ch: str) -> bool:
    cp = ord(ch)
    return (
        0x3400 <= cp <= 0x4DBF or 0x4E00 <= cp <= 0x9FFF or 0xF900 <= cp <= 0xFAFF
        or 0x20000 <= cp <= 0x323AF or cp == 0x3007
    )


def all_han(s: str) -> bool:
    return bool(s) and all(is_han(c) for c in s)


# ---------------------------------------------------------------- parsers

CEDICT_LINE = re.compile(r"^(\S+) (\S+) \[([^\]]*)\](?: \{([^}]*)\})?(?: /(.*)/)?\s*$")


def parse_cedict_like(text: str):
    """Yield (trad, simp, pinyin_raw, jyutping_raw|None, [glosses])."""
    for line in text.splitlines():
        if not line or line.startswith("#"):
            continue
        m = CEDICT_LINE.match(line)
        if not m:
            continue
        trad, simp, py, jp, gl = m.groups()
        glosses = [g.strip() for g in gl.split("/")] if gl else []
        yield trad, simp, py, jp, [g for g in glosses if g]


def parse_unihan(zip_path: Path):
    readings = defaultdict(dict)
    for line in zip_text(zip_path, "Unihan_Readings.txt").splitlines():
        if not line.startswith("U+"):
            continue
        cp, field, value = line.split("\t", 2)
        readings[chr(int(cp[2:], 16))][field] = value
    variants = defaultdict(dict)
    for line in zip_text(zip_path, "Unihan_Variants.txt").splitlines():
        if not line.startswith("U+"):
            continue
        cp, field, value = line.split("\t", 2)
        if field in ("kSimplifiedVariant", "kTraditionalVariant"):
            variants[chr(int(cp[2:], 16))][field] = [
                chr(int(v.split("<")[0][2:], 16)) for v in value.split()
            ]
    return readings, variants


def unihan_mandarin(fields: dict) -> list[tuple[str, float]]:
    """Ordered (numbered pinyin, share) readings for a character."""
    counts: dict[str, float] = {}
    if "kHanyuPinlu" in fields:
        for m in re.finditer(r"(\S+?)\((\d+)\)", fields["kHanyuPinlu"]):
            s = marked_to_numbered(m.group(1))
            if s:
                counts[s] = counts.get(s, 0) + int(m.group(2))
    order = []
    for key in ("kMandarin", "kTGHZ2013", "kXHC1983", "kHanyuPinyin"):
        if key not in fields:
            continue
        for tok in re.split(r"[\s,]+", fields[key]):
            tok = tok.split(":")[-1]
            s = marked_to_numbered(tok) if tok else None
            if s and s not in order:
                order.append(s)
    total = sum(counts.values())
    out = []
    for s in sorted(order, key=lambda r: -counts.get(r, 0)):
        share = counts[s] / total if total and s in counts else 0.0
        out.append((s, share))
    for s in counts:
        if s not in order:
            out.append((s, counts[s] / total))
    return out


# ---------------------------------------------------------------- readings database


class Readings:
    """Collects headwords, readings and senses, then writes readings.db."""

    def __init__(self):
        self.ids: dict[tuple[str, str], int] = {}
        self.hw: list[list] = []  # [trad, simp, freq]
        self.readings: dict[tuple[int, str], dict[str, float]] = defaultdict(dict)
        self.senses: list[tuple] = []
        self.sense_keys: set[tuple] = set()
        self.with_senses: set[int] = set()
        # per headword: hsk, band, reg, cl, pos (see Readings.write)
        self.info: dict[int, dict] = defaultdict(dict)

    def headword(self, trad: str, simp: str) -> int:
        key = (trad, simp)
        hid = self.ids.get(key)
        if hid is None:
            hid = len(self.hw) + 1
            self.ids[key] = hid
            self.hw.append([trad, simp, 0])
        return hid

    def add_reading(self, hid: int, lang: str, sylls: list[str], score: float):
        r = " ".join(sylls)
        cur = self.readings[(hid, lang)]
        cur[r] = max(cur.get(r, -1e9), score)

    def add_sense(self, hid, lang, reading, gloss, src, pos="", reg="", label="",
                  written="", vern="", colloc="", alt=""):
        """alt: the reading in the other language, when known (CEDICT pinyin -> jyutping)."""
        key = (hid, lang, gloss.lower())
        if key in self.sense_keys:
            return
        self.sense_keys.add(key)
        self.with_senses.add(hid)
        self.senses.append((hid, lang, reading, len(self.senses), gloss, pos, reg, label,
                            written, vern, colloc, src, alt))

    def write(self, path: Path, meta: dict):
        path.unlink(missing_ok=True)
        db = sqlite3.connect(path)
        db.executescript(
            """
            PRAGMA page_size=4096;
            CREATE TABLE meta(k TEXT PRIMARY KEY, v TEXT) WITHOUT ROWID;
            CREATE TABLE hw(id INTEGER PRIMARY KEY, trad TEXT NOT NULL, simp TEXT NOT NULL,
                            freq INTEGER NOT NULL);
            CREATE TABLE form(form TEXT NOT NULL, hw INTEGER NOT NULL,
                              PRIMARY KEY(form, hw)) WITHOUT ROWID;
            CREATE TABLE reading(hw INTEGER NOT NULL, lang TEXT NOT NULL, rank INTEGER NOT NULL,
                                 r TEXT NOT NULL, PRIMARY KEY(hw, lang, rank)) WITHOUT ROWID;
            CREATE TABLE sense(hw INTEGER NOT NULL, ord INTEGER NOT NULL, lang TEXT NOT NULL,
                               r TEXT, gloss TEXT NOT NULL, pos TEXT, reg TEXT, label TEXT,
                               written TEXT, vern TEXT, colloc TEXT, src TEXT, alt TEXT,
                               PRIMARY KEY(hw, ord)) WITHOUT ROWID;
            -- hsk: HSK 2.0 level 1-6 (Mandarin); band: 1 common, 2 everyday, 3 advanced
            -- (Cantonese frequency); reg: 'col' colloquial / 'wri' written Cantonese;
            -- cl: measure words as in CEDICT ('個|个[ge4],隻|只[zhi1]'); pos: jieba tag
            CREATE TABLE info(hw INTEGER PRIMARY KEY, hsk INTEGER, band INTEGER, reg TEXT,
                              cl TEXT, pos TEXT);
            """
        )
        db.executemany("INSERT INTO meta VALUES(?,?)", sorted(meta.items()))
        db.executemany(
            "INSERT INTO hw VALUES(?,?,?,?)",
            ((i + 1, t, s, int(f)) for i, (t, s, f) in enumerate(self.hw)),
        )
        forms = set()
        for i, (t, s, _) in enumerate(self.hw):
            forms.add((t, i + 1))
            forms.add((s, i + 1))
        db.executemany("INSERT INTO form VALUES(?,?)", sorted(forms))
        rows = []
        for (hid, lang), rs in self.readings.items():
            for rank, (r, _) in enumerate(sorted(rs.items(), key=lambda kv: -kv[1])):
                rows.append((hid, lang, rank, r))
        db.executemany("INSERT INTO reading VALUES(?,?,?,?)", rows)
        # renumber senses per headword, keeping insertion order
        per_hw = defaultdict(int)
        out = []
        for s in sorted(self.senses, key=lambda s: (s[0], s[3])):
            hid = s[0]
            out.append((hid, per_hw[hid], s[1], s[2] or None, s[4], s[5] or None, s[6] or None,
                        s[7] or None, s[8] or None, s[9] or None, s[10] or None, s[11],
                        s[12] or None))
            per_hw[hid] += 1
        db.executemany("INSERT INTO sense VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)", out)
        db.executemany(
            "INSERT INTO info VALUES(?,?,?,?,?,?)",
            ((hid, i.get("hsk"), i.get("band"), i.get("reg"), i.get("cl"), i.get("pos"))
             for hid, i in sorted(self.info.items()) if i),
        )
        db.commit()
        db.execute("VACUUM")
        db.close()


# ---------------------------------------------------------------- word facts


def load_hsk(repo: Path) -> dict[str, int]:
    """Simplified and Traditional word -> its HSK 2.0 level (the lowest, if listed twice)."""
    levels: dict[str, int] = {}
    for level in range(1, 7):
        for e in json.loads((repo / "wordlists" / "exclusive" / "old" / f"{level}.json").read_text()):
            forms = {e["simplified"]} | {f["traditional"] for f in e.get("forms", [])}
            for w in forms:
                levels[w] = min(levels.get(w, 9), level)
    return levels


def frequency_bands(freq: dict[str, int], common: int = 3000, everyday: int = 12000) -> dict[str, int]:
    """Cantonese has no official word levels: the most frequent words are band 1 (common),
    the next band 2 (everyday), the rest band 3 (advanced)."""
    ranked = sorted((w for w, f in freq.items() if f > 0), key=lambda w: -freq[w])
    return {w: 1 if i < common else 2 if i < everyday else 3 for i, w in enumerate(ranked)}


# ---------------------------------------------------------------- English keyword index

STOP = {
    "a", "an", "the", "to", "of", "and", "or", "for", "in", "on", "at", "with", "by", "from",
    "as", "is", "be", "sb", "sth", "one", "one's", "oneself", "etc", "it", "its", "that",
    "this", "into", "up", "out", "not", "no", "so", "very", "also", "used", "kind", "type",
    "sort", "something", "someone", "somebody",
}
SKIP_GLOSS = re.compile(
    r"^(CL:|see |see also|variant of|old variant|archaic variant|ancient variant|"
    r"surname |abbr\.|abbreviation|used in|also written|also pr\.|Taiwan pr\.|erhua variant|"
    r"\(?[A-Z][a-z]+ pr\.|unofficial variant|Japanese variant|\(?coll\.\)? variant|"
    r"\(onom\.|\(bound form\)$|\(Tw\)$|radical in Chinese|Kangxi radical)",
    re.I,
)
WORD = re.compile(r"[a-z]+(?:'[a-z]+)?")


def gloss_keys(gloss: str) -> list[tuple[str, float]]:
    """English codes for one gloss, with a match score (1.0 = the whole gloss)."""
    if SKIP_GLOSS.match(gloss):
        return []
    g = re.sub(r"\([^)]*\)|\[[^\]]*\]|\{[^}]*\}", " ", gloss)
    keys: dict[str, float] = {}
    # alternatives are separated by ';' (commas usually list things inside one meaning)
    for alt in g.split(";"):
        alt = alt.strip().lower()
        alt = re.sub(r"^(to|a|an|the|be|to be)\s+", "", alt)
        words = [w.replace("'", "") for w in WORD.findall(alt)]
        if not words:
            continue
        if len(words) <= 3:
            code = "".join(words)
            keys[code] = max(keys.get(code, 0), 1.0 if len(words) == 1 else 0.9)
        content = [w for w in words if w not in STOP and len(w) > 1]
        if len(words) > 1:
            # a word inside a longer gloss: weaker the longer the gloss
            for w in content:
                keys[w] = max(keys.get(w, 0), 0.3 / len(words))
    return [(k, v) for k, v in keys.items() if 1 <= len(k) <= 24]


def build_en_dict(entries, freq_of, path: Path, name: str, per_code: int = 40):
    """entries: iterable of (text, [glosses], source_weight)."""
    best: dict[tuple[str, str], float] = {}
    old_use = re.compile(r"^\((archaic|literary|old|classical|Classical Chinese|obsolete)\)", re.I)
    for text, glosses, src_w in entries:
        f = freq_of(text)
        # an entry whose meanings are marked archaic/literary should not outrank everyday words
        if any(old_use.match(g) for g in glosses):
            src_w *= 0.15
        fscore = (1.0 + math.log10(1.0 + f)) ** 2
        idx = 0
        for gloss in glosses:
            ks = gloss_keys(gloss)
            if not ks:
                continue
            pos = 1.0 / (1.0 + 0.3 * idx)
            idx += 1
            for code, match in ks:
                score = match * pos * fscore * src_w / (1.0 + 0.15 * max(0, len(text) - 2))
                key = (code, text)
                if score > best.get(key, 0.0):
                    best[key] = score
    by_code: dict[str, list[tuple[float, str]]] = defaultdict(list)
    for (code, text), score in best.items():
        by_code[code].append((score, text))
    lines = []
    for code in sorted(by_code):
        for score, text in sorted(by_code[code], reverse=True)[:per_code]:
            lines.append(f"{text}\t{code}\t{max(1, int(score * 1000))}")
    write_dict(path, name, lines, sort="by_weight")
    return len(lines)


# ---------------------------------------------------------------- Rime output

DICT_HEADER = """# Rime dictionary — generated by lychee/data/build_data.py; do not edit.
# {note}
---
name: {name}
version: "{version}"
sort: {sort}
{extra}...
"""


def write_dict(path: Path, name: str, lines: list[str], sort: str = "by_weight",
               extra: str = "use_preset_vocabulary: false\n", note: str = ""):
    with open(path, "w", encoding="utf-8") as f:
        f.write(DICT_HEADER.format(name=name, version=DATA_VERSION, sort=sort, extra=extra,
                                   note=note or "See lychee/LICENSES.md for sources."))
        for line in lines:
            f.write(line + "\n")


def compile_rime(rime_dir: Path):
    build = rime_dir / "build"
    shutil.rmtree(build, ignore_errors=True)
    user = rime_dir.parent.parent / "rime-user"
    shutil.rmtree(user, ignore_errors=True)
    user.mkdir()
    run(["rime_deployer", "--build", str(user), str(rime_dir), str(build)])
    shutil.rmtree(user, ignore_errors=True)
    # user-specific outputs that must not ship
    for p in build.glob("*.userdb*"):
        shutil.rmtree(p, ignore_errors=True) if p.is_dir() else p.unlink()


def write_stamp(rime_dir: Path):
    h = hashlib.sha256()
    for f in sorted(p for p in rime_dir.rglob("*") if p.is_file()):
        h.update(f.relative_to(rime_dir).as_posix().encode())
        h.update(sha256(f).encode())
    (rime_dir / "STAMP").write_text(f"{DATA_VERSION}-{h.hexdigest()[:16]}\n")


# ---------------------------------------------------------------- fuzzy spellings

# Spelling rules added in front of a schema's own algebra, on the dictionary's codes (numbered
# syllables): sounds learners and many native speakers merge type the same.
FUZZY_CMN = [
    r"derive/^([zcs])h/$1/", r"derive/^([zcs])([^h])/$1h$2/",
    r"derive/^n/l/", r"derive/^l/n/",
    r"derive/([ei])n(\d?)$/$1ng$2/", r"derive/([ei])ng(\d?)$/$1n$2/",
    r"derive/([^i])an(\d?)$/$1ang$2/", r"derive/([^i])ang(\d?)$/$1an$2/",
    r"derive/^f/h/", r"derive/^h([aeiu])/f$1/",
]
FUZZY_YUE = [
    r"derive/^n(?!g)/l/", r"derive/^l/n/",
    r"derive/^ng(?=[aeiou])//", r"derive/^(?=[aeo])/ng/",
    r"derive/^([gk])w(?=o)/$1/",
    r"derive/ng(?=\d)/n/", r"derive/k(?=\d)/t/",
]
FUZZY = {"lychee_cmn": FUZZY_CMN, "lychee_yue": FUZZY_YUE, "lychee_yue_yale": FUZZY_YUE}


def write_fuzzy_schema(rime_dir: Path, schema_id: str, rules: list[str]):
    text = (HERE / "rime" / f"{schema_id}.schema.yaml").read_text(encoding="utf-8")
    fuzzy = f"{schema_id}_fuzzy"
    text = text.replace(f"schema_id: {schema_id}\n", f"schema_id: {fuzzy}\n", 1)
    text = re.sub(r"^(  prism: )\S+$", rf"\g<1>{fuzzy}", text, count=1, flags=re.M)
    if f"prism: {fuzzy}" not in text:
        text = text.replace(f"  dictionary: {dict_of(text)}\n",
                            f"  dictionary: {dict_of(text)}\n  prism: {fuzzy}\n", 1)
    added = "".join(f"    - {r}\n" for r in rules)
    text = text.replace("  algebra:\n", "  algebra:\n    # fuzzy spellings (Lychee setting)\n" + added, 1)
    assert f"schema_id: {fuzzy}" in text and f"prism: {fuzzy}" in text
    (rime_dir / f"{fuzzy}.schema.yaml").write_text(text, encoding="utf-8")


def dict_of(schema_text: str) -> str:
    return re.search(r"^  dictionary: (\S+)$", schema_text, re.M).group(1)


# ---------------------------------------------------------------- Korean

KO_INITIALS = ["g", "kk", "n", "d", "tt", "r", "m", "b", "pp", "s", "ss", "", "j", "jj",
               "ch", "k", "t", "p", "h"]
KO_VOWELS = ["a", "ae", "ya", "yae", "eo", "e", "yeo", "ye", "o", "wa", "wae", "oe", "yo",
             "u", "wo", "we", "wi", "yu", "eu", "ui", "i"]
KO_FINALS = ["", "g", "kk", "gs", "n", "nj", "nh", "d", "l", "lg", "lm", "lb", "ls", "lt",
             "lp", "lh", "m", "b", "bs", "s", "ss", "ng", "j", "ch", "k", "t", "p", "h"]


def ko_syllable(ch: str) -> str | None:
    """The Revised Romanization of one Hangul syllable, letter by letter (as spelled)."""
    n = ord(ch) - 0xAC00
    if not 0 <= n < 11172:
        return None
    return KO_INITIALS[n // 588] + KO_VOWELS[(n % 588) // 28] + KO_FINALS[n % 28]


def build_ko_dict(words_file: Path, path: Path) -> int:
    """Rime dictionary for lychee_ko: each word's syllables spelled in romanisation; the schema's
    spelling algebra adds the pronounced forms (kimchi for gimchi, gamsahamnida ...)."""
    lines, seen = [], set()
    for raw in words_file.read_text(encoding="utf-8").splitlines():
        parts = raw.split()
        if len(parts) != 2 or not parts[1].isdigit():
            continue
        word, count = parts[0], int(parts[1])
        if not 1 <= len(word) <= 8:
            continue
        sylls = [ko_syllable(c) for c in word]
        if any(s is None for s in sylls) or word in seen:
            continue
        seen.add(word)
        lines.append(f"{word}\t{' '.join(sylls)}\t{count}")
    # every syllable, so anything can be spelled out
    for n in range(11172):
        c = chr(0xAC00 + n)
        if c not in seen:
            lines.append(f"{c}\t{ko_syllable(c)}\t0")
    write_dict(path, "lychee_ko", lines,
               note="Korean words: FrequencyWords (Hermit Dave), from OpenSubtitles 2018, CC BY-SA 4.0.")
    return len(lines)


# ---------------------------------------------------------------- main


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", type=Path, default=HERE / "out")
    ap.add_argument("--cache", type=Path, default=HERE / ".cache")
    ap.add_argument("--no-compile", action="store_true", help="skip rime_deployer")
    ap.add_argument("--opencc", type=Path,
                    help="OpenCC data dir matching the app's libopencc (fcitx5-android prebuilt opencc/data)")
    args = ap.parse_args()

    import opencc  # pip install opencc

    t2s = opencc.OpenCC("t2s")
    src = fetch_sources(args.cache)
    out: Path = args.out
    rime_dir = out / "assets" / "rime"
    assets = out / "assets" / "lychee"
    shutil.rmtree(out, ignore_errors=True)
    rime_dir.mkdir(parents=True)
    assets.mkdir(parents=True)

    rc = src["rime-cantonese"]
    td = src["typeduck"]

    # -------- frequencies
    yue_freq: dict[str, int] = {}
    with open(rc / "essay-cantonese.txt", encoding="utf-8") as f:
        for line in f:
            p = line.rstrip("\n").split("\t")
            if len(p) == 2 and p[1].isdigit():
                yue_freq[p[0]] = max(yue_freq.get(p[0], 0), int(p[1]))
    cmn_freq: dict[str, int] = {}
    cmn_pos: dict[str, str] = {}
    with open(src["jieba"], encoding="utf-8") as f:
        for line in f:
            p = line.split()
            if len(p) >= 2 and p[1].isdigit():
                if int(p[1]) >= cmn_freq.get(p[0], 0) and len(p) >= 3:
                    cmn_pos[p[0]] = p[2]
                cmn_freq[p[0]] = max(cmn_freq.get(p[0], 0), int(p[1]))

    unihan, variants = parse_unihan(src["unihan"])

    R = Readings()

    # per-character reading weights, used to rank the readings of single characters
    pinlu_share: dict[str, dict[str, float]] = {
        ch: dict(unihan_mandarin(f)) for ch, f in unihan.items()
    }
    canto_pct: dict[str, dict[str, float]] = defaultdict(dict)
    for row in dict_body(rc / "jyut6ping3.chars.dict.yaml"):
        if len(row) >= 2 and JYUT_SYL.match(row[1]):
            pct = float(row[2].rstrip("%")) if len(row) > 2 and row[2].endswith("%") else 100.0
            canto_pct[row[0]][row[1]] = max(canto_pct[row[0]].get(row[1], 0), pct)

    def yue_score(base: float, text: str, sylls: list[str]) -> float:
        return base + (canto_pct[text].get(sylls[0], 0) / 100 if len(text) == 1 else 0)

    def cmn_score(base: float, text: str, sylls: list[str]) -> float:
        return base + (pinlu_share.get(text, {}).get(sylls[0], 0) if len(text) == 1 else 0)

    # -------- CC-CEDICT Cantonese readings (Pleco, CC BY-SA 3.0): CEDICT entry -> jyutping
    canto_of: dict[tuple[str, str, str], str] = {}
    for trad, simp, py, jp, _ in parse_cedict_like(zip_text(src["canto-readings"], ".txt")):
        sylls = norm_jyutping(jp or "")
        psy = norm_pinyin(py)
        if not sylls:
            continue
        R.add_reading(R.headword(trad, simp), "yue", sylls, yue_score(8.0, trad, sylls))
        if psy:
            canto_of[(trad, simp, " ".join(psy))] = " ".join(sylls)

    # -------- CC-CEDICT (Mandarin readings + English)
    cedict = list(parse_cedict_like(gzip.decompress(src["cedict"].read_bytes()).decode("utf-8")))
    for trad, simp, py, _, glosses in cedict:
        hid = R.headword(trad, simp)
        sylls = norm_pinyin(py)
        if sylls:
            R.add_reading(hid, "cmn", sylls, cmn_score(10.0, simp, sylls))
        reading = " ".join(sylls) if sylls else None
        alt = canto_of.get((trad, simp, reading or ""), "")
        for g in glosses:
            R.add_sense(hid, "cmn", reading, g, "cedict",
                        pos="cl" if g.startswith("CL:") else "", alt=alt)
            if g.startswith("CL:") and "cl" not in R.info[hid]:
                R.info[hid]["cl"] = g[3:].strip()

    # -------- CC-Canto (Pleco, CC BY-SA 3.0): Cantonese words and meanings
    cccanto = list(parse_cedict_like(zip_text(src["cccanto"], ".txt")))
    for trad, simp, py, jp, glosses in cccanto:
        hid = R.headword(trad, simp)
        sylls = norm_jyutping(jp or "")
        psy = norm_pinyin(py)
        if sylls:
            R.add_reading(hid, "yue", sylls, yue_score(9.0, trad, sylls))
        if psy:
            R.add_reading(hid, "cmn", psy, cmn_score(5.0, simp, psy))
        for g in glosses:
            R.add_sense(hid, "yue", " ".join(sylls) if sylls else None, g, "cccanto",
                        alt=" ".join(psy) if psy else "")

    # -------- TypeDuck (CC BY 4.0): readings, English, register, written equivalents
    typeduck_entries = []
    typeduck_reg: dict[str, set[str]] = defaultdict(set)
    for row in dict_body(td / "jyut6ping3_scolar.dict.yaml"):
        if len(row) < 2:
            continue
        fields = next(csv.reader([row[0]]))
        fields += [""] * (21 - len(fields))
        text = row[1]
        sylls = norm_jyutping(fields[0])
        if not text or not sylls:
            continue
        hid = R.headword(text, t2s.convert(text))
        R.add_reading(hid, "yue", sylls, yue_score(10.0, text, sylls))
        eng = fields[16].strip()
        if eng:
            R.add_sense(hid, "yue", " ".join(sylls), eng, "typeduck", pos=fields[10],
                        reg=fields[11], label=fields[12], written=fields[13],
                        vern=fields[14], colloc=fields[15])
            typeduck_entries.append((text, [g.strip() for g in eng.split(";")], 1.2))
        if fields[11]:
            typeduck_reg[text].add(fields[11])

    # -------- rime-cantonese characters and words (readings only)
    for row in dict_body(rc / "jyut6ping3.chars.dict.yaml"):
        sylls = norm_jyutping(row[1]) if len(row) >= 2 else None
        if sylls:
            hid = R.headword(row[0], t2s.convert(row[0]))
            R.add_reading(hid, "yue", sylls, yue_score(6.0, row[0], sylls))
    for row in dict_body(rc / "jyut6ping3.words.dict.yaml"):
        sylls = norm_jyutping(row[1]) if len(row) >= 2 else None
        if sylls:
            R.add_reading(R.headword(row[0], t2s.convert(row[0])), "yue", sylls, 6.5)

    # -------- Unihan: fills characters the dictionaries above do not cover
    by_trad: dict[str, list[int]] = defaultdict(list)
    simp_forms: set[str] = set()
    for (trad, simp), hid in R.ids.items():
        if len(trad) == 1:
            by_trad[trad].append(hid)
        if len(simp) == 1:
            simp_forms.add(simp)
    for ch, fields in unihan.items():
        hids = by_trad.get(ch)
        if not hids:
            if ch in simp_forms:
                continue  # a simplified form of characters we already have
            simp = variants.get(ch, {}).get("kSimplifiedVariant", [ch])[0]
            hids = [R.headword(ch, simp)]
        cmn = unihan_mandarin(fields)
        yue = [t for t in fields.get("kCantonese", "").split() if JYUT_SYL.match(t)]
        for hid in hids:
            if not R.readings.get((hid, "cmn")):
                for i, (syl, share) in enumerate(cmn):
                    R.add_reading(hid, "cmn", [syl], 4.0 + share - 0.01 * i)
            if not R.readings.get((hid, "yue")):
                for i, tok in enumerate(yue):
                    R.add_reading(hid, "yue", [tok], yue_score(4.0 - 0.01 * i, ch, [tok]))
            if "kDefinition" in fields and hid not in R.with_senses:
                R.add_sense(hid, "*", None, fields["kDefinition"], "unihan")

    # -------- frequencies per headword
    for i, (trad, simp, _) in enumerate(R.hw):
        R.hw[i][2] = max(yue_freq.get(trad, 0), cmn_freq.get(simp, 0))

    # -------- word facts: HSK level, Cantonese band and register, part of speech
    hsk = load_hsk(src["hsk"])
    bands = frequency_bands(yue_freq)
    swc_colloquial, swc_written = set(), set()
    for line in (HERE / "swc" / "swc.tsv").read_text(encoding="utf-8").splitlines():
        body = line.split("#", 1)[0]
        if "\t" in body:
            a, b = (x.strip() for x in body.split("\t", 1))
            if a != b:
                swc_colloquial.add(a)
                swc_written.add(b)
    cedict_trad = {trad for trad, _, _, _, _ in cedict}
    cccanto_only = {trad for trad, _, _, _, _ in cccanto if trad not in cedict_trad}
    for i, (trad, simp, _) in enumerate(R.hw):
        hid = i + 1
        info = R.info[hid]
        level = hsk.get(simp) or hsk.get(trad)
        if level:
            info["hsk"] = level
        if R.readings.get((hid, "yue")) and trad in bands:
            info["band"] = bands[trad]
        regs = typeduck_reg.get(trad, set())
        if trad in swc_colloquial or trad in cccanto_only or regs == {"ver"}:
            info["reg"] = "col"
        elif "wri" in regs or (len(trad) >= 2 and trad in swc_written):
            info["reg"] = "wri"
        if simp in cmn_pos:
            info["pos"] = cmn_pos[simp]

    meta = {
        "version": str(DATA_VERSION),
        "cedict": "2026-10-13",
        "sources": "CC-CEDICT (CC BY-SA 4.0); CC-Canto + CC-CEDICT Cantonese readings "
                   "(Pleco, CC BY-SA 3.0); TypeDuck (CC BY 4.0); rime-cantonese (CC BY 4.0); "
                   "Unihan (Unicode License v3); HSK levels: complete-hsk-vocabulary (MIT); "
                   "parts of speech: jieba (MIT)",
        "license": "CC BY-SA 4.0",
    }
    R.write(assets / "readings.db", meta)
    print(f"readings.db: {len(R.hw)} headwords, {len(R.senses)} senses", file=sys.stderr)

    # -------- Rime: Cantonese (rime-cantonese dictionaries, unchanged)
    for name in ("jyut6ping3.chars", "jyut6ping3.words", "jyut6ping3.phrase",
                 "jyut6ping3.lettered"):
        shutil.copy(rc / f"{name}.dict.yaml", rime_dir / f"{name}.dict.yaml")
    shutil.copy(rc / "essay-cantonese.txt", rime_dir / "essay-cantonese.txt")
    write_dict(rime_dir / "lychee_yue.dict.yaml", "lychee_yue", [],
               extra="vocabulary: essay-cantonese\nimport_tables:\n"
                     "  - jyut6ping3.chars\n  - jyut6ping3.words\n"
                     "  - jyut6ping3.phrase\n  - jyut6ping3.lettered\n",
               note="Cantonese entries come from rime-cantonese (CC BY 4.0), "
                    "https://github.com/rime/rime-cantonese")

    # -------- Rime: Mandarin (CC-CEDICT words, Unihan characters, jieba frequencies)
    simp_chars = set()
    for _, simp, _, _, _ in cedict:
        simp_chars.update(simp)
    for ch, fields in unihan.items():
        if "kTGHZ2013" in fields:
            simp_chars.add(ch)
    char_codes: dict[str, set[str]] = defaultdict(set)
    # each character's most common reading, for words read character by character
    char_best: dict[str, str] = {}
    entries: dict[tuple[str, str], int] = {}

    def put(text, code, weight):
        key = (text, code)
        if weight > entries.get(key, -1):
            entries[key] = weight

    for ch in sorted(simp_chars):
        if not is_han(ch):
            continue
        rs = unihan_mandarin(unihan.get(ch, {}))
        base = cmn_freq.get(ch, 0)
        n = len(rs)
        for i, (syl, share) in enumerate(rs):
            code = pinyin_code([syl])
            char_codes[ch].add(code)
            char_best.setdefault(ch, code)
            w = base * share if share else base / (n + i)
            put(ch, code, max(1, int(w)))
    for trad, simp, py, _, _ in cedict:
        sylls = norm_pinyin(py)
        if not sylls or not all_han(simp) or len(sylls) != len(simp):
            continue
        code = pinyin_code(sylls)
        if len(simp) == 1:
            char_codes[simp].add(code)
            put(simp, code, max(1, cmn_freq.get(simp, 0) // 4))
        else:
            put(simp, code, max(1, cmn_freq.get(simp, 0)))
    have_words = {t for t, _ in entries}
    for word, f in cmn_freq.items():
        if len(word) < 2 or word in have_words or not all_han(word):
            continue
        codes = [char_codes.get(c) for c in word]
        # every character has one reading (tones aside: a word's tones follow the character's
        # most common one, which tone typing may miss but plain typing never does)
        if all(c and len({re.sub(r"\d", "", x) for x in c}) == 1 and ch in char_best
               for c, ch in zip(codes, word)):
            put(word, " ".join(char_best[ch] for ch in word), max(1, f))
    write_dict(rime_dir / "lychee_cmn.dict.yaml", "lychee_cmn",
               [f"{t}\t{c}\t{w}" for (t, c), w in sorted(entries.items())],
               note="Words: CC-CEDICT (CC BY-SA 4.0); characters: Unihan (Unicode License v3); "
                    "weights: jieba dict.txt (MIT).")
    print(f"lychee_cmn: {len(entries)} entries", file=sys.stderr)

    # -------- Rime: English -> Chinese
    yue_words = set(yue_freq)
    for row in dict_body(rc / "jyut6ping3.words.dict.yaml"):
        yue_words.add(row[0])

    def yue_en_entries():
        yield from typeduck_entries
        for trad, simp, py, jp, glosses in cccanto:
            yield trad, glosses, 1.1
        for trad, simp, py, jp, glosses in cedict:
            if trad in yue_words:
                yield trad, glosses, 0.8

    n = build_en_dict(yue_en_entries(), lambda t: yue_freq.get(t, 0),
                      rime_dir / "lychee_en_yue.dict.yaml", "lychee_en_yue")
    print(f"lychee_en_yue: {n} entries", file=sys.stderr)
    trad_of = {simp: trad for trad, simp, _, _, _ in cedict}

    def cmn_en_freq(simp: str) -> float:
        # jieba (news) undercounts conversation; the Cantonese corpus helps for shared words
        f = cmn_freq.get(simp, 0)
        # at most 3x what jieba says, so Cantonese usage cannot dominate Mandarin
        return max(f, min(yue_freq.get(trad_of.get(simp, simp), 0) / 20, f * 3))

    def cmn_en_entries():
        for _, simp, _, _, g in cedict:
            # Cantonese words belong in EN→粵, not EN→普
            yield simp, g, 0.15 if any("Cantonese" in x for x in g) else 1.0

    n = build_en_dict(cmn_en_entries(),
                      cmn_en_freq,
                      rime_dir / "lychee_en_cmn.dict.yaml", "lychee_en_cmn")
    print(f"lychee_en_cmn: {n} entries", file=sys.stderr)

    # -------- Korean, typed in romanisation: words from a frequency list, every syllable too
    n = build_ko_dict(src["ko-words"], rime_dir / "lychee_ko.dict.yaml")
    print(f"lychee_ko: {n} entries", file=sys.stderr)

    # -------- Rime: schemas and shared config (hand-written, in ./rime)
    for f in (HERE / "rime").glob("*.yaml"):
        shutil.copy(f, rime_dir / f.name)
    # fuzzy variants (Settings: zh/z, n/l, ng ...), each with its own prism
    for schema_id, rules in FUZZY.items():
        write_fuzzy_schema(rime_dir, schema_id, rules)

    (assets / "data-licenses.txt").write_text((HERE / "DATA-LICENSES.txt").read_text())


    if not args.no_compile:
        compile_rime(rime_dir)
        print("rime-data compiled", file=sys.stderr)
    if args.opencc:
        # Rime's simplifier filter reads these from <shared data>/opencc (Apache-2.0).
        dest = rime_dir / "opencc"
        dest.mkdir()
        for name in ("t2s.json", "s2t.json", "STCharacters.ocd2", "STPhrases.ocd2",
                     "TSCharacters.ocd2", "TSPhrases.ocd2"):
            shutil.copy(args.opencc / name, dest / name)
    write_stamp(rime_dir)


if __name__ == "__main__":
    main()
