#!/usr/bin/env bash
# Advanced: turn off apps from a list by hand. ./setup.sh does the same with tiers; use this to try a list.
# Nothing is uninstalled. A backup record is written first, so ./restore.sh undoes it.
#
#   scripts/debloat.sh <TV-IP|serial> <list.txt> [safe|more|ask]        # dry run: what would be turned off
#   scripts/debloat.sh <TV-IP|serial> <list.txt> [safe|more|ask] --apply
#   scripts/debloat.sh <TV-IP|serial> --undo <changes-or-debloat.log>    # turn those back on
#
# Tiers: safe = only "safe" lines (default), more = safe + more, ask = every line including "ask" ones.
# The protect list in scripts/lib.sh is hard-coded and refuses launchers, setup, Play services, keyboards,
# Live TV / tuner / HDMI inputs, OTA, network, System UI and chip-vendor system packages, whatever the list says.
set -euo pipefail
cd "$(dirname "$0")/.."
# shellcheck source=scripts/lib.sh
. scripts/lib.sh
ADDR="${1:-}"; shift || true

if [ "${1:-}" = "--undo" ]; then
  LOG="${2:-}"; [ -f "$LOG" ] || die "log not found: $LOG"
  connect_tv "$ADDR"
  { sed -n 's/.*pm disable-user --user 0 \([^ ]*\).*/\1/p' "$LOG"; sed -n 's/^disabled \([^ ]*\).*/\1/p' "$LOG"; } | sort -u | while read -r p; do
    echo "enable $p"; "$ADB" -s "$SERIAL" shell pm enable "$p" </dev/null >/dev/null || echo "  (failed: $p)"
  done
  echo "Done."; exit 0
fi

LIST="${1:-}"; shift || true
[ -f "$LIST" ] || die "give a list file, e.g. examples/debloat/google-tv-common.txt"
TIER=safe; APPLY=0
for a in "$@"; do case "$a" in safe|more|ask) TIER="$a" ;; --apply) APPLY=1 ;; *) die "unknown option $a" ;; esac; done
connect_tv "$ADDR"
ENABLED="$(sh_tv pm list packages -e | sed 's/^package://')"
if [ "$APPLY" = 1 ]; then
  TVDIR="$(tv_dir)"; TS="$(date +%Y%m%d-%H%M%S)"; mkdir -p "$TVDIR"
  write_record "$TVDIR/record-$TS.txt"
  [ -f "$TVDIR/record-first.txt" ] || cp "$TVDIR/record-$TS.txt" "$TVDIR/record-first.txt"
  CHANGES_FILE="$TVDIR/changes-$TS.log"; : > "$CHANGES_FILE"
  echo "Backup record: $TVDIR/record-$TS.txt"
fi
TAB="$(printf '\t')"
while IFS="$TAB" read -r t p act why <&3; do
  case "$TIER:$t" in safe:more|safe:ask|more:ask) continue ;; esac
  if is_protected "$p"; then echo "SKIP (protected)  $p"; continue; fi
  if ! printf '%s\n' "$ENABLED" | grep -qx "$p"; then echo "skip (not on/off already)  $p"; continue; fi
  if [ "$APPLY" = 1 ]; then
    if [ "$act" = bg ]; then run_tv cmd appops set "$p" RUN_ANY_IN_BACKGROUND ignore; echo "no background  $p"
    elif run_tv pm disable-user --user 0 "$p" | grep -q disabled; then echo "disabled $p"; else echo "FAILED   $p"; fi
  else
    echo "would $([ "$act" = bg ] && echo 'stop background for' || echo 'disable')  $p   # $why"
  fi
done 3<<LST
$(list_entries "$LIST")
LST
if [ "$APPLY" = 1 ]; then echo; echo "Undo with: ./restore.sh --serial $SERIAL   (or scripts/debloat.sh $SERIAL --undo $CHANGES_FILE)"
else echo; echo "Dry run only. Add --apply to do it."; fi
