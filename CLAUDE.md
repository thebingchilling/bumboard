# Lychee keyboard

Lychee is an Android keyboard for language learners (English, Cantonese, Mandarin, Japanese,
Korean, handwriting), built on AOSP LatinIME. This repository (`thebingchilling/bumboard`) is
private and is the testing ground; its name stays `bumboard`, everything inside is Lychee.

## Working rules

- Push straight to `main`. No feature branches, no pull requests unless asked.
- GitHub Actions minutes are limited; use them sparingly:
  - A push to `main` starts a full `build.yml` run (about 15 minutes) only when it changes
    `app/` (or `build.yml`); docs, `packs/` and the packs workflow never start a build. Commit
    locally while working and push once, when the round is finished (or when the owner says
    to push).
  - A push while a build is running cancels it: the minutes it used are wasted. Push again
    only after the run has finished.
  - Start no workflow by hand (and re-run none) unless the owner asks. `packs.yml` only runs by
    hand; when the pack builders (`packs/`) changed, ask the owner, then run the packs first and the app
    build after them (one build that bundles the new packs, not two).
  - Before pushing, review the diff for compile errors (CI is the only compiler); a red build
    costs another run. Fix a red build in one push.
  - Reading run status and logs costs no minutes.
- Don't compile locally: GitHub Actions builds and tests (`build.yml`, `packs.yml`). Check
  the runs on `main` after pushing and fix any red build.
- Every build on `main` is published by `build.yml` as a debug release (a full release, not a
  pre-release) of this private repository (`lychee-0.1.0-dev.N`, the APK attached, notes from the commit), like Quartz's releases;
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
  pack from its sources' newest data and uploads only the packs whose contents changed, with
  a date version (`packs/publish.py`). It uses a token of the
  lychee-keyboard account (`PACKS_TOKEN` secret), so uploads appear as lychee-keyboard's.
- The packs page (https://lychee-keyboard.github.io/) is pushed by the same runs to the
  `lychee-keyboard/lychee-keyboard.github.io` repository (main branch), which GitHub Pages
  serves; the `lychee` repository's `gh-pages` only forwards /lychee/ there.
- Release notes and file names say Lychee, never BumBoard or Claude.

## Product constraints

- No network permissions, ever (`INTERNET`, `ACCESS_NETWORK_STATE`, ...). CI fails the
  build if one appears. Downloads open in the browser (Custom Tab); opening the downloaded
  file installs it (`PackImportActivity`), or the user picks it.
- Downloaded models may be any size, but must not slow the phone: memory-mapped data
  (Rime dictionaries, Zinnia handwriting models) is fine; models that hold hundreds of MB in
  memory are not. AI features must be tiny.
- A learning app: Japanese and Korean are typed in romanisation on QWERTY (no native kana or
  Hangul layouts). Candidates show readings and meanings.
- Freemium later; no billing or unlocking yet (`Features.isEnabled` is always true).
- The keyboard follows Gboard's look and behaviour unless the owner chose otherwise.

## Layout

Everything the app build uses is in `app/` (a push changing it starts `build.yml`); the rest
is outside it.

- `app/` — the Gradle project (`settings.gradle.kts`, `gradlew`, ...) and:
  - `app/latinime/` — the app. LatinIME's Java in `java/`; Lychee's own code in
    `lychee/kotlin/app/lychee`, resources in `lychee/res`, native bridges (Rime, Zinnia) in
    `lychee/cpp`. LatinIME changes are marked `// LYCHEE`. The app icon (two lychees) is drawn
    in `java/res/drawable/lychee_icon_*` with PNG launchers in `java/res/mipmap-*`; its SVG
    sources and store PNGs are in `lychee/icon`.
  - `app/core/` — pure Kotlin logic with JVM unit tests (romanisation, glide decoder, symbols ...).
  - `app/data/` — build scripts for the data bundled in the APK (Rime schemas and
    dictionaries, readings, emoji, Korean word list).
  - `app/scripts/` — fetching prebuilt librime, Zinnia, fonts, and the packs bundled in the APK.
- `packs/` — the pack builders and the packs page (Japanese, Korean, English dictionaries,
  stroke order, examples, handwriting, font mirrors; `packs/site`), run by `packs.yml`.
- `.github/workflows/` — `build.yml` (the app) and `packs.yml` (the packs, by hand).
- Packs that come with the app: handwriting, the Japanese, Korean and English dictionaries,
  stroke order (Chinese, Japanese from KanjiVG, Korean made from letter strokes) and example
  sentences are bundled in the APK (`app/scripts/bundle-packs.py` fetches the newest
  published ones; `packs/BundledPacks.kt` in the app unpacks them). Only fonts are downloads. Emoji are
  always the bundled Noto Color Emoji (`app/data/emoji_sources.json`, the newest release); there
  are no other emoji fonts.
- `app/latinime/lychee/assets/lychee/packs.json` — the downloads catalogue (fonts and data packs).
- Pack updates: Lychee can't go online, so "Check for updates" opens the packs page with the
  installed versions after the `#`; the page hands the newest back via `lychee://updates`
  (`packs/Updates.kt`, `PackImportActivity`). Only installed packs are checked.
