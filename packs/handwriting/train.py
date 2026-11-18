#!/usr/bin/env python3
"""Trains Lychee's handwriting recogniser on made-up handwriting (synth.py), for a set time,
carrying on from a checkpoint when one is given (so training adds up over several runs).

  train.py --minutes 330 --checkpoint out/checkpoint.pt [--classes 500] [--workers 3]

The checkpoint holds the weights, the optimiser, the step and the class list. When the class
list has grown or changed (new stroke-order data), what was learnt carries over: the characters
already known keep their weights and the new ones start fresh. A checkpoint of another model
design is not resumed (training starts afresh).

Real handwriting: every real/*.jsonl (one sample per line, as Lychee saves them: Settings >
Advanced > Save handwriting samples, then Diagnostics > Save to a file; {"typed": "字",
"ink": [[x, y, x, y, ...], ...]}) is used too. About one in five (picked by the ink, the same
every run) is kept aside to test on; the rest are mixed into training (each time a little
changed), REAL_SHARE of the batches' samples while there are any.

Accuracy is reported on fixed sets of samples (the same every run): made-up messy and neat
writing, and the real writing kept aside.
"""

from __future__ import annotations

import argparse
import math
import os
import sys
import time
from pathlib import Path

import numpy as np
import torch
import torch.nn.functional as F
from torch.utils.data import DataLoader, IterableDataset

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import ink  # noqa: E402
import synth  # noqa: E402
import templates  # noqa: E402
from model import Recogniser  # noqa: E402

ARCH = "conv4-bilstm160-v1"
BATCH = 192
REAL_SHARE = 0.15


def real_samples(classes: list[str]) -> tuple[list, list]:
    """The real handwriting in real/*.jsonl, as (strokes, label): for training, and to test on."""
    import hashlib
    import json

    index = {c: i for i, c in enumerate(classes)}
    train, test = [], []
    for f in sorted((HERE / "real").glob("*.jsonl")):
        for line in f.read_text(encoding="utf-8").splitlines():
            try:
                d = json.loads(line)
            except ValueError:
                continue
            label = index.get(d.get("typed", ""))
            ink = [np.array(s, dtype=np.float64).reshape(-1, 2) for s in d.get("ink", []) if len(s) >= 2]
            if label is None or not ink:
                continue
            key = hashlib.sha256(line.encode()).digest()[0]
            (test if key < 52 else train).append((ink, label))
    return train, test


def class_list(chars: dict, limit: int | None) -> list[str]:
    classes = sorted(chars)
    if limit:
        rng = np.random.default_rng(7)
        classes = sorted(rng.choice(classes, size=limit, replace=False).tolist())
    return classes


def writings(chars: dict, classes: list[str]) -> list[list]:
    """Each class's model writings, Chinese and Japanese shapes both where there are both."""
    return [[w for lang in sorted(chars[c]) for w in chars[c][lang]] for c in classes]


class Samples(IterableDataset):
    """Endless batches of made-up handwriting, each batch of one length (no padding)."""

    def __init__(self, models: list[list], seed: int, real: list | None = None):
        self.models = models
        self.seed = seed
        self.real = real or []

    def __iter__(self):
        info = torch.utils.data.get_worker_info()
        wid = info.id if info else 0
        rng = np.random.default_rng([self.seed, wid, int(time.time())])
        buckets: dict[int, tuple[list, list]] = {}
        while True:
            if self.real and rng.random() < REAL_SHARE:
                strokes, label = self.real[int(rng.integers(len(self.real)))]
                # the real ink in its own units: to the unit box, then a little changed
                allp = np.concatenate(strokes)
                side = float((allp.max(0) - allp.min(0)).max()) or 1.0
                f = ink.features(synth.write([(s - allp.min(0)) / side for s in strokes], rng, rng.uniform(0.05, 0.5)))
            else:
                label = int(rng.integers(len(self.models)))
                ws = self.models[label]
                f = ink.features(synth.write(ws[int(rng.integers(len(ws)))], rng))
            if f is None:
                continue
            xs, ys = buckets.setdefault(len(f), ([], []))
            xs.append(f)
            ys.append(label)
            if len(xs) == BATCH:
                yield torch.from_numpy(np.stack(xs)), torch.tensor(ys)
                buckets.pop(len(f))


def fixed_set(models: list[list], n: int, neat: bool) -> list[tuple[torch.Tensor, torch.Tensor]]:
    """The same samples every run, grouped by length."""
    rng = np.random.default_rng(1234 if not neat else 4321)
    groups: dict[int, tuple[list, list]] = {}
    for _ in range(n):
        label = int(rng.integers(len(models)))
        ws = models[label]
        f = ink.features(synth.write(ws[int(rng.integers(len(ws)))], rng, 0.7 if neat else None))
        xs, ys = groups.setdefault(len(f), ([], []))
        xs.append(f)
        ys.append(label)
    return [(torch.from_numpy(np.stack(xs)), torch.tensor(ys)) for xs, ys in groups.values()]


@torch.no_grad()
def accuracy(model, batches) -> tuple[float, float]:
    model.eval()
    top1 = top5 = total = 0
    for x, y in batches:
        for i in range(0, len(x), 256):
            logits = model(x[i:i + 256])
            best = logits.topk(5, dim=1).indices
            top1 += (best[:, 0] == y[i:i + 256]).sum().item()
            top5 += (best == y[i:i + 256, None]).any(dim=1).sum().item()
            total += len(best)
    model.train()
    return top1 / total, top5 / total


def lr_at(step: int, peak: float) -> float:
    warm = 2000
    if step < warm:
        return peak * (step + 1) / warm
    return peak * math.sqrt(warm / (step + 1))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--minutes", type=float, default=60)
    ap.add_argument("--checkpoint", type=Path, default=HERE / "out" / "checkpoint.pt")
    ap.add_argument("--classes", type=int, default=0, help="train on this many classes only (a quick test)")
    ap.add_argument("--workers", type=int, default=max(1, (os.cpu_count() or 2) - 1))
    ap.add_argument("--threads", type=int, default=os.cpu_count() or 2)
    ap.add_argument("--peak-lr", type=float, default=2e-3)
    ap.add_argument("--eval-every", type=float, default=20, help="minutes")
    args = ap.parse_args()
    torch.set_num_threads(args.threads)
    deadline = time.time() + args.minutes * 60

    chars = templates.load(HERE / ".cache")
    classes = class_list(chars, args.classes or None)
    models = writings(chars, classes)
    model = Recogniser(len(classes))
    opt = torch.optim.AdamW(model.parameters(), lr=args.peak_lr, weight_decay=0.01)
    step = 0
    seen = 0
    scores: dict[str, float] = {}
    if args.checkpoint.exists():
        ck = torch.load(args.checkpoint, map_location="cpu", weights_only=False)
        if ck.get("arch") == ARCH and ck.get("classes") == classes:
            model.load_state_dict(ck["model"])
            opt.load_state_dict(ck["opt"])
            step, seen = ck["step"], ck.get("seen", 0)
            scores = ck.get("scores", {})
            print(f"resumed at step {step} ({seen:,} samples seen)", flush=True)
        elif ck.get("arch") == ARCH:
            # the class list changed: everything carries over but the last layer, whose rows
            # follow their characters; new characters start fresh (the optimiser too)
            state = ck["model"]
            old = {c: i for i, c in enumerate(ck["classes"])}
            last = [k for k in state if k.startswith("head.") and k.endswith(".weight")][-1]
            bias = last[:-len("weight")] + "bias"
            mine = model.state_dict()
            w, b = mine[last].clone(), mine[bias].clone()
            b[:] = state[bias].mean()
            kept = 0
            for i, c in enumerate(classes):
                j = old.get(c)
                if j is not None:
                    w[i], b[i] = state[last][j], state[bias][j]
                    kept += 1
            state = {k: v for k, v in state.items() if k not in (last, bias)}
            state[last], state[bias] = w, b
            model.load_state_dict(state)
            step, seen = ck["step"], ck.get("seen", 0)
            print(f"resumed at step {step} with a new class list: {kept} of {len(classes)} known", flush=True)
        else:
            print("checkpoint is for another model: starting afresh", flush=True)
    args.checkpoint.parent.mkdir(parents=True, exist_ok=True)

    messy = fixed_set(models, 3000, neat=False)
    neat = fixed_set(models, 1500, neat=True)
    real_train, real_test = real_samples(classes)
    real_groups: dict[int, tuple[list, list]] = {}
    for strokes, label in real_test:
        f = ink.features(strokes)
        xs, ys = real_groups.setdefault(len(f), ([], []))
        xs.append(f)
        ys.append(label)
    real = [(torch.from_numpy(np.stack(xs)), torch.tensor(ys)) for xs, ys in real_groups.values()]
    print(f"real handwriting: {len(real_train)} to train on, {len(real_test)} to test on", flush=True)

    def save():
        tmp = args.checkpoint.with_suffix(".tmp")
        torch.save({"arch": ARCH, "classes": classes, "model": model.state_dict(), "opt": opt.state_dict(),
                    "step": step, "seen": seen, "scores": scores}, tmp)
        tmp.replace(args.checkpoint)

    def report():
        m1, m5 = accuracy(model, messy)
        n1, n5 = accuracy(model, neat)
        scores.update(messy_top1=m1, messy_top5=m5, neat_top1=n1, neat_top5=n5)
        line = f"step {step} seen {seen:,}: messy top1 {m1:.3f} top5 {m5:.3f} | neat top1 {n1:.3f} top5 {n5:.3f}"
        if real:
            r1, r5 = accuracy(model, real)
            scores.update(real_top1=r1, real_top5=r5, real_count=len(real_test))
            line += f" | real top1 {r1:.3f} top5 {r5:.3f} ({len(real_test)})"
        print(line, flush=True)

    loader = DataLoader(Samples(models, seed=step, real=real_train), batch_size=None, num_workers=args.workers,
                        prefetch_factor=4 if args.workers else None, persistent_workers=bool(args.workers))
    started = time.time()
    last_eval = time.time()
    last_log = time.time()
    loss_avg = None
    model.train()
    for x, y in loader:
        for group in opt.param_groups:
            group["lr"] = lr_at(step, args.peak_lr)
        loss = F.cross_entropy(model(x), y, label_smoothing=0.1)
        opt.zero_grad(set_to_none=True)
        loss.backward()
        torch.nn.utils.clip_grad_norm_(model.parameters(), 2.0)
        opt.step()
        step += 1
        seen += len(y)
        loss_avg = loss.item() if loss_avg is None else 0.98 * loss_avg + 0.02 * loss.item()
        now = time.time()
        if now - last_log > 60:
            print(f"step {step} loss {loss_avg:.3f} lr {lr_at(step, args.peak_lr):.2e}", flush=True)
            last_log = now
        if now - last_eval > args.eval_every * 60:
            report()
            save()
            last_eval = time.time()
        if now > deadline:
            break
    report()
    save()


if __name__ == "__main__":
    main()
