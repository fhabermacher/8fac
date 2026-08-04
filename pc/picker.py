#!/usr/bin/env python3
"""Service picker for the 8fac hotkey.

A borderless card: search field on top, the ~10 most-recent services as
big flat buttons below.
- click one, or Tab/Shift+Tab onto it and hit Enter/Space
- type to filter live; Enter takes the top match
  (free text wins if nothing matches, so new services need no extra flow)
- Esc cancels

Follows the desktop light/dark preference. Prints the choice to stdout.
"""
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import tkinter as tk  # noqa: E402
from tkinter import font as tkfont  # noqa: E402

from eightfac import config  # noqa: E402

SERVICES = config.CONF_DIR / "services.txt"
MAX_BUTTONS = 10

DARK = dict(bg="#16181D", card="#1E212A", fg="#ECEFF4", dim="#8B93A7",
            field="#262A35", accent="#6F7BF7", hover="#2F3545")
LIGHT = dict(bg="#F4F5F8", card="#FFFFFF", fg="#1B1E27", dim="#6B7280",
            field="#EDEFF4", accent="#4A54C4", hover="#E4E7F2")


def palette() -> dict:
    try:
        out = subprocess.run(
            ["gsettings", "get", "org.gnome.desktop.interface", "color-scheme"],
            capture_output=True, text=True, timeout=2).stdout
        return LIGHT if "light" in out else DARK
    except Exception:
        return DARK


def main():
    known = SERVICES.read_text().split() if SERVICES.exists() else []
    c = palette()

    root = tk.Tk()
    root.title("8fac")
    root.configure(bg=c["bg"])
    root.attributes("-topmost", True)
    # A WM-managed dialog, NOT overrideredirect: unmanaged windows never get
    # keyboard focus from the window manager, which silently breaks Tab.
    try:
        root.attributes("-type", "dialog")
    except tk.TclError:
        pass
    W = 380
    root.geometry("%dx%d+%d+%d" % (
        W, 120, root.winfo_screenwidth() // 2 - W // 2,
        int(root.winfo_screenheight() * 0.28)))

    ui = tkfont.Font(family="Ubuntu", size=11)
    ui_big = tkfont.Font(family="Ubuntu", size=13)
    ui_small = tkfont.Font(family="Ubuntu", size=9)

    card = tk.Frame(root, bg=c["card"], highlightthickness=1,
                    highlightbackground=c["hover"])
    card.pack(fill="both", expand=True, padx=8, pady=8)

    header = tk.Frame(card, bg=c["card"])
    header.pack(fill="x", padx=16, pady=(14, 8))
    tk.Label(header, text="8fac", font=ui_big, bg=c["card"],
             fg=c["accent"]).pack(side="left")
    tk.Label(header, text="code for…", font=ui, bg=c["card"],
             fg=c["dim"]).pack(side="left", padx=(8, 0))

    query = tk.StringVar()
    entry = tk.Entry(card, textvariable=query, font=ui_big, bd=0,
                     bg=c["field"], fg=c["fg"], insertbackground=c["accent"],
                     relief="flat", highlightthickness=2,
                     highlightbackground=c["field"], highlightcolor=c["accent"])
    entry.pack(fill="x", padx=16, ipady=8)

    listing = tk.Frame(card, bg=c["card"])
    listing.pack(fill="both", expand=True, padx=16, pady=(10, 4))

    footer = tk.Label(card, text="↹ move   ↵ select   esc cancel",
                      font=ui_small, bg=c["card"], fg=c["dim"])
    footer.pack(pady=(2, 12))

    filtered = []

    def done(choice: str):
        print(choice)
        root.destroy()

    def make_button(parent, text):
        b = tk.Button(parent, text=text, font=ui, anchor="w", bd=0,
                      bg=c["card"], fg=c["fg"], activebackground=c["hover"],
                      activeforeground=c["fg"], relief="flat", padx=12, pady=7,
                      highlightthickness=2, highlightbackground=c["card"],
                      highlightcolor=c["accent"], cursor="hand2",
                      takefocus=1, command=lambda: done(text))
        b.bind("<Enter>", lambda e: b.configure(bg=c["hover"]))
        b.bind("<Leave>", lambda e: b.configure(bg=c["card"]))
        b.bind("<FocusIn>", lambda e: b.configure(bg=c["hover"]))
        b.bind("<FocusOut>", lambda e: b.configure(bg=c["card"]))
        return b

    def refilter(*_):
        nonlocal filtered
        q = query.get().lower()
        filtered = [s for s in known if q in s.lower()][:MAX_BUTTONS]
        for w in listing.winfo_children():
            w.destroy()
        for s in filtered:
            make_button(listing, s).pack(fill="x", pady=1)
        if not filtered:
            hint = (f'↵ requests "{q.strip()}"' if q.strip()
                    else "No history yet — type a service name and press ↵.\n"
                         "Services appear here after first use.")
            tk.Label(listing, text=hint, font=ui_small, bg=c["card"],
                     fg=c["dim"], justify="left").pack(anchor="w", pady=4)
        rows = max(len(filtered), 1)
        root.geometry("%dx%d" % (W, 150 + rows * 36))

    def on_enter(_=None):
        w = root.focus_get()
        if isinstance(w, tk.Button):
            w.invoke()
        elif filtered:
            done(filtered[0])
        elif query.get().strip():
            done(query.get().strip())

    def cancel(_=None):
        root.destroy()
        sys.exit(1)

    query.trace_add("write", refilter)
    root.bind("<Return>", on_enter)
    root.bind("<KP_Enter>", on_enter)
    root.bind("<Escape>", cancel)
    root.bind("<FocusOut>", lambda e: None)

    def take_focus():
        root.lift()
        root.focus_force()
        entry.focus_set()

    refilter()
    take_focus()
    root.after(80, take_focus)  # some WMs map the window a beat later
    root.mainloop()
    sys.exit(1)


if __name__ == "__main__":
    main()
