#!/usr/bin/env python3
"""Rebuilds every picture in Noto Color Emoji from Google's own vector sources.

The Unicode 18 release of Noto Color Emoji (googlefonts/noto-emoji, 2D/fonts) was built from
bitmaps whose gradients came out wrong (the screaming face's blue forehead is black; many other
emoji are dark or banded). Its SVG sources at the same commit are right. This keeps the font as
Google made it (its characters, sequences, metrics and layout) and replaces the picture of every
emoji it draws with one rendered from that emoji's SVG: 2D/svg for emoji, and the waved flags in
third_party/region-flags/waved-svg for flags. Each emoji's glyph is found by shaping its sequence
from emoji-test.txt (all of them, fully-qualified or not) with HarfBuzz.

  python3 build_noto.py FONT.ttf NOTO_REPO emoji-test.txt OUT.ttf

Needs fontTools, uharfbuzz, cairosvg and Pillow.
"""

from __future__ import annotations

import io
import re
import sys
from pathlib import Path


def svg_name(cps: list[int]) -> str:
    return "emoji_u" + "_".join(f"{cp:04x}" for cp in cps if cp != 0xFE0F) + ".svg"


def glyph_sources(font_path: str, repo: Path, emoji_test: str) -> dict[int, Path]:
    """Glyph id -> the SVG of the emoji it draws."""
    import uharfbuzz as hb
    font = hb.Font(hb.Face(hb.Blob.from_file_path(font_path)))
    dirs = [repo / "2D/svg", repo / "third_party/region-flags/waved-svg"]
    out: dict[int, Path] = {}
    for line in open(emoji_test, encoding="utf-8"):
        m = re.match(r"^([0-9A-F ]+);\s*(fully-qualified|minimally-qualified|unqualified|component)", line)
        if not m:
            continue
        cps = [int(c, 16) for c in m.group(1).split()]
        name = svg_name(cps)
        svg = next((d / name for d in dirs if (d / name).exists()), None)
        if svg is None:
            continue
        buf = hb.Buffer()
        buf.add_codepoints(cps)
        buf.guess_segment_properties()
        hb.shape(font, buf)
        glyphs = [g.codepoint for g in buf.glyph_infos]
        if len(glyphs) == 1 and glyphs[0] != 0:
            out.setdefault(glyphs[0], svg)
    return out


def render(svg: Path, w: int, h: int):
    import cairosvg
    from PIL import Image
    side = min(w, h)
    png = cairosvg.svg2png(url=str(svg), output_width=side, output_height=side, unsafe=True)
    art = Image.open(io.BytesIO(png)).convert("RGBA")
    pic = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    pic.paste(art, ((w - side) // 2, (h - side) // 2))
    return pic


def encode(pic) -> bytes:
    """A small PNG: 256 colours with alpha, dithered (no banding), like Noto's own pictures."""
    buf = io.BytesIO()
    try:
        method = pic.Quantize.LIBIMAGEQUANT if hasattr(pic, "Quantize") else 3
        small = pic.quantize(256, method=method, dither=1)
    except Exception:
        small = pic.quantize(256, method=2, dither=1)  # fast octree: supports alpha everywhere
    small.save(buf, "PNG", optimize=True)
    return buf.getvalue()


def build(src: str, repo: str, emoji_test: str, dst: str) -> tuple[int, int]:
    from fontTools.ttLib import TTFont
    from PIL import Image
    font = TTFont(src)
    order = font.getGlyphOrder()
    sources = {order[gid]: svg for gid, svg in glyph_sources(src, Path(repo), emoji_test).items()}
    rebuilt = kept = 0
    for strike in font["CBDT"].strikeData:
        for name, glyph in strike.items():
            svg = sources.get(name)
            if svg is None:
                kept += 1
                continue
            w, h = Image.open(io.BytesIO(glyph.imageData)).size
            try:
                glyph.imageData = encode(render(svg, w, h))
                rebuilt += 1
            except Exception as e:  # a source that can't be drawn keeps Google's picture
                print(f"{svg.name}: kept ({e})", file=sys.stderr)
                kept += 1
    font.save(dst)
    return rebuilt, kept


if __name__ == "__main__":
    rebuilt, kept = build(*sys.argv[1:5])
    print(f"Noto Color Emoji: {rebuilt} pictures rebuilt from their SVGs, {kept} kept")
