#!/usr/bin/env python3
"""Turns a training checkpoint into the handwriting pack the app reads:

  lychee-handwriting.zip
    pack.json
    data/handwriting.onnx   the recogniser (int8 weights), input "ink" [1, points, 6] (ink.py),
                            output "logits" [1, classes]
    data/handwriting.txt    the classes, one character per line, in the model's order
    LICENSE.txt and the data's licences

  export.py --checkpoint out/checkpoint.pt --out out/packs
"""

from __future__ import annotations

import argparse
import json
import sys
import zipfile
from pathlib import Path

import numpy as np
import torch

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent / "dict"))
import ink  # noqa: E402
from model import Recogniser  # noqa: E402

PACK_VERSION = 1

LICENCE = """Lychee handwriting recogniser.

A neural network trained by Lychee on handwriting it made up from stroke-order data; no
handwriting by any person, and no other recogniser, went into it.

The stroke-order data it learnt from:
- Chinese: Make Me a Hanzi by Shaunak Kishore (https://github.com/skishore/makemeahanzi),
  graphics derived from fonts by Arphic Technology, Arphic Public License (APL.txt).
- Japanese: KanjiVG by Ulrich Apel (https://kanjivg.tagaini.net), CC BY-SA 3.0
  (https://creativecommons.org/licenses/by-sa/3.0/).
- Korean: made by Lychee from each letter's standard stroke order.

The model is shared under CC BY-SA 4.0 (https://creativecommons.org/licenses/by-sa/4.0/).
"""


# a model reading less than this share of the messy test samples right is not published: the
# app would rather keep PP-OCR than use a half-trained recogniser
READY = 0.8


def export(checkpoint: Path, out: Path, work: Path, force: bool = False) -> Path | None:
    from onnxruntime.quantization import QuantType, quantize_dynamic
    import onnxruntime as ort

    ck = torch.load(checkpoint, map_location="cpu", weights_only=False)
    classes = ck["classes"]
    score = ck.get("scores", {}).get("messy_top1", 0.0)
    print(f"checkpoint: step {ck['step']}, {ck.get('seen', 0):,} samples, scores {ck.get('scores')}", file=sys.stderr)
    if score < READY and not force:
        print(f"::warning::the handwriting model reads {score:.0%} of messy samples right (needs {READY:.0%}); "
              "not published yet, train it longer", file=sys.stderr)
        return None
    model = Recogniser(len(classes))
    model.load_state_dict(ck["model"])
    model.eval()
    work.mkdir(parents=True, exist_ok=True)
    full = work / "handwriting-fp32.onnx"
    example = torch.zeros(1, 64, ink.FEATURES)
    torch.onnx.export(model, (example,), str(full), input_names=["ink"], output_names=["logits"],
                      dynamic_axes={"ink": {1: "points"}}, opset_version=17, dynamo=False)
    small = work / "handwriting.onnx"
    quantize_dynamic(str(full), str(small), weight_type=QuantType.QInt8)

    # the int8 model must still agree with the trained one
    rng = np.random.default_rng(0)
    session = ort.InferenceSession(str(small))
    agree = 0
    for n in (32, 64, 128, 256):
        x = rng.normal(0, 0.3, (1, n, ink.FEATURES)).astype(np.float32)
        x[0, :, 4:] = (rng.random((n, 2)) < 0.1)
        with torch.no_grad():
            want = model(torch.from_numpy(x)).argmax().item()
        got = int(session.run(None, {"ink": x})[0].argmax())
        agree += want == got
    print(f"int8 model: {small.stat().st_size >> 10} KB, agrees with the trained one on {agree}/4", file=sys.stderr)

    chars = work / "handwriting.txt"
    chars.write_text("\n".join(classes) + "\n", encoding="utf-8")
    out.mkdir(parents=True, exist_ok=True)
    pack = out / "lychee-handwriting.zip"
    pack.unlink(missing_ok=True)
    from build_dicts import HANZI, fetch
    apl = fetch(HANZI.format("APL/english/ARPHICPL.TXT"), HERE / ".cache", "hanzi-APL.txt")
    with zipfile.ZipFile(pack, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("pack.json", json.dumps({"id": "handwriting", "name": "Handwriting", "version": PACK_VERSION}))
        z.write(small, "data/handwriting.onnx")
        z.write(chars, "data/handwriting.txt")
        z.writestr("LICENSE.txt", LICENCE)
        z.writestr("APL.txt", apl.read_text(encoding="utf-8", errors="replace"))
    print(f"{pack}: {pack.stat().st_size >> 10} KB, {len(classes)} characters, step {ck['step']}", file=sys.stderr)
    return pack


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--checkpoint", type=Path, default=HERE / "out" / "checkpoint.pt")
    ap.add_argument("--out", type=Path, default=HERE / "out" / "packs")
    ap.add_argument("--force", action="store_true", help="export even when it is not good enough yet")
    args = ap.parse_args()
    export(args.checkpoint, args.out, HERE / "out" / "export", args.force)


if __name__ == "__main__":
    main()
