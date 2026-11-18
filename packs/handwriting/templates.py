"""The characters Lychee's handwriting recogniser knows, each with one or more model writings
(every stroke's centre line, in writing order), from the stroke-order data Lychee already uses:

  Chinese   Make Me a Hanzi (graphics: Arphic Public License), simplified and traditional;
            AnimCJK (Arphic Public License / LGPL): mainland shapes and stroke orders, and
            Taiwan's (traditional characters as written in Taiwan, its own stroke orders)
  Japanese  KanjiVG (Ulrich Apel, CC BY-SA 3.0): kana, and kanji in their Japanese shapes;
            AnimCJK's Japanese kanji and kana
  Both      Tomoe's handwriting dictionaries (Kouhei Sutou, Red Hat; LGPL 2.1): characters
            written by hand with a stroke editor, simplified Chinese and Japanese
  Korean    the 2,350 common Hangul syllables (KS X 1001) and the letters, made from each
            letter's standard strokes (packs/dict/strokes_local.py); AnimCJK's Hanja

A character written in several places is one class with every place's writing, so the
recogniser knows each region's shapes and stroke orders. Coordinates: a unit
box, x right, y down.
"""

from __future__ import annotations

import json
import sys
import zipfile
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / "dict"))
import strokes_local  # noqa: E402
from build_dicts import HANZI, KANJIVG, fetch  # noqa: E402

# AnimCJK, pinned (graphics*.txt: the same format as Make Me a Hanzi's graphics.txt)
ANIMCJK = "https://raw.githubusercontent.com/parsimonhi/animCJK/c63765b150cbd567c71d05cf4a4f8d00b57045c9/{}"
ANIMCJK_SETS = {
    "graphicsZhHans.txt": "zh",
    "graphicsZhHant.txt": "zh-tw",
    "graphicsJa.txt": "ja",
    "graphicsJaKana.txt": "ja",
    "graphicsKo.txt": "ko-hanja",
}


TOMOE = "https://downloads.sourceforge.net/project/tomoe/tomoe/tomoe-0.6.0/tomoe-0.6.0.tar.gz"
TOMOE_SHA256 = "ee600f97fa434c13f6c41a1db9e03eccd551151ed48ece9c56cd9e828228ea1e"
TOMOE_SETS = {"handwriting-zh_CN.xml": "zh-tomoe", "handwriting-ja.xml": "ja-tomoe"}


def tomoe(cache: Path):
    """(char, strokes in the unit box) from Tomoe's two handwriting dictionaries."""
    import hashlib
    import re
    import tarfile

    tgz = fetch(TOMOE, cache, "tomoe-0.6.0.tar.gz")
    if hashlib.sha256(tgz.read_bytes()).hexdigest() != TOMOE_SHA256:
        raise SystemExit("tomoe-0.6.0.tar.gz: checksum mismatch")
    char_re = re.compile(r"<character>\s*<utf8>(.*?)</utf8>(.*?)</character>", re.S)
    stroke_re = re.compile(r"<stroke>(.*?)</stroke>", re.S)
    point_re = re.compile(r'<point x="(-?\d+)" y="(-?\d+)"')
    with tarfile.open(tgz) as t:
        for name, lang in TOMOE_SETS.items():
            xml = t.extractfile(f"tomoe-0.6.0/data/{name}").read().decode("utf-8")
            for m in char_re.finditer(xml):
                import html
                ch = html.unescape(m.group(1)).strip()
                strokes = [np.array([[int(x) / 1000.0, int(y) / 1000.0] for x, y in point_re.findall(st)], dtype=np.float32)
                           for st in stroke_re.findall(m.group(2))]
                strokes = [st for st in strokes if len(st)]
                if len(ch) == 1 and strokes:
                    yield ch, lang, strokes


def _unit(medians) -> list[np.ndarray]:
    """Make Me a Hanzi's coordinates (1024 box, y up from 900) to the unit box, y down."""
    return [np.array([[x / 1024.0, (900.0 - y) / 1024.0] for x, y in s], dtype=np.float32)
            for s in medians if len(s) > 0]


def common_hangul() -> list[str]:
    """The 2,350 Hangul syllables of KS X 1001 (the ones in everyday use)."""
    out = []
    for hi in range(0xB0, 0xC9):
        for lo in range(0xA1, 0xFF):
            try:
                ch = bytes([hi, lo]).decode("euc_kr")
            except UnicodeDecodeError:
                continue
            if "가" <= ch <= "힣":
                out.append(ch)
    return out


def load(cache: Path) -> dict[str, dict[str, list[list[np.ndarray]]]]:
    """{char: {"zh"|"ja"|"ko": [writing, ...]}}, a writing being a list of strokes."""
    cache.mkdir(parents=True, exist_ok=True)
    chars: dict[str, dict[str, list]] = {}

    def add(ch, lang, medians):
        strokes = _unit(medians)
        if strokes:
            chars.setdefault(ch, {}).setdefault(lang, []).append(strokes)

    graphics = fetch(HANZI.format("graphics.txt"), cache, "hanzi-graphics.txt")
    for line in graphics.read_text(encoding="utf-8").splitlines():
        if line.strip():
            g = json.loads(line)
            add(g["character"], "zh", g["medians"])

    for name, lang in ANIMCJK_SETS.items():
        f = fetch(ANIMCJK.format(name), cache, "animcjk-" + name)
        for line in f.read_text(encoding="utf-8").splitlines():
            if line.strip():
                g = json.loads(line)
                ch = g["character"]
                # characters only (not the components and strokes AnimCJK also draws)
                # (compatibility characters left out: they look the same as ordinary ones)
                if len(ch) == 1 and (0x3040 <= ord(ch) <= 0x30FF or 0x3400 <= ord(ch) <= 0x9FFF
                                     or 0x20000 <= ord(ch) <= 0x2FFFF):
                    add(ch, lang, g["medians"])

    for ch, lang, strokes in tomoe(cache):
        chars.setdefault(ch, {}).setdefault(lang, []).append(strokes)

    kvg_zip = fetch(KANJIVG, cache, "kanjivg-main.zip")
    kvg = cache / "kanjivg"
    if not (kvg / "kanji").is_dir():
        with zipfile.ZipFile(kvg_zip) as z:
            z.extractall(kvg)
    for ch, medians, _ in strokes_local.japanese(kvg / "kanji"):
        add(ch, "ja", medians)

    common = set(common_hangul())
    for ch, medians, _ in strokes_local.korean():
        if ch in common or not ("가" <= ch <= "힣"):
            add(ch, "ko", medians)
    return chars


if __name__ == "__main__":
    c = load(HERE / ".cache")
    langs = {}
    for ch, d in c.items():
        for lang in d:
            langs[lang] = langs.get(lang, 0) + 1
    print(len(c), "characters", langs)
