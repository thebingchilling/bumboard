#!/usr/bin/env python3
"""Builds the small fonts behind Google Sans Flex on the keys.

Google Sans Flex has no ⅙ ⅓ ∞ ★ ✓ ‰ † ← ♥ (and the Greek and Cyrillic letters of other
layouts' popups). The keyboard draws vulgar fractions with Google Sans Flex's own 'frac'
feature (1⁄6), and everything else it lacks from Google's Noto fonts, the nearest open
match: each popup character missing from Google Sans Flex is taken from the first of Noto
Sans, Noto Sans Math and Noto Sans Symbols 2 that has it, and each of those fonts is cut down
to just those characters (a few KB each).

Usage: subset-symbols.py <GoogleSansFlex.ttf> <out dir> <keyboard xml dir> <noto font>...
"""
import pathlib
import re
import sys

from fontTools import subset
from fontTools.ttLib import TTFont


# every symbol block an English keyboard draws from: Latin-1 and Extended, IPA, spacing
# modifiers and combining marks, Greek, Cyrillic, general punctuation, super- and subscripts,
# currency, letterlike symbols, number forms, arrows, maths, technical, enclosed numbers,
# shapes, symbols and dingbats
BLOCKS = [
    (0x00A0, 0x036F), (0x0370, 0x03FF), (0x0400, 0x04FF), (0x1E00, 0x1EFF),
    (0x2000, 0x206F), (0x2070, 0x209F), (0x20A0, 0x20CF), (0x2100, 0x214F), (0x2150, 0x218F),
    (0x2190, 0x21FF), (0x2200, 0x22FF), (0x2300, 0x23FF), (0x2460, 0x24FF), (0x25A0, 0x25FF),
    (0x2600, 0x26FF), (0x2700, 0x27BF), (0x27C0, 0x27EF), (0x27F0, 0x27FF), (0x2900, 0x297F),
    (0x2B00, 0x2BFF),
]


def popup_chars(xml_dir: pathlib.Path) -> set[str]:
    """Everything the keys and their popups show: the layouts' labels and popups, LatinIME's
    text tables (where most symbol popups are), and the common symbol blocks."""
    chars = set()
    for f in xml_dir.glob("*.xml"):
        for m in re.finditer(r'(?:keySpec|moreKeys|keyHintLabel|keyLabel)="([^"]*)"', f.read_text(encoding="utf-8")):
            chars.update(m.group(1))
    table = xml_dir.parent.parent / "src/com/android/inputmethod/keyboard/internal/KeyboardTextsTable.java"
    if table.exists():
        text = table.read_text(encoding="utf-8")
        for m in re.finditer(r'\\u([0-9A-Fa-f]{4})', text):
            chars.add(chr(int(m.group(1), 16)))
        chars.update(c for c in text if ord(c) > 127)
    for lo, hi in BLOCKS:
        chars.update(chr(c) for c in range(lo, hi + 1))
    return chars


def cjk(c: str) -> bool:
    cp = ord(c)
    return (0x2E80 <= cp <= 0x9FFF or 0xAC00 <= cp <= 0xD7AF or 0xF900 <= cp <= 0xFAFF
            or 0xFE10 <= cp <= 0xFE4F or 0xFF00 <= cp <= 0xFFEF)


def main() -> None:
    gsf, out, xml_dir, *sources = sys.argv[1:]
    out_dir = pathlib.Path(out)
    out_dir.mkdir(parents=True, exist_ok=True)
    have = TTFont(gsf).getBestCmap()
    needed = sorted(c for c in popup_chars(pathlib.Path(xml_dir))
                    if ord(c) > 127 and ord(c) not in have and not cjk(c)
                    and not 0xD800 <= ord(c) <= 0xDFFF and not 0xE000 <= ord(c) <= 0xF8FF)
    left = set(needed)
    for n, source in enumerate(sources):
        cmap = TTFont(source).getBestCmap()
        take = sorted(c for c in left if ord(c) in cmap)
        if not take:
            continue
        left -= set(take)
        options = subset.Options()
        options.layout_features = ["*"]
        options.name_IDs = ["*"]
        options.notdef_outline = False
        font = TTFont(source)
        sub = subset.Subsetter(options)
        sub.populate(unicodes=[ord(c) for c in take])
        sub.subset(font)
        dest = out_dir / f"LycheeSymbols{n}.ttf"
        font.save(dest)
        print(f"{dest.name}: {''.join(take)} from {pathlib.Path(source).name} ({dest.stat().st_size} bytes)")
    if left:
        print(f"no Google font has: {''.join(sorted(left))}")


if __name__ == "__main__":
    main()
