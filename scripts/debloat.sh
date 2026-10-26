#!/usr/bin/env bash
# OPTIONAL. Reversibly disable apps you don't use. Nothing is uninstalled; every change is logged for undo.
#
#   scripts/debloat.sh <TV-IP> <list.txt>            # dry run: shows what would be disabled
#   scripts/debloat.sh <TV-IP> <list.txt> --apply    # do it (writes debloat-<date>.log)
#   scripts/debloat.sh <TV-IP> --undo <debloat-xxx.log>
#
# list.txt: one package per line, # for comments. Start from examples/debloat/google-tv-common.txt
# and delete the lines you want to keep. Ask yourself for each one: "do I use this?"
#
# Hard safety list (refused even if listed). Learned the hard way:
#  - the stock launcher (com.google.android.apps.tv.launcherx / tvlauncher): on Google TV it also runs the
#    Settings-button quick panel; disabling it breaks Settings
#  - Live TV / tuner / TV input apps: they switch HDMI inputs
#  - system updates (OTA), Google Play services / Play Store, setup, settings, remote/Bluetooth, keyboards
set -euo pipefail
cd "$(dirname "$0")/.."
. scripts/lib.sh
ADDR="${1:-}"; shift || true

PROTECTED_REGEX='^(android|com\.android\.(systemui|tv\.settings|providers\..*|bluetooth|vending|shell|phone|externalstorage|packageinstaller|permissioncontroller|inputdevices|tv)|com\.google\.android\.(apps\.tv\.launcherx|tvlauncher|gms|gsf|gms\..*|tv|tv\.remote\.service|tungsten\.setupwraith|leanback\.ime|inputmethod\..*|tvrecommendations|apps\.tv\.launcherx\..*|permissioncontroller|ext\.services|ext\.shared|packageinstaller|configupdater|partnersetup|onetimeinitializer)|io\.github\.lars21\.cozyhometv)$|tuner|tvinput|tvcenter|wwtv|\.ota|ota\.|fota|update|systemupdate|remote|keyboard|realtek\.tv$|setup'

if [ "${1:-}" = "--undo" ]; then
  LOG="${2:-}"; [ -f "$LOG" ] || die "log not found: $LOG"
  connect_tv "$ADDR"
  grep -E '^disabled ' "$LOG" | awk '{print $2}' | while read -r p; do
    echo "enable $p"; "$ADB" -s "$SERIAL" shell pm enable "$p" </dev/null >/dev/null || echo "  (failed: $p)"
  done
  echo "Done."; exit 0
fi

LIST="${1:-}"; APPLY="${2:-}"
[ -f "$LIST" ] || die "give a list file, e.g. examples/debloat/google-tv-common.txt"
connect_tv "$ADDR"
INSTALLED="$(sh_tv pm list packages -e | tr -d '\r' | sed 's/^package://')"
LOG="debloat-$(date +%Y%m%d-%H%M%S).log"
[ "$APPLY" = "--apply" ] && echo "# scripts/debloat.sh on $SERIAL $(date)" > "$LOG"

grep -vE '^\s*(#|$)' "$LIST" | awk '{print $1}' | while read -r p; do
  if printf '%s' "$p" | grep -qE "$PROTECTED_REGEX"; then echo "SKIP (protected)  $p"; continue; fi
  if ! printf '%s\n' "$INSTALLED" | grep -qx "$p"; then echo "skip (not found)  $p"; continue; fi
  if [ "$APPLY" = "--apply" ]; then
    # one pm call per shell: chained pm commands can silently stop after the first
    if "$ADB" -s "$SERIAL" shell pm disable-user --user 0 "$p" </dev/null | grep -q "disabled"; then echo "disabled $p" | tee -a "$LOG"; else echo "FAILED   $p"; fi
  else
    echo "would disable     $p"
  fi
done
if [ "$APPLY" = "--apply" ]; then echo; echo "Undo with: scripts/debloat.sh ${ADDR} --undo $LOG"; else echo; echo "Dry run only. Add --apply to do it."; fi
