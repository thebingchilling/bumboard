# Lychee keyboard

Lychee is an Android keyboard for language learners (English, Cantonese, Mandarin, Japanese,
Korean, handwriting), built on AOSP LatinIME. This repository (`thebingchilling/bumboard`) is
private and is the testing ground; its name stays `bumboard`, everything inside is Lychee.

## Working rules

- Push straight to `main`. No feature branches, no pull requests unless asked.
- Don't compile locally: GitHub Actions builds and tests (`build.yml`, `packs.yml`). Check
  the runs on `main` after pushing and fix any red build.
- Never publish the Lychee app (APK or a release of it) anywhere until the owner says so.
- Packs may be published to the public `thebingchilling/lychee` repository (see below).

## Public repository: `thebingchilling/lychee`

Public facing, so be careful:

- Only the pack zips (`lychee-*.zip`) and `manifest.json` go on the `packs` pre-release, and
  only the packs page (`data/packs/site`: index.html, manifest.json, Google Sans Flex) on its
  `gh-pages` branch. No app code, APKs, internal notes or anything else from this repository.
- `packs.yml` publishes by itself on `main` (weekly, and when the pack builders change): it
  rebuilds every pack from its sources' newest data and uploads only the packs whose contents
  changed, with a date version (`data/packs/publish.py`). It uses the owner's token
  (`PACKS_TOKEN` secret), so uploads appear as the owner's.
- The packs page (https://thebingchilling.github.io/lychee/) is only pushed when the
  `LYCHEE_PAGE` repository variable is `on`; the owner turns it on.
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
- `latinime/lychee/assets/lychee/packs.json` — the downloads catalogue; `previews/` holds the
  pictures of emoji fonts shown before they are downloaded (`data/packs/fonts/render_preview.js`).
- Pack updates: Lychee can't go online, so "Check for updates" opens the packs page with the
  installed versions after the `#`; the page hands the newest back via `lychee://updates`
  (`packs/Updates.kt`, `PackImportActivity`). Only installed packs are checked.
