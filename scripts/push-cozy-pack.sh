#!/usr/bin/env bash
# Copy your pictures/music to the TV and let the app read them.
#   scripts/push-cozy-pack.sh <TV-IP> <folder> [--cozy] [--app-folder]
#     <folder>       e.g. examples/sample-cozy-pack, or your own folder (see docs/cozy-pack.md)
#     --cozy         switch the app to the Cozy look now (opens the home screen)
#     --app-folder   copy into the app's own folder instead of /sdcard/CozyHome (no permission needed,
#                    but it is deleted if you uninstall the app)
set -euo pipefail
cd "$(dirname "$0")/.."
. scripts/lib.sh
ADDR="${1:-}"; SRC="${2:-}"; shift 2 || true
COZY=0; APPDIR=0
for a in "$@"; do case "$a" in --cozy) COZY=1 ;; --app-folder) APPDIR=1 ;; *) die "unknown option $a" ;; esac; done
[ -d "$SRC" ] || die "folder not found: $SRC"
connect_tv "$ADDR"
sh_tv pm path "$PKG" >/dev/null 2>&1 || die "Cozy Home TV is not installed. Run scripts/install.sh first."

# Pictures larger than 3840x2160 waste memory on 2 GB TVs; the app scales them down, but warn anyway.
if command -v sips >/dev/null 2>&1; then
  for f in "$SRC"/*.jpg "$SRC"/*.jpeg "$SRC"/*.png "$SRC"/*.webp; do [ -f "$f" ] || continue
    w="$(sips -g pixelWidth "$f" 2>/dev/null | awk '/pixelWidth/{print $2}')"; [ -n "$w" ] && [ "$w" -gt 3840 ] && echo "note: $f is ${w}px wide; 1920 or 3840 is plenty."
  done
fi

if [ "$APPDIR" = 1 ]; then DEST="/sdcard/Android/data/$PKG/files/CozyHome"; else DEST="/sdcard/CozyHome"; fi
echo "Copying $SRC -> $DEST ..."
sh_tv mkdir -p "$DEST"
"$ADB" -s "$SERIAL" push "$SRC"/. "$DEST"/ >/dev/null
sh_tv ls -la "$DEST"

if [ "$APPDIR" = 0 ]; then
  SDK="$(sdk_int)"
  if [ "${SDK:-0}" -ge 30 ]; then sh_tv appops set --uid "$PKG" MANAGE_EXTERNAL_STORAGE allow
  else sh_tv pm grant "$PKG" android.permission.READ_EXTERNAL_STORAGE || true; fi
  echo "Allowed Cozy Home TV to read $DEST."
fi

if [ "$COZY" = 1 ]; then
  sh_tv am start -n "$HOME_COMPONENT" --es mode cozy --ez reload true >/dev/null
  echo "Switched to the Cozy look."
else
  echo "Done. Turn on the Cozy look in the app: Settings (gear) > Look > Cozy."
fi
