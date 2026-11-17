"""The ink as the recogniser reads it: the same steps here (training) and in the app
(app/core: app.lychee.core.handwriting.InkFeatures, checked against this by its unit test).

  1. The strokes (x right, y down, any units) are centred on their bounding box and scaled so
     its longer side is 1 (a character keeps its shape: 一 stays flat).
  2. They are resampled along their length to N points in all, N growing with the ink's length
     (STEP apart or so, a multiple of 16 from MIN_POINTS to MAX_POINTS): each stroke gets its
     share by length (a dot one point, any other stroke at least two), ends included.
  3. Each point is read as 6 numbers: x, y, the step from the point before (dx, dy, times 10;
     across a pen lift too, so where the next stroke starts is known), and whether it starts a
     stroke and whether it ends one.

Everything in double precision, then float32, so both sides agree.
"""

from __future__ import annotations

import math

import numpy as np

STEP = 0.04
MIN_POINTS = 32
MAX_POINTS = 256
FEATURES = 6


def point_count(total_length: float, strokes: int) -> int:
    n = math.ceil(total_length / STEP / 16.0) * 16
    n = min(MAX_POINTS, max(MIN_POINTS, n))
    return max(n, 2 * strokes)


def _resample(s: np.ndarray, n: int) -> np.ndarray:
    """n points evenly spaced along the stroke, its ends included. The point for distance t is
    on the first segment whose far end is at least t along (zero-length segments skipped by
    that rule)."""
    if n == 1 or len(s) == 1:
        return np.repeat(s[:1], n, axis=0)
    seg = np.sqrt(((s[1:] - s[:-1]) ** 2).sum(axis=1))
    cum = np.concatenate([[0.0], np.cumsum(seg)])
    t = cum[-1] * np.arange(n) / (n - 1)
    j = np.minimum(np.searchsorted(cum[1:], t, side="left"), len(seg) - 1)
    sj = seg[j]
    f = np.where(sj > 0, np.clip((t - cum[j]) / np.where(sj > 0, sj, 1.0), 0.0, 1.0), 0.0)
    return s[j] + f[:, None] * (s[j + 1] - s[j])


def features(strokes: list) -> np.ndarray | None:
    """[N, 6] float32 for the ink (a list of [n, 2] arrays), or None when there is none."""
    strokes = [np.asarray(s, dtype=np.float64).reshape(-1, 2) for s in strokes]
    strokes = [s for s in strokes if len(s) > 0]
    if not strokes:
        return None
    allp = np.concatenate(strokes)
    lo = allp.min(axis=0)
    hi = allp.max(axis=0)
    side = max(hi[0] - lo[0], hi[1] - lo[1])
    if side <= 0:
        side = 1.0
    centre = (lo + hi) / 2.0
    strokes = [(s - centre) / side for s in strokes]
    # summed in order (cumsum, not numpy's pairwise sum), so the app's sums come out the same
    lengths = [float(np.cumsum(np.sqrt(((s[1:] - s[:-1]) ** 2).sum(axis=1)))[-1]) if len(s) > 1 else 0.0
               for s in strokes]
    total = 0.0
    for L in lengths:
        total += L
    n = point_count(total, len(strokes))
    base = [2 if L > 0 else 1 for L in lengths]
    rest = n - sum(base)
    counts = list(base)
    if rest > 0 and total > 0:
        shares = [rest * L / total for L in lengths]
        whole = [int(math.floor(x)) for x in shares]
        counts = [b + w for b, w in zip(base, whole)]
        left = rest - sum(whole)
        order = sorted(range(len(strokes)), key=lambda i: (-(shares[i] - whole[i]), i))
        for i in order[:left]:
            counts[i] += 1
    elif rest > 0:
        counts[0] += rest
    pts = np.concatenate([_resample(s, c) for s, c in zip(strokes, counts)])
    out = np.zeros((len(pts), FEATURES))
    out[:, 0:2] = pts
    out[1:, 2:4] = (pts[1:] - pts[:-1]) * 10.0
    starts = np.cumsum([0] + counts[:-1])
    out[starts, 4] = 1.0
    out[starts + np.array(counts) - 1, 5] = 1.0
    return out.astype(np.float32)
