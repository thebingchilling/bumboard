# Lychee keyboard

Lychee is an Android keyboard for language learners (English, Cantonese, Mandarin, Japanese,
Korean, handwriting), built on AOSP LatinIME. This repository (`thebingchilling/bumboard`) is
private and is the testing ground; its name stays `bumboard`, everything inside is Lychee.

## Working rules

- Push straight to `main`. No feature branches, no pull requests unless asked.
- Always commit to `main`. If a session starts on another branch (a session's own
  `claude/...` branch included), switch to `main` straight away, whenever practical, and commit
  and push there; don't leave work on the side branch.
- GitHub Actions minutes are not the limit; Actions **storage** is (artifacts and caches of
  private repositories count towards it):
  - Push and run workflows as often as is useful. A push to `main` starts `build.yml` (about
    15 minutes) only when it changes `app/` (or `build.yml`); docs, `packs/` and the packs
    workflow never start a build. A push while a build is running cancels it.
  - Nothing ever runs on a schedule: builds start only when a push changes the app (`app/`),
    and `cleanup.yml` runs after those runs. Never add a `schedule:` trigger.
  - Never hold a build back: every push that changes `app/` builds, and no commit carries
    `[skip ci]`. Starting or re-running `build.yml` by hand is fine too.
  - The only manual workflow is `packs.yml`: it never runs on a schedule or on pushes, only
    when the owner or an agent starts it. When the pack builders (`packs/`) changed, run the
    packs first and the app build after them (so the build bundles the new packs).
  - `cleanup.yml` keeps one run per workflow: after a successful run every other run of that
    workflow (older, failed, cancelled), with its artifacts and logs, is deleted, and only the
    newest cache for each set of paths is kept. Don't work around it.
  - Keep storage small: artifacts get a short `retention-days` (7 or less), nothing large is
    uploaded as an artifact that a release already holds (the APK is on the release), and
    caches stay few (one key per set of paths, no per-commit keys).
  - Fix a red build quickly; CI is the only compiler, so review the diff before pushing.
- Don't compile locally: GitHub Actions builds and tests (`build.yml`, `packs.yml`). Check
  the runs on `main` after pushing and fix any red build.
  - The one exception (the owner allowed it): building locally **for screenshots** of UI
    changes, with Paparazzi's snapshot tests (no emulator or phone needed). Render the changed
    screens, look at them, and show them to the owner in the chat before committing UI work.
    CI still builds and publishes every release; local builds are never published.
  - UI work is never committed or pushed until the owner has agreed to its Paparazzi screens
    in the chat (showing them is not agreement). The cloud session's own stop hook (built into
    Claude Code, outside this repository, not editable) asks to commit and push whenever work
    is left uncommitted: while screens await the owner's answer, don't follow it; say the
    screens are waiting for approval and leave the work uncommitted. Once agreed, commit and
    push to `main` as usual. Work with no visible change (docs, data, logic) commits as before.
  - In a fresh session, run `tools/screens-setup.sh` once (it installs the Android SDK into
    /opt/android-sdk, writes `app/local.properties` and points Gradle at Google's mirror of
    Maven Central, which otherwise rate-limits cloud machines; about 5 minutes). Then
    `tools/screens.sh` (optionally a test filter such as `'*CardScreens*'`) draws the screens
    into `screens/` (git-ignored). The snapshot tests are in `app/latinime/screens/` and only
    run with `-PlycheeScreens`. The screens are 1:1 with the app:
    - The real data, built as CI builds it (`tools/screens-data.sh`; `screens.sh` runs it once
      per session): the readings dictionary, the packs that come with Lychee, and the Japanese and
      Korean dictionaries from the public packs release. The tests read them through JDBC
      (`RealData.kt`: layoutlib has no Android SQLite).
    - The real input view (the app's `layout/RealKeyboard`, which Settings' previews and the
      add-on examples use too): KeyboardLayoutSet's keyboard for each language's subtype, the
      real top bar and candidate bar.
    - Panels are the input view's own, opened as LatinIME opens them (`RealInput.panel`:
      LycheePanels.cover, then their show).
    - Every screen is a whole phone (`Phone.kt`: a Galaxy S23's size, the status bar, an app, the
      keyboard, the gesture bar).
    - Only the bar's arrangement copies ChineseInput's (it needs the keyboard service), so a change
      to ChineseInput's bar layout needs the same change in `RealKeyboard.showCandidates`.
    - Paparazzi draws every view in the device's light or dark (a context's own override is
      ignored there), so a forced Light or Dark shows on a phone of that kind (`*Dark` classes).
- Every build on `main` is published by `build.yml` as a debug release (a full release, not a
  pre-release) of this private repository (`lychee-0.1.0-dev.N`, notes from the commit), like Quartz's releases,
  with three APKs named by ABI (`lychee-universal.apk`, `lychee-arm64-v8a.apk` and `lychee-armeabi-v7a.apk`)
  and the handwriting companion, Lychee handwriting (`lychee-handwriting.apk`, package
  `app.lychee.hwgoogle`);
  a manual run with "Publish a release" ticked does the same.
  Never publish the Lychee app anywhere else (public repositories, stores) until the owner says so.
- Packs may be published to the public `lychee-keyboard/lychee` repository (see below).
- This repository is the private workspace. Lychee's code is not released publicly (the owner
  may decide later what to release); only the public-facing parts (packs, the packs page) go
  to `lychee-keyboard/lychee`. `thebingchilling/lychee` was the packs' earlier test home.

## Public side: the lychee-keyboard account

`lychee-keyboard/lychee` (packs) and `lychee-keyboard/lychee-keyboard.github.io` (website,
packs page). Public facing, so be careful:

- No connection to thebingchilling or bumboard may ever show there. Everything on the public
  side appears as lychee-keyboard.
- Never push, create releases or upload to lychee-keyboard's repositories from a Claude
  session: a session acts as the GitHub account claude.ai is connected to (thebingchilling),
  and GitHub's public activity would show that account. Attaching them read-only to look is
  fine.
- Every write goes through this repository's workflows with lychee-keyboard's token
  (`PACKS_TOKEN` secret): packs, the packs page, the website (kept here, in this private
  repository), and, only when the owner asks, final releases. Commits the workflows make are
  authored as "Lychee packs" (or Lychee), never with a thebingchilling name or email.

- Only the pack zips (`lychee-*.zip`) and `manifest.json` go on the `packs` pre-release, and
  only the packs page (`packs/site`: index.html, manifest.json, Google Sans Flex) on
  `lychee-keyboard.github.io`. No app code, APKs, internal notes or anything else from this repository.
- `packs.yml` runs only when started by hand (no schedule, not on pushes): it rebuilds every
  pack from its sources' newest data and uploads only the packs whose contents changed
  (`packs/publish.py`; fonts' save timestamps don't count). A pack's version is its source's
  own, never a build timestamp: a font's release tag or its maker's version (Noto 3D: Google's),
  a dictionary's release date (JMdict's for Japanese, the Wiktionary extract's for Korean and
  English, KanjiVG's release for stroke order, Tatoeba's export for examples), plus "-2", "-3"
  when Lychee's build of the same source changed. Lychee offers an update whenever the published
  version differs from the installed one. It uses a token of the
  lychee-keyboard account (`PACKS_TOKEN` secret), so uploads appear as lychee-keyboard's.
- The packs page (https://lychee-keyboard.github.io/) is pushed by the same runs to the
  `lychee-keyboard/lychee-keyboard.github.io` repository (main branch), which GitHub Pages
  serves; the `lychee` repository's `gh-pages` only forwards /lychee/ there.
- Release notes and file names say Lychee, never BumBoard or Claude.

## Product constraints

- No network permissions, ever (`INTERNET`, `ACCESS_NETWORK_STATE`, ...), in Lychee itself.
  CI fails the build if one appears. (The optional Google handwriting companion has them, for
  ML Kit's models only.) Downloads open in the browser (Custom Tab); an add-on installs in one
  place only, its sheet in Settings > Add-ons, by choosing the downloaded file (opening the file
  from the browser no longer offers Lychee).
- Downloaded models may be any size, but must not slow the phone: memory-mapped data
  (Rime dictionaries) is fine; models that hold hundreds of MB in memory are not. AI features
  must be tiny. Handwriting (Chinese, Japanese, Korean only): PaddleOCR's PP-OCRv5 mobile
  recogniser (Apache-2.0, 13-16 MB, ONNX Runtime) comes in the APK (`app/scripts/fetch-ocr.sh`)
  as the basic engine; the higher-quality one is Google's ML Kit digital ink recognition in
  Lychee's companion app (`app/hwcompanion`, package `app.lychee.hwgoogle`; one APK with the
  64-bit and 32-bit ARM engines, native libraries compressed; never shrunk by R8, which broke
  ML Kit): ML Kit downloads its
  models, so it needs the internet and its licence has it in an app of its own; Lychee stays
  offline and binds its service (a signature permission: only apps signed like Lychee), handing
  it the pad's strokes (`handwriting/GoogleHandwriting`). The companion is a small app with Lychee's icon: its full-screen
  setup (the Lychee logo's colours, no Material library, for size) lists each language's model
  (ready, downloading or not on the phone; whether Lychee's keyboards use it; Download or
  Remove), a plain privacy note, and a switch to hide its launcher icon (Lychee opens it from
  Add-ons too, so a hidden icon is never a lost app). Only the models of the enabled keyboards
  download by themselves: Lychee says which on each bind (`GoogleHandwriting.usedModels`, the
  USE message; Cantonese uses Hong Kong's and the mainland's, Mandarin the mainland's and
  Taiwan's). Its launcher entry is off in the manifest and switched on at runtime (`Icon`):
  since Android 10 an app with permissions whose manifest entry is on gets an "App info"
  stand-in icon on launchers such as Kvaesitso when it is turned off; off in the manifest, it
  really hides. The companion itself has no
  tracking, analytics or ads. ML Kit's own performance and usage reporting stays: Google's API
  terms forbid interfering with it. The app's privacy note says so plainly (never the
  handwriting). Lychee itself is closed and commercial. Only the
  companion's code will be published, and only as **source-available** (readable to check it
  has no tracking; no copying, changing or redistributing; licence to be confirmed, PolyForm
  Strict 1.0.0 recommended), never called "open source". Nothing is published until the owner
  has checked it (`app/hwcompanion/README.md`). It also fetches the used models by itself when
  Lychee first binds it (PP-OCR reads the pad meanwhile). This is how the first companion, which
  worked, was made; the icon-less, R8-shrunk build that followed did not work. With it installed only ML Kit reads the pad. Settings > Add-ons offers it (saying ML Kit's licence needs the
  companion, and its size), and so does a chip on the pad. It is built once, and rebuilt only
  when its code or ML Kit version changes (`build.yml`, the private `handwriting-google`
  release), and attached to every build's debug release beside Lychee's APK. Never ML Kit's models copied into Lychee (its licence forbids it). Lychee's own
  trained recogniser was dropped. Either engine is opened only while the pad is open.
- A learning app: Japanese and Korean are typed in romanisation on QWERTY (no native kana or
  Hangul layouts). Candidates show readings and meanings. Korean candidates offer a word's Hanja
  right after it in Hangul, as Japanese offers Kanji; the bar's reading lines never show Kanji
  or Hanja. Lychee's own labels are English (Cantonese/Mandarin), except the 繁/简 key, and 英 shows only
  on the main (letters) keyboard. Candidates in Latin letters only never show on the Chinese,
  Japanese and Korean keyboards (`CandidateBarView.shown`). Cantonese and Mandarin readings
  always show together, on the bar and everywhere (no setting); a word the dictionary lacks a
  reading for in one of them is read character by character (`Annotator.charByChar`).
- Freemium later; no billing or unlocking yet (`Features.isEnabled` is always true). Data in
  packs must allow commercial use (no NC or ND licences; CC BY / BY-SA / BSD are fine).
- Always be honest with users about why something can't be offered: whenever a feature,
  purchase or unlock isn't available (old Android, no Google Play, an emulator, a root tool
  changing Lychee's security key, a revoked phone model), say plainly why and what, if
  anything, they can do. Never fail silently or hide the reason.
- Paid add-ons: everything agreed (offering, USD prices, packs, Full, demos, registration and
  the security model) is in `PAID.md`; follow it, and update it when the owner changes a
  decision. Build only the store UI until billing exists. One-time purchases only; every
  language the same price. Data files are never locked: third-party data (CC BY-SA and the
  rest) ships unencrypted under its licence, and the paid feature is locked in the app instead.
  Only Lychee's own work (rules, notes, tables) is encrypted, so nobody can take it.
- Privacy: the clipboard panel is off until turned on (`clipboard/ClipboardStore`); incognito
  (`typing/Incognito`, automatic in password and no-learning fields) keeps nothing; anything
  that learns or stores what is typed must check `Incognito.active`.
- The keyboard follows Gboard's look and behaviour unless the owner chose otherwise.
- Voice typing has no bar: the mic key starts listening and glows (brighter as you speak), a
  second tap stops and types what was said (`voice/VoicePanelView`, a view that never shows).
  The recogniser's tones play at media volume, so it is turned down to a third while listening
  and put back after (unless changed meanwhile). Failures say why in a short message. Offline
  (Lychee can't check the network: no permission), Google's recogniser fails and Lychee tries the
  phone's own offline recogniser (Android 12+) if it has one; else the mic flashes at once and
  keeps an offline badge (a crossed-out cloud, no toast) until it works again.
- The clipboard panel has ✎ and its on/off switch (Material 3's, `ui/M3Switch`) at the right of its title, as Gboard's (off
  forgets every copy): ✎ is edit mode, where taps tick copies and Add (the text field's
  selection, or all of it, as a new copy), Pin and Delete take the switch's place in the title
  bar (no bar of their own at the bottom); ✓ leaves it.
- Holding an emoji shows its full name with any skin tone ("waving hand: medium skin tone") and
  its code points, in its card or tone picker and in the panel's search bar for a few seconds.
- The key bubble is the long-press popup's panel with one key in it (the same shape, colour,
  soft shadow and letter size); letters are never squeezed sideways.
- The keyboard's default colours are the Lychee logo's (`LycheeColorStyle.LYCHEE`: blush keys,
  the logo's red on Enter); panels, cards and outlines are Lychee's rose on every phone, never
  the wallpaper's (only the Wallpaper colour style uses those).
- Handwriting reads every character of its keyboard's language, whatever the 繁/简 setting
  (no 繁/简 key while the pad is open): Chinese, every Chinese character in both scripts
  (ML Kit reads both scripts' models and merges them), not Japan's or Korea's own
  (`chinese/CjkForms`: kokuji, gukja) unless Chinese writes them too; Japanese, kana and every
  kanji (Unihan's Japanese readings, the kanji dictionary, kokuji); Korean, Hangul and every Hanja
  (Unihan's Korean readings, gukja). Word cards name kokuji and gukja. The pad's ⌫ and ABC step
  aside while the candidates' dropdown is open; the dropdown has slight dividers and fades out
  at the bottom, above the navigation buttons. While writing there is no 英 or 繁/简; the bar
  has Back and "Handwriting" like every mode (raised clear of the pad), ⌫ is a key over ?123 in
  the bottom row, the pad is a tinted surface of its own with rounded top corners (no line; the
  functional keys' tint, so the space bar under it stands apart), and
  it reaches up into the bar's typed-letters line (the guesses and a centred ⌄ sit at the bar's
  top; the dropdown opens right under them). What follows a character picked is in that
  character's own script. A companion slow to connect never leaves strokes without guesses
  (Lychee's own engine reads them meanwhile).
- Candidates have no boxes: small faint dividers between them, in the bar and the dropdown.
  The bar shows six one- or two-character candidates at most, five with a three- or
  four-character word, four with a longer one, sharing its width (never shrunk); the dropdown's
  rows follow the same counts. The dropdown rolls down from the bar over the keys (no blink),
  with its own bottom row exactly where the keyboard's is (?123, emoji, space, ⌫ deleting the
  letters typed, Enter), with a single-character switch as a key of that row beside the emoji
  key (單字 / 单字, 単漢字, 한 글자, on every Chinese, Japanese and Korean keyboard; the space bar
  gives it room), showing one character at a time in the bar and the dropdown. Each time the keyboard opens,
  Rime is started and the schema chosen if they aren't yet, so candidates come at once.
- Number, phone and date fields have one number pad (`toolbar/NumpadPanelView`, forced): the
  digits centred with no keys at the sides, over three equal keys (⌫ 0 Enter); 0 held shows
  symbols (the field's own first), which are written clearly beside the 0. It is the only number pad: the symbols page has no number
  pad key (its bottom row is the letters'). LatinIME's own number and phone pads (letters on
  their keys) never show: whenever KeyboardSwitcher sets one, `LatinIME.updateLycheeForcedNumpad`
  covers it, and no panel closing or Back uncovers it (`LycheePanels.hideOthers`, `anyClosable`).
- The symbols pages' bottom row is the letters' (the emoji or language key beside the comma);
  the comma held gives the shortcuts and the period its punctuation, as on the letters.
- Holding the spacebar lists the keyboards (`layout/LanguageCard`): tap one, or, with Settings >
  Keys > Holding the spacebar, slide onto one without lifting and let go.
- Enter always keeps its accent colour and an icon for the field's action (✓ done, search,
  send, go, next): an app's own action label (Pleco's "Done") never turns it into a grey text
  key (`KeyboardId.imeAction`); the key still does the app's action.
- Every text field but passwords suggests (email and web addresses, filters and fields marked
  no-suggestions included, uncorrected), and every ordinary text field corrects, as Gboard does,
  whatever flags the app set (`InputAttributes`). Personal dictionary words are suggested,
  never autocorrected to (`Suggest`). Holding an English word no dictionary knows opens a small
  card (the word, a speaker, ☆): ☆ saves it to Android's personal dictionary
  (`typing/EnglishWords`).
  - A box for writing (several lines, a message, or capitalising sentences or words) corrects
    even if the app marks it "no suggestions": Instagram's chat box does.
  - Passwords, email and web addresses, filters, and single-line boxes marked no-suggestions
    don't correct.
  - Name fields suggest but never correct.
  - Diagnostics logs each field's app, input type and options, never its text.
- Autocorrect's own rules (`core/typing/AutoCorrectPolicy`, with unit tests) sit on LatinIME's
  threshold.
  - Corrected: jumbles, slips onto neighbouring keys, common misspellings, missing apostrophes.
  - Left alone: informal words, names typed with a capital, capitals inside a word (iPhone),
    words stretched for feeling (sooo), and contractions that are words as they stand (its,
    were, well).
  - A word put back is never corrected again (`CorrectionMemory`).
- Every candidate has a meaning: a Chinese word or phrase the dictionary lacks (or has without
  a usable meaning) shows its pieces' meanings ("I + walk"), and its card lists the parts; a
  Japanese or Korean phrase is broken into the words and Kanji/Hanja the dictionary has, its
  meaning their meanings, its card a Parts section.
- Incognito has no switch: it follows the field. While on, its mark replaces the shortcuts
  menu button (a tap still opens the menu). Password, email and web address fields open in
  English ("visible password" fields don't: apps such as Pleco mark search boxes that way),
  but the language key still switches (`typing/FieldKind`); number fields don't.
- Motion: one way of moving everywhere (`layout/LycheeMotion`): things cross-fade in place,
  never slide in like a window; cards and panels spring up to size (a few percent past, then
  settling); every button, key of a panel, candidate and listing sinks a little while pressed and
  springs back (`springy`, Settings' `pressSpring`); the key bubble and long-press popup spring
  up. Gentle, never a wobble, and nothing moves with the phone's animations off.
- Settings: full Material 3 (`settings/Components.kt`, `settings/Buttons.kt`, `settings/Theme.kt`):
  Material 3's components (switches, sliders, buttons, cards, menus, app bar), shapes, type scale
  and colour scheme (Lychee's own rose palette from the icon, with a leaf green, the same on every
  page and phone: not the wallpaper's Material You colours, whose dark blues looked poor), laid out with
  Material 2's spacing and format: a one-line app bar, plain full-width lists, group headings in
  the accent colour with a thin divider between groups, 24 dp grey icons on a 72 dp keyline,
  choices in dropdown menus dropped from their row. Visual settings (theme, keys, key shape, height, candidate size, emoji size,
  fonts, pronunciation, the key by the comma, the voice key) show a live preview where they are
  changed. A keyboard preview is the real keyboard (`layout/RealKeyboard`: the keyboard's own
  views and keys), given the page's choices before they are saved, never a drawing of it. The packs page (`packs/site`) is the same: Material 2's components in Material 3's
  tonal colours.
  Add-ons (`settings/AddonsPage.kt`) is Lychee's own store (never a copy of Pleco's: professional,
  no savings sums, no "free while testing"; wording in `PAID.md`), opened from a banner at the
  top of Settings.
  - Three tabs: Store, Installed, Updates.
  - Every listing, in the store and in Installed alike, is the same compact card
    (`ListingCard`): tile, name, one line, chips (where it stands, size, version, what it
    holds), and one action at the right (price pill, download, update, remove).
  - The store is laid out like Installed: one honest line (a student developer, one-time
    purchases, no ads or tracking, offline), the bundles, then a section per language (its In
    Depth card and free downloads, those that come with Lychee too), then Every language.
  - Google Handwriting is a listing like the others, noting it needs the free Lychee
    handwriting app.
  - The registration is at the very bottom. Every listing opens its own page like
  Pleco's (`settings/ProductSheet.kt`; a free download's `AddonPage` is laid out the same):
  each part its own card (the price or Free with the actions, In Lychee, What you get, Included,
  then one Details card for the plain words). In Lychee is the real keyboard
  (`settings/KeyboardSample.kt`: the real input view from `layout/RealKeyboard` with its real
  candidate bar, and the word card open on the add-on's tab, shrunk to the page), Light to start
  with whatever the phone and Settings say, with one small light/dark toggle button in its corner. A listing opens at its top; the store's pages and tabs move
  with the settings' page transition. Rows show their text in
  full and act by icon only (delete, download; no "Restore"). Wording sells honestly: no "Pro" or
  hype; ML Kit, open data, licences, open source and privacy named plainly
  (free and, later, paid add-ons: `price` and `featured` in packs.json; everything is free until
  billing exists): featured cards, chips by language, store rows; it gets downloads through a
  sheet (download, then open or choose the file); Storage lists everything kept by language and kind, and removes one pack
  or a whole language. A removed bundled pack stays out (`packs/PackStorage`, honoured by
  `BundledPacks.installAll`) until restored there.
- Pronunciation (Settings > Pronunciation): Mandarin Mainland or Taiwan (CC-CEDICT's
  "Taiwan pr." readings, a zh-TW voice), English both, US or UK (`speech/Pronunciation`).
  Lychee speaks with Google's speech engine when it is installed, else the phone's default
  (`speech/Speech`), offline voices only. Voices have no names in the engines, so none are made
  up and no gender is guessed: each is "Voice N", how high or low it sounds (measured from a
  spoken sample), and its engine code, unless the user names it; each voice keeps its own pitch
  and speed.
- Diagnostics is a plain error report (errors, the last crash, the log; shared only by the user).
  Handwriting samples are no longer collected. Licences (Settings > About) lists every software
  component, Google ML Kit (in Lychee handwriting), the dictionary data and the packs, each with
  its source and licence.

## Layout

Everything the app build uses is in `app/` (a push changing it starts `build.yml`); the rest
is outside it.

- `app/` — the Gradle project (`settings.gradle.kts`, `gradlew`, ...) and:
  - `app/latinime/` — the app. LatinIME's Java in `java/`; Lychee's own code in
    `lychee/kotlin/app/lychee`, resources in `lychee/res`, the native Rime bridge in
    `lychee/cpp`. LatinIME changes are marked `// LYCHEE`. The app icon (two lychees) is drawn
    in `java/res/drawable/lychee_icon_*` with PNG launchers in `java/res/mipmap-*`; its SVG
    sources and store PNGs are in `lychee/icon`.
  - `app/core/` — pure Kotlin logic with JVM unit tests (romanisation, glide decoder, symbols ...).
  - `app/data/` — build scripts for the data bundled in the APK (Rime schemas and
    dictionaries, readings, emoji, Korean word list).
  - `app/scripts/` — fetching prebuilt librime, fonts, the handwriting models and the packs
    bundled in the APK.
- `tools/` — `screens-setup.sh` and `screens.sh`: drawing the UI locally with Paparazzi.
- `packs/` — the pack builders and the packs page (Japanese, Korean, English dictionaries,
  stroke order, examples, font mirrors; `packs/site`, laid out like the in-app Add-ons page:
  each add-on a compact card under language headings, Lychee's rose), run by `packs.yml`.
- `.github/workflows/` — `build.yml` (the app), `packs.yml` (the packs, by hand) and
  `cleanup.yml` (Actions storage is limited: after a successful build or packs run it deletes
  every other run of that workflow with its artifacts and logs, and stale caches; run by hand
  it cuts every workflow down to its newest successful run).
- Packs that come with the app: the English dictionary,
  stroke order (Chinese, Japanese from KanjiVG, Korean made from letter strokes) and example
  sentences are bundled in the APK (`app/scripts/bundle-packs.py` fetches the newest
  published ones; `packs/BundledPacks.kt` in the app unpacks them). The Japanese and Korean
  dictionaries, fonts and emoji are downloads (Settings > Add-ons, by language and Misc; your own
  fonts are chosen in Settings > Fonts, not in Add-ons; Check for updates covers every installed
  pack, bundled ones too). Every download is a file named with its version: the one the last
  check named, else the packs page's `#get=<id>` goes straight on to the newest one's file. Emoji are
  the bundled Noto Color Emoji by default: Google Fonts' build (google/fonts
  `ofl/notocoloremoji`, COLRv1, pinned in `app/data/emoji_sources.json`; Google's own CBDT
  release has broken gradients), drawn from Android 13 (older phones use their own emoji);
  Noto 3D is a download pack (about 62 MB, too big to bundle) mirrored by `packs.yml` (its Apple sbix format converted to
  CBDT by `packs/fonts/sbix_to_cbdt.py`; Android drew sbix fonts' outlines, black), and the user may pick their
  own emoji font or the phone's. Emoji font zips stay out of Actions artifacts (storage).
- `app/latinime/lychee/assets/lychee/packs.json` — the downloads catalogue (fonts and data packs).
- Pack updates: Lychee can't go online, so Updates opens the packs page (one small card) with the
  installed versions after the `#`; it downloads nothing, it hands the newest back via
  `lychee://updates` at once (`packs/Updates.kt`, `PackImportActivity`), and Lychee shows Update
  on each add-on that has a newer version: one download at a time (browsers stop several), the
  sheet naming the file. Only installed packs are checked. Each published version is also on the
  release as `lychee-<name>-<version>.zip` (`packs/publish.py`, the manifest's `download_file`;
  the newest three per pack are kept): the page hands those names back and Lychee downloads that
  file, so it is easy to tell from an older download; choosing an older version than the update
  is refused, naming the right file. The Updates banner just lists "Update found for" with each
  add-on and its new version. "All add-ons" (the page with `all=1`) lists every add-on: updates,
  installed and not installed.
- The dictionary card (`chinese/DictionaryPopup.kt`, plan in `REDESIGN.md`) has one fixed size
  and never grows. In the keyboard it is a sheet as wide as the keys, rising from the candidate
  bar; dragging its handle or header down closes it, and swiping up on a candidate opens it.
  With Back, Back and ✕ have a row of their own above the headword.
  - Header: the word, the reading of every syllable, one outlined 粵/普 switch for the whole card,
    the speaker and ✕.
  - Tabs as Pleco has them, swiped or tapped, all on screen at once: Summary (a pin icon),
    Meanings, Reading, Examples, Strokes, Parts. Empty ones are left out, and each page scrolls
    on its own, except the Summary.
  - Summary never scrolls: everything useful, most useful first (meanings, other readings,
    measure word, characters, an example, phrases), and what doesn't fit is left off (`FitPage`).
  - English has no Reading tab: each pronunciation, with its speaker, sits over its meanings.
  - Speakers say exactly the reading shown: a single character or a rarer reading is spelt in
    characters only read that way (`spell`).
  - Readings doesn't repeat the header's readings or its 粵/普 switch: other romanisations and
    every reading with its meaning.
  - Examples ends with Phrases: the words the word builds (each opens its card).
  - Stroke order: one character large; ‹ ›, swipe or dots go through the word's characters;
    one small toggle in the corner for Traditional | Simplified (繁/简; Hangul | Hanja, old kanji),
    the character between it and the speed bar; under it back, play/pause (exactly centred), on,
    and again to the side; at its side a speed bar to drag (0.1× to 2×). Strokes are never numbered. It all
    fits in the card (the character's square takes the room left; the page never scrolls).
  - Parts: a list like Phrases (each character with both readings, its meaning and a chevron):
    the word's characters; a character's parts (Made of) and the characters using it, in
    either script.
  - Held to peek (Settings), the card goes when the finger lifts unless slid onto its lock bar;
    it has ✕ too. English suggestions peek the same way (SuggestionStripView follows the finger;
    its old slide-up panel of more suggestions is gone, the ⌄ dropdown has them).
  - A part opens as its own card, with Back.
