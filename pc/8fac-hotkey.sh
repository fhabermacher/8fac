#!/bin/bash
# Hotkey entry point: pick a service, request the code, type it into the
# focused field (falls back to clipboard). Bind to e.g. Super+F9.
set -u
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONF="${EIGHTFAC_CONF:-$HOME/.config/8fac}"

# Errors go to a log, never /dev/null: a silently crashing picker is
# indistinguishable from "user pressed Esc", which cost an afternoon once.
LOG="$CONF/hotkey.log"
service=$("$DIR/.venv/bin/python" "$DIR/pc/picker.py" 2>>"$LOG")
if [ -z "${service:-}" ]; then
    exit 0
fi

code_err=$("$DIR/.venv/bin/python" "$DIR/request.py" "$service" --type 2>&1 >/dev/null)
status=$?
if [ $status -ne 0 ]; then
    zenity --error --title="8fac" --text="${code_err:-request failed}" 2>/dev/null
fi
exit $status
