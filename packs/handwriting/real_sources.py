"""Real handwriting from open datasets whose licences allow commercial use, as single
characters (strokes in writing order, y down) with their labels:

  rmpp-hwr-db    Korean (and the digits and English it mixes in), written one character per
                 cell on a reMarkable Paper Pro by its community; CC BY 4.0
                 (https://github.com/lorancecall/rmpp-hwr-db, "rmpp-hwr-db contributors")
  hangul.ink     4,568 lines of Korean written with a Neo Smartpen by Jo YW; CC BY-SA 4.0
                 (https://huggingface.co/datasets/jbaudanza/korean-handwriting-strokes).
                 Lines are cut into syllables here: a Hangul syllable is written whole before
                 the next, so the line's strokes split, in order, into as many runs as the line
                 has syllables, at the places that keep each run narrow and apart from the next;
                 lines that don't split cleanly are left out.

load(cache) -> {source: [(strokes, char), ...]}
"""

from __future__ import annotations

import json
import sys
import urllib.request
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / "dict"))
from build_dicts import fetch  # noqa: E402

RMPP_TREE = "https://huggingface.co/api/datasets/lorancecall/rmpp-hwr-db/tree/main/data?recursive=true"
RMPP_FILE = "https://huggingface.co/datasets/lorancecall/rmpp-hwr-db/resolve/main/{}"
HANGUL_INK = "https://huggingface.co/datasets/jbaudanza/korean-handwriting-strokes/resolve/main/data/recordings.parquet"


def _get(url: str) -> bytes:
    with urllib.request.urlopen(url, timeout=120) as r:
        return r.read()


def rmpp(cache: Path) -> list:
    """Every filled cell of every page: (strokes, the character printed in it)."""
    out = []
    d = cache / "rmpp"
    d.mkdir(parents=True, exist_ok=True)
    try:
        files = [f["path"] for f in json.loads(_get(RMPP_TREE)) if f["type"] == "file" and "/page-" in f["path"]]
    except Exception as e:  # offline: whatever was fetched before
        print(f"rmpp-hwr-db: can't list ({e}); using the cache", file=sys.stderr)
        files = []
    for path in files:
        local = d / path.replace("/", "_")
        if not local.exists():
            local.write_bytes(_get(RMPP_FILE.format(path)))
    for local in sorted(d.glob("*.json")):
        page = json.loads(local.read_text(encoding="utf-8"))
        strokes = page.get("strokes", [])
        items = page.get("items", [])
        for item, assign in zip(items, page.get("assign", [])):
            if item.get("kind") != "row":
                continue
            for cell, idx in zip(item.get("cells", []), assign.get("cells", []) or []):
                ch = cell.get("char", "")
                ink = [np.array([[p[0], p[1]] for p in strokes[i]["points"]], dtype=np.float64)
                       for i in idx if i < len(strokes) and strokes[i].get("points")]
                if len(ch) == 1 and ink:
                    out.append((ink, ch))
    return out


def _split(strokes: list, n: int):
    """The line's strokes cut, in order, into n runs (one per syllable), or None. A run's cost
    is its width in line heights, squared (runs stay syllable-sized), plus how far it starts
    left of where the stroke before it ended (each syllable is right of the last)."""
    m = len(strokes)
    if n == 0 or m < n:
        return None
    lo = np.array([s[:, 0].min() for s in strokes])
    hi = np.array([s[:, 0].max() for s in strokes])
    top = min(s[:, 1].min() for s in strokes)
    bottom = max(s[:, 1].max() for s in strokes)
    height = max(bottom - top, 1e-6)
    INF = np.inf
    # cost[i, j]: strokes i..j-1 as one run (i < j)
    cost = np.full((m + 1, m + 1), INF)
    for i in range(m):
        width = (np.maximum.accumulate(hi[i:]) - np.minimum.accumulate(lo[i:])) / height
        start = 4 * max(0.0, hi[i - 1] - lo[i]) / height if i > 0 else 0.0
        cost[i, i + 1:] = width ** 2 + start
    best = np.full(m + 1, INF)
    best[0] = 0.0
    backs = []
    for _ in range(n):
        total = best[:, None] + cost
        backs.append(total.argmin(axis=0))
        best = total.min(axis=0)
    if not np.isfinite(best[m]):
        return None
    cuts = [m]
    for back in reversed(backs):
        cuts.append(int(back[cuts[-1]]))
    cuts = cuts[::-1]
    if cuts[0] != 0:
        return None
    runs = [strokes[cuts[k]:cuts[k + 1]] for k in range(n)]
    # clean only: every run no wider than a syllable could be, and apart from the next
    for a, b in zip(runs, runs[1:]):
        if max(s[:, 0].max() for s in a) - min(s[:, 0].min() for s in b) > 0.25 * height:
            return None
    for r in runs:
        if not r or (max(s[:, 0].max() for s in r) - min(s[:, 0].min() for s in r)) > 1.5 * height:
            return None
    return runs


def hangul_ink(cache: Path) -> list:
    import pyarrow.parquet as pq

    path = fetch(HANGUL_INK, cache, "hangul-ink-recordings.parquet")
    out = []
    lines = kept = 0
    table = pq.read_table(path, columns=["text", "strokes"])
    for text, strokes in zip(table.column("text").to_pylist(), table.column("strokes").to_pylist()):
        lines += 1
        chars = [c for c in text if not c.isspace()]
        ink = [np.stack([np.array(s["x"], dtype=np.float64), np.array(s["y"], dtype=np.float64)], 1)
               for s in strokes if s["x"]]
        # long lines have too many ways to go wrong to split cheaply
        if len(chars) > 24 or len(ink) > 200:
            continue
        runs = _split(ink, len(chars))
        if runs is None:
            continue
        kept += 1
        out.extend((r, c) for r, c in zip(runs, chars))
    print(f"hangul.ink: {kept} of {lines} lines split into syllables, {len(out)} characters", file=sys.stderr)
    return out


def load(cache: Path) -> dict[str, list]:
    cache.mkdir(parents=True, exist_ok=True)
    out = {}
    for name, fn in (("rmpp-hwr-db", rmpp), ("hangul.ink", hangul_ink)):
        try:
            out[name] = fn(cache)
        except Exception as e:
            print(f"{name}: not used ({e})", file=sys.stderr)
    return out


if __name__ == "__main__":
    for k, v in load(HERE / ".cache").items():
        print(k, len(v), "characters", len({c for _, c in v}), "distinct")
