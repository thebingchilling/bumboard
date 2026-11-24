# Redesign plan: the dictionary card and Add-ons

The plan, with the owner's decisions recorded here (as with `PAID.md`).

**Decided (dictionary card):** five tabs only: **Summary, Readings, Definitions, Parts,
Examples**. Stroke order goes in Parts, conjugation in Definitions, pitch accent, sound changes
and the Chinese readings of Kanji and Hanja in Readings. The tabs sit under the header. There is
one outlined 粵/普 switch in the header for the whole card. Parts open as their own cards, with
Back. Phase 1 (shell, header, five tabs, drill-down) is built.

**Waiting:** Pleco's screenshots for Add-ons. The public repository for Lychee handwriting is
not to be made until the owner has checked it.

## 1. The dictionary card

### What is wrong now (an honest critique)

- **One 1,500-line file draws everything by hand** (`chinese/DictionaryPopup.kt`): every
  section is built inline, with its own spacing, sizes and colours. That is why it looks
  improvised: there's no shared type scale, no fixed spacing grid, and the cards are boxed
  inside other cards.
- **Expanding fights scrolling.** The card opens as a summary, grows into the full entry, and
  scrolls inside a popup that itself sits over a scrolling keyboard. Gestures clash, and you
  never know how much is below.
- **The summary isn't a summary.**
  - It has stroke order, which belongs in the full card.
  - Its meanings are cut to a few lines.
  - When several entries match, characters and meanings run together with nothing between them.
- **Text is cut.**
  - Definitions are shortened to fixed lengths (48 characters, 16 for each piece of a phrase).
  - Reading tiles are clipped.
  - A phrase shows only its first character's reading.
- **No way to go deeper.** A part of a word (a character, a word inside a phrase) can't be
  opened on its own and then left again.

### Principles

1. **One shell, many pages.** The card has a fixed size and never grows. Its content is split
   into tabs, and you swipe between them or tap them. Each tab scrolls vertically on its own.
2. **Summary first, never truncated.** The first tab gives the best short answer. Every line of
   meaning wraps in full; nothing ends in "…".
3. **Drill down, then come back.** Tapping a part (a character, a word) opens it as a page of
   its own, with Back. The card keeps a small history, like a browser's.
4. **One visual system.**
   - A type scale, a 4/8/16 dp spacing grid, and Lychee's rose palette.
   - Sections are set apart by spacing and thin dividers, never by cards inside cards.
   - Motion follows `LycheeMotion`.
5. **Same card for every language.** Chinese, Japanese and Korean share the shell and the tabs.
   Only the content differs.

### Layout

```
┌────────────────────────────────────────────┐
│ ←  學校                         🔊 [粵|普]  ✕ │  header: Back (when deep), word, its other script,
│    hok6 haau6 · xuéxiào                    │  reading of EVERY syllable (wraps), speaker + language
├────────────────────────────────────────────┤
│ Summary  Meanings  Characters  Examples  … │  tab row: scrolls sideways; underline on the current
├────────────────────────────────────────────┤
│                                            │
│   (the tab's page: scrolls vertically;     │  pages: swipe left/right between tabs
│    swipe sideways for the next tab)        │
│                                            │
└────────────────────────────────────────────┘
               (keyboard below)
```

- **Header (always visible):**
  - The word large, with its other script beside it (學校 · 学校).
  - The reading of the whole word: all syllables, wrapping if long.
  - A speaker, with a small outlined language switch beside it (粵 | 普 for Chinese). It starts
    on the keyboard's language; a tap switches both the reading shown and the voice. Japanese
    and Korean have no switch.
  - ✕ to close, and ← when a part has been opened.
- **Tabs.** Only tabs with something in them appear:

*(The table below was the first proposal; the decided five tabs are above.)*

| Tab | What it holds |
| --- | --- |
| **Summary** | The 3–5 most relevant meanings in full, merged across entries and de-duplicated. Ranked by the reading typed, then how common, then usefulness ("variant of", "surname" and the like last). Then the measure word and level badges (HSK, JLPT, Korean level). With several entries, each starts with its word in a small tonal chip so words and meanings are clearly apart. No stroke order. |
| **Meanings** | Every entry and every sense, in full, grouped by reading and language, with usage notes. |
| **Characters** (or Parts) | Each character or piece: large, its readings in both languages and its meaning. Tap one to open its own card. A phrase the dictionary lacks shows its pieces here. Japanese and Korean show Kanji and Hanja, with kokuji and gukja notes. |
| **Strokes** | Animated stroke order for each character, with replay. |
| **Examples** | Sentences with romanisation, a speaker each, and the translation. |
| **Forms** | Traditional and Simplified, variants, Japanese old forms, and where a character comes from. |
| **Conjugation** | Japanese and Korean verbs and adjectives. |
| **Words** *(new)* | Common words containing this character, each opening its own card. |

- **Navigation.**
  - Tapping a character or word pushes its card; ← or the phone's Back returns.
  - ✕, tapping outside or swiping the card down closes it.
  - Tapping the headword types it, as now.
- **Size.** The card takes about 55% of the room above the keys (as now, but fixed). "Open in
  Lychee" (the full-screen Look up) stays for long reading.

### Your ideas, and where I'd change them

- **Tabs along the bottom.** I'd put them **under the header instead**. The card sits right
  above the keyboard, so a bottom tab row would be one finger-width from the candidate bar and
  the keys, where taps go astray. Tabs at the top also follow Material and Pleco's entry
  screens. If you prefer the bottom, it works too: it needs a clear gap and a raised bar.
- **"MINI" / 小 as the first tab's name.** Lychee's own labels are English except the 繁/简 key.
  I'd call it **Summary**. Don't name it after a size: what makes it useful is the choice of
  meanings, not that it's small.
- **No expanding: agreed.** Fixed size plus tabs is clearer and fixes the scrolling.
- **✕ in the corner: keep it.** Also close on tapping outside, on Back, and on swiping the card
  down, so the ✕ isn't the only way.
- **粵/普 switch by each speaker.** I'd have **one** switch in the header that sets the language
  for the whole card: readings, speakers and examples. A switch beside every speaker clutters
  the card and lets readings disagree. Speakers further down follow the header's choice.

### Engineering

- Split the card into a **model** and **views**.
  - The model is built from `Resolved` / `WordEntry` for Chinese, Japanese and Korean. Its pure
    logic goes in `app/core`, with tests: summary ranking, merging senses, readings for every
    syllable.
  - The views are a card shell, the tab row, the pager and one class per page.
- Pages are built only when first shown, and the shell is reused, so the card opens instantly.
- A small history (a stack of models) for drilling into parts.
- Built from Lychee's tokens (`M3Colors`, a type scale object), so every page looks alike.

### Phases

1. Shell, header (full readings, the language switch), tabs, Summary, Meanings. This replaces
   the current card.
2. Characters with drill-down, Strokes, Examples, Forms.
3. Japanese and Korean pages (Conjugation, kanji/hanja details), and Words.
4. Polish: accessibility (TalkBack labels, font scaling), motion, dark mode review.

## 2. Add-ons

### What is wrong now

- Too dense: every row explains itself in full. There's no visual hierarchy, and nothing to
  scan by.
- Bundles, features, fonts and free downloads all share one long list.

### Direction (to be refined from Pleco's screenshots)

- **Store home:** a short header (Lychee, made by a student developer, one-time purchases,
  offline), then a few large **category tiles**: Dictionaries, Handwriting, Learning tools,
  Fonts & emoji, Free downloads. Each tile has an icon, a one-line description and a count.
- **Category pages:** plain rows with icon, name, one line and price or status. Nothing more.
- **Product pages** (the Pleco-style pages already built): price, size, version, a worked
  example, what you get, the free trial, sources and licences, privacy. Kept, but restyled to
  the new system.
- **Your add-ons:** installed packs, updates and storage on a page of their own, reached from
  the top of the store.
- Registration stays at the bottom of the store home.

Screenshots wanted from Pleco: the Add-ons home, a category page, and a product page, so the
structure can be matched in Lychee's own style.

## 3. Open questions for the owner

1. Tabs under the header (recommended) or along the bottom?
2. First tab called "Summary" (recommended), or something else?
3. One 粵/普 switch in the header (recommended), or one by every speaker?
4. Lychee handwriting as open source:
   - Which licence? Apache-2.0 matches ML Kit and PP-OCR.
   - Which public repository? For example `lychee-keyboard/lychee-handwriting`, published
     through this repository's workflow as lychee-keyboard, never from a session.
