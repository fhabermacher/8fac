#!/bin/bash
# Hotkey entry point: pick a service, request the code, type it into the
# focused field (falls back to clipboard). Bind to e.g. Super+F9.
set -u
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONF="${EIGHTFAC_CONF:-$HOME/.config/8fac}"

known=$(cat "$CONF/services.txt" 2>/dev/null)
if [ -n "$known" ]; then
    service=$(zenity --list --title="8fac" --text="Code for which service?" \
        --column=service $known --height=280 2>/dev/null)
else
    service=$(zenity --entry --title="8fac" \
        --text="Service name (as enrolled on the phone):" 2>/dev/null)
fi
[ -z "${service:-}" ] && exit 0

code_err=$("$DIR/.venv/bin/python" "$DIR/request.py" "$service" --type 2>&1 >/dev/null)
status=$?
if [ $status -ne 0 ]; then
    zenity --error --title="8fac" --text="${code_err:-request failed}" 2>/dev/null
fi
exit $status
