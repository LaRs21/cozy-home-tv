# Shared helpers for the scripts. Source it; don't run it.
# TV address: first argument (192.168.1.50 or 192.168.1.50:5555) or the TV env var.
PKG="io.github.lars21.cozyhometv"
HOME_COMPONENT="$PKG/.HomeActivity"
A11Y_COMPONENT="$PKG/.HomeButtonService"
DREAM_COMPONENT="$PKG/.CozyDream"
ADB="${ADB:-adb}"

die() { echo "error: $*" >&2; exit 1; }

need_adb() { command -v "$ADB" >/dev/null 2>&1 || die "adb not found. Install Android platform-tools (macOS: brew install android-platform-tools)."; }

# connect_tv <addr>  -> sets SERIAL
connect_tv() {
  local addr="${1:-${TV:-}}"
  [ -n "$addr" ] || die "give the TV's IP address, e.g. 192.168.1.50 (TV: Settings > Network > About)"
  case "$addr" in *:*) SERIAL="$addr" ;; *) SERIAL="$addr:5555" ;; esac
  need_adb
  "$ADB" connect "$SERIAL" >/dev/null 2>&1 || true
  local st; st="$("$ADB" -s "$SERIAL" get-state 2>/dev/null || true)"
  if [ "$st" != "device" ]; then
    echo "Could not talk to $SERIAL (state: ${st:-none})."
    echo " - Turn on Developer options > USB debugging (and Wireless/Network debugging if your TV has it)."
    echo " - Accept the 'Allow debugging?' prompt on the TV, then run this again."
    exit 1
  fi
}

sh_tv() { "$ADB" -s "$SERIAL" shell "$@"; }
sdk_int() { sh_tv getprop ro.build.version.sdk | tr -d '\r'; }
