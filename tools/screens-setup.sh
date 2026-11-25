#!/usr/bin/env bash
# Sets up a fresh session (or computer) to draw Lychee's screens locally with Paparazzi, the way
# CLAUDE.md allows for UI work: the Android SDK, the build's pointer to it, and Google's mirror
# of Maven Central (Maven Central rate-limits shared cloud machines). Safe to run again: each
# step is skipped when already done. Then draw the screens with tools/screens.sh.
set -euo pipefail

SDK="${ANDROID_HOME:-/opt/android-sdk}"
REPO="$(cd "$(dirname "$0")/.." && pwd)"
TOOLS_ZIP=commandlinetools-linux-13114758_latest.zip

# 1. the Android SDK's command-line tools, then the platform and build tools the app needs
if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  echo "Installing the Android command-line tools into $SDK"
  tmp="$(mktemp -d)"
  curl -sSfL -o "$tmp/tools.zip" "https://dl.google.com/android/repository/$TOOLS_ZIP"
  mkdir -p "$SDK/cmdline-tools"
  unzip -q -o "$tmp/tools.zip" -d "$tmp"
  rm -rf "$SDK/cmdline-tools/latest"
  mv "$tmp/cmdline-tools" "$SDK/cmdline-tools/latest"
  rm -rf "$tmp"
fi
export ANDROID_HOME="$SDK"
if [ ! -d "$SDK/platforms/android-37.0" ] || [ ! -d "$SDK/build-tools/37.0.0" ]; then
  echo "Installing the Android platform and build tools (a few minutes)"
  yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" --licenses >/dev/null 2>&1 || true
  "$SDK/cmdline-tools/latest/bin/sdkmanager" "platforms;android-37.0" "build-tools;37.0.0" "platform-tools" >/dev/null
fi

# 2. the build's pointer to the SDK (git-ignored)
echo "sdk.dir=$SDK" > "$REPO/app/local.properties"

# 3. Google's mirror of Maven Central, tried first (this machine's Gradle only)
mkdir -p "$HOME/.gradle/init.d"
cat > "$HOME/.gradle/init.d/mirror.gradle" <<'GRADLE'
// Maven Central rate-limits shared cloud machines, so Google's mirror of it is tried first.
beforeSettings { settings ->
    def mirror = { r -> r.maven { url "https://maven-central.storage-download.googleapis.com/maven2/" } }
    mirror(settings.pluginManagement.repositories)
    mirror(settings.dependencyResolutionManagement.repositories)
}
GRADLE

echo "Ready. Draw the screens with: tools/screens.sh"
