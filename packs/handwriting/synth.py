"""Handwriting made up from model writings (templates.py): each sample is a character written
as a person might write it on a phone, never twice the same.

What varies, each by a random amount: the whole character's slant, rotation, width and height;
each stroke's place, size and angle; a gentle warp of the whole (a hand's own proportions);
stroke ends shortened, lengthened or hooked; neighbouring strokes joined without lifting the pen
(running script), now and then two strokes written in the other order or one written backwards;
a finger's wobble, rounded corners and the touch screen's jitter.
"""

from __future__ import annotations

import math

import numpy as np


def _densify(s: np.ndarray, spacing: float = 0.01) -> np.ndarray:
    s = np.asarray(s, dtype=np.float64)
    if len(s) < 2:
        return s
    d = s[1:] - s[:-1]
    n = np.maximum(1, (np.hypot(d[:, 0], d[:, 1]) / spacing).astype(int))
    seg = np.repeat(np.arange(len(d)), n)
    k = np.arange(n.sum()) - np.repeat(np.cumsum(n) - n, n) + 1
    pts = s[seg] + d[seg] * (k / n[seg])[:, None]
    return np.concatenate([s[:1], pts])


def _rot(theta: float) -> np.ndarray:
    c, s = math.cos(theta), math.sin(theta)
    return np.array([[c, -s], [s, c]])


def _extend(s: np.ndarray, amount: float, at_end: bool) -> np.ndarray:
    """Lengthens (amount > 0) or shortens (< 0) a stroke at one end, by a share of its length."""
    if len(s) < 3:
        return s
    seg = np.hypot(*(s[1:] - s[:-1]).T)
    total = seg.sum()
    if total <= 0:
        return s
    if amount < 0:
        cut = -amount * total
        if at_end:
            cum = np.cumsum(seg[::-1])
            k = int(np.searchsorted(cum, cut))
            return s[: max(2, len(s) - k)]
        cum = np.cumsum(seg)
        k = int(np.searchsorted(cum, cut))
        return s[min(k, len(s) - 2):]
    a, b = (s[-2], s[-1]) if at_end else (s[1], s[0])
    d = b - a
    n = np.hypot(*d)
    if n <= 0:
        return s
    tip = b + d / n * amount * total
    extra = _densify(np.array([b, tip]))[1:]
    return np.concatenate([s, extra]) if at_end else np.concatenate([extra[::-1], s])


def _smooth(s: np.ndarray, k: int) -> np.ndarray:
    if k <= 1 or len(s) < 2 * k + 1:
        return s
    pad = np.concatenate([np.repeat(s[:1], k, 0), s, np.repeat(s[-1:], k, 0)])
    kernel = np.ones(2 * k + 1) / (2 * k + 1)
    return np.stack([np.convolve(pad[:, 0], kernel, "valid"), np.convolve(pad[:, 1], kernel, "valid")], 1)


def _link(a: np.ndarray, b: np.ndarray, rng: np.random.Generator) -> np.ndarray:
    """The pen's path from one stroke's end to the next stroke's start without lifting: a curve
    bowing to one side (now and then a little loop), the way running script joins strokes."""
    d = b - a
    gap = float(np.hypot(*d))
    normal = np.array([-d[1], d[0]]) / (gap or 1.0)
    ctrl = (a + b) / 2 + normal * gap * rng.normal(0, 0.35) + rng.normal(0, 0.02, 2)
    if rng.random() < 0.12:
        ctrl = ctrl + normal * gap * rng.choice([-1, 1]) * rng.uniform(0.6, 1.2)
    n = max(3, int(gap / 0.01))
    t = np.linspace(0, 1, n)[1:, None]
    return (1 - t) ** 2 * a + 2 * (1 - t) * t * ctrl + t ** 2 * b


def style(rng: np.random.Generator) -> float:
    """How messy this sample is: most people's writing is hurried, much of it bad; neat
    textbook writing is the exception."""
    r = rng.random()
    if r < 0.15:
        return rng.uniform(0.5, 1.0)   # neat
    if r < 0.60:
        return rng.uniform(1.0, 1.7)   # casual, hurried
    return rng.uniform(1.7, 2.3)       # messy, scrawled


def write(template: list[np.ndarray], rng: np.random.Generator, strength: float | None = None) -> list[np.ndarray]:
    """One handwritten sample of a model writing (a list of strokes, unit box, y down)."""
    g = style(rng) if strength is None else strength
    strokes = [np.asarray(s, dtype=np.float64) for s in template]
    # the order often not the textbook's (learners especially), a stroke now and then backwards
    for _ in range(int(rng.poisson(0.12 * g))):
        if len(strokes) > 1:
            i = int(rng.integers(len(strokes) - 1))
            strokes[i], strokes[i + 1] = strokes[i + 1], strokes[i]
    for _ in range(int(rng.poisson(0.05 * g))):
        i = int(rng.integers(len(strokes)))
        strokes[i] = strokes[i][::-1]
    out = []
    for s in strokes:
        s = _densify(s)
        length = float(np.hypot(*(s[1:] - s[:-1]).T).sum()) if len(s) > 1 else 0.0
        # a short stroke (a dot, a tick) dashed off any old way, or left out altogether
        if length < 0.12 and len(strokes) > 3 and rng.random() < 0.03 * g:
            continue
        if length < 0.12 and rng.random() < 0.25 * g:
            ang = rng.uniform(0, 2 * math.pi)
            tip = s[0] + np.array([math.cos(ang), math.sin(ang)]) * rng.uniform(0.01, 0.08)
            s = _densify(np.array([s[0], tip]))
        # each stroke off: place, size, angle
        c = s.mean(axis=0)
        m = _rot(rng.normal(0, 0.06 * g)) * (1 + rng.normal(0, 0.08 * g))
        s = (s - c) @ m.T + c + rng.normal(0, 0.02 * g, 2)
        # ends shorter or longer, often a hook or flick where the pen lifts
        s = _extend(s, float(np.clip(rng.normal(0, 0.07 * g), -0.4, 0.4)), True)
        s = _extend(s, float(np.clip(rng.normal(0, 0.05 * g), -0.4, 0.4)), False)
        if rng.random() < 0.1 * g and len(s) > 1:
            ang = rng.uniform(0, 2 * math.pi)
            tail = s[-1] + np.array([math.cos(ang), math.sin(ang)]) * rng.uniform(0.015, 0.06)
            s = np.concatenate([s, _densify(np.array([s[-1], tail]))[1:]])
        out.append(s)
    if not out:
        out = [_densify(np.asarray(template[0], dtype=np.float64))]
    # running script, the usual way people write fast: neighbouring strokes joined without
    # lifting the pen, more the messier the hand
    rate = 0.0 if rng.random() < 0.3 / g else min(0.95, rng.uniform(0.2, 0.6) * g)
    if rate > 0 and len(out) > 1:
        joined = [out[0]]
        for s in out[1:]:
            prev = joined[-1]
            gap = float(np.hypot(*(s[0] - prev[-1])))
            if gap < 0.7 and rng.random() < rate:
                joined[-1] = np.concatenate([prev, _link(prev[-1], s[0], rng), s[1:]])
            else:
                joined.append(s)
        out = joined
    allp = np.concatenate(out)
    c = (allp.min(0) + allp.max(0)) / 2
    # a warp of the whole: a hand's own proportions, parts too big or too small
    amp = rng.uniform(0.0, 0.03) * g
    waves = [(rng.uniform(0.5, 2.0, 2), rng.uniform(0, 2 * math.pi), rng.normal(0, 1, 2)) for _ in range(3)]

    def warp(p):
        d = np.zeros_like(p)
        for f, ph, a in waves:
            d += np.outer(np.sin(2 * math.pi * (p @ f) + ph), a)
        return p + d * amp / 3

    # the whole character: rotation, slant, width and height
    m = _rot(rng.normal(0, 0.06 * g)) @ np.array([[1, rng.normal(0, 0.12 * g)], [0, 1]]) @ np.diag(
        np.exp(rng.normal(0, 0.1 * g, 2)))
    wobble = rng.uniform(0.0, 0.008) * g
    # hurried writing rounds every corner off
    corners = int(rng.integers(1, 2 + int(3 * g)))
    noise = rng.uniform(0.0, 0.0025)
    res = []
    for s in out:
        s = warp(s - c) @ m.T
        if wobble > 0 and len(s) > 4:
            walk = np.cumsum(rng.normal(0, 1, (len(s), 2)), 0)
            walk = _smooth(walk - np.linspace(walk[0], walk[-1], len(s)), 4)
            scale = np.abs(walk).max() or 1.0
            s = s + walk / scale * wobble
        s = _smooth(s, corners)
        s = s + rng.normal(0, noise, s.shape)
        # the touch screen's sampling: points every so often, not evenly
        if len(s) > 3:
            keep = np.sort(rng.choice(len(s), size=max(2, int(len(s) * rng.uniform(0.3, 1.0))), replace=False))
            keep[0], keep[-1] = 0, len(s) - 1
            s = s[np.unique(keep)]
        res.append(s)
    return res
