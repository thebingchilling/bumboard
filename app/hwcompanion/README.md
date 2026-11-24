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
- Handwriting is recognised on the phone and never sent anywhere.
- The app itself has no tracking, analytics or ads.
- Google's ML Kit sends Google figures about how it runs: the device model, the app's version,
  and performance such as how long recognition took. Google's API terms forbid interfering with
  that, so it is left as ML Kit ships it, and the app's privacy note says so. Your handwriting
  is never part of it.

## Licence

The code is published so anyone can check what the app does. Its licence (to be confirmed)
allows reading it, not copying, changing or redistributing it. ML Kit is Google's, under
Google's ML Kit and API terms.

## What is in it

- `RecognitionService`: the bound service Lychee uses. It receives strokes and returns ML Kit's
  guesses.
- `Models`: downloads the models and reports which are ready.
- `SetupActivity`: the one screen. It shows each language's model and whether it's ready, the
  privacy note, and a switch that hides the app icon.

Its only dependency is ML Kit's digital ink recognition (`com.google.mlkit:digital-ink-recognition`).
