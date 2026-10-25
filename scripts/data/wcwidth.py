#!/usr/bin/env python3
# Writes the character width table the terminals and mcos share (wcwidth, as glibc's): how many cells
# a code point takes. 0: combining marks and format characters (Unicode's Mn, Me, Cf; the soft
# hyphen shows, as glibc has it) and Hangul's medial and final jamo, which join the letter before;
# 2: East Asian Wide and Fullwidth (CJK, most emoji); everything else 1. From the Python's own
# Unicode data (its version is the table's, written at its top). Lines are ranges, hex, that aren't
# width 1: "first last width". Rerun after a Python with newer Unicode:
#   scripts/data/wcwidth.py common/src/main/resources/data/modularcomputers/lua/mcos/usr/share/wcwidth
import sys, unicodedata

OUT = sys.argv[1]
LAST = 0x10FFFF
SOFT_HYPHEN = 0x00AD
HANGUL_JAMO_MEDIAL = range(0x1160, 0x1200)  # Jungseong and Jongseong: they join the leading consonant

def width(cp):
	if 0xD800 <= cp <= 0xDFFF:
		return 1  # (surrogates: not characters)
	c = chr(cp)
	category = unicodedata.category(c)
	if cp != SOFT_HYPHEN and category in ("Mn", "Me", "Cf") or cp in HANGUL_JAMO_MEDIAL:
		return 0
	if unicodedata.east_asian_width(c) in ("W", "F"):
		return 2
	return 1

ranges = []
for cp in range(0x20, LAST + 1):
	w = width(cp)
	if w == 1:
		continue
	if ranges and ranges[-1][1] == cp - 1 and ranges[-1][2] == w:
		ranges[-1][1] = cp
	else:
		ranges.append([cp, cp, w])

with open(OUT, "w") as f:
	f.write(f"# wcwidth: Unicode {unicodedata.unidata_version}; first last width (hex), every other code point 1 (scripts/data/wcwidth.py)\n")
	for first, last, w in ranges:
		f.write(f"{first:x} {last:x} {w}\n")
print(len(ranges), "ranges")
