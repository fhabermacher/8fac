#!/usr/bin/env python3
"""Service picker for the 8fac hotkey: type to filter, click or
Enter/Tab/arrows to choose. Prints the chosen service to stdout.

Most-recently-used order (services.txt keeps MRU-first). Free text is
allowed — whatever is in the entry when you hit Enter wins if nothing
matches, so new services need no extra flow.
"""
import sys
import tkinter as tk
from pathlib import Path

from eightfac import config  # noqa: E402  (run from repo root)

SERVICES = config.CONF_DIR / "services.txt"


def main():
    known = SERVICES.read_text().split() if SERVICES.exists() else []

    root = tk.Tk()
    root.title("8fac — code for…")
    root.attributes("-topmost", True)
    root.geometry("+%d+%d" % (root.winfo_screenwidth() // 2 - 170,
                              root.winfo_screenheight() // 3))

    query = tk.StringVar()
    entry = tk.Entry(root, textvariable=query, font=("Sans", 14), width=32)
    entry.pack(padx=10, pady=(10, 4))
    listbox = tk.Listbox(root, font=("Sans", 13), height=min(10, max(3, len(known))),
                         activestyle="dotbox")
    listbox.pack(fill="both", expand=True, padx=10, pady=(0, 10))

    filtered = []

    def refilter(*_):
        nonlocal filtered
        q = query.get().lower()
        filtered = [s for s in known if q in s.lower()]
        listbox.delete(0, "end")
        for s in filtered:
            listbox.insert("end", s)
        if filtered:
            listbox.selection_clear(0, "end")
            listbox.selection_set(0)
            listbox.activate(0)

    def move(delta):
        if not filtered:
            return "break"
        cur = listbox.curselection()
        idx = (cur[0] + delta) % len(filtered) if cur else 0
        listbox.selection_clear(0, "end")
        listbox.selection_set(idx)
        listbox.activate(idx)
        listbox.see(idx)
        return "break"

    def choose(*_):
        cur = listbox.curselection()
        if cur and filtered:
            print(filtered[cur[0]])
        elif query.get().strip():
            print(query.get().strip())
        else:
            sys.exit(1)
        root.destroy()

    query.trace_add("write", refilter)
    entry.bind("<Return>", choose)
    entry.bind("<Tab>", lambda e: move(1))
    entry.bind("<ISO_Left_Tab>", lambda e: move(-1))
    entry.bind("<Down>", lambda e: move(1))
    entry.bind("<Up>", lambda e: move(-1))
    entry.bind("<Escape>", lambda e: sys.exit(1))
    listbox.bind("<Double-Button-1>", choose)
    listbox.bind("<Return>", choose)

    refilter()
    entry.focus_force()
    root.mainloop()


if __name__ == "__main__":
    main()
