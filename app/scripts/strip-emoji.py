#!/usr/bin/env python3
"""Takes the emoji out of the keyboard's text fonts, so Noto Color Emoji draws them.

The keyboard's fonts come before the emoji font (LycheeFonts puts it last, behind the symbols).
Android picks the first font that has a character, so a text font holding ✋ ⭐ ✅ ⚡ ⌚ drew
them flat and black, and one holding ☝ ✌ ✍ ⛹ split their skin-tone forms (☝🏽) into the hand
and a coloured square. This removes from each font's character map:
  - every character shown as an emoji by default (Emoji_Presentation, single code points),
  - the emoji that take skin tones without FE0F (Emoji_Modifier_Base),
so they fall through to the emoji font. Text-style symbols the keys show (← ♥ ★ ✓ ©) stay.

Usage: strip-emoji.py <font.ttf>...   (each font is rewritten in place)
"""
import sys

from fontTools.ttLib import TTFont

# Unicode 18.0 emoji-test.txt: fully-qualified single code points below U+2C00 (default emoji
# presentation), then the emoji modifier bases among them that take a skin tone without FE0F
RANGES = [
    (0x231A, 0x231B), (0x23E9, 0x23EC), (0x23F0, 0x23F0), (0x23F3, 0x23F3), (0x25FD, 0x25FE),
    (0x2614, 0x2615), (0x2648, 0x2653), (0x267F, 0x267F), (0x2693, 0x2693), (0x26A1, 0x26A1),
    (0x26AA, 0x26AB), (0x26BD, 0x26BE), (0x26C4, 0x26C5), (0x26CE, 0x26CE), (0x26D4, 0x26D4),
    (0x26EA, 0x26EA), (0x26F2, 0x26F3), (0x26F5, 0x26F5), (0x26FA, 0x26FA), (0x26FD, 0x26FD),
    (0x2705, 0x2705), (0x270A, 0x270B), (0x2728, 0x2728), (0x274C, 0x274C), (0x274E, 0x274E),
    (0x2753, 0x2755), (0x2757, 0x2757), (0x2795, 0x2797), (0x27B0, 0x27B0), (0x27BF, 0x27BF),
    (0x2B1B, 0x2B1C), (0x2B50, 0x2B50), (0x2B55, 0x2B55),
    # skin tones: ☝ ⛹ ✌ ✍
    (0x261D, 0x261D), (0x26F9, 0x26F9), (0x270C, 0x270D),
]
EMOJI = {c for lo, hi in RANGES for c in range(lo, hi + 1)} | set(range(0x1F000, 0x1FAFF + 1))


def strip(path: str) -> None:
    font = TTFont(path)
    removed = set()
    for table in font["cmap"].tables:
        if table.format == 14:
            for vs, entries in list(table.uvsDict.items()):
                table.uvsDict[vs] = [(c, g) for c, g in entries if c not in EMOJI]
            continue
        cmap = getattr(table, "cmap", None)
        if not cmap:
            continue
        for c in [c for c in cmap if c in EMOJI]:
            removed.add(c)
            del cmap[c]
    if removed:
        font.save(path)
    print(f"{path.rsplit('/', 1)[-1]}: {len(removed)} emoji taken out")


if __name__ == "__main__":
    for p in sys.argv[1:]:
        strip(p)
