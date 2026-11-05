"""Stroke order for Japanese and Korean, in Make Me a Hanzi's coordinates (a 1024 box, y up
from 900), stored as each stroke's centre line (the app draws it with a broad brush):

  japanese(kanjivg_dir)  kana and kanji from KanjiVG (Ulrich Apel, CC BY-SA 3.0): Japanese
                         stroke order and shapes.
  korean()               every Hangul syllable, made from its letters' strokes in their
                         standard order (initial, vowel, final), each letter drawn here by hand
                         and placed in the syllable as it is written.

Each yields (char, medians, decomposition)."""

from __future__ import annotations

import math
import re
from pathlib import Path

SCALE = 1024 / 109


def _to_mmah(x: float, y: float) -> list[int]:
    return [round(x), round(900 - y)]


# ---------------------------------------------------------------- KanjiVG

_TOKEN = re.compile(r"[MmCcSsLlHhVvZz]|-?(?:\d+\.?\d*|\.\d+)(?:e-?\d+)?")


def _path_points(d: str, step: float = 3.0) -> list[tuple[float, float]]:
    """Points along an SVG path (the commands KanjiVG uses), every [step] units or so."""
    toks = _TOKEN.findall(d)
    pts: list[tuple[float, float]] = []
    i = 0
    cmd = ""
    x = y = 0.0
    last_ctrl = None

    def num():
        nonlocal i
        v = float(toks[i])
        i += 1
        return v

    def cubic(p0, p1, p2, p3):
        length = math.dist(p0, p1) + math.dist(p1, p2) + math.dist(p2, p3)
        n = max(2, int(length / step))
        for k in range(1, n + 1):
            t = k / n
            u = 1 - t
            pts.append((u ** 3 * p0[0] + 3 * u * u * t * p1[0] + 3 * u * t * t * p2[0] + t ** 3 * p3[0],
                        u ** 3 * p0[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p2[1] + t ** 3 * p3[1]))

    while i < len(toks):
        if re.match(r"[A-Za-z]", toks[i]):
            cmd = toks[i]
            i += 1
            if cmd in "Zz":
                continue
        rel = cmd.islower()
        c = cmd.upper()
        if c == "M":
            nx, ny = num(), num()
            x, y = (x + nx, y + ny) if rel else (nx, ny)
            pts.append((x, y))
            cmd = "l" if rel else "L"
            last_ctrl = None
        elif c == "L":
            nx, ny = num(), num()
            nx, ny = (x + nx, y + ny) if rel else (nx, ny)
            cubic((x, y), (x, y), (nx, ny), (nx, ny))
            x, y = nx, ny
            last_ctrl = None
        elif c == "H":
            nx = num()
            nx = x + nx if rel else nx
            cubic((x, y), (x, y), (nx, y), (nx, y))
            x = nx
        elif c == "V":
            ny = num()
            ny = y + ny if rel else ny
            cubic((x, y), (x, y), (x, ny), (x, ny))
            y = ny
        elif c == "C":
            a = [num() for _ in range(6)]
            if rel:
                a = [a[0] + x, a[1] + y, a[2] + x, a[3] + y, a[4] + x, a[5] + y]
            cubic((x, y), (a[0], a[1]), (a[2], a[3]), (a[4], a[5]))
            last_ctrl = (a[2], a[3])
            x, y = a[4], a[5]
        elif c == "S":
            a = [num() for _ in range(4)]
            if rel:
                a = [a[0] + x, a[1] + y, a[2] + x, a[3] + y]
            c1 = (2 * x - last_ctrl[0], 2 * y - last_ctrl[1]) if last_ctrl else (x, y)
            cubic((x, y), c1, (a[0], a[1]), (a[2], a[3]))
            last_ctrl = (a[0], a[1])
            x, y = a[2], a[3]
        else:
            i += 1
    return pts


def _thin(pts: list[tuple[float, float]], spacing: float) -> list[tuple[float, float]]:
    """Points about [spacing] apart along the line, keeping both ends."""
    if len(pts) < 2:
        return pts
    out = [pts[0]]
    run = 0.0
    for a, b in zip(pts, pts[1:]):
        run += math.dist(a, b)
        if run >= spacing:
            out.append(b)
            run = 0.0
    if out[-1] != pts[-1]:
        out.append(pts[-1])
    return out


def japanese(kanjivg: Path):
    """Kana (U+3040-30FF) and kanji from KanjiVG's kanji/ folder (variants left out)."""
    stroke_re = re.compile(r'<path[^>]*\bid="kvg:[0-9a-f]+-s(\d+)"[^>]*\bd="([^"]+)"')
    for f in sorted(kanjivg.glob("*.svg")):
        if "-" in f.stem:
            continue
        cp = int(f.stem, 16)
        if not (0x3040 <= cp <= 0x30FF or 0x4E00 <= cp <= 0x9FFF or 0x3400 <= cp <= 0x4DBF):
            continue
        strokes = sorted(((int(n), d) for n, d in stroke_re.findall(f.read_text(encoding="utf-8"))))
        medians = []
        for _, d in strokes:
            pts = _thin([(x * SCALE, y * SCALE) for x, y in _path_points(d)], 48)
            medians.append([_to_mmah(x, y) for x, y in pts])
        if medians:
            yield chr(cp), medians, None


# ---------------------------------------------------------------- Hangul

def _circle(cx, cy, r, n=14):
    # written anticlockwise from the top, as ㅇ is
    return [(cx + r * math.sin(-2 * math.pi * k / n), cy - r * math.cos(2 * math.pi * k / n)) for k in range(n + 1)]


TICK = [(0.42, 0.02), (0.58, 0.12)]
# letters in a unit box (y down); each a list of strokes in writing order
CONSONANTS = {
    "ㄱ": [[(0.1, 0.15), (0.85, 0.15), (0.8, 0.9)]],
    "ㄴ": [[(0.15, 0.1), (0.15, 0.85), (0.9, 0.85)]],
    "ㄷ": [[(0.12, 0.15), (0.88, 0.15)], [(0.12, 0.15), (0.12, 0.85), (0.9, 0.85)]],
    "ㄹ": [[(0.12, 0.12), (0.85, 0.12), (0.85, 0.48)], [(0.12, 0.48), (0.85, 0.48)],
          [(0.12, 0.48), (0.12, 0.88), (0.9, 0.88)]],
    "ㅁ": [[(0.15, 0.12), (0.15, 0.88)], [(0.15, 0.12), (0.85, 0.12), (0.85, 0.88)], [(0.15, 0.88), (0.85, 0.88)]],
    "ㅂ": [[(0.15, 0.1), (0.15, 0.9)], [(0.85, 0.1), (0.85, 0.9)], [(0.15, 0.48), (0.85, 0.48)], [(0.15, 0.9), (0.85, 0.9)]],
    "ㅅ": [[(0.52, 0.1), (0.4, 0.5), (0.1, 0.9)], [(0.48, 0.45), (0.9, 0.9)]],
    "ㅇ": [_circle(0.5, 0.5, 0.38)],
    "ㅈ": [[(0.15, 0.15), (0.82, 0.15), (0.42, 0.6), (0.1, 0.9)], [(0.5, 0.5), (0.9, 0.9)]],
    "ㅊ": [TICK, [(0.15, 0.3), (0.82, 0.3), (0.42, 0.68), (0.1, 0.92)], [(0.5, 0.6), (0.9, 0.92)]],
    "ㅋ": [[(0.1, 0.15), (0.85, 0.15), (0.8, 0.9)], [(0.1, 0.5), (0.82, 0.5)]],
    "ㅌ": [[(0.12, 0.12), (0.85, 0.12)], [(0.12, 0.48), (0.85, 0.48)], [(0.12, 0.12), (0.12, 0.88), (0.9, 0.88)]],
    "ㅍ": [[(0.1, 0.12), (0.9, 0.12)], [(0.32, 0.12), (0.3, 0.85)], [(0.68, 0.12), (0.7, 0.85)], [(0.08, 0.86), (0.92, 0.86)]],
    "ㅎ": [TICK, [(0.15, 0.25), (0.85, 0.25)], _circle(0.5, 0.64, 0.26)],
}


def _side_by_side(a: str, b: str):
    """Two letters in one box (ㄲ, ㄳ ...): each takes half the width."""
    def squeeze(strokes, x0):
        return [[(x0 + x * 0.5, y) for x, y in s] for s in strokes]
    return squeeze(CONSONANTS[a], 0.0) + squeeze(CONSONANTS[b], 0.5)


for double, single in {"ㄲ": "ㄱ", "ㄸ": "ㄷ", "ㅃ": "ㅂ", "ㅆ": "ㅅ", "ㅉ": "ㅈ"}.items():
    CONSONANTS[double] = _side_by_side(single, single)
for cluster, (a, b) in {"ㄳ": "ㄱㅅ", "ㄵ": "ㄴㅈ", "ㄶ": "ㄴㅎ", "ㄺ": "ㄹㄱ", "ㄻ": "ㄹㅁ", "ㄼ": "ㄹㅂ",
                        "ㄽ": "ㄹㅅ", "ㄾ": "ㄹㅌ", "ㄿ": "ㄹㅍ", "ㅀ": "ㄹㅎ", "ㅄ": "ㅂㅅ"}.items():
    CONSONANTS[cluster] = _side_by_side(a, b)

STEM = [(0.5, 0.04), (0.5, 0.96)]
# vowels written beside the initial (right column)
VERTICAL = {
    "ㅣ": [STEM],
    "ㅏ": [STEM, [(0.5, 0.45), (0.85, 0.45)]],
    "ㅑ": [STEM, [(0.5, 0.35), (0.85, 0.35)], [(0.5, 0.6), (0.85, 0.6)]],
    "ㅓ": [[(0.12, 0.45), (0.5, 0.45)], STEM],
    "ㅕ": [[(0.12, 0.35), (0.5, 0.35)], [(0.12, 0.6), (0.5, 0.6)], STEM],
    "ㅐ": [[(0.35, 0.06), (0.35, 0.94)], [(0.35, 0.47), (0.62, 0.47)], [(0.78, 0.04), (0.78, 0.96)]],
    "ㅒ": [[(0.35, 0.06), (0.35, 0.94)], [(0.35, 0.36), (0.62, 0.36)], [(0.35, 0.58), (0.62, 0.58)], [(0.78, 0.04), (0.78, 0.96)]],
    "ㅔ": [[(0.08, 0.47), (0.38, 0.47)], [(0.38, 0.06), (0.38, 0.94)], [(0.78, 0.04), (0.78, 0.96)]],
    "ㅖ": [[(0.08, 0.36), (0.38, 0.36)], [(0.08, 0.58), (0.38, 0.58)], [(0.38, 0.06), (0.38, 0.94)], [(0.78, 0.04), (0.78, 0.96)]],
}
BAR = [(0.04, 0.55), (0.96, 0.55)]
# vowels written under the initial (bottom row)
HORIZONTAL = {
    "ㅡ": [BAR],
    "ㅗ": [[(0.5, 0.15), (0.5, 0.55)], BAR],
    "ㅛ": [[(0.35, 0.15), (0.35, 0.55)], [(0.65, 0.15), (0.65, 0.55)], BAR],
    "ㅜ": [[(0.04, 0.3), (0.96, 0.3)], [(0.5, 0.3), (0.5, 0.9)]],
    "ㅠ": [[(0.04, 0.3), (0.96, 0.3)], [(0.35, 0.3), (0.35, 0.9)], [(0.65, 0.3), (0.65, 0.9)]],
}
# both: the part under the initial, then the part beside it
MIXED = {"ㅘ": ("ㅗ", "ㅏ"), "ㅙ": ("ㅗ", "ㅐ"), "ㅚ": ("ㅗ", "ㅣ"), "ㅝ": ("ㅜ", "ㅓ"),
         "ㅞ": ("ㅜ", "ㅔ"), "ㅟ": ("ㅜ", "ㅣ"), "ㅢ": ("ㅡ", "ㅣ")}

INITIALS = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
VOWELS = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ"
FINALS = ["", "ㄱ", "ㄲ", "ㄳ", "ㄴ", "ㄵ", "ㄶ", "ㄷ", "ㄹ", "ㄺ", "ㄻ", "ㄼ", "ㄽ", "ㄾ", "ㄿ", "ㅀ",
          "ㅁ", "ㅂ", "ㅄ", "ㅅ", "ㅆ", "ㅇ", "ㅈ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ"]


def _place(strokes, box):
    x0, y0, x1, y1 = box
    return [[(x0 + x * (x1 - x0), y0 + y * (y1 - y0)) for x, y in s] for s in strokes]


def syllable(initial: str, vowel: str, final: str):
    """The syllable's strokes in a unit box, in writing order."""
    has_final = bool(final)
    out = []
    if vowel in VERTICAL:
        out += _place(CONSONANTS[initial], (0.04, 0.06, 0.52, 0.56) if has_final else (0.04, 0.14, 0.54, 0.86))
        out += _place(VERTICAL[vowel], (0.5, 0.02, 0.96, 0.6) if has_final else (0.5, 0.04, 0.96, 0.96))
    elif vowel in HORIZONTAL:
        out += _place(CONSONANTS[initial], (0.24, 0.02, 0.76, 0.34) if has_final else (0.2, 0.04, 0.8, 0.5))
        out += _place(HORIZONTAL[vowel], (0.04, 0.26, 0.96, 0.62) if has_final else (0.04, 0.4, 0.96, 0.96))
    else:
        under, beside = MIXED[vowel]
        out += _place(CONSONANTS[initial], (0.04, 0.03, 0.5, 0.3) if has_final else (0.04, 0.04, 0.52, 0.44))
        out += _place(HORIZONTAL[under], (0.02, 0.24, 0.62, 0.6) if has_final else (0.02, 0.36, 0.64, 0.92))
        out += _place(VERTICAL[beside], (0.56, 0.02, 0.96, 0.62) if has_final else (0.56, 0.03, 0.96, 0.97))
    if has_final:
        out += _place(CONSONANTS[final], (0.16, 0.66, 0.84, 0.98))
    return out


def korean():
    for n in range(11172):
        initial, rest = divmod(n, 21 * 28)
        vowel, final = divmod(rest, 28)
        parts = (INITIALS[initial], VOWELS[vowel], FINALS[final])
        strokes = syllable(*parts)
        # a little margin, like the other characters
        medians = [[_to_mmah(64 + x * 896, 64 + y * 896) for x, y in s] for s in strokes]
        yield chr(0xAC00 + n), medians, "".join(parts)
    # the letters on their own
    for letter, strokes in list(CONSONANTS.items()) + list(VERTICAL.items()) + list(HORIZONTAL.items()):
        medians = [[_to_mmah(160 + x * 704, 160 + y * 704) for x, y in s] for s in strokes]
        yield letter, medians, None
