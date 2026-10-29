"""English lookups for the Japanese and Korean packs: type an English word, get Japanese or
Korean words that mean it, like Lychee's English → Cantonese and Mandarin keyboards (same
scoring: data/build_data.py, build_en_dict)."""

from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from build_data import build_en_dict  # noqa: E402

SCHEMA = """# Type an English word, get {lang} words that mean it.
schema:
  schema_id: {name}
  name: {label}
  version: "1"
  description: English to {lang} lookup

switches:
  - name: ascii_mode
    reset: 0
    states: [ {label}, A ]

engine:
  processors:
    - ascii_composer
    - key_binder
    - speller
    - punctuator
    - selector
    - navigator
    - express_editor
  segmentors:
    - ascii_segmentor
    - abc_segmentor
    - punct_segmentor
    - fallback_segmentor
  translators:
    - punct_translator
    - table_translator
  filters:
    - uniquifier

speller:
  alphabet: abcdefghijklmnopqrstuvwxyz
  delimiter: " "

translator:
  dictionary: {name}
  enable_completion: true
  enable_sentence: false
  enable_user_dict: false
  enable_encoder: false
  comment_format:
    - xform/^~.*$//

punctuator:
  __include: lychee_common:/punctuator
key_binder:
  __include: lychee_common:/key_binder
"""


def write_english_schema(shared: Path, name: str, label: str, lang: str, entries, freq_of) -> int:
    """Writes {name}.schema.yaml and {name}.dict.yaml into [shared] (compile them with the
    rest). entries: (word, [glosses], weight)."""
    (shared / f"{name}.schema.yaml").write_text(SCHEMA.format(name=name, label=label, lang=lang), encoding="utf-8")
    # 15 words per English word keep the pack small; the bar and its dropdown show fewer
    return build_en_dict(entries, freq_of, shared / f"{name}.dict.yaml", name, per_code=15)
