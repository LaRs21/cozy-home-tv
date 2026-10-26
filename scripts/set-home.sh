#!/usr/bin/env bash
# Make Cozy Home TV the home screen, and optionally turn on the Home-button fix and the screensaver.
#   scripts/set-home.sh <TV-IP> [--home-fix] [--screensaver] [--open]
#     --home-fix     enable the accessibility helper that stops the Google TV flash when Home is pressed
#     --screensaver  make Cozy Home TV the screensaver
#     --open         show the home screen now (switches the TV away from whatever is playing!)
set -euo pipefail
cd "$(dirname "$0")/.."
. scripts/lib.sh
ADDR="${1:-}"; shift || true
FIX=0; DREAM=0; OPEN=0
for a in "$@"; do case "$a" in --home-fix) FIX=1 ;; --screensaver) DREAM=1 ;; --open) OPEN=1 ;; *) die "unknown option $a" ;; esac; done
connect_tv "$ADDR"
sh_tv pm path "$PKG" >/dev/null 2>&1 || die "Cozy Home TV is not installed. Run scripts/install.sh first."
SDK="$(sdk_int)"

echo "Setting the home app ..."
sh_tv cmd package set-home-activity "$HOME_COMPONENT" || true
if [ "${SDK:-0}" -ge 29 ]; then sh_tv cmd role add-role-holder --user 0 android.app.role.HOME "$PKG" 0 >/dev/null 2>&1 || sh_tv cmd role add-role-holder android.app.role.HOME "$PKG" || true; fi
echo "  done. (The stock launcher stays installed and enabled: on Google TV it also runs the Settings-button panel.)"

if [ "$FIX" = 1 ]; then
  echo "Turning on the Home-button fix ..."
  CUR="$(sh_tv settings get secure enabled_accessibility_services | tr -d '\r')"
  if [ -z "$CUR" ] || [ "$CUR" = "null" ]; then NEW="$A11Y_COMPONENT"
  elif printf '%s' "$CUR" | grep -q "$A11Y_COMPONENT"; then NEW="$CUR"
  else NEW="$CUR:$A11Y_COMPONENT"; fi
  sh_tv settings put secure enabled_accessibility_services "$NEW"
  sh_tv settings put secure accessibility_enabled 1
  echo "  done. Previous value (for undo): ${CUR:-<empty>}"
fi

if [ "$DREAM" = 1 ]; then
  echo "Setting the screensaver ..."
  CUR="$(sh_tv settings get secure screensaver_components | tr -d '\r')"
  sh_tv settings put secure screensaver_components "$DREAM_COMPONENT"
  sh_tv settings put secure screensaver_enabled 1
  echo "  done. Previous screensaver (for undo): ${CUR:-<empty>}"
fi

if [ "$OPEN" = 1 ]; then sh_tv am start -n "$HOME_COMPONENT" >/dev/null; echo "Opened the home screen."; fi
echo
echo "Press Home on the remote to check. If Google TV still flashes first, run again with --home-fix."
