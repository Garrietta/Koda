#!/usr/bin/env python3
"""Renames the app everywhere a person reads its name.

    python3 tools/rename_app.py Koda Rondo

It changes the words people see: every strings.xml, and the handful of
sentences still written in Kotlin. It leaves alone what is not the app's
name to a user: class and package names, log tags, User-Agent strings,
links to the upstream project and the Discord presence (which belongs to
the upstream Discord application).

Run it again with the names swapped round, or with a new one, to change
the name later. It reports what it touched and fails if it touched nothing.
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
SRC = ROOT / "app/src/main/java"

# A Kotlin line carrying any of these is not user-facing copy.
SKIP_LINE = ("http", "User-Agent", "USER_AGENT", "TAG", "Log.", "KLog.", '"Get ', 'put("name"')
STRING = re.compile(r'"(?:[^"\\]|\\.)*"')


def main(old, new):
    word = re.compile(rf"\b{re.escape(old)}\b")
    touched = {}

    for file in sorted(RES.glob("values*/strings.xml")):
        text = file.read_text(encoding="utf-8")
        changed, count = word.subn(new, text)
        if count:
            file.write_text(changed, encoding="utf-8")
            touched[file.relative_to(ROOT)] = count

    for file in sorted(SRC.rglob("*.kt")):
        lines = file.read_text(encoding="utf-8").split("\n")
        count = 0
        for i, line in enumerate(lines):
            stripped = line.lstrip()
            if stripped.startswith(("//", "*", "/*")) or any(s in line for s in SKIP_LINE):
                continue
            def swap(match):
                nonlocal count
                changed, hits = word.subn(new, match.group(0))
                count += hits
                return changed
            lines[i] = STRING.sub(swap, line)
        if count:
            file.write_text("\n".join(lines), encoding="utf-8")
            touched[file.relative_to(ROOT)] = count

    gradle = ROOT / "app/build.gradle.kts"
    text = gradle.read_text(encoding="utf-8")
    changed, count = re.subn(rf'"{re.escape(old)} Debug"', f'"{new} Debug"', text)
    if count:
        gradle.write_text(changed, encoding="utf-8")
        touched[gradle.relative_to(ROOT)] = count

    assert touched, f"nothing named {old} was found"
    strings = sum(c for f, c in touched.items() if f.name == "strings.xml")
    print(f"{old} -> {new}: {strings} in strings.xml across "
          f"{sum(1 for f in touched if f.name == 'strings.xml')} locales")
    for file, count in touched.items():
        if file.name != "strings.xml":
            print(f"  {count:3d}  {file}")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2])
