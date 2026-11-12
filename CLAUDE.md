# Lychee keyboard

Lychee is an Android keyboard for language learners (English, Cantonese, Mandarin, Japanese,
Korean, handwriting), built on AOSP LatinIME. This repository (`thebingchilling/bumboard`) is
private and is the testing ground; its name stays `bumboard`, everything inside is Lychee.

## Working rules

- Push straight to `main`. No feature branches, no pull requests unless asked.
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
  (Rime dictionaries) is fine; models that hold hundreds of MB in memory are not. AI features
  must be tiny. Handwriting is PaddleOCR's PP-OCRv5 mobile recogniser (Apache-2.0, 13-16 MB,
  ONNX Runtime), in the APK (`app/scripts/fetch-ocr.sh`), opened only while the pad is open.
- A learning app: Japanese and Korean are typed in romanisation on QWERTY (no native kana or
  Hangul layouts). Candidates show readings and meanings.
- Freemium later; no billing or unlocking yet (`Features.isEnabled` is always true). Data in
  packs must allow commercial use (no NC or ND licences; CC BY / BY-SA / BSD are fine).
- Privacy: the clipboard panel is off until turned on (`clipboard/ClipboardStore`); incognito
  (`typing/Incognito`, automatic in password and no-learning fields) keeps nothing; anything
  that learns or stores what is typed must check `Incognito.active`.
- The keyboard follows Gboard's look and behaviour unless the owner chose otherwise.

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
- `packs/` — the pack builders and the packs page (Japanese, Korean, English dictionaries,
  stroke order, examples, font mirrors; `packs/site`), run by `packs.yml`.
- `.github/workflows/` — `build.yml` (the app), `packs.yml` (the packs, by hand) and
  `cleanup.yml` (Actions storage is limited: after a successful build or packs run it deletes
  every other run of that workflow with its artifacts and logs, and stale caches; run by hand
  it cuts every workflow down to its newest successful run).
- Packs that come with the app: the Japanese, Korean and English dictionaries,
  stroke order (Chinese, Japanese from KanjiVG, Korean made from letter strokes) and example
  sentences are bundled in the APK (`app/scripts/bundle-packs.py` fetches the newest
  published ones; `packs/BundledPacks.kt` in the app unpacks them). Only fonts are downloads. Emoji are
  the bundled Noto Color Emoji by default (`app/data/emoji_sources.json`, the newest release);
  Noto 3D (two sizes) is a download pack mirrored by `packs.yml`, and the user may pick their
  own emoji font or the phone's. Emoji font zips stay out of Actions artifacts (storage).
- `app/latinime/lychee/assets/lychee/packs.json` — the downloads catalogue (fonts and data packs).
- Pack updates: Lychee can't go online, so "Check for updates" opens the packs page with the
  installed versions after the `#`; the page hands the newest back via `lychee://updates`
  (`packs/Updates.kt`, `PackImportActivity`). Only installed packs are checked.
