#!/usr/bin/env python3
"""Writes the manual's program pages (docs/programs/) from mcos's man pages.

The man pages (common/src/main/resources/data/modularcomputers/lua/mcos/usr/share/man/man1/*.1) are
the source: they use only the macros mcos's /bin/man supports (TH SH SS PP TP B I BR RB BI IB IR RI
br nf fi). The outputs are committed and never edited by hand; run this again after a page changes.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MAN = ROOT / "common/src/main/resources/data/modularcomputers/lua/mcos/usr/share/man/man1"
OUT = ROOT / "docs/programs"
# Where the first program page sorts in the sidebar (they're hidden there; the index lists them).
FIRST_ORDER = 10

FONT = re.compile(r"\\f([BIRP])")
SPECIAL = re.compile(r"([\\`*_<>|\[\]{}#])")


def unescape(s):
    return s.replace("\\(dq", '"').replace("\\-", "-").replace("\\e", "\\").replace("\\&", "")


def md_escape(s):
    return SPECIAL.sub(r"\\\1", s)


def wrap(font, s):
    """One piece of text in a roff font: bold (B), italic (I) or roman (R)."""
    if not s:
        return ""
    s = md_escape(s)
    lead = len(s) - len(s.lstrip())
    trail = len(s) - len(s.rstrip())
    core = s.strip()
    if not core:
        return s
    tag = {"B": "strong", "I": "em"}.get(font)
    if tag is None:
        return s
    # HTML tags, not * marks: kramdown can't read adjacent emphasis (*host***:***path*) reliably
    return s[:lead] + f"<{tag}>{core}</{tag}>" + (s[len(s) - trail:] if trail else "")


def inline(text):
    """Text with \\fB \\fI \\fR escapes, as Markdown."""
    text = unescape(text)
    out, font, pos = [], "R", 0
    for m in FONT.finditer(text):
        out.append(wrap(font, text[pos:m.start()]))
        font = "R" if m.group(1) in "RP" else m.group(1)
        pos = m.end()
    out.append(wrap(font, text[pos:]))
    return "".join(out)


def split_args(rest):
    """A macro line's arguments: words, or "quoted phrases"."""
    return [a[0] or a[1] for a in re.findall(r'"([^"]*)"|(\S+)', rest)]


ALTERNATE = {"BR": "BR", "RB": "RB", "BI": "BI", "IB": "IB", "IR": "IR", "RI": "RI"}


def macro_text(name, args):
    """The text a font macro (.B, .I, .BR...) makes from its arguments."""
    if name in ("B", "I"):
        return wrap(name, unescape(" ".join(args)))
    fonts = name  # two letters, alternating, arguments joined with no space between them
    return "".join(wrap(fonts[i % 2], unescape(a)) for i, a in enumerate(args))


def convert(path):
    lines = path.read_text().split("\n")
    name, summary, section = path.stem, "", ""
    body, para, defs = [], [], None  # defs: None, or [tag, [text...]] for a pending .TP
    in_code = False
    tp_state = 0  # 0 none, 1 expecting the tag line, 2 collecting the definition
    synopsis = False

    def flush_para():
        nonlocal para
        if para:
            text = ""
            for part in para:  # a part ending in a backslash is a hard line break
                text += part + ("\n" if part.endswith("\\") else " ")
            body.append(text.rstrip() + "\n")
            para = []

    def flush_tp():
        nonlocal defs, tp_state
        if defs is not None:
            tag, text = defs
            body.append(tag + "\n: " + " ".join(text).strip() + "\n")
            defs, tp_state = None, 0

    for raw in lines:
        if in_code:
            if raw.startswith(".fi"):
                body.append("```\n")
                in_code = False
            else:
                body.append(unescape(raw))
            continue
        if raw.startswith(".TH") or raw.startswith('.\\"'):
            continue
        m = re.match(r"^\.([A-Za-z]+)\s*(.*)$", raw)
        if not m:
            text = inline(raw)
            if tp_state == 1:
                defs = [text, []]
                tp_state = 2
            elif tp_state == 2:
                defs[1].append(text)
            elif section == "NAME":
                n, _, s = unescape(raw).partition(" - ")
                name, summary = n.strip(), s.strip()
            elif synopsis:
                para.append(text + "\\")
            else:
                para.append(text)
            continue
        mac, rest = m.group(1), m.group(2)
        args = split_args(rest)
        if mac in ("SH", "SS"):
            flush_para(); flush_tp()
            title = unescape(" ".join(args))
            section = title if mac == "SH" else section
            synopsis = title == "SYNOPSIS"
            if title == "NAME":
                continue
            body.append(("## " if mac == "SH" else "### ") + title.capitalize() + "\n")
        elif mac == "PP":
            flush_para(); flush_tp()
        elif mac == "TP":
            flush_para(); flush_tp()
            tp_state = 1
        elif mac == "br":
            if para:
                para[-1] += "\\"
        elif mac == "nf":
            flush_para(); flush_tp()
            body.append("```text")
            in_code = True
        elif mac in ("B", "I") or mac in ALTERNATE:
            text = macro_text(mac, args)
            if tp_state == 1:
                defs = [text, []]
                tp_state = 2
            elif tp_state == 2:
                defs[1].append(text)
            elif synopsis:
                # a synopsis line is made of several macros: join them, break the line at the next .B
                if para and mac in ("B",) and not para[-1].endswith("\\"):
                    para[-1] += "\\"
                para.append(text)
            else:
                para.append(text)
    flush_para(); flush_tp()
    if synopsis:
        pass
    return name, summary, "\n".join(body)


def clean_synopsis(md):
    """Synopsis lines were joined with spaces: a trailing backslash is a kramdown line break."""
    return md


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    pages = sorted(MAN.glob("*.1"))
    index = []
    for i, p in enumerate(pages):
        name, summary, body = convert(p)
        if name != p.stem:
            sys.exit(f"{p.name}: NAME says {name!r}")
        text = (
            "---\n"
            f"title: {name}\n"
            f"description: {summary}\n"
            "section: Programs\n"
            f"order: {FIRST_ORDER + i}\n"
            "hidden: true\n"
            "---\n"
            "<!-- Generated by scripts/docs_programs.py from the man page; edit the man page. -->\n"
            "{% raw %}\n"
            f"`{name}`: {summary}.\n\n"
            f"{body}\n"
            "{% endraw %}\n"
        )
        (OUT / f"{name}.md").write_text(text)
        index.append((name, summary))
    rows = "\n".join(f"| [{n}](/programs/{n}/) | {s} |" for n, s in index)
    (OUT / "index.md").write_text(
        "---\n"
        "title: Programs\n"
        "section: Programs\n"
        "order: 0\n"
        "---\n"
        "<!-- Generated by scripts/docs_programs.py from the man pages; edit those. -->\n"
        "The programs in mcos's `/bin`. Each page is the program's man page: the same text "
        "`man <name>` shows in the game. See [the shell](/mcos/shell/) for how to run them.\n\n"
        "| Program | What it does |\n|---|---|\n" + rows + "\n"
    )
    print(f"{len(index)} programs")


if __name__ == "__main__":
    main()
