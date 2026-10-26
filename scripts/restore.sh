#!/usr/bin/env bash
# Undo what set-home.sh did, and optionally uninstall Cozy Home TV.
#   scripts/restore.sh <TV-IP> [--uninstall] [--remove-pack]
set -euo pipefail
cd "$(dirname "$0")/.."
. scripts/lib.sh
ADDR="${1:-}"; shift || true
UNINSTALL=0; RMPACK=0
for a in "$@"; do case "$a" in --uninstall) UNINSTALL=1 ;; --remove-pack) RMPACK=1 ;; *) die "unknown option $a" ;; esac; done
connect_tv "$ADDR"

CUR="$(sh_tv settings get secure enabled_accessibility_services | tr -d '\r')"
if printf '%s' "$CUR" | grep -q "$A11Y_COMPONENT"; then
  NEW="$(printf '%s' "$CUR" | tr ':' '\n' | grep -v "^$A11Y_COMPONENT\$" | paste -sd ':' - || true)"
  if [ -z "$NEW" ]; then sh_tv settings delete secure enabled_accessibility_services >/dev/null; else sh_tv settings put secure enabled_accessibility_services "$NEW"; fi
  echo "Home-button fix turned off."
fi
if sh_tv settings get secure screensaver_components | grep -q "$PKG"; then
  sh_tv settings delete secure screensaver_components >/dev/null; echo "Screensaver set back to the TV's default."
fi
SDK="$(sdk_int)"
[ "${SDK:-0}" -ge 29 ] && sh_tv cmd role remove-role-holder android.app.role.HOME "$PKG" >/dev/null 2>&1 || true
echo "Cozy Home TV is no longer the home app (the TV's own launcher takes over)."
if [ "$RMPACK" = 1 ]; then sh_tv rm -rf /sdcard/CozyHome; echo "Removed /sdcard/CozyHome."; fi
if [ "$UNINSTALL" = 1 ]; then "$ADB" -s "$SERIAL" uninstall "$PKG" && echo "Uninstalled."; fi
echo "Press Home on the remote to check."
