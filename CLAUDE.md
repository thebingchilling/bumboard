# Lychee keyboard

Lychee is an Android keyboard for language learners (English, Cantonese, Mandarin, Japanese,
Korean, handwriting), built on AOSP LatinIME. This repository (`thebingchilling/bumboard`) is
private and is the testing ground; its name stays `bumboard`, everything inside is Lychee.

## Working rules

- Push straight to `main`. No feature branches, no pull requests unless asked.
- Don't compile locally: GitHub Actions builds and tests (`build.yml`, `packs.yml`). Check
  the runs on `main` after pushing and fix any red build.
- Every build on `main` is published by `build.yml` as a pre-release of this private repository
  (`lychee-0.1.0-dev.N`, the APK attached, notes from the commit), like Quartz's releases.
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
  only the packs page (`data/packs/site`: index.html, manifest.json, Google Sans Flex) on
  `lychee-keyboard.github.io`. No app code, APKs, internal notes or anything else from this repository.
- `packs.yml` publishes by itself on `main` (weekly, and when the pack builders change): it
  rebuilds every pack from its sources' newest data and uploads only the packs whose contents
  changed, with a date version (`data/packs/publish.py`). It uses a token of the
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

- `latinime/` — the app. LatinIME's Java in `java/`; Lychee's own code in `lychee/kotlin/app/lychee`,
  resources in `lychee/res`, native bridges (Rime, Zinnia) in `lychee/cpp`. LatinIME changes
  are marked `// LYCHEE`.
- `core/` — pure Kotlin logic with JVM unit tests (romanisation, glide decoder, symbols ...).
- `data/` — build scripts for bundled data (Rime schemas and dictionaries, emoji, Korean word
  list) and for packs (`data/packs/`: Japanese dictionary, handwriting, font mirrors).
- Packs that come with the app: handwriting, the Japanese and Korean dictionaries, stroke order
  and example sentences are bundled in the APK (`scripts/bundle-packs.py` fetches the newest
  published ones; `packs/BundledPacks.kt` unpacks them). Only fonts and emoji are downloads.
- `latinime/lychee/assets/lychee/packs.json` — the downloads catalogue; `previews/` holds the
  pictures of emoji fonts shown before they are downloaded (`data/packs/fonts/render_preview.js`).
- Pack updates: Lychee can't go online, so "Check for updates" opens the packs page with the
  installed versions after the `#`; the page hands the newest back via `lychee://updates`
  (`packs/Updates.kt`, `PackImportActivity`). Only installed packs are checked.
