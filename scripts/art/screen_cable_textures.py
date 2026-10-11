#!/usr/bin/env python3
# Draws the screen block's and the cable's 16x16 textures (needs Pillow), in the all-in-one's
# beige. The PNGs are committed; rerun after editing this to regenerate them, then check the preview.
#   scripts/art/screen_cable_textures.py src/main/resources/assets/modularcomputers/textures/block preview.png
import random, sys
from PIL import Image

OUT = sys.argv[1]
BEIGE = [(206, 197, 168), (199, 190, 160), (212, 203, 175), (195, 186, 156)]
LIGHT = (226, 218, 192)
SHADE = (172, 163, 135)
DARK = (148, 139, 113)
GLASS = (10, 10, 12)

def casing(seed, base=BEIGE):
	rnd = random.Random(seed)
	im = Image.new("RGB", (16, 16))
	for y in range(16):
		for x in range(16):
			im.putpixel((x, y), rnd.choices(base, weights=[6, 3, 2, 1])[0])
	for i in range(16):  # bevel: light top/left, dark bottom/right
		im.putpixel((i, 0), LIGHT); im.putpixel((0, i), LIGHT)
		im.putpixel((i, 15), SHADE); im.putpixel((15, i), SHADE)
	return im

def rect(im, x0, y0, x1, y1, c):
	for y in range(y0, y1 + 1):
		for x in range(x0, x1 + 1):
			im.putpixel((x, y), c)

def screen_front():
	"""A plain monitor face: one pane of glass, the same shape as the screen view (about 14:9),
	set into an even beige frame with a recessed edge. Nothing below it to read as a keyboard."""
	im = casing(11)
	# Glass: x 1..14, y 3..11; the display is drawn over it (DisplayRenderer.SCREEN).
	rect(im, 1, 3, 14, 11, GLASS)
	rect(im, 1, 2, 14, 2, DARK)    # recess: shadow above
	rect(im, 1, 12, 14, 12, LIGHT)  # and light below
	return im

def screen_side():
	return casing(12)

def screen_back():
	im = casing(13)
	# Vents, and the cable port in the middle.
	for y in (2, 4, 6):
		rect(im, 3, y, 12, y, DARK)
	rect(im, 6, 9, 9, 12, (40, 40, 46))
	rect(im, 7, 10, 8, 11, (90, 92, 100))
	return im

def screen_top():
	return casing(14)

def cable():
	# Dark rubber, a lighter line along it, and a little speckle. The model uses the middle
	# 4x4 for the core's faces and 4x6 strips for the arms.
	rnd = random.Random(15)
	im = Image.new("RGB", (16, 16))
	for y in range(16):
		for x in range(16):
			v = rnd.choice([44, 46, 48, 50])
			im.putpixel((x, y), (v, v, v + 4))
	for i in range(16):
		im.putpixel((i, 6), (30, 30, 34)); im.putpixel((i, 9), (30, 30, 34))
		im.putpixel((6, i), (30, 30, 34)); im.putpixel((9, i), (30, 30, 34))
		im.putpixel((i, 7), (78, 80, 88)); im.putpixel((7, i), (78, 80, 88))
	return im

textures = [("screen_front", screen_front), ("screen_side", screen_side), ("screen_back", screen_back),
	("screen_top", screen_top), ("cable", cable)]
for name, fn in textures:
	fn().save(f"{OUT}/{name}.png")

# Preview sheet, scaled up, for review.
sheet = Image.new("RGB", (20 * len(textures) + 4, 24), (40, 40, 40))
for i, (name, _) in enumerate(textures):
	sheet.paste(Image.open(f"{OUT}/{name}.png"), (4 + i * 20, 4))
sheet.resize((sheet.width * 8, sheet.height * 8), Image.NEAREST).save(sys.argv[2])
