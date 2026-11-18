Real handwriting for training and testing Lychee's handwriting recogniser (train.py).

Put files saved from Lychee here (Settings > Advanced > Save handwriting samples, write on the
pad, then Settings > About > Diagnostics > Save to a file), named `<who>-<date>.jsonl`. Each
line is one character: `{"lang": "cmn", "typed": "字", "guesses": [...], "ink": [[x, y, ...], ...]}`;
`typed` (what was picked) is the label. Only writing whose writer agreed to it being used to
train Lychee goes here, and nothing typed in other fields (the pad saves only what is written
on it). Samples labelled with a character the recogniser doesn't know are skipped.
