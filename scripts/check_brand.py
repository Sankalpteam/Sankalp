#!/usr/bin/env python3
"""Fail the build if the logo this project replaced is drawn anywhere again.

The mark is a watercolour brush stroke supplied as one image. Before it, every surface drew
its own version of a purple gradient diamond - three Compose screens, the web's branding.js,
the launcher vectors - and they drifted apart. That is not a mistake worth making twice, so
the old artwork is now a build error rather than a code review question.

What it looks for:

  * the old gradient's three colours, which only ever appeared together in that diamond;
  * the old diamond's path geometry in Compose or vector XML;
  * any file that draws the mark with <path> instead of using branding/sankalp-mark.png.

Usage:  python3 scripts/check_brand.py
"""

from __future__ import annotations

import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

SCANNED_DIRS = ("android", "web")
SCANNED_SUFFIXES = (".kt", ".xml", ".js", ".css", ".html", ".json", ".svg")

# The old mark, in the three places it was written down.
OLD_COLOURS = ("C15CFF", "7C3AED", "22C7FF")
OLD_GEOMETRY = (
    "M54,31.5 L61.2,46.2",          # launcher vector
    "moveTo(24f, 14f); lineTo(27.2f, 20.8f)",  # Compose canvas
    "rotate(45f",                    # the diamond's rotation, only ever the old mark
)

SKIP_DIRS = {".git", "node_modules", "build", ".gradle"}


def files() -> list[str]:
    found: list[str] = []
    for top in SCANNED_DIRS:
        for base, dirs, names in os.walk(os.path.join(ROOT, top)):
            dirs[:] = [d for d in dirs if d not in SKIP_DIRS]
            for name in names:
                if name.endswith(SCANNED_SUFFIXES):
                    found.append(os.path.join(base, name))
    return found


def main() -> int:
    problems: list[str] = []

    for path in files():
        try:
            text = open(path, encoding="utf-8", errors="ignore").read()
        except OSError:
            continue

        relative = os.path.relpath(path, ROOT)

        if all(colour in text for colour in OLD_COLOURS):
            problems.append(f"{relative}: draws the old purple gradient mark")

        for fragment in OLD_GEOMETRY:
            if fragment in text:
                problems.append(f"{relative}: contains the old mark's geometry ({fragment})")

    if problems:
        print("The logo this project replaced is still being drawn:\n")
        for problem in problems:
            print(f"  {problem}")
        print(
            "\nEvery surface must use branding/sankalp-mark.png - directly, or through "
            "scripts/brand_assets.py. See android/branding/README.md."
        )
        return 1

    print("Brand check: no copy of the old mark in android/ or web/.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
