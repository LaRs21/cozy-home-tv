# shellcheck shell=bash
# Shared helpers for setup.sh, restore.sh, doctor.sh and scripts/*.sh. Source it; don't run it.
# Works with the bash 3.2 that ships with macOS (no associative arrays, no ${x,,}).
#
# Choosing the TV (first match wins):
#   --serial <adb-serial>    any serial `adb devices` shows (emulator-5554, 192.168.1.50:5555, a USB id)
#   <TV-IP> or <IP:port>     e.g. 192.168.1.50 (port 5555 is added)
#   TV=<ip or serial>        environment variable

PKG="io.github.lars21.cozyhometv"
# shellcheck disable=SC2034  # constants used by the scripts that source this file
{ HOME_COMPONENT="$PKG/.HomeActivity"; A11Y_COMPONENT="$PKG/.HomeButtonService"; DREAM_COMPONENT="$PKG/.CozyDream"; }
ADB="${ADB:-adb}"
DRY_RUN="${DRY_RUN:-0}"
STATE_ROOT="${COZY_STATE_DIR:-$HOME/.cozy-home-tv}"
CHANGES_FILE="${CHANGES_FILE:-}"
# shellcheck disable=SC2034
THEMES="clean glass cozy neon starship arcade"

if [ -t 1 ]; then B=$'\033[1m'; G=$'\033[32m'; Y=$'\033[33m'; R=$'\033[31m'; N=$'\033[0m'; else B=""; G=""; Y=""; R=""; N=""; fi
say()  { printf '%s\n' "$*"; }
step() { printf '\n%s== %s%s\n' "$B" "$*" "$N"; }
ok()   { printf '%s  ok%s  %s\n' "$G" "$N" "$*"; }
warn() { printf '%s  !%s   %s\n' "$Y" "$N" "$*" >&2; }
die()  { printf '%serror:%s %s\n' "$R" "$N" "$*" >&2; exit 1; }
lower() { printf '%s' "$1" | tr '[:upper:]' '[:lower:]'; }

need_adb() { command -v "$ADB" >/dev/null 2>&1 || die "adb not found. Install Android platform-tools (macOS: brew install android-platform-tools; Linux: your package manager or https://developer.android.com/tools/releases/platform-tools)."; }

# to_serial <addr>: 192.168.1.50 -> 192.168.1.50:5555; anything else unchanged.
to_serial() {
  case "$1" in
    *:*) printf '%s' "$1" ;;
    *[!0-9.]*) printf '%s' "$1" ;;
    *) printf '%s:5555' "$1" ;;
  esac
}
is_network_serial() { printf '%s' "$1" | grep -qE '^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+:[0-9]+$'; }

# connect_tv <addr-or-serial>: sets SERIAL, or explains what to do and exits.
connect_tv() {
  local addr="${1:-${TV:-}}" st
  [ -n "$addr" ] || die "give the TV's IP address (TV: Settings > Network & Internet > your network) or --serial <adb serial>"
  need_adb
  SERIAL="$(to_serial "$addr")"
  if is_network_serial "$SERIAL"; then "$ADB" connect "$SERIAL" >/dev/null 2>&1 || true; fi
  st="$("$ADB" -s "$SERIAL" get-state 2>/dev/null || true)"
  if [ "$st" != "device" ]; then
    say "Could not talk to $SERIAL (state: ${st:-none})."
    say " - Turn on Developer options > USB debugging (and Network or Wireless debugging if your TV has it)."
    say " - Accept the 'Allow debugging?' prompt on the TV (tick 'Always allow'), then run this again."
    [ "$st" = "unauthorized" ] && say " - The TV is waiting for you to press Allow on its screen."
    exit 1
  fi
}

# Read-only shell on the TV, one command per call (chained pm commands can silently stop after the first).
sh_tv() { "$ADB" -s "$SERIAL" shell "$@" </dev/null | tr -d '\r'; }
sdk_int() { sh_tv getprop ro.build.version.sdk; }
setting_get() { sh_tv settings get "$1" "$2"; }   # prints "null" when unset

# Everything that CHANGES the TV goes through run_tv / run_adb, so --dry-run is honest and every change is logged.
log_change() { [ -n "$CHANGES_FILE" ] && [ "$DRY_RUN" != 1 ] && printf '%s\t%s\n' "$(date '+%Y-%m-%dT%H:%M:%S%z')" "$*" >> "$CHANGES_FILE"; return 0; }
run_tv() {
  if [ "$DRY_RUN" = 1 ]; then say "  [dry-run] adb -s $SERIAL shell $*" >&2; return 0; fi
  log_change "shell $*"
  "$ADB" -s "$SERIAL" shell "$@" </dev/null | tr -d '\r'
}
run_adb() {
  if [ "$DRY_RUN" = 1 ]; then say "  [dry-run] adb -s $SERIAL $*" >&2; return 0; fi
  log_change "adb $*"
  "$ADB" -s "$SERIAL" "$@" </dev/null
}

# settings_restore <ns> <key> <recorded value>: "null" means it was unset, so delete it.
settings_restore() {
  if [ "$3" = "null" ] || [ -z "$3" ]; then run_tv settings delete "$1" "$2" >/dev/null; else run_tv settings put "$1" "$2" "$3"; fi
}

# ---------- protect list (hard-coded; a list file can never override it) ----------
# Stock launchers and setup (Google TV runs the Settings-button panel and Wi-Fi ownership through them),
# Play services / Play Store, keyboards, Live TV / tuner / HDMI inputs, OTA / updaters, network / Wi-Fi,
# System UI and settings, and the chip vendors' (Realtek / MediaTek / MStar) system packages.
PROTECT_EXACT='^(android|com\.android\.(systemui|settings|tv\.settings|shell|phone|bluetooth|vending|externalstorage|packageinstaller|permissioncontroller|inputdevices|keychain|certinstaller|tv)|com\.google\.android\.(gms|gsf|tvlauncher|leanback\.launcher|apps\.tv\.launcherx|tungsten\.setupwraith|tvrecommendations|leanback\.ime|tv|packageinstaller|permissioncontroller|configupdater|partnersetup|onetimeinitializer|ext\.services|ext\.shared|tts|networkstack)|io\.github\.lars21\.cozyhometv)$'
PROTECT_PREFIX='^(com\.android\.(providers|server|networkstack|wifi|tethering|connectivity|inputmethod|location|ext|tv)\.|com\.google\.android\.(gms|gsf|apps\.tv\.launcherx|tv|inputmethod|networkstack|ext|tungsten)\.|com\.google\.android\.apps\.tv\.(launcherx|setup|overlay)|android\.)'
PROTECT_WORDS='(launcher|setupwraith|setupwizard|\.setup|setup\.|keyboard|inputmethod|\.ime(\.|$)|latin|tuner|tvinput|tv\.input|hdmi|livetv|tvcenter|wwtv|(^|\.)f?ota(\.|$)|otaupdate|update|upgrade|systemui|wifi|network|connectivity|bluetooth|remote|gms|systemserver)'
PROTECT_VENDOR='^(com\.)?(realtek|rtk|mediatek|mtk|mstar|amlogic|droidlogic)\.'
# Vendor apps (not system services) that were disabled on a real TV without side effects; these only skip
# the vendor-prefix rule, never the other rules. com.realtek.airplay2: the AirPlay receiver, disabled on a
# Panasonic Google TV (Realtek) on 26 Oct 2026, nothing else affected.
VENDOR_APP_EXCEPTIONS="com.realtek.airplay2"

is_protected() {
  local p; p="$(lower "$1")"
  printf '%s' "$p" | grep -qE "$PROTECT_EXACT" && return 0
  printf '%s' "$p" | grep -qE "$PROTECT_PREFIX" && return 0
  printf '%s' "$p" | grep -qE "$PROTECT_WORDS" && return 0
  case " $VENDOR_APP_EXCEPTIONS " in *" $p "*) return 1 ;; esac
  printf '%s' "$p" | grep -qE "$PROTECT_VENDOR" && return 0
  return 1
}

# ---------- debloat lists ----------
# Line format:  <tier> <package> [bg]   # reason
#   tier: safe | more | ask   (a line with only a package is treated as "safe", for old lists)
#   bg:   don't disable; only stop the app running in the background (cmd appops RUN_ANY_IN_BACKGROUND ignore)
# Header lines used to pick a list for a TV:
#   # match-brand: <regex>     against the lower-case brand / manufacturer
#   # match-chip: <regex>      against the lower-case board platform / hardware / SoC (optional)
#   # match-package: <pkg>     a package that must be installed (optional)
# list_entries <file>: prints "tier<TAB>package<TAB>action<TAB>reason"
list_entries() {
  awk '
    /^[ \t]*(#|$)/ { next }
    {
      line=$0; reason=""
      i=index(line, "#"); if (i>0) { reason=substr(line, i+1); line=substr(line, 1, i-1) }
      gsub(/^[ \t]+|[ \t]+$/, "", reason)
      n=split(line, f, /[ \t]+/); k=1; while (k<=n && f[k]=="") k++
      tier=f[k]; pkg=f[k+1]; act=f[k+2]
      if (tier!="safe" && tier!="more" && tier!="ask") { act=pkg; pkg=tier; tier="safe" }
      if (act=="") act="disable"
      if (pkg!="") printf "%s\t%s\t%s\t%s\n", tier, pkg, act, reason
    }' "$1"
}
list_header() { sed -n "s/^#[[:space:]]*$2:[[:space:]]*//p" "$1" | head -1; }

# ---------- per-TV state: backup records, change logs, generated restore scripts ----------
# $STATE_ROOT/tvs/<device-serial>/
#   record-first.txt        the first backup record ever taken for this TV (never overwritten)
#   record-<time>.txt       one per run, taken before that run changes anything
#   changes-<time>.log      every command that changed the TV, in order
#   restore-<device>.sh     plain adb commands that undo everything (works without this repo)
tv_id() { local s; s="$(sh_tv getprop ro.serialno)"; [ -n "$s" ] || s="$SERIAL"; printf '%s' "$s" | tr -c 'A-Za-z0-9._-' '_'; }
tv_dir() { printf '%s/tvs/%s' "$STATE_ROOT" "$(tv_id)"; }

RECORD_SETTINGS="secure:enabled_accessibility_services secure:accessibility_enabled secure:screensaver_components secure:screensaver_enabled secure:screensaver_activate_on_sleep secure:screensaver_activate_on_dock secure:screensaver_default_component global:window_animation_scale global:transition_animation_scale global:animator_duration_scale"

home_role_holders() { [ "$(sdk_int)" -ge 29 ] 2>/dev/null && sh_tv cmd role get-role-holders android.app.role.HOME | tr -d ' ' | tr '\n' ',' | sed 's/,*$//'; return 0; }
# The app that opens on Home. `cmd shortcut get-default-launcher` follows the Home role (API 29+);
# `resolve-activity` does not always, so it is only the fallback.
home_resolved() {
  local l; l="$(sh_tv cmd shortcut get-default-launcher 2>/dev/null | sed -n 's/.*ComponentInfo{\(.*\)}.*/\1/p' | head -1)"
  if [ -n "$l" ]; then printf '%s' "$l"; else sh_tv cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1; fi
}
app_version() { sh_tv dumpsys package "$1" | sed -n 's/^ *versionName=//p' | head -1; }
resumed_activity() { sh_tv dumpsys activity activities | grep -E 'topResumedActivity|mResumedActivity|ResumedActivity:' | head -1; }

# write_record <file> [bg packages...]: snapshot everything setup.sh may change.
write_record() {
  local f="$1" kv ns key p v; shift
  {
    printf '# Cozy Home TV backup record, format 1. Taken before any change. Fields are TAB separated.\n'
    printf 'meta\twritten\t%s\n' "$(date '+%Y-%m-%d %H:%M:%S %z')"
    printf 'meta\tadb_serial\t%s\n' "$SERIAL"
    printf 'meta\tdevice_id\t%s\n' "$(tv_id)"
    for p in ro.product.brand ro.product.manufacturer ro.product.model ro.product.device ro.board.platform ro.hardware ro.soc.manufacturer ro.soc.model ro.build.version.release ro.build.version.sdk ro.build.fingerprint; do
      printf 'prop\t%s\t%s\n' "$p" "$(sh_tv getprop "$p")"
    done
    for kv in $RECORD_SETTINGS; do ns="${kv%%:*}"; key="${kv#*:}"; printf 'setting\t%s\t%s\t%s\n' "$ns" "$key" "$(setting_get "$ns" "$key")"; done
    printf 'home\trole\t%s\n' "$(home_role_holders)"
    printf 'home\tresolved\t%s\n' "$(home_resolved)"
    v="$(app_version "$PKG")"; printf 'app\tinstalled\t%s\n' "${v:-none}"
    for p in "$@"; do printf 'appop\t%s\tRUN_ANY_IN_BACKGROUND\t%s\n' "$p" "$(sh_tv cmd appops get "$p" RUN_ANY_IN_BACKGROUND | sed -n 's/.*RUN_ANY_IN_BACKGROUND: \([a-z]*\).*/\1/p' | head -1)"; done
    sh_tv pm list packages -e | sed -n 's/^package:\(.*\)/pkg\t\1\tenabled/p'
    sh_tv pm list packages -d | sed -n 's/^package:\(.*\)/pkg\t\1\tdisabled/p'
  } > "$f"
}
# rec <file> <kind> <key> [<key2>]: the recorded value
rec() {
  case "$2" in
    setting) awk -F '\t' -v a="$3" -v b="$4" '$1=="setting" && $2==a && $3==b {print $4; exit}' "$1" ;;
    appop)   awk -F '\t' -v a="$3" '$1=="appop" && $2==a {print $4; exit}' "$1" ;;
    *)       awk -F '\t' -v k="$2" -v a="$3" '$1==k && $2==a {print $3; exit}' "$1" ;;
  esac
}
# changed_packages <dir>: packages any run disabled / background-restricted (from the change logs)
changed_packages() { cat "$1"/changes-*.log 2>/dev/null | sed -n 's/.*shell pm disable-user --user 0 \([^ ]*\).*/\1/p' | sort -u; }
changed_appops() { cat "$1"/changes-*.log 2>/dev/null | sed -n 's/.*shell cmd appops set \([^ ]*\) RUN_ANY_IN_BACKGROUND ignore.*/\1/p' | sort -u; }

# shellcheck disable=SC2016,SC2094  # the $S etc. are meant for the generated script
# gen_restore_script <record> <out> <packages to re-enable (space separated)> <appop packages>
gen_restore_script() {
  local r="$1" out="$2" pkgs="$3" ops="$4" p v kv ns key role resolved h
  {
    printf '#!/bin/sh\n'
    printf '# Undo Cozy Home TV setup on %s %s (%s), using the backup record taken %s.\n' \
      "$(rec "$r" prop ro.product.manufacturer)" "$(rec "$r" prop ro.product.model)" "$(rec "$r" meta device_id)" "$(rec "$r" meta written)"
    printf '# Plain adb, no repo needed:  sh %s [adb-serial]\n' "$(basename "$out")"
    printf 'S="${1:-%s}"\n' "$(rec "$r" meta adb_serial)"
    printf 'a() { echo "+ adb -s $S shell $*"; adb -s "$S" shell "$@" </dev/null; }\n'
    printf 'case "$S" in *:*) adb connect "$S" >/dev/null 2>&1 ;; esac\n'
    printf '# Apps that were turned off (they come back exactly as before):\n'
    for p in $pkgs; do [ "$(rec "$r" pkg "$p")" = "enabled" ] && printf 'a pm enable %s\n' "$p"; done
    for p in $ops; do v="$(rec "$r" appop "$p")"; printf 'a cmd appops set %s RUN_ANY_IN_BACKGROUND %s\n' "$p" "${v:-allow}"; done
    printf '# Settings as they were:\n'
    for kv in secure:enabled_accessibility_services secure:accessibility_enabled secure:screensaver_components secure:screensaver_enabled; do
      ns="${kv%%:*}"; key="${kv#*:}"; v="$(rec "$r" setting "$ns" "$key")"
      if [ "$v" = "null" ] || [ -z "$v" ]; then printf 'a settings delete %s %s\n' "$ns" "$key"; else printf "a settings put %s %s '%s'\n" "$ns" "$key" "$v"; fi
    done
    printf '# Home app as it was:\n'
    role="$(rec "$r" home role)"; resolved="$(rec "$r" home resolved)"
    if [ "$(rec "$r" prop ro.build.version.sdk)" -ge 29 ] 2>/dev/null; then
      printf 'a cmd role remove-role-holder android.app.role.HOME %s\n' "$PKG"
      for h in $(printf '%s' "$role" | tr ',' ' '); do [ "$h" != "$PKG" ] && printf 'a cmd role add-role-holder android.app.role.HOME %s\n' "$h"; done
    fi
    case "$resolved" in */*) case "$resolved" in "$PKG"/*) ;; *) printf 'a cmd package set-home-activity %s\n' "$resolved" ;; esac ;; esac
    printf 'echo "Done. Press Home on the remote to check."\n'
    if [ "$(rec "$r" app installed)" = "none" ]; then printf '# Cozy Home TV was not installed before. To remove it too:  adb -s "$S" uninstall %s\n' "$PKG"; fi
  } > "$out"
  chmod +x "$out"
}

sha256_of() { if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1" | awk '{print $1}'; else shasum -a 256 "$1" | awk '{print $1}'; fi; }
