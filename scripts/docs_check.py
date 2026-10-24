#!/usr/bin/env python3
"""Checks the manual (docs/): every page's front matter, every internal link and image.

A link is root-relative with a trailing slash ("/hardware/case/") and must name a page; an image
("/assets/img/x.png") must exist. Exits 1 with a list when anything is wrong.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DOCS = ROOT / "docs"
LINK = re.compile(r'\]\((/[^)\s#]*)(?:#[^)]*)?\)|(?:href|src)="(/[^"#]*)')
# Names that must never appear in a published page.
FORBIDDEN = [r"\bpopo\b"]


def front_matter(text):
    m = re.match(r"^---\n(.*?)\n---\n", text, re.S)
    if not m:
        return None
    fields = {}
    for line in m.group(1).splitlines():
        k, _, v = line.partition(":")
        fields[k.strip()] = v.split(" #")[0].strip()
    return fields


def page_exists(url):
    if url == "/":
        return (DOCS / "index.md").exists()
    rel = url.strip("/")
    return (DOCS / (rel + ".md")).exists() or (DOCS / rel / "index.md").exists()


def main():
    config = (DOCS / "_config.yml").read_text()
    sections = re.findall(r"^  - (\S.*)$", config.split("wiki_sections:")[1].split("\n\n")[0], re.M)
    errors, count = [], 0
    for md in sorted(DOCS.rglob("*.md")):
        rel = md.relative_to(DOCS)
        text = md.read_text()
        count += 1
        fm = front_matter(text)
        if fm is None:
            errors.append(f"{rel}: no front matter")
            continue
        for key in ("title", "section", "order"):
            if not fm.get(key):
                errors.append(f"{rel}: front matter lacks {key}")
        if fm.get("section") and fm["section"] not in sections:
            errors.append(f"{rel}: section {fm['section']!r} is not one of {sections}")
        if fm.get("order") and not re.fullmatch(r"-?\d+", fm["order"]):
            errors.append(f"{rel}: order {fm['order']!r} is not a number")
        for pat in FORBIDDEN:
            if re.search(pat, text, re.I):
                errors.append(f"{rel}: contains forbidden text /{pat}/")
        for m in LINK.finditer(text):
            url = m.group(1) or m.group(2)
            if url.startswith("//"):
                continue
            if url.startswith("/assets/"):
                if not (DOCS / url.lstrip("/")).exists():
                    errors.append(f"{rel}: missing file {url}")
            elif not page_exists(url):
                errors.append(f"{rel}: link to no page {url}")
    for e in errors:
        print(e)
    print(f"{count} pages, {len(errors)} problems")
    sys.exit(1 if errors else 0)


if __name__ == "__main__":
    main()
