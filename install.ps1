# 8fac PC-side installer (Windows). Run from the repo root:
#   powershell -ExecutionPolicy Bypass -File install.ps1
# Creates the venv, installs deps, and makes a Start-Menu shortcut whose
# hotkey (your choice) pops the service picker anywhere.
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

Write-Host "== 8fac PC install (Windows) =="
if (!(Test-Path .venv)) { py -3 -m venv .venv }
.\.venv\Scripts\pip.exe install -q -r requirements.txt
Write-Host "  python environment ready"

$hotkey = Read-Host "Hotkey for code requests [CTRL+ALT+8]"
if ([string]::IsNullOrWhiteSpace($hotkey)) { $hotkey = "CTRL+ALT+8" }

# Start-Menu shortcuts are the one place Windows lets a hotkey live
$ws = New-Object -ComObject WScript.Shell
$lnkPath = Join-Path $ws.SpecialFolders("Programs") "8fac code.lnk"
$lnk = $ws.CreateShortcut($lnkPath)
$lnk.TargetPath  = (Resolve-Path .\.venv\Scripts\pythonw.exe)
$lnk.Arguments   = "`"$(Resolve-Path .\pc\hotkey_win.pyw)`""
$lnk.WorkingDirectory = (Resolve-Path .)
$lnk.Hotkey      = $hotkey
$lnk.IconLocation = "shell32.dll,77"
$lnk.Save()
Write-Host "  shortcut with hotkey $hotkey installed"

Write-Host ""
Write-Host "Next steps:"
Write-Host "  1. .venv\Scripts\python pair.py --relay wss://YOUR-RELAY"
Write-Host "  2. scan the QR with the 8fac app, then press $hotkey"
Write-Host "NOTE: shortcut hotkeys need one Explorer session restart to arm."
