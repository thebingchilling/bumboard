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


def popup_chars(xml_dir: pathlib.Path) -> set[str]:
    chars = set()
    for f in xml_dir.glob("*.xml"):
        for m in re.finditer(r'(?:keySpec|moreKeys|keyHintLabel)="([^"]*)"', f.read_text(encoding="utf-8")):
            chars.update(m.group(1))
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
                    if ord(c) > 127 and ord(c) not in have and not cjk(c))
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
