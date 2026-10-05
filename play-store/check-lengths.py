#!/usr/bin/env python3
"""Checks listing.md's name, short and full description against Play's limits (30 / 80 / 4000)."""
import pathlib
import re
import sys

text = pathlib.Path(__file__).with_name("listing.md").read_text(encoding="utf-8")
limits = {"App name": 30, "Short description": 80, "Full description": 4000}
ok = True
for heading, limit in limits.items():
    m = re.search(r"## " + heading + r"[^\n]*\n.*?```\n(.*?)\n```", text, re.S)
    if not m:
        print(f"{heading}: not found"); ok = False; continue
    n = len(m.group(1))
    print(f"{heading}: {n}/{limit}")
    ok &= n <= limit
sys.exit(0 if ok else 1)
