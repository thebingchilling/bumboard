# Lychee: paid add-ons and security

What the owner has agreed for Lychee's paid add-ons: the offering, the prices, the demos, the
registration and the security model. Nothing here is built yet beyond what is marked. The
next step is the store UI only (prices, packs, labels, demo marks), with every button saying
"Free for now"; billing, encryption and the unlock function come later.

Lychee is modelled on Pleco: a genuinely good free app, paid depth bought once, bundles that
are clearly worth it, upgrades that credit what was paid, and paid content that cannot be
pirated.

## Principles

- **One-time purchases only.** Never subscriptions.
- **All prices in USD.** (Google Play sets local prices from them.)
- **Every language is treated the same.** Packs cost the same; the free tier is equal.
- **Typing is never paid**, in any language (Japanese kana-to-kanji included).
- **Only paid add-ons are encrypted or limited.** Everything free, every free download and every
  demo stays open and unlimited, on every phone.
- **Always be honest with users about why something can't be offered**: say plainly why, and
  what, if anything, they can do. Never fail silently or hide the reason.
- **No ads, no nags.** The free card shows what a pack adds as quiet, collapsed headings
  ("Pitch accent · in the Japanese pack"), nothing more.
- **Lychee keeps no network permission**, paid add-ons included (CI fails the build if one
  appears).

## Free (forever, unlimited, unencrypted)

- Every keyboard and language: English, Cantonese, Mandarin, Japanese (with kana-to-kanji
  conversion), Korean
- Readings and meanings on candidates
- A simple dictionary card for Chinese, Japanese, Korean and English (the word, its reading,
  its main meanings)
- The built-in handwriting (PP-OCR)
- Noto Color Emoji; LXGW WenKai (the Kaiti-style Chinese font already in the app)
- Themes, glide typing, clipboard, incognito
- Every free download (free packs and fonts stay on the public release, as now)

## Demos (free)

Every paid add-on works free on beginner content. The limit is the content itself, never a time
limit or a count, so there is nothing stored on the phone to reset.

| Add-on | Free demo |
|---|---|
| Full dictionary card (each language) | The complete card for beginner words: HSK 1-2 (Chinese), JLPT N5 (Japanese), TOPIK I (Korean); other words get the simple card with the pack's sections as headings |
| Stroke Order | Animated for the beginner characters |
| Example Sentences | All sentences for beginner words, one for other words |
| Google Handwriting | Single characters; whole words and phrases are paid |
| Font Pack | Every font and emoji style can be previewed in Settings > Fonts; using them on the keyboard is paid |

Demo content ships in its own open file; the paid entries are not in it.

## Individual add-ons

Individual add-ons always cover every language. Only things tied to one language (each full
dictionary card) are per language.

| Add-on | Covers | Price |
|---|---|---|
| Full dictionary card | One language (Chinese, Japanese or Korean) | $9.99 each |
| Google Handwriting | Chinese, Japanese and Korean | $5.99 |
| Font Pack | Every CJK font and every emoji style | $4.99 |
| Stroke Order | Every language | $3.99 |
| Example Sentences | Every language | $3.99 |

Google Handwriting is priced as a sweetener, not a headline: Gboard's handwriting is free.

## Packs

Every pack costs the same, **$14.99**. Chinese is the main pack and covers Cantonese and
Mandarin in one (one written language: the same characters, card, strokes and handwriting).

Each pack has the same six parts, filled in for its language, plus Google Handwriting, Stroke
Order and Example Sentences.

| | Chinese (Cantonese and Mandarin) | Japanese | Korean |
|---|---|---|---|
| 1. Full dictionary card | All meanings, both languages side by side, Taiwan readings, colloquial Cantonese labels | All meanings, old kanji forms and kokuji notes (built) | All meanings, Hanja on the card (built) |
| 2. Learner levels | HSK (in the data), Cantonese frequency bands | JLPT (Jonathan Waller's lists, CC BY) | TOPIK (source still to find; the national list's licence is unclear) |
| 3. Pronunciation detail | Tones in both languages, tone colours | Pitch accent (Kanjium, CC BY-SA) | Sound changes shown, e.g. 학교 [학꾜] (Lychee's own rules) |
| 4. Grammar help | Measure words (CC-CEDICT) | Verb and adjective conjugation tables (Lychee's own) | Verb and adjective conjugation tables (Lychee's own) |
| 5. Characters | Character breakdown (parts, radical), English-to-Chinese lookup | Kanji details: parts, grade, frequency, readings (KANJIDIC2 and KRADFILE, CC BY-SA) | Each Hanja's meaning and sound (훈음), links to the Chinese and Japanese forms |
| 6. Bonus fonts (pack only) | Chinese fonts | Japanese fonts | Korean fonts |

Value of each pack bought as separate add-ons: $9.99 + $5.99 + $3.99 + $3.99 = $23.96 (the bonus
fonts are not counted), so a pack is about 63% of its parts.

**Before launch:** build the Japanese and Korean extras (JLPT, pitch accent, conjugation, kanji
details; TOPIK once a usable licence is found, sound changes, conjugation, Hanja meaning and
sound) so every $14.99 pack is equally deep. Every data source is licence-checked first
(commercial use allowed: no NC or ND).

## Full

- **Everything Lychee makes, now and in the future**: all three full cards, every add-on, the
  whole Font Pack with every emoji style, and every future Lychee feature, language, font and
  emoji style.
- **$29.99 at launch**, rising to **$39.99** for new buyers once flashcards and the planned
  features land (early buyers got the better deal).
- Value today: $48.93 of add-ons, so Full is about 61% and falls as more is added.
- **Owning packs takes their price off Full**, down to $0: one pack leaves $15.00 to pay; owning
  two packs ($29.98) unlocks Full automatically.
- **Licensed third-party dictionaries**, if ever added, are sold separately with a 30% discount
  for Full owners (publishers take a royalty on every sale). The store says so upfront: "Full
  includes every Lychee add-on, now and future. Dictionaries from other publishers are sold
  separately, with a discount for Full owners."

## Launch

- Everyone testing Lychee now gets Full free.
- Full is $24.99 for the first month after launch.

## The store (UI first, no billing)

**Built:** `store/Store.kt` (every add-on, pack and Full with its price, contents, what is still to
come, and the honest reasons paid add-ons won't be available) and `settings/StoreSection.kt` (the
store in Settings > Add-ons).

- Opens with "Pick a bundle, or buy add-ons individually".
- Bundle cards first, Full at the top as the best value; each states its price, its item count
  and its value ("5 items, $23.96 value") and lists its contents and bonus fonts.
- Then categories with a one-line explanation each: Features, Dictionaries, Fonts.
- Each add-on shows its own price and the packs that include it ("$3.99 · in Chinese,
  Japanese, Korean, Full").
- Until billing exists every button says "Free for now" and everything stays unlocked
  (`Features.isEnabled` is always true).
- Where paid add-ons can't be offered (see below), they are shown with an honest note saying
  why.

## Growing later

- Flashcards built from words looked up while typing ($9.99): the next big paid feature.
- Recorded native audio, only from openly licensed sources (e.g. Wikimedia Commons, CC BY-SA).
- Licensed dictionaries, sold separately (see Full).
- Lychee's own billing (direct purchase on its website, Pleco-style) for phones without Google
  Play.
- Classroom licences: a teacher buys several registrations at once (later, with Lychee's own
  billing).

## Registration (Pleco-style)

- The first purchase creates a **Lychee registration**: a **registration ID** (e.g.
  `LY-7K4Q-92MD`, safe to show, quoted for support) and a **registration key** (a long secret
  code that proves ownership and is entered on a new phone). The unlock page shows both and the
  `lychee://unlock` link hands them to Lychee; the page suggests saving the key, like recovery
  codes. An email address is optional, only for recovering a lost key; nothing else personal is
  stored.
- **Every purchase attaches to the registration**: Google Play now, Lychee's own website later.
- **New phone, reinstall or family:** enter the registration key; the unlock page checks the
  purchases and issues keys for that phone.
- **No fixed phone limit.** A page lists the registration's phones, where the owner can remove
  old ones; only extreme patterns (hundreds of phones) are flagged for review, never blocked
  automatically.
- **Google Play Family Library** is on for every paid add-on (up to six family members on their
  own accounts).
- A leaked registration key only unlocks the genuine Lychee on more phones, which the owner sees
  in the list and can remove.

## Who can buy

| Phone | Paid add-ons |
|---|---|
| Any phone Lychee runs on (Android 7 or newer) with Google Play | Yes |
| Rooted, unlocked bootloader, root tools that spoof keys (e.g. TrickyStore) | **Yes.** Nothing about the phone is checked; nobody has to change their setup for Lychee |
| Emulators with Google Play | Yes: they pay like anyone else |
| No Google Play (de-Googled Android, Huawei, mainland China) | Not yet: once Lychee's own billing exists, through registration keys |

Phones without Google Play keep everything free, every free download and every demo, and are
told plainly why paid add-ons aren't available yet and what is coming. Phones that can't buy
don't see purchase buttons, so nobody pays for something they can't use.

## Security model

**The one rule:** nothing but Lychee's own unlock can turn on a paid feature in the genuine
Lychee. Keys and decrypted content may leak, as with any app; what matters is that a leaked key,
a decrypted file or a copied pack is useless in Lychee. Lychee has no way to accept them.

**How Lychee enforces it:**
1. **Paid content is encrypted**, entry by entry (each paid dictionary entry, level, pitch accent,
   Hanja breakdown, conjugation and sound-change rule, paid example sentence and stroke order),
   with one key per pack. The headword list stays open (the free card shows it anyway). Free
   features, free downloads and demos are never encrypted. The encrypted packs can stay on the
   public release, and a pack's key stays the same across versions.
2. **Paid features live in the encrypted data, not in code.** Conjugation rules, sound-change
   rules, levels: all data. With no unlocked key there is nothing to show.
3. **Lychee only ever takes a key from its own unlock**: a key wrapped for this phone's own key
   (created in the phone's secure key storage, which only this install of Lychee can use) and
   signed by Lychee's unlock function for this request's one-time code. There is no way to type,
   paste, import or load a key, and no code that reads a plain file of paid entries. A key from
   anywhere else, or one unlocked on another phone, is simply never accepted.
4. **The phone's secure key storage holds the key.** Where the phone supports it (Android 9 and
   newer), the key goes straight into the security chip and decryption happens there; on older
   phones the key is unwrapped by the secure key storage and used in memory. Either way it is
   only ever usable by this install, and nothing about the phone itself is checked.

**Buying:** Google Play Billing inside Lychee; the Play Store app does the network part, so
Lychee keeps no network permission (to confirm when building).

**Unlocking** (through the browser, like updates):
1. Lychee creates its own key in the phone's secure key storage.
2. It opens the unlock page in the browser with, after the `#`: the purchase receipt (or the
   registration key), a one-time code, Play Integrity's answers about the app and the account
   (never about the device), and its public key.
3. The unlock function checks the purchase with Google and that the request comes from the
   genuine Lychee from Google Play (Play Integrity's app answer). It never checks root, the
   bootloader or the phone's security level.
4. It returns the pack key wrapped for that install's key and signed for the one-time code,
   through `lychee://unlock`.

**What each attack gets:**

| Attack | Result |
|---|---|
| A leaked pack key, a decrypted dump, copied packs or backups | Useless in Lychee: it only accepts keys from its own unlock, wrapped for itself |
| Reading or patching Lychee's code (by hand or with AI) | The app holds no secret, and the unlock function only serves the genuine Lychee from Google Play |
| Intercepting the unlock | The key in transit opens only for that install; one-time codes stop replays |
| Frida, LSPosed, root, emulators | Can't make the genuine Lychee accept anything but its own unlock |

**The honest limit:** someone could pull a key out of their own phone (easier with root or an
emulator) and build a cracked copy of Lychee that uses it. That copy is not Lychee: it gets no
updates, no new packs and no unlock, and much of the paid data is openly licensed and free
elsewhere anyway. What Lychee sells is the dictionary in the keyboard, kept up to date. No
watermarking, and no refusing phones over it.

**Decrypted text never touches storage.** Paid entries exist in plain text only on screen and
in memory while a card shows: never written to caches, files, logs, crash reports or the
diagnostics log, and the paid packs and keys are left out of Android backups.

**Pack keys can be replaced.** If a pack's key were ever widely abused, its next version gets a
new key, fetched by the next update check through the browser, using the registration.

**Refunds.** Google tells the unlock function about refunded purchases. Lychee can't hear that
offline, so a refunded add-on is removed at the next trip through the browser (an update check
or an unlock), and the person is told why.

**Purchases are acknowledged at once.** Google Play refunds purchases that aren't acknowledged
within three days; Lychee acknowledges on the phone as soon as the purchase completes (no network
needed), so nobody loses a purchase by not finishing the unlock straight away.

**Works offline after unlocking, forever.** Once unlocked, the add-on needs nothing online again,
unless the person moves it to another phone or a refund removes it.

**Obfuscation** (R8 on Lychee's own code) is an extra speed bump only; the protection never
depends on it. The handwriting companion stays unshrunk (R8 broke ML Kit).

**Google Handwriting:** the companion has network access, so it checks a licence signed by the
unlock function, for that phone. Pirating it is pointless anyway: Gboard's handwriting is free.

**Font Pack:** the fonts' licence (SIL OFL) forbids selling a font on its own, so the Font Pack
sells Lychee's font feature (choosing fonts per language, on keys, candidates and cards); the
font files stay free downloads.

## What has to be built (at launch)

- **The unlock function and its small database**: a serverless function (e.g. Cloudflare
  Workers, with D1 or KV, on the free tier), under the lychee-keyboard name, set up and updated
  only through this repository's workflows with stored secrets (pack keys, Google Play
  credentials), never from a session. It checks purchases and registrations and Play Integrity's
  app and account answers, issues wrapped keys, keeps registrations,
  purchases and phones, and never sees anything typed. There is no server today; none is
  needed until billing.
- **Pack builders**: paid content as entry-by-entry encrypted data, demos as separate open
  files; free packs unchanged.
- **Lychee**: the store (UI first), registration, `lychee://unlock`, its own key in the secure
  key storage, secure key import where supported, entry-by-entry decryption, no other way in for
  keys or paid data, Play Billing and Play Integrity
  (each confirmed to add no network permission), honest notes wherever paid add-ons can't be
  offered, R8 on its own code.
- **The handwriting companion**: the server-signed licence check.
- **The unlock function's limits**: rate limits per phone and per registration, and long random
  registration keys, so keys can't be guessed or the function flooded.
- **A privacy policy** (Google Play requires one): what the unlock function receives (the
  receipt, the one-time code, the public key and an optional email), that it never receives
  anything typed, and that Lychee itself never goes online.
- **Testing before launch**: decryption speed per entry on phones with StrongBox, TEE and older
  software key storage.
