#!/usr/bin/env python3
"""Build BumBoard's emoji data.

Outputs (in --out, default bumboard/data/out; Gradle packages out/assets as APK assets):
  assets/bumboard/emoji.json             groups of emoji in Unicode order, with skin-tone
                                         variants folded under their base emoji and search
                                         keywords (English, Cantonese, Chinese)
  assets/bumboard/NotoColorEmoji.ttf     colour emoji font covering every emoji in the list

emoji.json:
  {"version": "18.0",
   "groups": [{"id": "smileys", "items": [[emoji, name, keywords, tones?], ...]}, ...]}
  tones is absent for emoji without skin tones, a 5-item list (light .. dark) for one person,
  or a 25-item list for two people: index (a - 1) * 5 + (b - 1) for tones a and b.

Every input is pinned in emoji_sources.json.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import sys
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path

HERE = Path(__file__).resolve().parent

GROUPS = {
    "Smileys & Emotion": "smileys",
    "People & Body": "people",
    "Animals & Nature": "animals",
    "Food & Drink": "food",
    "Travel & Places": "travel",
    "Activities": "activities",
    "Objects": "objects",
    "Symbols": "symbols",
    "Flags": "flags",
}
TONES = {
    "light skin tone": 1,
    "medium-light skin tone": 2,
    "medium skin tone": 3,
    "medium-dark skin tone": 4,
    "dark skin tone": 5,
}
CLDR_LANGS = ["en", "yue", "zh_Hant", "zh"]


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def fetch(cache: Path) -> dict[str, Path]:
    spec = json.loads((HERE / "emoji_sources.json").read_text())
    cache.mkdir(parents=True, exist_ok=True)
    paths = {}
    for name, s in spec.items():
        if name.startswith("_"):
            continue
        dest = cache / name
        if not dest.exists() or sha256(dest) != s["sha256"]:
            print(f"downloading {s['url']}", file=sys.stderr)
            tmp = dest.with_suffix(".part")
            with urllib.request.urlopen(s["url"]) as r, open(tmp, "wb") as f:
                shutil.copyfileobj(r, f)
            tmp.rename(dest)
        if sha256(dest) != s["sha256"]:
            sys.exit(f"sha256 mismatch for {name}")
        paths[name] = dest
    return paths


def annotations(paths: list[Path]) -> dict[str, list[str]]:
    """emoji (without FE0F) -> keywords and names, in file order."""
    out: dict[str, list[str]] = {}
    for path in paths:
        root = ET.parse(path).getroot()
        for a in root.iter("annotation"):
            cp = a.get("cp", "").replace("️", "")
            text = (a.text or "").strip()
            if not cp or not text or text == "↑↑↑":
                continue
            words = [text] if a.get("type") == "tts" else [w.strip() for w in text.split("|")]
            bucket = out.setdefault(cp, [])
            bucket.extend(w for w in words if w and w not in bucket)
    return out


def parse_name(name: str) -> tuple[str, list[int]]:
    """'kiss: woman, man, light skin tone' -> ('kiss: woman, man', [1])."""
    if ": " not in name:
        return name, []
    head, tail = name.split(": ", 1)
    attrs = tail.split(", ")
    tones = [TONES[a] for a in attrs if a in TONES]
    rest = [a for a in attrs if a not in TONES]
    return (f"{head}: {', '.join(rest)}" if rest else head), tones


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", type=Path, default=HERE / "out")
    ap.add_argument("--cache", type=Path, default=HERE / ".cache" / "emoji")
    args = ap.parse_args()
    src = fetch(args.cache)
    assets = args.out / "assets" / "bumboard"
    assets.mkdir(parents=True, exist_ok=True)

    words: dict[str, list[str]] = {}
    for lang in CLDR_LANGS:
        for kind in ("cldr", "cldr-derived"):
            for cp, ws in annotations([src[f"{kind}-{lang}.xml"]]).items():
                bucket = words.setdefault(cp, [])
                bucket.extend(w for w in ws if w not in bucket)

    line_re = re.compile(r"^([0-9A-F ]+?)\s*;\s*([a-z-]+)\s*#\s*(\S+)\s+E[\d.]+\s+(.+)$")
    groups: list[dict] = []
    group = None
    bases: dict[str, list] = {}  # base name -> item, within the current subgroup
    variants: dict[int, dict[tuple[int, ...], str]] = {}
    for line in src["emoji-test.txt"].read_text(encoding="utf-8").splitlines():
        if line.startswith("# group: "):
            title = line[len("# group: "):].strip()
            group = {"id": GROUPS[title], "items": []} if title in GROUPS else None
            if group:
                groups.append(group)
            continue
        if line.startswith("# subgroup: "):
            bases = {}
            continue
        m = line_re.match(line)
        if not m or group is None or m.group(2) != "fully-qualified":
            continue
        emoji = "".join(chr(int(c, 16)) for c in m.group(1).split())
        name = m.group(4).strip()
        base_name, tones = parse_name(name)
        if tones and base_name not in bases and base_name.endswith(": person, person"):
            # 'kiss: person, person, light skin tone, dark skin tone' belongs to 'kiss'
            base_name = base_name[: -len(": person, person")]
        if tones and base_name in bases:
            variants.setdefault(id(bases[base_name]), {})[tuple(tones)] = emoji
            continue
        key = emoji.replace("️", "")
        kw = words.get(key, [])
        keywords = " ".join(dict.fromkeys([name] + kw)).lower()
        item = [emoji, name, keywords]
        group["items"].append(item)
        bases[name] = item

    for g in groups:
        for item in g["items"]:
            v = variants.get(id(item))
            if not v:
                continue
            if any(len(k) == 2 for k in v):
                tones = []
                for a in range(1, 6):
                    for b in range(1, 6):
                        tones.append(v.get((a, b)) or v.get((a,)) if a == b else v.get((a, b), ""))
            else:
                tones = [v.get((t,), "") for t in range(1, 6)]
            if all(tones):
                item.append(tones)
            else:
                print(f"incomplete tones for {item[1]}: {len([t for t in tones if t])}", file=sys.stderr)
                item.append([t or item[0] for t in tones])

    data = {"version": "18.0", "groups": groups}
    (assets / "emoji.json").write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")),
                                       encoding="utf-8")
    shutil.copy(src["NotoColorEmoji.ttf"], assets / "NotoColorEmoji.ttf")
    total = sum(len(g["items"]) for g in groups)
    toned = sum(1 for g in groups for i in g["items"] if len(i) > 3)
    print(f"emoji.json: {total} emoji ({toned} with skin tones) in {len(groups)} groups", file=sys.stderr)


if __name__ == "__main__":
    main()
