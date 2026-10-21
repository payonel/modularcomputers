#!/usr/bin/env python3
# Draws the broken case found in the ancient sites, from the tier 1 case's textures (needs Pillow):
# cracked glass, missing keys, grime and rust, an empty board behind the back window. The PNGs
# are committed; rerun after all_in_one_textures.py, then check the preview.
#   scripts/art/ancient_site_textures.py common/src/main/resources/assets/modularcomputers/textures/block preview.png
import random, sys
from PIL import Image

TEX = sys.argv[1]
CRACK = (182, 190, 196)
GRIME = [(118, 104, 74), (104, 92, 66), (132, 118, 86)]
RUST = (126, 74, 40)

def src(name):
	return Image.open(f"{TEX}/{name}.png").convert("RGB")

def grime(im, seed, count):
	rnd = random.Random(seed)
	for _ in range(count):
		x, y = rnd.randrange(1, 15), rnd.randrange(1, 15)
		im.putpixel((x, y), rnd.choice(GRIME))
	return im

def front():
	im = src("all_in_one_front")
	# A crack across the glass, from a point of impact.
	for x, y in ((5, 4), (6, 5), (7, 5), (8, 6), (9, 6), (10, 7), (11, 8), (6, 3), (6, 2), (4, 5), (3, 6), (7, 6), (7, 7), (7, 8), (8, 9), (5, 4)):
		im.putpixel((x, y), CRACK)
	im.putpixel((6, 4), (230, 234, 238))
	# Keys gone.
	for x, y in ((3, 12), (7, 13), (10, 12), (5, 14)):
		im.putpixel((x, y), (30, 28, 24))
	im.putpixel((13, 11), (40, 30, 28))  # a dead LED
	return grime(im, 41, 14)

def side():
	im = grime(src("all_in_one_side"), 42, 18)
	for x, y in ((3, 2), (4, 3), (4, 4), (5, 5), (5, 6), (6, 7)):
		im.putpixel((x, y), (120, 110, 84))   # a split in the plastic
	im.putpixel((12, 13), RUST); im.putpixel((13, 13), RUST); im.putpixel((12, 14), RUST)
	return im

def top():
	im = grime(src("all_in_one_top"), 43, 22)
	rnd = random.Random(44)
	for _ in range(6):
		im.putpixel((rnd.randrange(1, 15), rnd.randrange(1, 15)), (88, 110, 60))  # moss
	return im

def back():
	im = src("all_in_one_back")
	# The board's gone: just its empty bay, and a rusted power button housing.
	for y in range(3, 7):
		for x in range(4, 12):
			im.putpixel((x, y), (36, 34, 30))
	for x, y in ((9, 9), (11, 10), (10, 12), (12, 12)):
		im.putpixel((x, y), RUST)
	return grime(im, 45, 12)

def inside():
	"""What's inside, seen past the lifted lid: the board, its chips long gone, dusty."""
	rnd = random.Random(46)
	im = Image.new("RGB", (16, 16))
	for y in range(16):
		for x in range(16):
			im.putpixel((x, y), rnd.choice([(36, 92, 54), (32, 84, 50), (40, 98, 58)]))
	for x in range(2, 14):
		im.putpixel((x, 4), (168, 140, 64))   # copper traces
		im.putpixel((x, 11), (168, 140, 64))
	for y in range(4, 12):
		im.putpixel((7, y), (168, 140, 64))
	for x0, y0 in ((3, 6), (9, 6), (3, 13)):   # empty sockets where chips were
		for y in range(y0, y0 + 3):
			for x in range(x0, x0 + 3):
				im.putpixel((x, y), (24, 24, 26))
		im.putpixel((x0 + 1, y0 + 1), (60, 60, 64))
	im.putpixel((10, 13), (196, 200, 206)); im.putpixel((11, 13), (196, 200, 206))  # a last RAM stick, bent
	return grime(im, 47, 16)

made = {"broken_case_inside": inside(), "broken_case_front": front(), "broken_case_side": side(), "broken_case_top": top(), "broken_case_back": back()}
for name, im in made.items():
	im.save(f"{TEX}/{name}.png")
sheet = Image.new("RGB", (20 * len(made) + 4, 24), (40, 40, 40))
for i, im in enumerate(made.values()):
	sheet.paste(im, (4 + i * 20, 4))
sheet.resize((sheet.width * 8, sheet.height * 8), Image.NEAREST).save(sys.argv[2])
