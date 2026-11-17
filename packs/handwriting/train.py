#!/usr/bin/env python3
"""Trains Lychee's handwriting recogniser on made-up handwriting (synth.py), for a set time,
carrying on from a checkpoint when one is given (so training adds up over several runs).

  train.py --minutes 330 --checkpoint out/checkpoint.pt [--classes 500] [--workers 3]

The checkpoint holds the weights, the optimiser, the step and the class list; one made for a
different class list or model is not resumed (training starts afresh). Accuracy is reported on
a fixed set of samples (the same every run), messy and neat.
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

    def __init__(self, models: list[list], seed: int):
        self.models = models
        self.seed = seed

    def __iter__(self):
        info = torch.utils.data.get_worker_info()
        wid = info.id if info else 0
        rng = np.random.default_rng([self.seed, wid, int(time.time())])
        buckets: dict[int, tuple[list, list]] = {}
        while True:
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
        else:
            print("checkpoint is for another model or class list: starting afresh", flush=True)
    args.checkpoint.parent.mkdir(parents=True, exist_ok=True)

    messy = fixed_set(models, 3000, neat=False)
    neat = fixed_set(models, 1500, neat=True)

    def save():
        tmp = args.checkpoint.with_suffix(".tmp")
        torch.save({"arch": ARCH, "classes": classes, "model": model.state_dict(), "opt": opt.state_dict(),
                    "step": step, "seen": seen, "scores": scores}, tmp)
        tmp.replace(args.checkpoint)

    def report():
        m1, m5 = accuracy(model, messy)
        n1, n5 = accuracy(model, neat)
        scores.update(messy_top1=m1, messy_top5=m5, neat_top1=n1, neat_top5=n5)
        print(f"step {step} seen {seen:,}: messy top1 {m1:.3f} top5 {m5:.3f} | neat top1 {n1:.3f} top5 {n5:.3f}",
              flush=True)

    loader = DataLoader(Samples(models, seed=step), batch_size=None, num_workers=args.workers,
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
