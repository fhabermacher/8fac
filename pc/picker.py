#!/usr/bin/env python3
"""Service picker for the 8fac hotkey.

A text field plus the ~10 most-recent services as real BUTTONS:
- click one, or Tab/Shift+Tab onto it and hit Enter/Space
- or just type: buttons filter live; Enter takes the top match
  (free text wins if nothing matches, so new services need no extra flow)
- Esc cancels

Prints the chosen service to stdout; exit 1 on cancel.
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import tkinter as tk  # noqa: E402

from eightfac import config  # noqa: E402

SERVICES = config.CONF_DIR / "services.txt"
MAX_BUTTONS = 10


def main():
    known = (SERVICES.read_text().split() if SERVICES.exists() else [])

    root = tk.Tk()
    root.title("8fac — code for…")
    root.attributes("-topmost", True)
    root.geometry("+%d+%d" % (root.winfo_screenwidth() // 2 - 170,
                              root.winfo_screenheight() // 3))

    def done(choice: str):
        print(choice)
        root.destroy()

    query = tk.StringVar()
    entry = tk.Entry(root, textvariable=query, font=("Sans", 14), width=30)
    entry.pack(padx=12, pady=(12, 6))
    frame = tk.Frame(root)
    frame.pack(fill="both", expand=True, padx=12, pady=(0, 12))

    filtered = []

    def refilter(*_):
        nonlocal filtered
        q = query.get().lower()
        filtered = [s for s in known if q in s.lower()][:MAX_BUTTONS]
        for w in frame.winfo_children():
            w.destroy()
        for s in filtered:
            tk.Button(frame, text=s, font=("Sans", 12), anchor="w",
                      command=lambda s=s: done(s)
                      ).pack(fill="x", pady=1)
        if not filtered and q:
            tk.Label(frame, text=f'Enter ↵ requests "{q.strip()}"',
                     font=("Sans", 10), fg="gray").pack()

    def on_enter(_=None):
        focused = root.focus_get()
        if isinstance(focused, tk.Button):
            focused.invoke()
        elif filtered:
            done(filtered[0])
        elif query.get().strip():
            done(query.get().strip())
        else:
            sys.exit(1)

    query.trace_add("write", refilter)
    root.bind("<Return>", on_enter)
    root.bind("<Escape>", lambda e: sys.exit(1))

    refilter()
    entry.focus_force()
    root.mainloop()
    sys.exit(1)  # window closed without a choice


if __name__ == "__main__":
    main()
