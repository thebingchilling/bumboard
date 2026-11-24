# Lychee handwriting

Google's ML Kit handwriting recognition for the handwriting pad of the Lychee keyboard
(Chinese, Japanese and Korean).

Lychee itself has no internet access. ML Kit downloads its handwriting models from Google, and
its licence needs it to be in an app of its own. So this small app holds ML Kit, downloads the
models, and reads the strokes Lychee hands it. Only apps signed with Lychee's key can use it,
through a signature permission.

## What it does with the internet

- It downloads Google's handwriting models: Chinese (Simplified, Hong Kong, Taiwan), Japanese
  and Korean. Each is about 20 MB.
- That's all. Handwriting is recognised on the phone and never sent anywhere.
- No tracking, analytics or ads. ML Kit normally reports how it is used through Google's data
  transport library. Its backend and schedulers are removed from the manifest
  (`src/main/AndroidManifest.xml`), and the build fails if they come back
  (`.github/workflows/build.yml`).

## What is in it

- `RecognitionService`: the bound service Lychee uses. It receives strokes and returns ML Kit's
  guesses.
- `Models`: downloads the models and reports which are ready.
- `SetupActivity`: the one screen. It shows each language's model and whether it's ready, the
  privacy note, and a switch that hides the app icon.

Its only dependency is ML Kit's digital ink recognition (`com.google.mlkit:digital-ink-recognition`).
