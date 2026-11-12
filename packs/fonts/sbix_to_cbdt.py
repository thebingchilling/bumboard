#!/usr/bin/env python3
"""Converts an Apple-format (sbix) colour emoji font into Android's own (CBDT/CBLC).

Noto 3D comes as sbix, with a black placeholder outline behind every picture. Android draws
outlines when a font has them, so emoji came out as black shapes. This keeps the pictures (the
largest strike, as PNG, like Noto Color Emoji), drops sbix and the outlines (glyf/loca), and
writes a bitmap-only font Android draws in colour. Pictures taller than 128 pixels are scaled
down to 128 (CBDT's metrics are bytes), the strike's size scaled with them. Needs Pillow.

  python3 sbix_to_cbdt.py IN.ttf OUT.ttf
"""

from __future__ import annotations

import io
import struct
import sys
import tempfile
from xml.sax.saxutils import quoteattr

from fontTools.ttLib import TTFont


def png_size(data: bytes) -> tuple[int, int]:
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("not a PNG")
    return struct.unpack(">II", data[16:24])


def convert(src: str, dst: str) -> int:
    font = TTFont(src)
    sbix = font["sbix"]
    ppem = max(sbix.strikes)
    strike = sbix.strikes[ppem]
    order = font.getGlyphOrder()
    upem = font["head"].unitsPerEm
    hmtx = font["hmtx"].metrics

    def image(name: str, depth: int = 0):
        g = strike.glyphs.get(name)
        if g is None or depth > 4:
            return None
        if g.graphicType == "dupe":
            target = order[struct.unpack(">H", g.imageData[:2])[0]]
            return image(target, depth + 1)
        if g.graphicType == "png " and g.imageData:
            return g
        return None

    from PIL import Image

    # one scale for every picture: the tallest becomes 128 pixels
    tallest = max(png_size(g.imageData)[1] for g in (image(n) for n in order) if g is not None)
    scale = min(1.0, 128 / tallest)
    out_ppem = max(1, round(ppem * scale))

    entries = []  # (glyph id, name, png, metrics)
    for gid, name in enumerate(order):
        g = image(name)
        if g is None:
            continue
        png = g.imageData
        w, h = png_size(png)
        if scale < 1.0:
            w2, h2 = max(1, round(w * scale)), max(1, round(h * scale))
            buf = io.BytesIO()
            Image.open(io.BytesIO(png)).convert("RGBA").resize((w2, h2), Image.LANCZOS).save(buf, "PNG", optimize=True)
            png, w, h = buf.getvalue(), w2, h2
        bearing_x = round(g.originOffsetX * scale)
        bearing_y = min(127, h + round(g.originOffsetY * scale))
        advance = min(255, round(hmtx[name][0] * out_ppem / upem))
        entries.append((gid, name, png, (h, w, bearing_x, bearing_y, advance)))
    if not entries:
        raise ValueError("no PNG pictures in the sbix table")

    # runs of consecutive glyph ids, one index subtable each
    runs: list[list] = []
    for e in entries:
        if runs and runs[-1][-1][0] == e[0] - 1:
            runs[-1].append(e)
        else:
            runs.append([e])

    ascent = min(127, round(font["hhea"].ascent * out_ppem / upem))
    descent = max(-128, round(font["hhea"].descent * out_ppem / upem))
    width_max = max(e[3][1] for e in entries)

    def line_metrics(direction: str) -> str:
        return f"""<sbitLineMetrics direction="{direction}">
  <ascender value="{ascent}"/><descender value="{descent}"/><widthMax value="{width_max}"/>
  <caretSlopeNumerator value="0"/><caretSlopeDenominator value="0"/><caretOffset value="0"/>
  <minOriginSB value="0"/><minAdvanceSB value="0"/><maxBeforeBL value="0"/><minAfterBL value="0"/>
  <pad1 value="0"/><pad2 value="0"/>
</sbitLineMetrics>"""

    cbdt = ['<CBDT><header version="3.0"/><strikedata index="0">']
    for gid, name, png, (h, w, bx, by, adv) in entries:
        cbdt.append(f"""<cbdt_bitmap_format_17 name={quoteattr(name)}><SmallGlyphMetrics>
<height value="{h}"/><width value="{w}"/><BearingX value="{bx}"/><BearingY value="{by}"/><Advance value="{adv}"/>
</SmallGlyphMetrics><rawimagedata>{png.hex()}</rawimagedata></cbdt_bitmap_format_17>""")
    cbdt.append("</strikedata></CBDT>")

    cblc = [f"""<CBLC><header version="3.0"/><strike index="0"><bitmapSizeTable>
{line_metrics("hori")}{line_metrics("vert")}
<colorRef value="0"/><startGlyphIndex value="{entries[0][0]}"/><endGlyphIndex value="{entries[-1][0]}"/>
<ppemX value="{out_ppem}"/><ppemY value="{out_ppem}"/><bitDepth value="32"/><flags value="1"/>
</bitmapSizeTable>"""]
    for run in runs:
        cblc.append(f'<eblc_index_sub_table_1 imageFormat="17" firstGlyphIndex="{run[0][0]}" lastGlyphIndex="{run[-1][0]}">')
        cblc.extend(f"<glyphLoc name={quoteattr(e[1])}/>" for e in run)
        cblc.append("</eblc_index_sub_table_1>")
    cblc.append("</strike></CBLC>")

    with tempfile.NamedTemporaryFile("w", suffix=".ttx", delete=False) as f:
        f.write('<?xml version="1.0" encoding="UTF-8"?>\n<ttFont sfntVersion="\\x00\\x01\\x00\\x00">\n')
        f.write("\n".join(cbdt))
        f.write("\n".join(cblc))
        f.write("</ttFont>\n")
        xml = f.name
    font.importXML(xml)
    # pictures only: no sbix, no outlines (Android would draw those, in black)
    for tag in ("sbix", "glyf", "loca"):
        if tag in font:
            del font[tag]
    font["maxp"].tableVersion = 0x00005000
    font.save(dst)
    return len(entries)


if __name__ == "__main__":
    n = convert(sys.argv[1], sys.argv[2])
    print(f"{sys.argv[2]}: {n} colour pictures (CBDT)")
