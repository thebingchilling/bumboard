#!/usr/bin/env python3
"""Decide which freshly built packs are new, version them, and write the packs manifest.

The packs workflow rebuilds every pack from its sources' newest data. A pack whose contents
are the same as the published one's (compared file by file, ignoring pack.json and zip dates)
is left alone; a changed one gets a new version: its source's own (a font's release or its
maker's version, a dictionary's release date: never the time of the build), or, when Lychee's
build of the same source changed, a revision of it ("2026.11.18-2"), written into its
pack.json, and goes into --upload.

Each published version is also uploaded under a name that says what it is and which version
("lychee-korean-dictionary-2026.11.18.2.zip", the manifest's "download_file"): Lychee downloads
that one, so the file in Downloads is easy to tell apart from an earlier one when it is chosen.

manifest.json lists every published pack: its version, what it is, its size and a digest of
its contents (how the next run tells what changed), and for emoji fonts the Emoji version.
The packs page (packs/site) reads it to tell Lychee users which of their packs have
updates; it is also attached to the "packs" release, where the next run finds it.

  publish.py --packs out/packs --previous manifest.json --upload out/upload --manifest out/manifest.json
"""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import io
import json
import re
import shutil
import sys
import tempfile
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
CATALOGUE = ROOT / "app/latinime/lychee/assets/lychee/packs.json"
RELEASE = "https://github.com/lychee-keyboard/lychee/releases/download/packs/"
FONT = (".ttf", ".otf", ".ttc")

sys.path.insert(0, str(HERE / "fonts"))


def font_bytes(data: bytes) -> bytes:
    """A TrueType/OpenType font with its 'head' table's checksum adjustment and modified time
    zeroed: tools stamp the time into fonts they save, which would make an unchanged font look
    new. Anything that isn't a single sfnt font is returned as it is."""
    if len(data) < 12 or data[:4] not in (b"\x00\x01\x00\x00", b"OTTO", b"true"):
        return data
    count = int.from_bytes(data[4:6], "big")
    for i in range(count):
        rec = 12 + 16 * i
        if data[rec:rec + 4] == b"head":
            off = int.from_bytes(data[rec + 8:rec + 12], "big")
            if off + 36 > len(data):
                return data
            b = bytearray(data)
            b[off + 8:off + 12] = b"\0" * 4    # checkSumAdjustment
            b[off + 28:off + 36] = b"\0" * 8   # modified
            # the head table's own checksum in the directory changes with them
            b[rec + 4:rec + 8] = b"\0" * 4
            return bytes(b)
    return data


# 2: fonts' 'head' timestamps and checksums are left out of the digest (font_bytes)
# 3: so are Rime's recorded build times (rime_bytes)
DIGEST_V = 3


def same_as_published(old: dict, d: str) -> bool:
    """Whether the published file of [old] has digest [d] (the new way): downloaded once, for
    entries whose digest was made the old way."""
    import tempfile
    import urllib.request
    url = old.get("download") or old.get("url")
    if not url:
        return False
    try:
        with tempfile.TemporaryDirectory() as tmp:
            f = Path(tmp) / "published.zip"
            with urllib.request.urlopen(url, timeout=120) as r, open(f, "wb") as out:
                while chunk := r.read(1 << 20):
                    out.write(chunk)
            return digest(f) == d
    except Exception as e:  # unreachable or not a zip: treat the pack as changed
        print(f"{url}: {e}", file=sys.stderr)
        return False


def rime_bytes(name: str, data: bytes) -> bytes:
    """A compiled Rime file without what records when it was built: the build schema's
    __build_info block (its sources' times) and the prism's schema checksum (taken over them).
    The dictionary itself (table, prism, dict checksum) still counts."""
    if name.endswith(".schema.yaml") and "/build/" in name:
        out, skip = [], False
        for line in data.decode("utf-8", "replace").splitlines(True):
            if line.startswith("__build_info:"):
                skip = True
                continue
            if skip and line[:1] not in (" ", "\t"):
                skip = False
            if not skip:
                out.append(line)
        return "".join(out).encode("utf-8")
    if name.endswith(".prism.bin") and len(data) > 40:
        return data[:36] + b"\0" * 4 + data[40:]
    return data


def digest(zip_path: Path) -> str:
    """The pack's contents, without pack.json (which holds the version) or zip dates, and with
    fonts' save timestamps ignored: a pack is only new when what is in it changed."""
    h = hashlib.sha256()
    with zipfile.ZipFile(zip_path) as z:
        for name in sorted(n for n in z.namelist() if n != "pack.json" and not n.endswith("/")):
            data = z.read(name)
            if name.lower().endswith(FONT):
                data = font_bytes(data)
            elif name.startswith("rime/"):
                data = rime_bytes(name, data)
            h.update(name.encode() + b"\0")
            h.update(hashlib.sha256(data).digest())
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


def versioned_name(name: str, version: str) -> str:
    """The file a version is downloaded as: "lychee-korean-dictionary-2026.11.18.2.zip"."""
    slug = re.sub(r"[^a-z0-9]+", "-", name.lower()).strip("-") or "pack"
    return f"lychee-{slug}-{version}.zip"


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
        # a digest made the old way (fonts' save timestamps counted): compare with the published
        # file itself, digested the new way, so a font that only got a new timestamp isn't new
        if old and old.get("digest") != d and old.get("digest_v") != DIGEST_V and same_as_published(old, d):
            old = dict(old, digest=d, digest_v=DIGEST_V)
            packs[pid] = old
        source_now = str(info.get("version", ""))
        if old and old.get("digest") == d and not old.get("source") and source_now and old["version"] != source_now:
            # published under a build date before versions were the sources' own: the same
            # contents take their source's version, and the old one is recorded as the same
            # ("same_as"), so the packs page doesn't offer it as an update
            name = versioned_name(cat.get("file_stem") or cat.get("name", old.get("name", pid)), source_now)
            label = {"id": pid, "name": info.get("name", cat.get("name", pid)), "version": source_now, "source": source_now}
            rewrite(zip_path, args.upload / name, label)
            rewrite(zip_path, args.upload / zip_path.name, label)
            packs[pid] = dict(old, version=source_now, source=source_now, digest_v=DIGEST_V,
                              same_as=[old["version"]] + old.get("same_as", []),
                              download_file=name, download=RELEASE + name)
            changed.append(f"{pid} {old['version']} -> {source_now} (same contents, relabelled)")
            continue
        if old and old.get("digest") == d:
            # published before versioned names: its version uploaded once under one
            if not old.get("download_file"):
                name = versioned_name(cat.get("file_stem") or cat.get("name", old.get("name", pid)), old["version"])
                rewrite(zip_path, args.upload / name, {"id": pid, "name": info.get("name", cat.get("name", pid)),
                                                       "version": old["version"], "source": str(info.get("version", ""))})
                packs[pid] = dict(old, download_file=name, download=RELEASE + name)
                changed.append(f"{pid} {old['version']} (as {name})")
            continue
        # the version is the source's own (a font's release or its maker's version, a dictionary's
        # release date), never this run's time. Lychee's build of the same source changing (new
        # data added from elsewhere, a fix) makes a revision of it: "2026.11.18-2"
        source = str(info.get("version", "")) or (old or {}).get("source") or "1"
        if old and old.get("source") == source:
            rest = old["version"][len(source):] if old["version"].startswith(source) else ""
            n = int(rest[1:]) + 1 if rest.startswith("-") and rest[1:].isdigit() else 2
            version = f"{source}-{n}"
        else:
            version = source
        info = {"id": pid, "name": info.get("name", cat.get("name", pid)), "version": version, "source": source}
        entry = {
            "name": cat.get("name", info["name"]),
            "summary": cat.get("summary", ""),
            "version": version,
            "file": zip_path.name,
            "url": RELEASE + zip_path.name,
            "digest": d,
            "digest_v": DIGEST_V,
            "source": source,
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
        # the same file under a name saying what it is and which version
        # named by the catalogue's file_stem when it has one: the store's friendlier names never
        # change the files people already know
        entry["download_file"] = versioned_name(cat.get("file_stem") or entry["name"], version)
        entry["download"] = RELEASE + entry["download_file"]
        shutil.copyfile(dest, args.upload / entry["download_file"])
        packs[pid] = entry
        changed.append(f"{pid} {version}" + (f" (was {old['version']})" if old else " (new)"))

    # packs no longer in the catalogue drop out of the manifest
    packs = {k: v for k, v in packs.items() if k in catalogue}
    manifest = {
        "updated": dt.datetime.now(dt.timezone.utc).isoformat(timespec="seconds"),
        "release": "https://github.com/lychee-keyboard/lychee/releases/tag/packs",
        "packs": dict(sorted(packs.items())),
    }
    args.manifest.parent.mkdir(parents=True, exist_ok=True)
    args.manifest.write_text(json.dumps(manifest, ensure_ascii=False, indent=1) + "\n")
    print("changed: " + (", ".join(changed) if changed else "nothing"), file=sys.stderr)


if __name__ == "__main__":
    main()
