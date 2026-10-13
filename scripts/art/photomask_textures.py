#!/usr/bin/env python3
# Draws the chip-making textures (needs Pillow): the silicon and chip items, the photomask
# block's faces, and its gear (drawn by PhotomaskRenderer, turning as it's cranked). The PNGs are
# committed; rerun after editing this to regenerate them, then check the preview.
#   scripts/art/photomask_textures.py src/main/resources/assets/modularcomputers/textures preview.png
import math, random, sys
from PIL import Image

OUT = sys.argv[1]
CLEAR = (0, 0, 0, 0)

def rect(im, x0, y0, x1, y1, c):
	for y in range(y0, y1 + 1):
		for x in range(x0, x1 + 1):
			im.putpixel((x, y), c)

def silicon():
	"""A polished silicon ingot: blue-gray, with a mirror sheen, as refined silicon looks."""
	im = Image.new("RGBA", (16, 16), CLEAR)
	body = [(98, 108, 128, 255), (110, 121, 142, 255), (90, 99, 118, 255)]
	rnd = random.Random(3)
	for y in range(5, 12):
		for x in range(2 + (11 - y) // 2, 14 - (y - 5) // 2):
			im.putpixel((x, y), rnd.choice(body))
	rect(im, 5, 5, 12, 5, (170, 182, 204, 255))      # top edge, lit
	for x in range(6, 10):
		im.putpixel((x, 7), (205, 215, 232, 255))    # sheen
	rect(im, 3, 11, 12, 11, (60, 66, 80, 255))      # shadowed base
	return im

def chip():
	"""A die in its package: a black chip with silver pins along two sides and a dot for pin 1."""
	im = Image.new("RGBA", (16, 16), CLEAR)
	rect(im, 4, 3, 11, 12, (30, 30, 34, 255))
	rect(im, 5, 4, 10, 11, (42, 42, 48, 255))
	for y in range(4, 12, 2):
		im.putpixel((3, y), (196, 200, 206, 255)); im.putpixel((2, y), (150, 154, 160, 255))
		im.putpixel((12, y), (196, 200, 206, 255)); im.putpixel((13, y), (150, 154, 160, 255))
	im.putpixel((6, 5), (120, 124, 130, 255))       # pin 1
	return im

STEEL = [(122, 128, 136), (114, 120, 128), (130, 136, 144), (108, 114, 122)]

def casing(seed):
	rnd = random.Random(seed)
	im = Image.new("RGBA", (16, 16))
	for y in range(16):
		for x in range(16):
			im.putpixel((x, y), rnd.choices(STEEL, weights=[6, 3, 2, 1])[0] + (255,))
	for i in range(16):
		im.putpixel((i, 0), (162, 168, 176, 255)); im.putpixel((0, i), (162, 168, 176, 255))
		im.putpixel((i, 15), (78, 82, 90, 255)); im.putpixel((15, i), (78, 82, 90, 255))
	return im

def top():
	"""The mask itself: a glass plate with a chrome circuit pattern, held in a frame."""
	im = casing(31)
	rect(im, 2, 2, 13, 13, (60, 64, 72, 255))
	rect(im, 3, 3, 12, 12, (150, 196, 210, 255))    # glass
	chrome = (226, 230, 236, 255)
	for (x0, y0, x1, y1) in ((4, 4, 11, 4), (4, 4, 4, 9), (6, 6, 11, 6), (8, 6, 8, 11), (4, 11, 11, 11), (10, 8, 10, 11), (6, 8, 6, 9)):
		rect(im, x0, y0, x1, y1, chrome)
	return im

def front():
	"""The front, where the gear turns: a round recess for it, and a small output chute below."""
	im = casing(32)
	for y in range(16):
		for x in range(16):
			d = math.hypot(x - 7.5, y - 7.5)
			if d <= 5.6:
				im.putpixel((x, y), (40, 42, 48, 255))
			elif d <= 6.4:
				im.putpixel((x, y), (70, 74, 82, 255))
	rect(im, 6, 14, 9, 14, (30, 30, 34, 255))
	return im

def side():
	im = casing(33)
	for y in (4, 6, 8, 10):
		rect(im, 4, y, 11, y, (78, 82, 90, 255))
	return im

def bottom():
	return casing(34)

def gear():
	"""A brass gear: eight teeth, a ring, a hub with a hole. Transparent around it."""
	im = Image.new("RGBA", (16, 16), CLEAR)
	brass, dark, light = (196, 150, 64, 255), (140, 100, 36, 255), (232, 196, 110, 255)
	for y in range(16):
		for x in range(16):
			dx, dy = x - 7.5, y - 7.5
			d = math.hypot(dx, dy)
			a = (math.degrees(math.atan2(dy, dx)) + 360) % 45
			tooth = a < 22.5
			if d <= 1.2:
				continue                              # the axle hole
			if d <= 2.2:
				im.putpixel((x, y), dark)
			elif d <= 5.2:
				im.putpixel((x, y), light if dx + dy < -2 else brass)
			elif d <= 7.4 and tooth:
				im.putpixel((x, y), brass if dx + dy < 2 else dark)
	return im

items = {"silicon": silicon, "chip": chip}
blocks = {"photomask_top": top, "photomask_front": front, "photomask_side": side, "photomask_bottom": bottom, "photomask_gear": gear}
for name, fn in items.items():
	fn().save(f"{OUT}/item/{name}.png")
for name, fn in blocks.items():
	fn().save(f"{OUT}/block/{name}.png")

sheet = Image.new("RGBA", (20 * 7 + 4, 24), (40, 40, 40, 255))
for i, (folder, name) in enumerate([("item", n) for n in items] + [("block", n) for n in blocks]):
	sheet.alpha_composite(Image.open(f"{OUT}/{folder}/{name}.png").convert("RGBA"), (4 + i * 20, 4))
sheet.resize((sheet.width * 8, sheet.height * 8), Image.NEAREST).save(sys.argv[2])
