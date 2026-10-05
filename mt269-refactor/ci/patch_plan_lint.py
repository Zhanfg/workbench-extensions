#!/usr/bin/env python3
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
README = (ROOT / "README.md").read_text(encoding="utf-8")

required = [
    "hotfix3",
    "targetSdk",
    "generation",
    "state machine",
    "sorting",
]
missing = [x for x in required if x.lower() not in README.lower()]
if missing:
    raise SystemExit("missing architecture invariants: " + ", ".join(missing))

java = "\n".join(p.read_text(encoding="utf-8") for p in (ROOT / "src").glob("*.java"))
for forbidden in ["Thread.sleep(600)", "newFixedThreadPool(1)"]:
    if forbidden in java:
        raise SystemExit(f"forbidden regression pattern: {forbidden}")

print("patch-plan lint: PASS")
