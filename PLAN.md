# Lychee: next build plan

Everything below is built together and pushed to `main` as it goes, each push building
green. Packs stay private until a publish is started by hand; nothing goes public, and the
app is not released, without the owner's say.

**Status (2026-10-28):** everything in Data, Candidates, Long-press card, Typing and Look is
on `main`. The new packs (Korean dictionary, stroke order, example sentences, and the
Japanese dictionary's JMdict meanings, version 2) are built by `packs.yml` but not
published: start the workflow by hand on `main` after reviewing its "List the packs'
contents" step. Still to check on a phone: TTS voices per language, the forced light/dark
theme, key sounds, and the handwriting bar.

## Data

- **Chinese dictionary** (built in) gains, per word:
  - level: HSK 1–6 (Mandarin); Common / Everyday / Advanced frequency bands (Cantonese,
    which has no official scale)
  - colloquial (口語) or written (書面) mark for Cantonese words (from the Cantonese →
    Standard Written Chinese table and CC-Canto's Cantonese-only words)
  - measure words (CC-CEDICT `CL:`)
  - part of speech (jieba's word list, already used for weights)
  - Traditional and Simplified forms side by side
- **Japanese dictionary pack** gains meanings, part of speech and kana readings (JMdict,
  CC BY-SA).
- **New packs** (built by `packs.yml`, published only by hand):
  - Korean dictionary: meanings, Hanja, part of speech (Wiktionary data, CC BY-SA)
  - Stroke order and character breakdown (Make Me a Hanzi: Arphic Public License /
    LGPL, data only, licence files in the pack; ~9,500 characters, so some Hong Kong-only
    characters have none and the card leaves those parts out)
  - Example sentences (Tatoeba, CC BY; credited on the licences page)

## Candidates

- A reading and meaning on every candidate, in Chinese, Japanese and Korean
- Tone colours on pinyin and Jyutping
- Level badge and colloquial/written badge
- The other script beside the word (學校 / 学校)
- Furigana (kana reading) for Japanese, Hanja for Korean
- What is being typed shown with its readings in the bar
- Candidate size set per language
- An expanding notch that gives the candidates more room

## Long-press card

- Tap to hear: the phone's own text-to-speech (offline once its voice data is installed)
- Stroke order and character breakdown (parts, radical)
- Measure word and part of speech
- Example sentences

## Typing

- Tone typing (ni3hao3 narrows the candidates)
- Fuzzy pinyin and Jyutping (zh/z, n/l, ng ...), on or off in Settings
- Personal dictionary editor for each language
- Emoji search in Chinese and Cantonese, and a kaomoji set
- Handwriting: candidates in the bar, and next-character suggestions after each character
- 「 」 on the Cantonese symbol page

## Look

- More themes, with light/dark following the time of day
- Key borders on/off and corner roundness
- Key sounds
- Gboard-style resize, one-handed and floating handles
- Panels switch instantly
- Menu labels in the keyboard's language

## Much later (not in this build)

Quiz, listening drill, words you meet, explain a sentence, copy with readings, word book,
text expansion, split keyboard, freemium tiers.
