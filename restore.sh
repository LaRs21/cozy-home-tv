#!/usr/bin/env bash
# Undo ./setup.sh, from the backup record it took before changing anything.
#
#   ./restore.sh --serial <serial> | --ip <addr>   [options]
#     --record <file>   use this record (default: the TV's first record, record-first.txt)
#     --all             also put back any other app that was on in the record and is off now, and the animation scales
#     --uninstall       also uninstall Cozy Home TV (only if it wasn't installed before setup, unless --force)
#     --remove-pack     also delete /sdcard/CozyHome (your Cozy pack copy on the TV)
#     --dry-run         print the commands, change nothing
#     --yes             don't ask
#
# What it puts back: every app setup.sh turned off (only those, unless --all), background limits it set,
# the accessibility services, the screensaver and the Home app as recorded.
set -euo pipefail
cd "$(dirname "$0")"
# shellcheck source=scripts/lib.sh
. scripts/lib.sh
ADDR=""; RECORD=""; ALL=0; UNINSTALL=0; FORCE=0; RMPACK=0; YES=0
while [ $# -gt 0 ]; do
  case "$1" in
    --ip|--serial) ADDR="${2:?}"; shift ;;
    --record) RECORD="${2:?}"; shift ;;
    --all) ALL=1 ;; --uninstall) UNINSTALL=1 ;; --force) FORCE=1 ;; --remove-pack) RMPACK=1 ;;
    --dry-run|-n) DRY_RUN=1 ;; --yes|-y) YES=1 ;;
    -h|--help) sed -n '2,15p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    -*) die "unknown option: $1" ;;
    *) ADDR="$1" ;;
  esac
  shift
done
connect_tv "$ADDR"
TVDIR="$(tv_dir)"
[ -n "$RECORD" ] || RECORD="$TVDIR/record-first.txt"
[ -f "$RECORD" ] || die "no backup record for this TV ($RECORD). Was ./setup.sh run from this computer? Records live in $STATE_ROOT/tvs/."
[ "$(rec "$RECORD" meta device_id)" = "$(tv_id)" ] || die "$RECORD is for another TV ($(rec "$RECORD" meta device_id))"
say "${B}Restoring $(sh_tv getprop ro.product.model) ($SERIAL) from the record taken $(rec "$RECORD" meta written)${N}"
[ "$DRY_RUN" = 1 ] && say "${Y}Dry run: nothing will change.${N}"
if [ "$DRY_RUN" = 0 ] && [ "$YES" = 0 ]; then printf 'Go ahead? [Y/n] '; read -r a || a=""; case "$(lower "${a:-y}")" in y|yes) ;; *) say "Nothing changed."; exit 0 ;; esac; fi
mkdir -p "$TVDIR"; [ "$DRY_RUN" = 1 ] || CHANGES_FILE="$TVDIR/restore-$(date +%Y%m%d-%H%M%S).log"
SDK="$(sdk_int)"

step "Apps"
NOW_OFF="$(sh_tv pm list packages -d | sed 's/^package://')"
if [ "$ALL" = 1 ]; then CANDIDATES="$(awk -F '\t' '$1=="pkg" && $3=="enabled" {print $2}' "$RECORD")"; else CANDIDATES="$(changed_packages "$TVDIR")"; fi
n=0
for p in $CANDIDATES; do
  [ "$(rec "$RECORD" pkg "$p")" = enabled ] || continue          # it was already off before setup: leave it off
  printf '%s\n' "$NOW_OFF" | grep -qx "$p" || continue
  run_tv pm enable "$p" >/dev/null; ok "turned back on: $p"; n=$((n+1))
done
for p in $(changed_appops "$TVDIR"); do
  v="$(rec "$RECORD" appop "$p")"; run_tv cmd appops set "$p" RUN_ANY_IN_BACKGROUND "${v:-allow}"; ok "background running for $p: ${v:-allow}"; n=$((n+1))
done
[ "$n" -gt 0 ] || say "  nothing to turn back on"

step "Home button and accessibility"
REC_A11Y="$(rec "$RECORD" setting secure enabled_accessibility_services)"; CUR_A11Y="$(setting_get secure enabled_accessibility_services)"
case "$REC_A11Y" in *"$A11Y_COMPONENT"*) say "  the Home-button service was already on before setup: left on" ;; *)
  NEW="$(printf '%s' "$CUR_A11Y" | tr ':' '\n' | grep -vx "$A11Y_COMPONENT" | grep -vx null | paste -sd ':' - || true)"
  if [ -z "$NEW" ]; then settings_restore secure enabled_accessibility_services "$REC_A11Y"; settings_restore secure accessibility_enabled "$(rec "$RECORD" setting secure accessibility_enabled)"
  elif [ "$NEW" != "$CUR_A11Y" ]; then run_tv settings put secure enabled_accessibility_services "$NEW"; fi
  ok "accessibility services: ${NEW:-$REC_A11Y}" ;;
esac

step "Screensaver"
if setting_get secure screensaver_components | grep -q "$PKG" && ! rec "$RECORD" setting secure screensaver_components | grep -q "$PKG"; then
  settings_restore secure screensaver_components "$(rec "$RECORD" setting secure screensaver_components)"
  settings_restore secure screensaver_enabled "$(rec "$RECORD" setting secure screensaver_enabled)"
  ok "screensaver: $(rec "$RECORD" setting secure screensaver_components)"
else say "  unchanged"; fi
if [ "$ALL" = 1 ]; then for k in window_animation_scale transition_animation_scale animator_duration_scale; do
  v="$(rec "$RECORD" setting global "$k")"; [ "$(setting_get global "$k")" = "$v" ] || { settings_restore global "$k" "$v"; ok "$k: $v"; }
done; fi

step "Home app"
REC_HOME="$(rec "$RECORD" home resolved)"; REC_ROLE="$(rec "$RECORD" home role)"
case "$REC_HOME" in "$PKG"/*) say "  Cozy Home TV was already the Home app before setup: left as is" ;; *)
  if [ "$SDK" -ge 29 ]; then
    run_tv cmd role remove-role-holder android.app.role.HOME "$PKG" >/dev/null || true
    for h in $(printf '%s' "$REC_ROLE" | tr ',' ' '); do run_tv cmd role add-role-holder android.app.role.HOME "$h" >/dev/null || true; done
  fi
  case "$REC_HOME" in */*) run_tv cmd package set-home-activity "$REC_HOME" >/dev/null || true ;; esac
  ok "Home app: ${REC_HOME:-the TV launcher}" ;;
esac

if [ "$RMPACK" = 1 ]; then step "Cozy pack"; run_tv rm -rf /sdcard/CozyHome; ok "removed /sdcard/CozyHome"; fi
if [ "$UNINSTALL" = 1 ]; then
  step "Uninstall"
  if [ "$(rec "$RECORD" app installed)" != none ] && [ "$FORCE" = 0 ]; then warn "Cozy Home TV was installed before setup ($(rec "$RECORD" app installed)); add --force to uninstall it anyway"
  else run_adb uninstall "$PKG" >/dev/null; ok "uninstalled"; fi
fi

if [ "$DRY_RUN" = 0 ]; then
  step "Check"
  say "  Home app now:  $(home_resolved)   (recorded: $REC_HOME)"
  say "  Screensaver:   $(setting_get secure screensaver_components)   Accessibility: $(setting_get secure enabled_accessibility_services)"
  bad=0
  for p in $CANDIDATES; do [ "$(rec "$RECORD" pkg "$p")" = enabled ] && sh_tv pm list packages -d | grep -qx "package:$p" && { warn "still off: $p"; bad=1; }; done
  NOWH="$(home_resolved)"
  [ "${NOWH%%/*}" = "${REC_HOME%%/*}" ] || case "$REC_HOME" in */*) warn "Home is $NOWH, the record says $REC_HOME"; bad=1 ;; esac
  [ "$bad" = 0 ] && ok "everything matches the record"
  say "Press Home on the remote to check. Log of this restore: $CHANGES_FILE"
fi
