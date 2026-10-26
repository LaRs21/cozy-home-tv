#!/usr/bin/env bash
# Install (or update) only. ./setup.sh does everything (install, Home, theme, screensaver) in one go.
# Install (or update) Cozy Home TV on the TV over Wi-Fi.
#   scripts/install.sh <TV-IP> [path/to/cozy-home-tv.apk]
# Without an APK path it uses the release APK in dist/ (a debug one if that is all there is) (run ./build.sh first, or download one from Releases).
set -euo pipefail
cd "$(dirname "$0")/.."
. scripts/lib.sh
connect_tv "${1:-}"
APK="${2:-}"; DBG=""
if [ -z "$APK" ]; then
  for f in dist/cozy-home-tv-*.apk; do [ -f "$f" ] || continue; case "$f" in *-debug.apk) DBG="$f" ;; *) APK="$f" ;; esac; done
  [ -n "$APK" ] || APK="${DBG:-}"
fi
if [ -f "$APK.sha256" ]; then [ "$(awk '{print $1; exit}' "$APK.sha256")" = "$(sha256_of "$APK")" ] || die "checksum mismatch for $APK"; echo "checksum ok"; fi
[ -f "$APK" ] || die "no APK found. Build one with ./build.sh or pass a path."
echo "Installing $APK on $SERIAL ..."
if ! "$ADB" -s "$SERIAL" install -r "$APK"; then
  echo
  echo "If it failed with INSTALL_FAILED_UPDATE_INCOMPATIBLE, an install signed with another key is on the TV."
  echo "Uninstall it first:  adb -s $SERIAL uninstall $PKG   (this resets the app's settings)"
  exit 1
fi
echo
echo "Installed. Next:"
echo "  ./setup.sh --ip ${1:-<TV-IP>}                     # or the whole setup in one go"
echo "  scripts/set-home.sh ${1:-<TV-IP>}                 # make it the home screen"
echo "  scripts/set-home.sh ${1:-<TV-IP>} --home-fix      # optional: stop the Google TV flash on Home"
echo "  scripts/push-cozy-pack.sh ${1:-<TV-IP>} <folder>  # optional: your own pictures + music"
