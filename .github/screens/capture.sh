#!/usr/bin/env bash
# Screenshots of Lychee on an emulator, for reviewing the UI (screens.yml). Installs the
# release's APK, opens Settings > Add-ons and the dictionary card (Look up in Lychee), and saves
# each screen to screens/. Taps find their target by its text (uiautomator), not by position.
set -u
PKG=app.lychee
OUT=screens
mkdir -p "$OUT"

# closes Android's "isn't responding" dialog (a slow emulator's launcher), waiting it out
dismiss_anr() {
  for _ in 1 2 3; do
    timeout 30 adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
    adb pull /sdcard/ui.xml /tmp/ui.xml >/dev/null 2>&1
    grep -q "responding" /tmp/ui.xml || return 0
    tap_text Wait
    sleep 2
  done
}

shot() {
  sleep "${2:-3}"
  dismiss_anr
  adb exec-out screencap -p > "$OUT/$1.png"
  echo "saved $1 ($(timeout 20 adb shell dumpsys activity activities | grep -m1 -E 'mResumedActivity|topResumedActivity' | tr -s ' '))"
}

# taps the first element whose text (or description) is exactly $1
tap_text() {
  timeout 30 adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb pull /sdcard/ui.xml /tmp/ui.xml >/dev/null 2>&1
  local xy
  xy=$(python3 - "$1" <<'PY'
import re, sys, xml.etree.ElementTree as ET
want = sys.argv[1]
for n in ET.parse('/tmp/ui.xml').iter('node'):
    if want in (n.get('text'), n.get('content-desc')):
        a, b, c, d = map(int, re.findall(r'\d+', n.get('bounds')))
        print((a + c) // 2, (b + d) // 2)
        break
PY
)
  if [ -n "$xy" ]; then adb shell input tap $xy; else echo "not found: $1"; fi
}

scroll() { adb shell input swipe 540 1700 540 700 400; }

adb install -r -g lychee-universal.apk || { echo "::error::Lychee didn't install"; exit 1; }
adb shell settings put system screen_off_timeout 1800000
# let the freshly booted phone settle (its launcher is slow to start), then wake it
sleep 60
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
dismiss_anr

# --- Settings > Add-ons ---
timeout 40 adb shell am start -n $PKG/app.lychee.settings.SettingsActivity --es app.lychee.settings.PAGE Packs
shot addons-1-store 6
scroll; shot addons-2-store-scrolled
adb shell input keyevent KEYCODE_MOVE_HOME
for cat in Dictionaries Handwriting "Learning tools" "Fonts & emoji"; do
  timeout 40 adb shell am start -n $PKG/app.lychee.settings.SettingsActivity --es app.lychee.settings.PAGE Packs >/dev/null
  sleep 3
  scroll; sleep 1
  tap_text "$cat"
  shot "addons-3-${cat// /-}"
done
timeout 40 adb shell am start -n $PKG/app.lychee.settings.SettingsActivity --es app.lychee.settings.PAGE Packs >/dev/null
sleep 3; tap_text Installed; shot addons-4-installed
tap_text Updates; shot addons-5-updates
# a listing's page
timeout 40 adb shell am start -n $PKG/app.lychee.settings.SettingsActivity --es app.lychee.settings.ADDON card_zh >/dev/null
shot addons-6-listing-card_zh 5
timeout 40 adb shell am start -n $PKG/app.lychee.settings.SettingsActivity --es app.lychee.settings.ADDON pack_zh >/dev/null
shot addons-7-listing-pack_zh 5

# --- the dictionary card (Look up in Lychee: the same card as the keyboard's) ---
card() {
  local word=$1 name=$2
  adb shell am force-stop $PKG
  timeout 40 adb shell am start -a android.intent.action.PROCESS_TEXT -t text/plain \
    -n $PKG/app.lychee.lookup.LookupActivity --es android.intent.extra.PROCESS_TEXT "$word" >/dev/null
  shot "card-$name-1-summary" 10
  for tab in Readings Definitions Parts Examples; do
    tap_text "$tab"
    shot "card-$name-$tab" 2
  done
}
card 學校 xuexiao
card 行 hang
card 我今日去學校 phrase

adb logcat -d -b crash > "$OUT/crash.txt" 2>&1
adb logcat -d -s Lychee:* AndroidRuntime:E > "$OUT/lychee-log.txt" 2>&1
ls -la "$OUT"
