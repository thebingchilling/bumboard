#!/usr/bin/env python3
"""Mirror the font and emoji packs in Lychee's catalogue as one-tap downloads.

The fonts' own release pages list many files, which is hard to pick from on a phone. This
fetches each font named in latinime/lychee/assets/lychee/packs.json (its latest release,
the file matching 'pick', ties broken by 'prefer') and writes lychee-<id>.zip: the font, its
licence when the release has one, and a pack.json so Lychee knows which pack it is. The
packs workflow attaches these to the "packs" release; the catalogue's 'download' links point
there. Uses GH_TOKEN for the GitHub API when set.
"""

from __future__ import annotations

import argparse
import io
import json
import os
import re
import sys
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
CATALOGUE = ROOT / "latinime/lychee/assets/lychee/packs.json"
FONT = re.compile(r"(?i)\.(ttf|otf)$")
LICENCE = re.compile(r"(?i)(^|/)(OFL|LICEN[CS]E)[^/]*$")


def get(url: str, api: bool = False) -> bytes:
    headers = {"User-Agent": "lychee-packs"}
    token = os.environ.get("GH_TOKEN")
    if api:
        headers["Accept"] = "application/vnd.github+json"
    if token and ("api.github.com" in url or api):
        headers["Authorization"] = f"Bearer {token}"
    with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=600) as r:
        return r.read()


def best(names: list[str], pick: re.Pattern, prefer: re.Pattern | None) -> str | None:
    matches = [n for n in names if pick.search(n)]
    if not matches:
        return None
    if prefer:
        preferred = [n for n in matches if prefer.search(n)]
        if preferred:
            matches = preferred
    return sorted(matches, key=len)[0]


def from_zip(data: bytes, pick, prefer):
    """(font name, font bytes, licence bytes or None) from a zip, or None."""
    with zipfile.ZipFile(io.BytesIO(data)) as z:
        names = [n for n in z.namelist() if not n.endswith("/") and "__MACOSX" not in n]
        name = best([n for n in names if FONT.search(n)], pick, prefer)
        if not name:
            return None
        licence = next((z.read(n) for n in names if LICENCE.search(n)), None)
        return Path(name).name, z.read(name), licence


def fetch(pack: dict):
    """(font name, font bytes, licence bytes or None, version) for a catalogue font."""
    pick = re.compile(pack.get("pick") or r"\.(ttf|otf)$")
    prefer = re.compile(pack["prefer"]) if pack.get("prefer") else None
    url = pack["url"]
    m = re.match(r"https://github\.com/([^/]+)/([^/]+)/releases/latest", url)
    if not m:
        # a direct link to the font itself
        data = get(url)
        return Path(url).name, data, None, "latest"
    release = json.loads(get(f"https://api.github.com/repos/{m[1]}/{m[2]}/releases/latest", api=True))
    tag = release.get("tag_name", "latest")
    assets = {a["name"]: a["browser_download_url"] for a in release.get("assets", [])}
    licence = None
    for name, link in assets.items():
        if LICENCE.search(name):
            licence = get(link)
    name = best([n for n in assets if FONT.search(n)], pick, prefer)
    if name:
        return name, get(assets[name]), licence, tag
    # the font is inside one of the zips: try the likeliest first
    zips = [n for n in assets if n.lower().endswith(".zip")]
    zips.sort(key=lambda n: (0 if prefer and prefer.search(n) else 1, 0 if re.search(r"(?i)ttf|otf", n) else 1, len(n)))
    for z in zips:
        found = from_zip(get(assets[z]), pick, prefer)
        if found:
            return found[0], found[1], found[2] or licence, tag
    raise SystemExit(f"{pack['id']}: no font matching {pick.pattern} in {url}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", type=Path, required=True)
    args = ap.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)
    packs = json.loads(CATALOGUE.read_text())["packs"]
    for pack in packs:
        if pack["kind"] not in ("font_zh", "font_emoji") or pack.get("custom"):
            continue
        name, font, licence, version = fetch(pack)
        if not licence:
            # every published zip carries its licence: the catalogue's when the release has no file
            licence = (f"{pack['name']}\n{pack.get('credit', '')}\nLicence: {pack.get('license', '')}\n"
                       f"Source: {pack['url']}\n").encode()
        dest = args.out / f"lychee-{pack['id'].replace('_', '-')}.zip"
        with zipfile.ZipFile(dest, "w", zipfile.ZIP_DEFLATED) as z:
            z.writestr("pack.json", json.dumps({"id": pack["id"], "name": pack["name"], "version": version}, ensure_ascii=False))
            z.writestr(name, font)
            if licence:
                z.writestr("LICENSE.txt", licence)
        print(f"{pack['id']}: {name} {version} -> {dest.name} ({dest.stat().st_size >> 20} MB)", file=sys.stderr)


if __name__ == "__main__":
    main()
