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

- Only the pack zips (`lychee-*.zip`) go there, on the `packs` pre-release. No app code,
  APKs, internal notes or anything from this private repository.
- Packs are uploaded by `packs.yml` with the owner's token (`PACKS_TOKEN` secret), so they
  appear as the owner's. Publishing only runs when the workflow is started by hand on `main`
  (workflow_dispatch); review the "List the packs' contents" step of a normal run first.
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
- `latinime/lychee/assets/lychee/packs.json` — the downloads catalogue.
