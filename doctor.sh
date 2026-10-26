#!/usr/bin/env bash
# Read-only health check for Cozy Home TV. It never changes the TV; it only reads, then tells you what to run.
#   ./doctor.sh --serial <serial> | --ip <addr>      (or ./doctor.sh with TV=... set)
# Useful after a TV update or reboot: is Cozy Home TV still Home, is the Home-button service still on,
# did an update turn apps back on, is debugging still open?
set -euo pipefail
cd "$(dirname "$0")"
# shellcheck source=scripts/lib.sh
. scripts/lib.sh
ADDR=""
while [ $# -gt 0 ]; do
  case "$1" in --ip|--serial) ADDR="${2:?}"; shift ;; -h|--help) sed -n '2,5p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;; -*) die "unknown option: $1" ;; *) ADDR="$1" ;; esac
  shift
done
WARN=0
good() { printf '%s  ok%s    %-16s %s\n' "$G" "$N" "$1" "$2"; }
bad()  { printf '%s  check%s %-16s %s\n' "$Y" "$N" "$1" "$2"; WARN=$((WARN+1)); }
info() { printf '        %-16s %s\n' "$1" "$2"; }

need_adb; good adb "$("$ADB" version | head -1)"
connect_tv "$ADDR"
SDK="$(sdk_int)"; TVDIR="$(tv_dir)"
info TV "$(sh_tv getprop ro.product.manufacturer) $(sh_tv getprop ro.product.model) ($(sh_tv getprop ro.product.device)), Android $(sh_tv getprop ro.build.version.release) (API $SDK)"
info chip "$(sh_tv getprop ro.board.platform) $(sh_tv getprop ro.hardware) $(sh_tv getprop ro.soc.manufacturer)"
info build "$(sh_tv getprop ro.build.fingerprint)"

V="$(app_version "$PKG")"
if [ -n "$V" ]; then good app "Cozy Home TV $V installed"; else bad app "Cozy Home TV is not installed: ./setup.sh"; fi
H="$(home_resolved)"
case "$H" in "$PKG"/*) good home "$H" ;; *) bad home "Home opens $H, not Cozy Home TV: ./scripts/set-home.sh $SERIAL --home-fix" ;; esac
[ "$SDK" -ge 29 ] && info "home role" "$(home_role_holders)"
A="$(setting_get secure enabled_accessibility_services)"
case "$A" in *"$A11Y_COMPONENT"*)
  if sh_tv dumpsys accessibility | grep -q HomeButtonService; then good "home button" "service on and bound"
  else bad "home button" "service listed but not bound (some TVs drop it after a reboot): ./scripts/set-home.sh $SERIAL --home-fix"; fi ;;
  *) bad "home button" "service off (the stock home may flash first): ./scripts/set-home.sh $SERIAL --home-fix" ;; esac
S="$(setting_get secure screensaver_components)"
case "$S" in *"$PKG"*) good screensaver "$S" ;; *) info screensaver "$S (not Cozy Home TV; ./scripts/set-home.sh $SERIAL --screensaver to change)" ;; esac
for p in com.google.android.apps.tv.launcherx com.google.android.tvlauncher com.google.android.tungsten.setupwraith; do
  if sh_tv pm list packages -d | grep -qx "package:$p"; then bad "stock app off" "$p is disabled; Cozy Home TV never needs that (pm enable $p)"; fi
done
if [ -f "$TVDIR/record-first.txt" ]; then
  good record "$TVDIR/record-first.txt ($(rec "$TVDIR/record-first.txt" meta written))"
  OFF="$(sh_tv pm list packages -d | sed 's/^package://')"; back=""
  for p in $(changed_packages "$TVDIR"); do printf '%s\n' "$OFF" | grep -qx "$p" || back="$back $p"; done
  if [ -n "$back" ]; then info "turned back on" "${back# } (an update or you turned them on again; ./setup.sh can turn them off again)"; fi
  [ -f "$TVDIR/restore-$(tv_id).sh" ] && info "plain undo" "$TVDIR/restore-$(tv_id).sh"
else info record "none on this computer ($STATE_ROOT/tvs/$(tv_id)); ./setup.sh writes one before any change"; fi
M="$(sh_tv dumpsys meminfo "$PKG" | awk '/TOTAL PSS:/ {printf "%.1f MB", $3/1024}')"; [ -n "$M" ] && info memory "Cozy Home TV uses $M"
info "TV memory" "$(sh_tv cat /proc/meminfo | awk '/MemTotal/ {t=$2} /MemAvailable/ {a=$2} END {printf "%.0f MB free of %.0f MB", a/1024, t/1024}')"
info "now showing" "$(resumed_activity | sed 's/^ *//')"
P="$(sh_tv getprop service.adb.tcp.port)"; W="$(setting_get global adb_wifi_enabled)"
NET=0; is_network_serial "$SERIAL" && NET=1; case "${P:-}" in ""|0|-1) ;; *) NET=1 ;; esac; [ "$W" = 1 ] && NET=1
if [ "$NET" = 1 ]; then
  bad debugging "network debugging is on (port ${P:-?}, wireless ${W}). Turn it off when you're done: Settings > System > Developer options"
else good debugging "network debugging is off"; fi
echo
if [ "$WARN" = 0 ]; then say "${G}All good.${N}"; else say "${Y}$WARN thing(s) to check above.${N} doctor.sh changed nothing."; fi
