"""The characters Lychee's handwriting recogniser knows, each with one or more model writings
(every stroke's centre line, in writing order), from the stroke-order data Lychee already uses:

  Chinese   Make Me a Hanzi (graphics: Arphic Public License), simplified and traditional
  Japanese  KanjiVG (Ulrich Apel, CC BY-SA 3.0): kana, and kanji in their Japanese shapes
  Korean    the 2,350 common Hangul syllables (KS X 1001) and the letters, made from each
            letter's standard strokes (packs/dict/strokes_local.py)

A character in both Chinese and Japanese is one class with both writings. Coordinates: a unit
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
