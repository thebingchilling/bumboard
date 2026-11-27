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
- **Data files are never locked.** Every pack's third-party data (CC-CEDICT, JMdict, KANJIDIC2,
  Kanjium, the Basic Korean Dictionary, Wiktionary, Tatoeba...) ships unencrypted under its own
  licence. What is paid is the feature in Lychee: without the add-on, Lychee doesn't use that
  data. Only **Lychee's own work** is encrypted (its conjugation and sound-change rules, its own
  notes, selections and tables), so nobody can lift it to build their own. Everything free and
  every demo stays open and unlimited, on every phone.
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
| Full dictionary card (each language) | The complete card for beginner words: HSK 1-2 (Chinese), JLPT N5 (Japanese), beginner level (Korean, from the National Institute of Korean Language); other words get the simple card with the pack's sections as headings |
| Stroke Order | Animated for the beginner characters |
| Example Sentences | All sentences for beginner words, one for other words |
| Google Handwriting | Single characters; whole words and phrases are paid |
| Font Pack | Every font and emoji style can be previewed in Settings > Fonts; using them on the keyboard is paid |

Demo content ships in its own open file; the paid entries are not in it.

## Names

Names a learner can say comfortably and remember, with a light touch only where it helps (the
Satchel, The Whole Lychee); one pattern for every language; no commas in names. Each name has a
plain line under it saying what it does. Written for learners, never generic marketing or
AI-sounding copy (`store/Store.kt`, `packs.json`).

| | Chinese | Japanese | Korean | English |
|---|---|---|---|---|
| Free dictionary | Pocket Chinese (built in) | Pocket Japanese | Pocket Korean | Pocket English (built in) |
| Full dictionary card, $9.99 | The Chinese Handbook | The Japanese Handbook | The Korean Handbook | The English Handbook |
| Pack, $14.99 | The Chinese Satchel | The Japanese Satchel | The Korean Satchel | The English Satchel |
| Full, $29.99 | The Whole Lychee | | | |

Add-ons for every language: Scribble (Google Handwriting, $5.99), Brushwork (stroke order,
$3.99), Overheard (example sentences, $3.99), Inkwell (fonts and emoji styles, $4.99).

Built-in dictionaries count as installed (as Pleco lists its own): Pocket Chinese and Pocket
English show in Installed, removable, and come back from the app. The data behind an add-on
(Brushwork's strokes, Overheard's sentences, Inkwell's fonts and emoji) is on that add-on's page,
never a listing of its own. Files on the release keep their packs' own names (`file_stem` in
packs.json: lychee-japanese-dictionary-<version>.zip and so on).

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

Every pack (Satchel) costs the same, **$14.99**. Chinese covers Cantonese and Mandarin in one
(one written language: the same characters, card, strokes and handwriting). English is the
fourth language, with the same parts.

Each pack has the same six parts, filled in for its language, plus Google Handwriting, Stroke
Order and Example Sentences.

| | Chinese (Cantonese and Mandarin) | Japanese | Korean |
|---|---|---|---|
| 1. Full dictionary card | All meanings, both languages side by side, Taiwan readings, colloquial Cantonese labels | All meanings, old kanji forms and kokuji notes (built) | All meanings, Hanja on the card (built) |
| 2. Learner levels | HSK (in the data), Cantonese frequency bands | JLPT (Jonathan Waller's lists, CC BY; built) | Beginner, intermediate, advanced (the National Institute of Korean Language's Basic Korean Dictionary, CC BY-SA 2.0 KR; built). TOPIK's own list has no reuse licence |
| 3. Pronunciation detail | Tones in both languages, tone colours | Pitch accent (Kanjium, CC BY-SA; built) | Sound changes shown, e.g. 학교 [학꾜] (the Basic Korean Dictionary's standard pronunciations; built) |
| 4. Grammar help | Measure words (CC-CEDICT) | Verb and adjective conjugation tables (Lychee's own rules, by JMdict's verb classes; built) | Verb and adjective conjugation tables (from the Basic Korean Dictionary's key forms, which carry every irregularity; built) |
| 5. Characters | Character breakdown (parts, radical), English-to-Chinese lookup | Kanji details: parts, grade, frequency, readings (KANJIDIC2 and KRADFILE, CC BY-SA; built) | Each Hanja's meaning and sound (built) |
| 6. Bonus fonts (pack only) | Chinese fonts | Japanese fonts | Korean fonts |

Value of each pack bought as separate add-ons: $9.99 + $5.99 + $3.99 + $3.99 = $23.96 (the bonus
fonts are not counted), so a pack is about 63% of its parts.

**Built:** the Japanese and Korean extras above are in the packs (Japanese pack version 3,
Korean pack with the Basic Korean Dictionary's data) and on the word card, so every pack is
equally deep. Every data source is licensed for commercial use (no NC or ND), with attribution
in each pack's LICENSE.txt. Still to do before paid launch: move the conjugation rules into the
encrypted pack data (they are code for now), per the security model.

## English

The English Handbook ($9.99) and the English Satchel ($14.99), like the others:

| Part | English |
|---|---|
| 1. Full dictionary card | Every meaning, phrasal verbs and idioms (Wiktionary via kaikki.org, CC BY-SA; Open English WordNet, CC BY 4.0; built) |
| 2. Learner levels | Word frequency (built). CEFR levels only from a list licensed for commercial use: the Oxford and Cambridge lists are not; CEFR-J's licence is still to be checked |
| 3. Pronunciation detail | US and UK IPA with stress, each voice (CMUdict, BSD; Wiktionary; built) |
| 4. Grammar help | Every form: plurals, past tenses, -ing, comparatives (Wiktionary; built) |
| 5. Word parts | Where a word comes from, its roots and affixes, words meaning the same and the opposite (Wiktionary, WordNet; built) |
| 6. Bonus fonts (Satchel only) | Latin fonts for the keys (Atkinson Hyperlegible, Lexend, Patrick Hand; OFL) |

The English Satchel holds the English Handbook, Scribble (English handwriting: Google's ML Kit
reads whole words; Lychee's own engine one letter at a time) and Overheard (English sentences).
Brushwork doesn't apply to English letters. The extras are in Pocket English's pack (the
`more` table of en.db), unencrypted under their licences; the paid card is locked in the app.

## Full

- **Everything Lychee makes, now and in the future**: all three full cards, every add-on, the
  whole Font Pack with every emoji style, and every future Lychee feature, language, font and
  emoji style.
- **$29.99 at launch**, rising to **$39.99** for new buyers once flashcards and the planned
  features land (early buyers got the better deal).
- Value today: $58.92 of add-ons (with the English Handbook), so Full is about half and falls
  as more is added.
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
come, and the honest reasons paid add-ons won't be available) and `settings/AddonsPage.kt` /
`settings/ProductSheet.kt` (the store in Settings > Add-ons).

The owner's decisions on wording and look (professional, Lychee's own, never Pleco's copy):
- It reads as the real store: no "free while in testing" anywhere, and no savings or value
  sums ("save $8.97" sounds cheap). Bundles say how many add-ons they hold.
- Only two promises, plainly, in the small shared card on every listing: "Works offline.
  Nothing you type leaves your phone." and "Pay once and keep it, with every update." The
  small print adds that updates to open data follow its new releases for as long as they stay
  free to use. No "no ads" or "yours to keep".
- Simple layout: a short intro card, the bundles (Full large, the three packs in one card),
  categories as plain list rows, one large "All add-ons" card, the registration at the bottom.
  No grids of tiles. Prices are rounded pill buttons.
- A listing: its tile, name and line; the price with Try the demo and Buy, and what it is for,
  straight on the page (not in cards); then In Lychee (a worked example), What you get,
  Included, Try it free (a card of its own), one small card with what every add-on promises
  (works offline, nothing typed leaves the phone; pay once with every update), and sources and
  licences as small print at the bottom.
- Until billing exists everything stays unlocked (`Features.isEnabled` is always true); a tap
  on Buy or Try the demo says plainly that purchases aren't available yet.
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
  purchases and issues keys for that phone's security chip.
- **No fixed phone limit.** A page lists the registration's phones, where the owner can remove
  old ones; only extreme patterns (hundreds of phones) are flagged for review, never blocked
  automatically.
- **Google Play Family Library** is on for every paid add-on (up to six family members on their
  own accounts).
- A leaked registration key can't be used to pirate: it only puts keys into the security chips
  of more phones running the genuine Lychee, which the owner sees in the list and can remove.

## Who can buy

| Phone | Paid add-ons |
|---|---|
| Android 9 or newer, Google Play, keys proven to be in the phone's security chip | Yes |
| Rooted, or with an unlocked bootloader | **Yes.** The chip still keeps keys safe; root and the bootloader are never refused |
| A rooted phone that fails Google's integrity checks | Yes: the chip's own certificate proves the genuine Lychee instead |
| A root tool faking keys for Lychee (e.g. TrickyStore with a leaked key) | No, until Lychee is left off the tool's list (one setting); then it uses the real chip and works |
| Emulators; phones storing keys in software only | No |
| Android 8.1 and older (they can't put keys straight into the chip) | No |
| A phone model whose own factory key was leaked and revoked | No |
| No Google Play (de-Googled Android, Huawei, mainland China) | Not yet: once Lychee's own billing exists, through registration keys |

Everyone who can't buy keeps everything free, every free download and every demo, and is told
plainly why and what, if anything, they can do. For example, a refused unlock with a faked key
says: "Your phone's security key isn't the phone's own, which usually means a root tool is
changing it for Lychee. Lychee works with root and unlocked bootloaders: remove Lychee from your
root tool's list (for example TrickyStore's), then try again." Phones that can't buy don't see
purchase buttons, so nobody pays for something that won't work.

## Security model

**The rule:** only the genuine Lychee can ever unlock or run paid add-ons, however the app is
modified. No app can stop someone changing their own copy, so the rule isn't enforced by checks
inside Lychee (they could be deleted). It is enforced by two things a cracked copy can't change:
the unlock function, and the phone's security chip.

| Layer | What it stops | Removable from the app? |
|---|---|---|
| **1. The unlock function only serves the genuine Lychee:** it requires Google's signed Play Integrity answer, or the security chip's certificate, naming Lychee's package and signing key | Cracked copies ever getting a key | **No:** it lives in the function and in Google's or the chip's signatures |
| **2. Keys only in the security chip**, usable only by the app with Lychee's signature | Cracked copies using a genuine install's keys, even on the same phone; keys being copied out | **No:** enforced by the chip |
| 3. Google Play's automatic protection (Play Console), once Lychee is on Google Play: Google's own obfuscated anti-tampering, which makes a modified copy refuse to run | Modified copies running at all | Yes, with effort: an extra layer |
| 4. Lychee checks its own signature and switches paid features off if it doesn't match | Casual tampering | Yes: an extra layer |

Layers 1 and 2 are the guarantee; 3 and 4 only raise the effort.

**How the content is protected** (owner's decision: the data files are never locked):
1. **Third-party data is never encrypted.** It ships open under its licence (CC BY-SA forbids
   technological measures on it anyway), with its LICENSE.txt. The paid feature is locked in
   Lychee: until the add-on is unlocked, Lychee simply doesn't read that data.
2. **Only Lychee's own work is encrypted**, entry by entry, one key per pack: the conjugation and
   sound-change rules, and Lychee's own notes, selections and tables. These are data, not code,
   so a copy with every check removed still lacks them. A pack's key stays the same across
   versions, and encrypted packs can stay on the public release.
3. **The key goes straight into the security chip** (Android's secure key import) and never
   comes out; Lychee's code never sees it. When a card opens, Lychee hands that one entry to the
   chip and gets back only that entry's text (a few milliseconds). Nothing ever decrypts a whole
   file.
4. **Lychee only ever takes a key from its own unlock.** There is no way to type, paste, import or
   load a key, and no code that reads a plain file of paid entries, so a decrypted dump can't be
   imported back.

**Buying:** Google Play Billing inside Lychee; the Play Store app does the network part, so
Lychee keeps no network permission (to confirm when building, for Billing and Play Integrity).

**Unlocking** (through the browser, like updates):
1. Lychee creates its own key in the phone's secure key storage and gets the chip's certificate
   for it (key attestation: which app owns the key and its signing fingerprint, whether it is in
   hardware, chained to Google's root).
2. It asks Google Play for a Play Integrity token bound to this request's one-time code.
3. It opens the unlock page in the browser with, after the `#`: the purchase receipt (or the
   registration key), the one-time code, the Play Integrity token and the chip's certificate.
4. The unlock function checks, with Google: the purchase; that the request comes from the genuine
   Lychee, by Play Integrity's app answer (from Google Play, Lychee's signing key) or, when that
   answer isn't given (phones failing Google's most basic integrity level, as some rooted phones
   do), by the chip's certificate naming Lychee's package and signing key; and that the key is in
   hardware (TEE or StrongBox), with a chain to Google's root whose factory key isn't on Google's
   revocation list. It never refuses root or an unlocked bootloader, and ignores Play Integrity's
   device answer.
5. It returns the pack key wrapped for that phone's chip and signed for the one-time code,
   through `lychee://unlock`.

**What each attack gets:**

| Attack | Result |
|---|---|
| Removing the Play Integrity call from a copy | No token, so no key |
| Faking a Play Integrity token or a chip certificate | Impossible: only Google or the chip's factory key can sign them |
| Borrowing a token from the genuine Lychee | Fails: each is tied to one request's one-time code, and Google reports the installed app's signature |
| Patching a copy to skip unlocking | It can read the open data (free elsewhere anyway), but Lychee's own encrypted work stays locked, with no key to open it |
| Reading Lychee's code (by hand or with AI) | Nothing: the app holds no key or secret |
| A leaked key, a decrypted dump, copied packs or backups | Useless in Lychee, which only takes keys from its own unlock; the key never leaves the chip anyway |
| Intercepting the unlock | The key in transit opens only inside that phone's chip; one-time codes stop replays |
| Emulators, faked keys, software key storage | Refused: no hardware certificate, or a revoked chain |

**The precise limit:** on a rooted phone with a real purchase, someone could use hooking tools to
make the genuine Lychee decrypt its own entries one by one, save the text, and ship it inside a
cracked copy. No app can rule that out, Pleco's included. It needs a real purchase and great
effort, for data that is mostly openly licensed and free elsewhere. The open data itself can be
read by anyone: that is its licence, and what Lychee sells is the feature in the keyboard and
its own work, kept up to date. No watermarking.

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

**Works offline after unlocking, forever.** Once a key is in the chip, the add-on needs nothing
online again, unless the person moves it to another phone or a refund removes it.

**Obfuscation** (R8 on Lychee's own code) is an extra speed bump only, like layers 3 and 4; the
protection never depends on it. The handwriting companion stays unshrunk (R8 broke ML Kit).

**Google Handwriting:** the companion has network access, so it checks a licence signed by the
unlock function, for that phone. Pirating it is pointless anyway: Gboard's handwriting is free.

**Font Pack:** the fonts' licence (SIL OFL) forbids selling a font on its own, so the Font Pack
sells Lychee's font feature (choosing fonts per language, on keys, candidates and cards); the
font files stay free downloads.

## What has to be built (at launch)

- **The unlock function and its small database**: a serverless function (e.g. Cloudflare
  Workers, with D1 or KV, on the free tier), under the lychee-keyboard name, set up and updated
  only through this repository's workflows with stored secrets (pack keys, Google Play
  credentials), never from a session. It checks purchases and registrations, Play Integrity's
  app answer and the chip's certificate (with Google's revocation list), issues wrapped keys, keeps registrations,
  purchases and phones, and never sees anything typed. There is no server today; none is
  needed until billing.
- **Pack builders**: third-party data always open (with its licence); only Lychee's own work
  as entry-by-entry encrypted data; demos as separate open files; free packs unchanged.
- **Lychee**: the store (UI first), registration, `lychee://unlock`, its own key and the chip's
  certificate, secure key import, decryption inside the chip, no other way in for keys or paid
  data, its own signature check (layer 4), Play Billing and Play Integrity
  (each confirmed to add no network permission), honest notes wherever paid add-ons can't be
  offered, R8 on its own code.
- **The handwriting companion**: the server-signed licence check.
- **The unlock function's limits**: rate limits per phone and per registration, and long random
  registration keys, so keys can't be guessed or the function flooded.
- **A privacy policy** (Google Play requires one): what the unlock function receives (the
  receipt, the one-time code, the Play Integrity token, the chip's certificate and an optional
  email), that it never receives
  anything typed, and that Lychee itself never goes online.
- **On Google Play**: turn on automatic protection in Play Console (layer 3).
- **Testing before launch**: Chromebooks running Android apps (they may not give a hardware
  certificate), and decryption speed per entry with StrongBox versus TEE.

## Decided: share-alike data stays open

Much of the paid data is CC BY-SA, and its licence forbids encryption or DRM on it. The owner's
decision (option 3 with 2): the data files are never locked, Lychee locks the paid features in
the app, and only Lychee's own work is encrypted. See the principles and the security model
above.
