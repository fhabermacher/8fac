#!/bin/bash
# 8fac PC-side installer (Linux). Creates the venv, installs deps, and
# registers a global hotkey of your choice (GNOME; instructions otherwise).
#   ./install.sh                interactive
#   ./install.sh --hotkey '<Super>F9' --yes
set -e
cd "$(dirname "$0")"

HOTKEY="" ; YES=0
while [ $# -gt 0 ]; do case "$1" in
    --hotkey) HOTKEY="$2"; shift 2 ;;
    --yes) YES=1; shift ;;
    *) echo "unknown arg $1"; exit 1 ;;
esac; done

echo "== 8fac PC install =="
[ -d .venv ] || python3 -m venv .venv
.venv/bin/pip install -q -r requirements.txt
echo "✓ python environment ready"

if [ -z "$HOTKEY" ] && [ "$YES" = 0 ]; then
    echo
    echo "Global hotkey for requesting a code (GNOME syntax)."
    echo "Examples: <Super>F9   <Ctrl><Alt>8   <Super>space"
    read -rp "Hotkey [<Super>F9]: " HOTKEY
fi
HOTKEY="${HOTKEY:-<Super>F9}"

if command -v gsettings >/dev/null && \
   gsettings list-schemas 2>/dev/null | grep -q gnome.settings-daemon; then
    KB=/org/gnome/settings-daemon/plugins/media-keys/custom-keybindings/eightfac/
    CUR=$(gsettings get org.gnome.settings-daemon.plugins.media-keys custom-keybindings)
    case "$CUR" in *eightfac*) : ;; *)
        NEW=$(python3 - "$CUR" "$KB" <<'EOF'
import ast, sys
cur = ast.literal_eval(sys.argv[1]) if sys.argv[1] != '@as []' else []
cur.append(sys.argv[2]); print(str(cur))
EOF
        )
        gsettings set org.gnome.settings-daemon.plugins.media-keys custom-keybindings "$NEW" ;;
    esac
    S=org.gnome.settings-daemon.plugins.media-keys.custom-keybinding:$KB
    gsettings set $S name '8fac code'
    gsettings set $S command "$(pwd)/pc/8fac-hotkey.sh"
    gsettings set $S binding "$HOTKEY"
    echo "✓ hotkey $HOTKEY registered (GNOME)"
else
    echo "! Not GNOME — bind this command to a key in your DE's settings:"
    echo "    $(pwd)/pc/8fac-hotkey.sh"
fi

echo
echo "Next steps:"
echo "  1. .venv/bin/python pair.py --relay wss://YOUR-RELAY   (shows QR)"
echo "  2. scan it with the 8fac app  →  press your hotkey"
