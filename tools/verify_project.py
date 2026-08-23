#!/usr/bin/env python3
"""
Static consistency checks for the project, runnable without the Android SDK.

Catches the class of mistake a compiler would catch but that is easy to introduce while
editing by hand: a resource referenced from Kotlin that does not exist, a malformed XML
file, an import that points at nothing, unbalanced braces.

Run:  python3 tools/verify_project.py
Exit code is non-zero if anything failed.
"""
import os
import re
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")
SRC = os.path.join(ROOT, "app", "src")
MANIFEST = os.path.join(ROOT, "app", "src", "main", "AndroidManifest.xml")
PACKAGE = "com.bgremover.pngmaker"

errors = []
warnings = []


def walk(root, ext):
    for dirpath, _, filenames in os.walk(root):
        for name in filenames:
            if name.endswith(ext):
                yield os.path.join(dirpath, name)


# ---------------------------------------------------------------- XML well-formedness
xml_files = list(walk(RES, ".xml")) + [MANIFEST] + list(
    walk(os.path.join(ROOT, ".github"), ".xml")
)
for path in xml_files:
    try:
        ET.parse(path)
    except ET.ParseError as exc:
        errors.append(f"XML parse error in {os.path.relpath(path, ROOT)}: {exc}")

# ---------------------------------------------------------------- declared resources
declared = {"string": set(), "color": set(), "style": set(),
            "drawable": set(), "mipmap": set(), "xml": set(), "plurals": set()}

for path in walk(os.path.join(RES, "values"), ".xml"):
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError:
        continue
    for child in root:
        name = child.get("name")
        if not name:
            continue
        if child.tag in declared:
            declared[child.tag].add(name)
for path in walk(os.path.join(RES, "values-night"), ".xml"):
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError:
        continue
    for child in root:
        if child.get("name") and child.tag in declared:
            declared[child.tag].add(child.get("name"))

for folder in os.listdir(RES):
    kind = folder.split("-")[0]
    if kind not in ("drawable", "mipmap", "xml"):
        continue
    for entry in os.listdir(os.path.join(RES, folder)):
        base = entry.rsplit(".", 1)[0]
        declared[kind].add(base)

# ---------------------------------------------------------------- Kotlin R.* references
kotlin_files = list(walk(SRC, ".kt"))
r_ref = re.compile(r"\bR\.(string|drawable|mipmap|color|style|xml|plurals)\.([A-Za-z0-9_]+)")

for path in kotlin_files:
    text = open(path, encoding="utf-8").read()
    for kind, name in r_ref.findall(text):
        if name not in declared.get(kind, set()):
            errors.append(
                f"{os.path.relpath(path, ROOT)}: R.{kind}.{name} is not declared"
            )

# ---------------------------------------------------------------- manifest references
manifest_text = open(MANIFEST, encoding="utf-8").read()
for kind, name in re.findall(r'"@(drawable|mipmap|style|xml|color)/([A-Za-z0-9_.]+)"',
                             manifest_text):
    if name not in declared.get(kind, set()):
        errors.append(f"AndroidManifest.xml: @{kind}/{name} is not declared")

# ---------------------------------------------------------------- theme references
for path in walk(os.path.join(RES, "values"), ".xml") :
    text = open(path, encoding="utf-8").read()
    for kind, name in re.findall(r'>@(drawable|mipmap|color|style)/([A-Za-z0-9_.]+)<', text):
        if name not in declared.get(kind, set()):
            errors.append(
                f"{os.path.relpath(path, ROOT)}: @{kind}/{name} is not declared"
            )

# ---------------------------------------------------------------- Kotlin symbol table
declared_symbols = {}          # fully-qualified name -> file
package_of = {}
top_level = re.compile(
    r"^\s*(?:@\w+\s+)*(?:public\s+|internal\s+|private\s+|abstract\s+|open\s+|sealed\s+|"
    r"data\s+|value\s+|annotation\s+|enum\s+)*"
    r"(?:class|object|interface|fun|val|var|typealias)\s+([A-Za-z_][A-Za-z0-9_]*)",
    re.MULTILINE,
)

for path in kotlin_files:
    text = open(path, encoding="utf-8").read()
    match = re.search(r"^package\s+([\w.]+)", text, re.MULTILINE)
    if not match:
        errors.append(f"{os.path.relpath(path, ROOT)}: no package declaration")
        continue
    pkg = match.group(1)
    package_of[path] = pkg
    # Only top-level declarations: those at column 0.
    for line in text.splitlines():
        if line.startswith((" ", "\t", "}", ")")):
            continue
        m = top_level.match(line)
        if m:
            declared_symbols.setdefault(f"{pkg}.{m.group(1)}", path)

# Nested members referenced via import (e.g. an enum entry or companion const) are allowed
# through by also registering `Outer.Inner` names.
nested = re.compile(r"^\s{4}(?:@\w+\s+)*(?:public\s+|internal\s+|private\s+|"
                    r"data\s+|sealed\s+|open\s+|abstract\s+|enum\s+)*"
                    r"(?:class|object|interface|fun|val)\s+([A-Za-z_][A-Za-z0-9_]*)")

for path in kotlin_files:
    text = open(path, encoding="utf-8").read()
    pkg = package_of.get(path)
    if not pkg:
        continue
    current_outer = None
    for line in text.splitlines():
        if not line.startswith((" ", "\t")):
            m = top_level.match(line)
            if m:
                current_outer = m.group(1)
        else:
            m = nested.match(line)
            if m and current_outer:
                declared_symbols.setdefault(f"{pkg}.{current_outer}.{m.group(1)}", path)

# ---------------------------------------------------------------- internal imports
import_re = re.compile(r"^import\s+(" + re.escape(PACKAGE) + r"[\w.]*)", re.MULTILINE)
for path in kotlin_files:
    text = open(path, encoding="utf-8").read()
    for imported in import_re.findall(text):
        if imported.endswith(".R") or f".{PACKAGE}.R." in f".{imported}.":
            continue
        if imported in (f"{PACKAGE}.R", f"{PACKAGE}.BuildConfig"):
            continue
        if imported in declared_symbols:
            continue
        # top-level function imports from a file (e.g. util.formatFileSize)
        if imported.rsplit(".", 1)[0] + ".*" in declared_symbols:
            continue
        errors.append(
            f"{os.path.relpath(path, ROOT)}: import {imported} does not resolve to a "
            f"declaration in this project"
        )

# ---------------------------------------------------------------- brace balance
for path in kotlin_files + list(walk(ROOT, ".kts")):
    if "/build/" in path:
        continue
    text = open(path, encoding="utf-8").read()
    # Strip strings and comments crudely before counting.
    stripped = re.sub(r'"""(?:.|\n)*?"""', '""', text)
    stripped = re.sub(r'"(?:\\.|[^"\\])*"', '""', stripped)
    stripped = re.sub(r"'(?:\\.|[^'\\])'", "''", stripped)
    stripped = re.sub(r"//[^\n]*", "", stripped)
    stripped = re.sub(r"/\*(?:.|\n)*?\*/", "", stripped)
    for open_ch, close_ch in (("{", "}"), ("(", ")"), ("[", "]")):
        if stripped.count(open_ch) != stripped.count(close_ch):
            errors.append(
                f"{os.path.relpath(path, ROOT)}: unbalanced '{open_ch}{close_ch}' "
                f"({stripped.count(open_ch)} vs {stripped.count(close_ch)})"
            )

# ---------------------------------------------------------------- unused strings
used_strings = set()
for path in kotlin_files:
    text = open(path, encoding="utf-8").read()
    used_strings.update(re.findall(r"R\.string\.([A-Za-z0-9_]+)", text))
used_strings.update(re.findall(r'@string/([A-Za-z0-9_]+)', manifest_text))
unused = sorted(declared["string"] - used_strings)
if unused:
    warnings.append("unused string resources: " + ", ".join(unused))

# ---------------------------------------------------------------- report
print(f"Kotlin files      : {len(kotlin_files)}")
print(f"XML files         : {len(xml_files)}")
print(f"String resources  : {len(declared['string'])}")
print(f"Top-level symbols : {len(declared_symbols)}")
print()

for warning in warnings:
    print(f"WARN  {warning}")
for error in errors:
    print(f"FAIL  {error}")

print()
if errors:
    print(f"{len(errors)} problem(s) found.")
    sys.exit(1)
print("All consistency checks passed.")
