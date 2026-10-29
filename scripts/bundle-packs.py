#!/usr/bin/env python3
"""Puts the packs that come with Lychee into the APK's assets.

Handwriting, the Japanese and Korean dictionaries, stroke order and example sentences are
part of the app (only fonts and emoji are downloads): this fetches their newest published
zips (the packs workflow builds them from their sources and publishes them on the lychee
repository's "packs" release), checks each against the manifest's digest, and writes
bundled.json (pack id -> version) beside them for app.lychee.packs.BundledPacks.

Usage: bundle-packs.py <assets/lychee/bundled dir>
"""
import hashlib
import io
import json
import pathlib
import sys
import urllib.request
import zipfile

RELEASE = "https://github.com/lychee-keyboard/lychee/releases/download/packs"
BUNDLED = ["hw_zh", "hw_ja", "rime_ja", "dict_ko", "strokes_zh", "examples"]


def fetch(url: str) -> bytes:
    for attempt in range(4):
        try:
            with urllib.request.urlopen(url, timeout=120) as r:
                return r.read()
        except Exception as e:  # network hiccup: try again
            if attempt == 3:
                raise
            print(f"retrying {url}: {e}", file=sys.stderr)
    raise RuntimeError(url)


def content_digest(data: bytes) -> str:
    """The manifest's digest: the zip's contents without pack.json (data/packs/publish.py)."""
    h = hashlib.sha256()
    with zipfile.ZipFile(io.BytesIO(data)) as z:
        for name in sorted(n for n in z.namelist() if n != "pack.json" and not n.endswith("/")):
            h.update(name.encode() + b"\0")
            h.update(hashlib.sha256(z.read(name)).digest())
    return h.hexdigest()


def main() -> None:
    out = pathlib.Path(sys.argv[1])
    out.mkdir(parents=True, exist_ok=True)
    manifest = json.loads(fetch(f"{RELEASE}/manifest.json"))["packs"]
    versions = {}
    for pack_id in BUNDLED:
        entry = manifest.get(pack_id)
        if not entry:
            print(f"::warning::{pack_id} is not published; Lychee ships without it", file=sys.stderr)
            continue
        data = fetch(entry["url"])
        digest = content_digest(data)
        if entry.get("digest") and digest != entry["digest"]:
            sys.exit(f"{pack_id}: digest mismatch ({digest} != {entry['digest']})")
        (out / f"{pack_id}.zip").write_bytes(data)
        versions[pack_id] = entry["version"]
        print(f"{pack_id} {entry['version']}: {len(data) / 1e6:.1f} MB")
    (out / "bundled.json").write_text(json.dumps(versions, indent=1) + "\n")


if __name__ == "__main__":
    main()
