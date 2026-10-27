#!/usr/bin/env bash
# Cozy Home TV: one-step setup for Android TV / Google TV, from a Mac or Linux computer on the same Wi-Fi.
#
#   ./setup.sh                      interactive, safe defaults (nothing is turned off unless you ask)
#   ./setup.sh --dry-run            shows every command it would run on the TV, changes nothing
#   ./setup.sh --yes --ip 192.168.1.50 --theme sky_kingdom
#   ./setup.sh --yes --serial emulator-5554 --theme world_tree --debloat safe
#
# Options
#   --ip <addr>           the TV's IP address (port 5555 is added), or IP:port
#   --serial <serial>     any device `adb devices` lists (USB, emulator, IP:port)
#   --pair <ip:port>      Android 11+ wireless debugging: pair first (use with --code <6 digits>)
#   --theme <id>          clean (default), sky_kingdom, moonlit_whale, desert_kingdom, world_tree
#   --debloat <tier>      none (default), safe, more     --list <file>  use this debloat list
#   --pack <folder>       push your own pictures and music (a Cozy pack) and switch to the Cozy look
#   --no-screensaver      leave the screensaver as it is (default: make Cozy Home TV the screensaver)
#   --apk <file>          install this APK instead of dist/ or the GitHub release
#   --reinstall           if a copy signed with another key is installed, remove it first (resets its settings)
#   --no-verify           skip the Home-button test (it opens Settings on the TV and presses Home)
#   --yes                 don't ask; take the defaults and the options above
#   --dry-run             change nothing; print the commands instead
#
# Undo everything: ./restore.sh     Check later: ./doctor.sh
set -euo pipefail
cd "$(dirname "$0")"
# shellcheck source=scripts/lib.sh
. scripts/lib.sh

VERSION="$(sed -n 's/.*android:versionName="\([^"]*\)".*/\1/p' AndroidManifest.xml 2>/dev/null | head -1)"; VERSION="${VERSION:-1.2.0}"
RELEASE_URL="${COZY_RELEASE_URL:-https://github.com/LaRs21/cozy-home-tv/releases/download/v$VERSION}"

ADDR=""; PAIR=""; CODE=""; THEME=""; TIER=""; LIST=""; PACK=""; DREAM=1; APK=""; REINSTALL=0; VERIFY=1; YES=0
while [ $# -gt 0 ]; do
  case "$1" in
    --ip|--serial) ADDR="${2:?}"; shift ;;
    --pair) PAIR="${2:?}"; shift ;;
    --code) CODE="${2:?}"; shift ;;
    --theme) THEME="${2:?}"; shift ;;
    --debloat) TIER="${2:?}"; shift ;;
    --list) LIST="${2:?}"; shift ;;
    --pack) PACK="${2:?}"; shift ;;
    --no-pack) PACK="-" ;;
    --no-screensaver) DREAM=0 ;;
    --apk) APK="${2:?}"; shift ;;
    --reinstall) REINSTALL=1 ;;
    --no-verify) VERIFY=0 ;;
    --yes|-y) YES=1 ;;
    --dry-run|-n) DRY_RUN=1 ;;
    -h|--help) sed -n '2,25p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) die "unknown option: $1 (see ./setup.sh --help)" ;;
  esac
  shift
done
case "${TIER:-none}" in none|safe|more) ;; *) die "--debloat is none, safe or more" ;; esac
TIER_FLAG="${TIER:+1}"
[ -z "$THEME" ] || case " $THEMES " in *" $THEME "*) ;; *) die "unknown theme '$THEME' (one of: $THEMES)" ;; esac
[ -z "$LIST" ] || [ -f "$LIST" ] || die "list not found: $LIST"
[ -z "$PACK" ] || [ "$PACK" = "-" ] || [ -d "$PACK" ] || die "folder not found: $PACK"

# ask <question> <default y|n>: yes -> 0
ask() {
  local a; [ "$YES" = 1 ] && { [ "$2" = y ]; return; }
  if [ "$2" = y ]; then printf '%s [Y/n] ' "$1"; else printf '%s [y/N] ' "$1"; fi
  read -r a || a=""; a="$(lower "${a:-$2}")"; [ "$a" = y ] || [ "$a" = yes ]
}
# prompt <question> <default>: prints the answer
prompt() { local a; if [ "$YES" = 1 ]; then printf '%s' "$2"; return; fi; printf '%s ' "$1" >&2; read -r a || a=""; printf '%s' "${a:-$2}"; }

say "${B}Cozy Home TV $VERSION setup${N}"
[ "$DRY_RUN" = 1 ] && say "${Y}Dry run: nothing on the TV will change. Every command that would change it is printed.${N}"

# ---------------------------------------------------------------- 1. adb
step "1/10  adb (Android platform-tools)"
if ! command -v "$ADB" >/dev/null 2>&1 && [ -x "$STATE_ROOT/platform-tools/adb" ]; then ADB="$STATE_ROOT/platform-tools/adb"; fi
if ! command -v "$ADB" >/dev/null 2>&1; then
  say "adb is not installed. It's Google's free tool for talking to Android devices."
  OS="$(uname -s)"
  if [ "$OS" = Darwin ] && command -v brew >/dev/null 2>&1 && ask "Install it with Homebrew (brew install android-platform-tools)?" y; then
    if [ "$DRY_RUN" = 1 ]; then say "  [dry-run] brew install android-platform-tools"; else brew install android-platform-tools; fi
  elif ask "Download Google's platform-tools into $STATE_ROOT/platform-tools?" y; then
    case "$OS" in Darwin) Z=darwin ;; Linux) Z=linux ;; *) die "unsupported OS $OS: install platform-tools yourself" ;; esac
    URL="https://dl.google.com/android/repository/platform-tools-latest-$Z.zip"
    if [ "$DRY_RUN" = 1 ]; then say "  [dry-run] curl -fL $URL, unzip into $STATE_ROOT"; else
      mkdir -p "$STATE_ROOT"; curl -fL "$URL" -o "$STATE_ROOT/platform-tools.zip"; (cd "$STATE_ROOT" && unzip -qo platform-tools.zip && rm platform-tools.zip)
      ADB="$STATE_ROOT/platform-tools/adb"
    fi
  fi
  if ! command -v "$ADB" >/dev/null 2>&1; then
    [ "$DRY_RUN" = 1 ] && { say "Dry run stops here: adb is needed to read the TV."; exit 0; }
    die "adb is still missing. Install platform-tools and run ./setup.sh again."
  fi
fi
ok "$("$ADB" version | head -1)"

# ---------------------------------------------------------------- 2. find / pair the TV
step "2/10  Connect to the TV"
dev_steps() {
  cat <<'TXT'
  On the TV (one time):
   1. Settings > System > About (Google TV), or Settings > Device Preferences > About (Android TV).
   2. Select "Android TV OS build" (or "Build") and press OK 7 times, until it says you are a developer.
   3. Back to Settings > System (or Device Preferences) > Developer options:
        - turn on "USB debugging" (on many TVs this also allows debugging over the network), and
        - if there is "Network debugging", turn it on too.
      Android 11+ TVs may only have "Wireless debugging": turn it on and choose "Pair device with pairing code".
   4. Find the TV's IP address: Settings > Network & Internet > your Wi-Fi (e.g. 192.168.1.50).
   5. When the TV asks "Allow debugging?", tick "Always allow from this computer" and choose Allow.
TXT
}
if [ -n "$PAIR" ]; then
  [ -n "$CODE" ] || CODE="$(prompt "Pairing code shown on the TV:" "")"
  "$ADB" pair "$PAIR" "$CODE" || die "pairing failed. Check the code and the IP:port on the TV's pairing screen."
  [ -n "$ADDR" ] || ADDR="$(prompt "Now the IP:port under 'Wireless debugging' (not the pairing one):" "")"
fi
if [ -z "$ADDR" ] && [ -n "${TV:-}" ]; then ADDR="$TV"; fi
if [ -z "$ADDR" ]; then
  [ "$YES" = 1 ] && die "with --yes, say which TV: --ip <address> or --serial <serial>"
  LISTED="$("$ADB" devices | awk 'NR>1 && $2=="device" {print $1}')"
  MDNS="$("$ADB" mdns services 2>/dev/null | awk '/_adb-tls-connect|_adb._tcp/ {print $NF}' | sort -u || true)"
  if [ -n "$LISTED$MDNS" ]; then
    say "Devices adb can see:"; i=0
    for d in $LISTED $MDNS; do i=$((i+1)); say "  $i) $d"; done
  else
    say "No TV found yet."; dev_steps
  fi
  say "  p) pair a TV with a pairing code (Android 11+ wireless debugging)"
  say "  h) how to turn on developer mode"
  while [ -z "$ADDR" ]; do
    a="$(prompt "Pick a number, type the TV's IP address, or p / h:" "")"
    case "$a" in
      "") ;;
      h) dev_steps ;;
      p) pp="$(prompt "IP:port on the TV's 'Pair device' screen:" "")"; pc="$(prompt "Pairing code:" "")"
         "$ADB" pair "$pp" "$pc" || warn "pairing failed, try again"
         ADDR="$(prompt "Now the IP:port under 'Wireless debugging':" "")" ;;
      *[!0-9]*) ADDR="$a" ;;
      *) i=0; for d in $LISTED $MDNS; do i=$((i+1)); [ "$i" = "$a" ] && ADDR="$d"; done
         [ -n "$ADDR" ] || warn "no device $a" ;;
    esac
  done
fi
connect_tv "$ADDR"
ok "connected to $SERIAL"

# ---------------------------------------------------------------- 3. identify
step "3/10  Identify the TV"
P_BRAND="$(lower "$(sh_tv getprop ro.product.brand)")"; P_MANU="$(lower "$(sh_tv getprop ro.product.manufacturer)")"
P_MODEL="$(sh_tv getprop ro.product.model)"; P_DEVICE="$(sh_tv getprop ro.product.device)"
P_CHIP="$(lower "$(sh_tv getprop ro.board.platform) $(sh_tv getprop ro.hardware) $(sh_tv getprop ro.soc.manufacturer) $(sh_tv getprop ro.soc.model)")"
SDK="$(sdk_int)"; REL="$(sh_tv getprop ro.build.version.release)"
INSTALLED_PKGS="$(sh_tv pm list packages | sed 's/^package://')"
has_pkg() { printf '%s\n' "$INSTALLED_PKGS" | grep -qx "$1"; }
KIND="Android TV"; has_pkg com.google.android.apps.tv.launcherx && KIND="Google TV"
say "  ${P_MANU:-?} $P_MODEL ($P_DEVICE), $KIND, Android $REL (API $SDK), chip: $(printf '%s' "$P_CHIP" | tr -s ' ')"
case "$P_MANU $P_BRAND" in *amazon*) die "this is a Fire TV. Cozy Home TV is for Android TV / Google TV; Fire TV blocks other home apps." ;; esac
[ "${SDK:-0}" -ge 26 ] || die "Android $REL is too old (Cozy Home TV needs Android 8.0 / API 26 or newer)."
if [ -z "$LIST" ]; then
  for f in examples/debloat/*.txt; do
    [ "$(basename "$f")" = google-tv-common.txt ] && continue
    mb="$(list_header "$f" match-brand)"; mc="$(list_header "$f" match-chip)"; mp="$(list_header "$f" match-package)"
    [ -n "$mb" ] || continue
    printf '%s %s' "$P_BRAND" "$P_MANU" | grep -qE "$mb" || continue
    [ -z "$mc" ] || printf '%s' "$P_CHIP" | grep -qE "$mc" || continue
    [ -z "$mp" ] || has_pkg "$mp" || continue
    LIST="$f"; break
  done
  [ -n "$LIST" ] || LIST="examples/debloat/google-tv-common.txt"
fi
ok "debloat list for this TV: $LIST"

# ---------------------------------------------------------------- 4. backup record
step "4/10  Backup record (before any change)"
TVDIR="$(tv_dir)"; TS="$(date +%Y%m%d-%H%M%S)"
BG_PKGS="$(list_entries "$LIST" | awk -F '\t' '$3=="bg" {print $2}' | tr '\n' ' ')"
if [ "$DRY_RUN" = 1 ]; then
  RECORD="$(mktemp "${TMPDIR:-/tmp}/cozy-record.XXXXXX")"
  # shellcheck disable=SC2086
  write_record "$RECORD" $BG_PKGS
  say "  (dry run: record written to $RECORD only; the real one goes to $TVDIR/)"
  FIRST="$RECORD"; [ -f "$TVDIR/record-first.txt" ] && FIRST="$TVDIR/record-first.txt"
else
  mkdir -p "$TVDIR"; RECORD="$TVDIR/record-$TS.txt"
  # shellcheck disable=SC2086
  write_record "$RECORD" $BG_PKGS
  if [ ! -f "$TVDIR/record-first.txt" ]; then (set -o noclobber; cat "$RECORD" > "$TVDIR/record-first.txt"); ok "first record: $TVDIR/record-first.txt (never overwritten)"
  else ok "earlier first record kept: $TVDIR/record-first.txt"; fi
  FIRST="$TVDIR/record-first.txt"
  CHANGES_FILE="$TVDIR/changes-$TS.log"; : > "$CHANGES_FILE"
fi
ok "this run's record: $RECORD ($(grep -c '^pkg' "$RECORD") packages, Home: $(rec "$RECORD" home resolved))"
RESTORE_SH="$TVDIR/restore-$(tv_id).sh"

# ---------------------------------------------------------------- 5. theme
step "5/10  Theme"
if [ -z "$THEME" ]; then
  if [ "$YES" = 1 ]; then THEME=clean; else
    say "  1) Clean          dark and plain, no pictures; fastest and lightest (default)"
    say "  2) Sky Kingdom    a sky ship and floating islands, your apps on the sail, gate, crystal and tower"
    say "  3) Moonlit Whale  a sky whale by moonlight, apps in the crystal, sail, lighthouse and banner"
    say "  4) Desert Kingdom a golden desert city, apps on the windmill, fountain, minaret and tapestry"
    say "  5) World Tree     a sunlit forest valley, apps in the ring, banner, bell and crystal"
    say "  Pictures: docs/screenshots/theme-*.jpg. You can change it any time in the app: Settings > Theme."
    a="$(prompt "Theme [1]:" 1)"
    case "$a" in 1|clean) THEME=clean ;; 2|sky_kingdom) THEME=sky_kingdom ;; 3|moonlit_whale) THEME=moonlit_whale ;; 4|desert_kingdom) THEME=desert_kingdom ;; 5|world_tree) THEME=world_tree ;; *) warn "not a choice; using Clean"; THEME=clean ;; esac
  fi
fi
ok "theme: $THEME"

# ---------------------------------------------------------------- 6. debloat (off by default)
step "6/10  Turn off apps you don't use (optional, off by default)"
if [ -z "$TIER" ]; then
  if [ "$YES" = 1 ]; then TIER=none; else
    say "  none) leave every app as it is (default)"
    say "  safe) optional apps from the list that are known to be safe to turn off"
    say "  more) safe, plus a few more you probably don't use"
    say "  Apps are only turned off (pm disable-user), never uninstalled, and ./restore.sh turns them back on."
    TIER="$(lower "$(prompt "Tier [none]:" none)")"; case "$TIER" in none|safe|more) ;; *) TIER=none ;; esac
  fi
fi
PLAN=""; PLAN_BG=""
if [ "$TIER" != none ]; then
  ENABLED="$(sh_tv pm list packages -e | sed 's/^package://')"
  TAB="$(printf '\t')"
  while IFS="$TAB" read -r t p act why <&3; do
    [ -n "$p" ] || continue
    if [ "$t" = more ] && [ "$TIER" != more ]; then continue; fi
    if is_protected "$p"; then say "  protected, never touched: $p"; continue; fi
    if ! printf '%s\n' "$ENABLED" | grep -qx "$p"; then say "  not on this TV or already off: $p"; continue; fi
    if [ "$t" = ask ]; then
      if [ "$YES" = 1 ]; then say "  skipped (asks first; run without --yes to choose): $p  ($why)"; continue; fi
      if [ "$act" = bg ]; then q="Stop $p running in the background? ($why)"; else q="Turn off $p? ($why)"; fi
      ask "  $q" n || continue
    fi
    if [ "$act" = bg ]; then PLAN_BG="$PLAN_BG $p"; else PLAN="$PLAN $p"; fi
  done 3<<LST
$(list_entries "$LIST")
LST
  if [ -z "$PLAN$PLAN_BG" ]; then say "  nothing to do for this tier."; else
    say "  Plan:"; for p in $PLAN; do say "    turn off             $p"; done; for p in $PLAN_BG; do say "    no background runs   $p"; done
    if [ "$YES" = 1 ] && [ -n "$TIER_FLAG" ]; then say "  (--yes --debloat $TIER given: going ahead)"
    elif ! ask "  Go ahead?" n; then PLAN=""; PLAN_BG=""; say "  OK, nothing turned off."; fi
  fi
fi
# The plain-adb restore script is written now, before the first change.
ALL_PKGS="$(printf '%s %s' "$PLAN" "$(changed_packages "$TVDIR" | tr '\n' ' ')" | tr ' ' '\n' | sort -u | tr '\n' ' ')"
ALL_OPS="$(printf '%s %s' "$PLAN_BG" "$(changed_appops "$TVDIR" | tr '\n' ' ')" | tr ' ' '\n' | sort -u | tr '\n' ' ')"
if [ "$DRY_RUN" = 1 ]; then
  gen_restore_script "$FIRST" "${RECORD}.restore.sh" "$ALL_PKGS" "$ALL_OPS"; say "  (dry run) plain-adb undo script preview: ${RECORD}.restore.sh"
else
  gen_restore_script "$FIRST" "$RESTORE_SH" "$ALL_PKGS" "$ALL_OPS"; ok "plain-adb undo script: $RESTORE_SH"
fi
for p in $PLAN; do
  out="$(run_tv pm disable-user --user 0 "$p")"
  if [ "$DRY_RUN" = 1 ]; then continue; elif printf '%s' "$out" | grep -q disabled; then ok "turned off $p"; else warn "could not turn off $p: $out"; fi
done
for p in $PLAN_BG; do run_tv cmd appops set "$p" RUN_ANY_IN_BACKGROUND ignore; run_tv am force-stop "$p"; ok "$p no longer runs in the background"; done

# ---------------------------------------------------------------- 7. Cozy pack
step "7/10  Your own pictures and music (optional)"
if [ -z "$PACK" ] && [ "$YES" = 0 ]; then
  say "  A Cozy pack is a folder with 1 picture, or morning/day/evening/night pictures, and optional music."
  say "  See docs/cozy-pack.md, or try examples/sample-cozy-pack. Press Enter to skip."
  PACK="$(prompt "  Folder:" "-")"; [ "$PACK" = "-" ] || [ -d "$PACK" ] || { warn "no such folder, skipping"; PACK="-"; }
fi
[ -n "$PACK" ] || PACK="-"
[ "$PACK" = "-" ] && say "  skipped"

# ---------------------------------------------------------------- 8. install
step "8/10  Install Cozy Home TV $VERSION"
SUM=""
if [ -z "$APK" ] && [ -f "dist/cozy-home-tv-$VERSION.apk" ]; then APK="dist/cozy-home-tv-$VERSION.apk"; fi
if [ -z "$APK" ]; then
  DL="$STATE_ROOT/downloads"; APK="$DL/cozy-home-tv-$VERSION.apk"
  if [ "$DRY_RUN" = 1 ]; then say "  [dry-run] download $RELEASE_URL/cozy-home-tv-$VERSION.apk (+ .sha256)"; else
    mkdir -p "$DL"; say "  downloading the signed release from GitHub ..."
    curl -fL "$RELEASE_URL/cozy-home-tv-$VERSION.apk" -o "$APK" || die "download failed. Build it with ./build.sh or pass --apk <file>."
    curl -fL "$RELEASE_URL/cozy-home-tv-$VERSION.apk.sha256" -o "$APK.sha256" || die "checksum download failed"
  fi
fi
if [ "$DRY_RUN" = 0 ] || [ -f "$APK" ]; then
  [ -f "$APK" ] || die "APK not found: $APK"
  if [ -f "$APK.sha256" ]; then
    SUM="$(awk '{print $1; exit}' "$APK.sha256")"; GOT="$(sha256_of "$APK")"
    [ "$SUM" = "$GOT" ] || die "checksum mismatch for $APK (expected $SUM, got $GOT). Don't install it."
    ok "checksum ok ($GOT)"
  else
    case "$APK" in *debug*) warn "debug build without a checksum: fine for testing, not for a real TV" ;; *) die "no checksum file $APK.sha256 next to the APK" ;; esac
  fi
fi
BEFORE_VER="$(app_version "$PKG")"
out="$(run_adb install -r "$APK" 2>&1 || true)"
if printf '%s' "$out" | grep -q INSTALL_FAILED_UPDATE_INCOMPATIBLE; then
  say "  A copy of Cozy Home TV signed with a different key is installed (e.g. a build of your own)."
  if [ "$REINSTALL" = 1 ] || { [ "$YES" = 0 ] && ask "  Remove it and install the release? Its settings (favourites, hidden apps) are reset." n; }; then
    run_adb uninstall "$PKG" >/dev/null; out="$(run_adb install "$APK" 2>&1 || true)"
  else die "left the installed copy alone. Run again with --reinstall, or pass --apk with an APK signed like the installed one."; fi
fi
if [ "$DRY_RUN" = 1 ]; then printf '%s\n' "$out"; else
  printf '%s' "$out" | grep -q Success || die "install failed: $out"
  ok "installed $(app_version "$PKG") (was: ${BEFORE_VER:-not installed})"
fi

# ---------------------------------------------------------------- 9. Home
step "9/10  Make it the home screen (Google's launcher stays on)"
OLD_A11Y="$(setting_get secure enabled_accessibility_services)"; OLD_A11Y_ON="$(setting_get secure accessibility_enabled)"
OLD_ROLE="$(home_role_holders)"; OLD_HOME="$(home_resolved)"
set_home() {
  run_tv cmd package set-home-activity "$HOME_COMPONENT" >/dev/null || true
  if [ "$SDK" -ge 29 ]; then run_tv cmd role add-role-holder android.app.role.HOME "$PKG" >/dev/null || warn "could not give Cozy Home TV the Home role"; fi
  local new
  case "$OLD_A11Y" in ""|null) new="$A11Y_COMPONENT" ;; *"$A11Y_COMPONENT"*) new="$OLD_A11Y" ;; *) new="$OLD_A11Y:$A11Y_COMPONENT" ;; esac
  run_tv settings put secure enabled_accessibility_services "$new"
  run_tv settings put secure accessibility_enabled 1
}
rollback_home() {
  warn "rolling back the Home change"
  settings_restore secure enabled_accessibility_services "$OLD_A11Y"; settings_restore secure accessibility_enabled "$OLD_A11Y_ON"
  if [ "$SDK" -ge 29 ]; then
    run_tv cmd role remove-role-holder android.app.role.HOME "$PKG" >/dev/null 2>&1 || true
    for h in $(printf '%s' "$OLD_ROLE" | tr ',' ' '); do [ "$h" = "$PKG" ] || run_tv cmd role add-role-holder android.app.role.HOME "$h" >/dev/null 2>&1 || true; done
  fi
  case "$OLD_HOME" in "$PKG"/*) ;; */*) run_tv cmd package set-home-activity "$OLD_HOME" >/dev/null || true ;; esac
}
set_home
ok "Home app and Home-button service set (previous Home: ${OLD_HOME:-?})"
if [ "$DRY_RUN" = 1 ] || [ "$VERIFY" = 0 ]; then say "  Home test skipped"
elif [ "$YES" = 1 ] || ask "  Test it now? This opens Settings on the TV and presses Home (anything playing stops)." y; then
  sleep 2; passed=0
  for try in 1 2; do
    run_tv am start -a android.settings.SETTINGS >/dev/null; sleep 3
    run_tv input keyevent KEYCODE_HOME; sleep 3
    top="$(resumed_activity)"
    if printf '%s' "$top" | grep -q "${COZY_VERIFY_EXPECT:-$PKG}"; then passed=1; break; fi   # COZY_VERIFY_EXPECT: test hook for the rollback
    say "  try $try: Home opened ${top:-?}"
  done
  if [ "$passed" = 1 ]; then ok "Home from inside another app lands on Cozy Home TV"
  else rollback_home; die "Home did not land on Cozy Home TV, so the Home app and the Home-button service were put back as they were. ./restore.sh undoes the rest (apps turned off, screensaver; --uninstall removes the app). Run ./doctor.sh and see README > Troubleshooting."; fi
fi

# ---------------------------------------------------------------- 10. screensaver, theme, verify
step "10/10  Screensaver, theme and final check"
if [ "$DREAM" = 1 ] && ask "  Use Cozy Home TV as the screensaver (it follows the theme)?" y; then
  run_tv settings put secure screensaver_components "$DREAM_COMPONENT"; run_tv settings put secure screensaver_enabled 1
  ok "screensaver set (was: $(rec "$RECORD" setting secure screensaver_components))"
fi
if [ "$PACK" != "-" ]; then
  DEST=/sdcard/CozyHome; run_tv mkdir -p "$DEST" >/dev/null
  run_adb push "$PACK"/. "$DEST"/ >/dev/null
  if [ "$SDK" -ge 30 ]; then run_tv appops set --uid "$PKG" MANAGE_EXTERNAL_STORAGE allow; else run_tv pm grant "$PKG" android.permission.READ_EXTERNAL_STORAGE || true; fi
  MODE=cozy; ok "Cozy pack copied to $DEST"
else MODE=default; fi
run_tv am start -n "$HOME_COMPONENT" --es mode "$MODE" --es theme "$THEME" --ez reload true >/dev/null
ok "theme $THEME, look: $MODE"
if [ "$DRY_RUN" = 0 ]; then
  sleep 4
  "$ADB" -s "$SERIAL" exec-out screencap -p > "$TVDIR/screenshot-$TS.png" 2>/dev/null && ok "screenshot: $TVDIR/screenshot-$TS.png"
  say "  Home app:      $(home_resolved)"
  say "  Home button:   $(sh_tv dumpsys accessibility | grep -q HomeButtonService && echo 'service bound' || echo 'service NOT bound (see ./doctor.sh)')"
  say "  Screensaver:   $(setting_get secure screensaver_components)"
  say "  Memory:        $(sh_tv dumpsys meminfo "$PKG" | awk '/TOTAL PSS:/ {printf "%.1f MB", $3/1024}') used by Cozy Home TV, $(sh_tv cat /proc/meminfo | awk '/MemAvailable/ {printf "%.0f MB", $2/1024}') free on the TV"
  say "  Turned off:    ${PLAN:- none}"
fi
say
say "${B}Done.${N} Undo everything any time:  ./restore.sh --serial $SERIAL"
[ "$DRY_RUN" = 1 ] || say "  (or without this repo:  sh $RESTORE_SH)"
say "  Health check later (e.g. after a TV update):  ./doctor.sh --serial $SERIAL"
if is_network_serial "$SERIAL"; then
  say "${Y}Security: turn debugging off now that you're done:${N} TV Settings > System > Developer options >"
  say "  USB debugging / Network debugging / Wireless debugging > Off. Anyone on your Wi-Fi could otherwise ask to connect."
fi
