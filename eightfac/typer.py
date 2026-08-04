"""Deliver a code into the focused input field, cross-platform.

Preference order:
- Windows: SendInput with KEYEVENTF_UNICODE (no dependencies)
- macOS: osascript keystroke
- Linux X11: python-xlib XTEST (no external tools, no sudo)
- Linux fallbacks: xdotool, wtype (Wayland), ydotool
- Last resort everywhere: clipboard
"""
import shutil
import subprocess
import sys
import time


def type_text(text: str) -> bool:
    """Try to type into the focused window. True on (apparent) success."""
    for attempt in (_windows, _macos, _x11_xtest, _cli_tools):
        try:
            if attempt(text):
                return True
        except Exception:
            continue
    return False


def copy_text(text: str) -> bool:
    for argv in (["wl-copy"], ["xclip", "-selection", "clipboard"],
                 ["xsel", "-ib"], ["clip"], ["pbcopy"]):
        if shutil.which(argv[0]):
            subprocess.run(argv, input=text.encode(), check=False,
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            return True
    try:  # universal fallback: tkinter owns the clipboard briefly
        import tkinter
        r = tkinter.Tk()
        r.withdraw()
        r.clipboard_clear()
        r.clipboard_append(text)
        r.update()
        r.after(200, r.destroy)
        r.mainloop()
        return True
    except Exception:
        return False


def _windows(text: str) -> bool:
    if sys.platform != "win32":
        return False
    import ctypes
    from ctypes import wintypes

    KEYEVENTF_UNICODE, KEYEVENTF_KEYUP = 0x0004, 0x0002

    class KEYBDINPUT(ctypes.Structure):
        _fields_ = [("wVk", wintypes.WORD), ("wScan", wintypes.WORD),
                    ("dwFlags", wintypes.DWORD), ("time", wintypes.DWORD),
                    ("dwExtraInfo", ctypes.POINTER(wintypes.ULONG))]

    class INPUT(ctypes.Structure):
        class _U(ctypes.Union):
            _fields_ = [("ki", KEYBDINPUT)]
        _anonymous_ = ("u",)
        _fields_ = [("type", wintypes.DWORD), ("u", _U)]

    inputs = []
    for ch in text:
        for flags in (KEYEVENTF_UNICODE, KEYEVENTF_UNICODE | KEYEVENTF_KEYUP):
            i = INPUT(type=1)
            i.ki = KEYBDINPUT(0, ord(ch), flags, 0, None)
            inputs.append(i)
    arr = (INPUT * len(inputs))(*inputs)
    sent = ctypes.windll.user32.SendInput(len(inputs), arr,
                                          ctypes.sizeof(INPUT))
    return sent == len(inputs)


def _macos(text: str) -> bool:
    if sys.platform != "darwin":
        return False
    subprocess.run(["osascript", "-e",
                    f'tell application "System Events" to keystroke "{text}"'],
                   check=True, capture_output=True)
    return True


def _x11_xtest(text: str) -> bool:
    if not sys.platform.startswith("linux"):
        return False
    from Xlib import X, display as xdisplay  # noqa: WPS433
    from Xlib.ext import xtest

    d = xdisplay.Display()
    try:
        # keysym_to_keycodes returns a lazy map in python-xlib ≥0.30
        shift_code = list(d.keysym_to_keycodes(0xFFE1))[0][0]  # Shift_L
        for ch in text:
            codes = list(d.keysym_to_keycodes(ord(ch)))
            if not codes:
                return False
            code, index = codes[0][0], codes[0][1]
            shifted = index in (1, 3, 5)
            if shifted:
                xtest.fake_input(d, X.KeyPress, shift_code)
            xtest.fake_input(d, X.KeyPress, code)
            xtest.fake_input(d, X.KeyRelease, code)
            if shifted:
                xtest.fake_input(d, X.KeyRelease, shift_code)
            d.sync()
            time.sleep(0.005)
        return True
    finally:
        d.close()


def _cli_tools(text: str) -> bool:
    for tool, argv in (("xdotool", ["xdotool", "type", "--delay", "12",
                                    "--", text]),
                       ("wtype", ["wtype", text]),
                       ("ydotool", ["ydotool", "type", "--", text])):
        if shutil.which(tool):
            return subprocess.run(argv, check=False,
                                  stdout=subprocess.DEVNULL,
                                  stderr=subprocess.DEVNULL).returncode == 0
    return False
