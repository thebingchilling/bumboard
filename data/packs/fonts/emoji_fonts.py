#!/usr/bin/env python3
"""Which Emoji version an emoji font covers: the newest version any of whose new characters
the font has. Lychee shows it next to each emoji font (Settings > Fonts).

  python3 emoji_fonts.py FONT.ttf [FONT.ttf ...]

Needs fontTools. render_preview.js draws the preview strips.
"""

from __future__ import annotations

import sys
from pathlib import Path

# characters new in each Emoji version (single code points; any one counts), newest first
VERSIONS = [
    ("17.0", [0x1FAEA, 0x1F6D8, 0x1FA8A, 0x1FAC8]),
    ("16.0", [0x1FAE9, 0x1FA89, 0x1FADF]),
    ("15.0", [0x1FAE8, 0x1FA75]),
    ("14.0", [0x1FAE0, 0x1FAE1]),
    ("13.0", [0x1F972]),
    ("12.0", [0x1F971]),
    ("11.0", [0x1F970]),
]

def emoji_version(font: Path) -> str:
    from fontTools.ttLib import TTFont

    cmap = TTFont(str(font), lazy=True).getBestCmap()
    return next((v for v, cps in VERSIONS if any(cp in cmap for cp in cps)), "")


def main(argv: list[str]) -> None:
    for f in argv:
        print(f"{f}\t{emoji_version(Path(f))}")


if __name__ == "__main__":
    main(sys.argv[1:])
