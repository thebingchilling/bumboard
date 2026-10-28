#!/usr/bin/env python3
"""Decide which freshly built packs are new, version them, and write the packs manifest.

The packs workflow rebuilds every pack from its sources' newest data. A pack whose contents
are the same as the published one's (compared file by file, ignoring pack.json and zip dates)
is left alone; a changed one gets a new version, the date ("2026.10.28", with ".2" and so on
for a second change that day), written into its pack.json, and goes into --upload. A pack the
manifest doesn't list yet keeps the version its build gave it.

manifest.json lists every published pack: its version, what it is, its size and a digest of
its contents (how the next run tells what changed), and for emoji fonts the Emoji version.
The packs page (data/packs/site) reads it to tell Lychee users which of their packs have
updates; it is also attached to the "packs" release, where the next run finds it.

  publish.py --packs out/packs --previous manifest.json --upload out/upload --manifest out/manifest.json
"""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import io
import json
import sys
import tempfile
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
CATALOGUE = ROOT / "latinime/lychee/assets/lychee/packs.json"
RELEASE = "https://github.com/thebingchilling/lychee/releases/download/packs/"
FONT = (".ttf", ".otf", ".ttc")

sys.path.insert(0, str(HERE / "fonts"))


def digest(zip_path: Path) -> str:
    """The pack's contents, without pack.json (which holds the version) or zip dates."""
    h = hashlib.sha256()
    with zipfile.ZipFile(zip_path) as z:
        for name in sorted(n for n in z.namelist() if n != "pack.json" and not n.endswith("/")):
            h.update(name.encode() + b"\0")
            h.update(hashlib.sha256(z.read(name)).digest())
    return h.hexdigest()


def emoji_version(zip_path: Path) -> str | None:
    from emoji_fonts import emoji_version as version

    with zipfile.ZipFile(zip_path) as z, tempfile.TemporaryDirectory() as tmp:
        font = next((n for n in z.namelist() if n.lower().endswith(FONT)), None)
        if not font:
            return None
        path = Path(tmp) / "font"
        path.write_bytes(z.read(font))
        return version(path) or None


def rewrite(src: Path, dest: Path, info: dict) -> None:
    """Copies the zip with a new pack.json."""
    with zipfile.ZipFile(src) as zin, zipfile.ZipFile(dest, "w", zipfile.ZIP_DEFLATED) as zout:
        zout.writestr("pack.json", json.dumps(info, ensure_ascii=False))
        for item in zin.infolist():
            if item.filename != "pack.json":
                zout.writestr(item, zin.read(item.filename))


def next_version(previous: str | None, today: str) -> str:
    if not previous or not previous.startswith(today):
        return today
    rest = previous[len(today):].lstrip(".")
    return f"{today}.{int(rest) + 1 if rest.isdigit() else 2}"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--packs", type=Path, required=True)
    ap.add_argument("--previous", type=Path)
    ap.add_argument("--upload", type=Path, required=True)
    ap.add_argument("--manifest", type=Path, required=True)
    args = ap.parse_args()
    args.upload.mkdir(parents=True, exist_ok=True)

    previous = {}
    if args.previous and args.previous.exists():
        previous = json.loads(args.previous.read_text()).get("packs", {})
    catalogue = {p["id"]: p for p in json.loads(CATALOGUE.read_text())["packs"]}
    today = dt.datetime.now(dt.timezone.utc).strftime("%Y.%m.%d")
    packs = dict(previous)
    changed = []

    for zip_path in sorted(args.packs.glob("lychee-*.zip")):
        with zipfile.ZipFile(zip_path) as z:
            info = json.loads(z.read("pack.json"))
        pid = info["id"]
        cat = catalogue.get(pid, {})
        d = digest(zip_path)
        old = previous.get(pid)
        if old and old.get("digest") == d:
            continue
        # what the build called it (a font's release tag, a builder's version), for the notes
        source = str(info.get("version", ""))
        # a pack's first listing keeps the version it was already published with, so packs
        # installed before the manifest existed don't all show an update
        version = next_version(old["version"], today) if old else (source or today)
        info = {"id": pid, "name": info.get("name", cat.get("name", pid)), "version": version, "source": source}
        entry = {
            "name": cat.get("name", info["name"]),
            "summary": cat.get("summary", ""),
            "version": version,
            "file": zip_path.name,
            "url": RELEASE + zip_path.name,
            "digest": d,
            "updated": today,
        }
        if cat.get("kind") == "font_emoji":
            ev = emoji_version(zip_path)
            if ev:
                info["emoji"] = entry["emoji"] = ev
        if cat.get("kind", "").startswith("font"):
            entry["note"] = f"Version {source}" if source else "Newest release"
        else:
            entry["note"] = "Rebuilt with the latest data from its sources"
        dest = args.upload / zip_path.name
        rewrite(zip_path, dest, info)
        entry["size"] = dest.stat().st_size
        packs[pid] = entry
        changed.append(f"{pid} {version}" + (f" (was {old['version']})" if old else " (new)"))

    # packs no longer in the catalogue drop out of the manifest
    packs = {k: v for k, v in packs.items() if k in catalogue}
    manifest = {
        "updated": dt.datetime.now(dt.timezone.utc).isoformat(timespec="seconds"),
        "release": "https://github.com/thebingchilling/lychee/releases/tag/packs",
        "packs": dict(sorted(packs.items())),
    }
    args.manifest.parent.mkdir(parents=True, exist_ok=True)
    args.manifest.write_text(json.dumps(manifest, ensure_ascii=False, indent=1) + "\n")
    print("changed: " + (", ".join(changed) if changed else "nothing"), file=sys.stderr)


if __name__ == "__main__":
    main()
