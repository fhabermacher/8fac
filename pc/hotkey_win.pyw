# Windows hotkey entry point (launched by the Start-Menu shortcut's hotkey;
# .pyw = no console window). Picker -> request -> type/clipboard.
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PY = ROOT / ".venv" / "Scripts" / "python.exe"

service = subprocess.run(
    [PY, ROOT / "pc" / "picker.py"], capture_output=True, text=True
).stdout.strip()
if service:
    subprocess.run([PY, ROOT / "request.py", service, "--type"],
                   creationflags=subprocess.CREATE_NO_WINDOW)
