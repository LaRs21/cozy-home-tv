#!/usr/bin/env bash
# Kept for old instructions: the full undo is now ./restore.sh at the top of the repo (it uses the backup record).
#   scripts/restore.sh <TV-IP|serial> [--uninstall] [--remove-pack] [--dry-run] [--yes]
exec "$(dirname "$0")/../restore.sh" "$@"
