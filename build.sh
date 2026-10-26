#!/usr/bin/env bash
# Build Cozy Home TV without Gradle: aapt + javac + d8 + zipalign + apksigner (all from the Android SDK).
#
# Needs: JDK 17, Android SDK with platforms;android-34 and any build-tools (34+).
#
# Signing (release):
#   COZY_KEYSTORE=/path/to/release.keystore         keystore file (keep it OUT of this repo)
#   COZY_KEYSTORE_PASS=...  or  COZY_KEYSTORE_PASS_FILE=/path/to/password.txt
#   COZY_KEY_ALIAS=cozyhometv                        (default)
#   COZY_KEY_PASS=...                                (default: same as the keystore password)
# Without COZY_KEYSTORE the APK is signed with the standard Android debug key (~/.android/debug.keystore),
# fine for trying it out; you can't later update a debug-signed install with a release-signed APK (uninstall first).
#
# Optional: ANDROID_SDK (or ANDROID_HOME / ANDROID_SDK_ROOT), JAVA_HOME, BUILD_TOOLS=34.0.0
set -euo pipefail
cd "$(dirname "$0")"

# ---- tools ----
if [ -z "${JAVA_HOME:-}" ]; then
  for j in /opt/homebrew/opt/openjdk@17 /usr/local/opt/openjdk@17 /usr/lib/jvm/java-17-openjdk-amd64 /usr/lib/jvm/java-17-openjdk; do
    [ -x "$j/bin/javac" ] && { export JAVA_HOME="$j"; break; }
  done
  if [ -z "${JAVA_HOME:-}" ] && [ -x /usr/libexec/java_home ]; then JAVA_HOME="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"; export JAVA_HOME; fi
fi
[ -n "${JAVA_HOME:-}" ] && export PATH="$JAVA_HOME/bin:$PATH"
command -v javac >/dev/null || { echo "JDK 17 not found: set JAVA_HOME"; exit 1; }

SDK="${ANDROID_SDK:-${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}}"
if [ -z "$SDK" ]; then for s in "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do [ -d "$s" ] && { SDK="$s"; break; }; done; fi
[ -d "$SDK" ] || { echo "Android SDK not found: set ANDROID_SDK"; exit 1; }
JAR="$SDK/platforms/android-34/android.jar"
[ -f "$JAR" ] || { echo "Missing $JAR: sdkmanager 'platforms;android-34'"; exit 1; }
BTV="${BUILD_TOOLS:-$(ls "$SDK/build-tools" | sort -V | tail -1)}"
BT="$SDK/build-tools/$BTV"
[ -x "$BT/aapt" ] || { echo "Missing build-tools in $SDK/build-tools"; exit 1; }

VERSION="$(sed -n 's/.*android:versionName="\([^"]*\)".*/\1/p' AndroidManifest.xml | head -1)"
PKG_DIR=io/github/lars21/cozyhometv
BUILD=build; DIST=dist
rm -rf "$BUILD"; mkdir -p "$BUILD/classes" "$BUILD/dex" "$BUILD/gen" "$DIST"

echo "== Cozy Home TV $VERSION (build-tools $BTV)"
"$BT/aapt" package -f -m -J "$BUILD/gen" -M AndroidManifest.xml -S res -I "$JAR"
javac -nowarn -Xlint:-options -encoding UTF-8 -source 8 -target 8 -classpath "$JAR" -d "$BUILD/classes" src/$PKG_DIR/*.java "$BUILD/gen/$PKG_DIR/R.java"
find "$BUILD/classes" -name '*.class' > "$BUILD/classes.list"
"$BT/d8" --release --min-api 26 --lib "$JAR" --output "$BUILD/dex" @"$BUILD/classes.list"
"$BT/aapt" package -f -0 mp3 -0 ogg -M AndroidManifest.xml -S res -A assets -I "$JAR" -F "$BUILD/unsigned.apk"
(cd "$BUILD/dex" && zip -q ../unsigned.apk classes.dex)
"$BT/zipalign" -f 4 "$BUILD/unsigned.apk" "$BUILD/aligned.apk"

# ---- signing ----
if [ -n "${COZY_KEYSTORE:-}" ]; then
  [ -f "$COZY_KEYSTORE" ] || { echo "COZY_KEYSTORE not found: $COZY_KEYSTORE"; exit 1; }
  if [ -z "${COZY_KEYSTORE_PASS:-}" ] && [ -n "${COZY_KEYSTORE_PASS_FILE:-}" ]; then COZY_KEYSTORE_PASS="$(tr -d '\r\n' < "$COZY_KEYSTORE_PASS_FILE")"; fi
  [ -n "${COZY_KEYSTORE_PASS:-}" ] || { echo "Set COZY_KEYSTORE_PASS or COZY_KEYSTORE_PASS_FILE"; exit 1; }
  export COZY_KEYSTORE_PASS; export COZY_KEY_PASS="${COZY_KEY_PASS:-$COZY_KEYSTORE_PASS}"
  OUT="$DIST/cozy-home-tv-$VERSION.apk"
  "$BT/apksigner" sign --ks "$COZY_KEYSTORE" --ks-key-alias "${COZY_KEY_ALIAS:-cozyhometv}" \
    --ks-pass env:COZY_KEYSTORE_PASS --key-pass env:COZY_KEY_PASS --out "$OUT" "$BUILD/aligned.apk"
else
  DKS="$HOME/.android/debug.keystore"
  if [ ! -f "$DKS" ]; then mkdir -p "$HOME/.android"; keytool -genkeypair -keystore "$DKS" -storepass android -keypass android -alias androiddebugkey \
      -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1; fi
  OUT="$DIST/cozy-home-tv-$VERSION-debug.apk"
  echo "   (no COZY_KEYSTORE set: signing with the Android debug key)"
  "$BT/apksigner" sign --ks "$DKS" --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out "$OUT" "$BUILD/aligned.apk"
fi
rm -f "$OUT.idsig"
"$BT/apksigner" verify "$OUT"
echo "== Built $OUT ($(du -h "$OUT" | cut -f1))"
