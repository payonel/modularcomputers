#!/usr/bin/env python3
# Draws the 16x16 textures of the blocks on the cable network: the screen, the cable, the
# expansion hub and the robotic piston (needs Pillow), in the all-in-one's beige. Screens come
# in their tier's era, matching the cases (scripts/art/case_tier_textures.py; user's pick,
# 2026-10-12): an 80s beige terminal with green-phosphor glass, a 90s platinum color monitor
# with an RGB badge, a modern graphite flat panel. The PNGs are
# committed; rerun after editing this to regenerate them, then check the preview.
#   scripts/art/cabled_block_textures.py src/main/resources/assets/modularcomputers/textures/block preview.png
import random, sys
from PIL import Image

OUT = sys.argv[1]
BEIGE = [(206, 197, 168), (199, 190, 160), (212, 203, 175), (195, 186, 156)]
LIGHT = (226, 218, 192)
SHADE = (172, 163, 135)
DARK = (148, 139, 113)
GLASS = (10, 10, 12)
PHOSPHOR_GLASS = (6, 33, 15)  # a monochrome terminal's green glass, unlit

# Each tier's casing: noise colors, bevel light and shadow, recess shadow.
ERAS = {
	1: (BEIGE, LIGHT, SHADE, DARK),
	2: ([(201, 196, 184), (195, 190, 178), (207, 202, 190), (190, 185, 173)], (222, 218, 208), (165, 160, 149), (141, 136, 124)),
	3: ([(42, 44, 48), (38, 40, 44), (47, 49, 54), (35, 37, 41)], (64, 67, 73), (24, 25, 28), (20, 21, 24)),
}

def casing(seed, base=BEIGE, tier=1):
	if tier != 1:
		base = ERAS[tier][0]
	light, shade = ERAS[tier][1], ERAS[tier][2]
	rnd = random.Random(seed)
	im = Image.new("RGB", (16, 16))
	for y in range(16):
		for x in range(16):
			im.putpixel((x, y), rnd.choices(base, weights=[6, 3, 2, 1])[0])
	for i in range(16):  # bevel: light top/left, dark bottom/right
		im.putpixel((i, 0), light); im.putpixel((0, i), light)
		im.putpixel((i, 15), shade); im.putpixel((15, i), shade)
	return im

def rect(im, x0, y0, x1, y1, c):
	for y in range(y0, y1 + 1):
		for x in range(x0, x1 + 1):
			im.putpixel((x, y), c)

def screen_front(tier=1):
	"""A plain monitor face: one pane of glass, the same shape as the screen view (about 14:9),
	set into an even frame with a recessed edge. Nothing below it to read as a keyboard."""
	_, light, _, dark = ERAS[tier]
	im = casing(11, tier=tier)
	# Glass: x 1..14, y 3..11; the display is drawn over it (DisplayRenderer.SCREEN). Keep it there.
	rect(im, 1, 3, 14, 11, PHOSPHOR_GLASS if tier == 1 else GLASS)
	rect(im, 1, 2, 14, 2, dark)    # recess: shadow above
	rect(im, 1, 12, 14, 12, light)  # and light below
	return im

def tier_screen_front(tier):
	"""The screen face, with its tier as dots in the frame's bottom right corner: iron, gold,
	diamond, as on the CPUs' lids."""
	im = screen_front(tier)
	color = [(196, 200, 206), (230, 190, 60), (90, 220, 220)][tier - 1]
	for i in range(tier):
		im.putpixel((13 - 2 * i, 14), color)
	if tier == 2:  # a color monitor's RGB badge
		for i, c in enumerate(((229, 57, 53), (67, 160, 71), (30, 136, 229))):
			im.putpixel((2 + i, 14), c)
	return im

def screen_side(tier=1):
	return casing(12, tier=tier)

def screen_back(tier=1):
	im = casing(13, tier=tier)
	# Vents, and the cable port in the middle.
	for y in (2, 4, 6):
		rect(im, 3, y, 12, y, ERAS[tier][3])
	rect(im, 6, 9, 9, 12, (40, 40, 46))
	rect(im, 7, 10, 8, 11, (90, 92, 100))
	return im

def screen_top(tier=1):
	return casing(14, tier=tier)

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

def expansion_hub():
	"""Four card bays side by side, each with a card's metal bracket showing; the same on every
	side, since the hub takes a cable anywhere."""
	im = casing(16)
	for i in range(4):
		x = 2 + i * 3
		rect(im, x, 2, x + 2, 13, (40, 40, 46))      # bay
		rect(im, x + 1, 3, x + 1, 12, (176, 180, 190))  # bracket
		im.putpixel((x + 1, 6), (90, 92, 100))       # bracket screw slot
	return im

def robotic_piston_port():
	"""The port face: beige, with the cable socket."""
	im = casing(17)
	rect(im, 5, 5, 10, 10, (40, 40, 46))
	rect(im, 6, 6, 9, 9, (90, 92, 100))
	return im

def robotic_piston_plate():
	"""A pushing face: a steel plate with a dark rim and bolts in the corners."""
	rnd = random.Random(18)
	im = Image.new("RGB", (16, 16))
	for y in range(16):
		for x in range(16):
			v = rnd.choice([150, 154, 158, 162])
			im.putpixel((x, y), (v, v + 2, v + 6))
	for i in range(16):
		for x, y in ((i, 0), (0, i), (i, 15), (15, i)):
			im.putpixel((x, y), (70, 72, 78))
	for i in range(1, 15):
		im.putpixel((i, 1), (196, 198, 204)); im.putpixel((1, i), (196, 198, 204))
	for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
		im.putpixel((x, y), (90, 92, 100))
	return im

def paint_gun_nozzle():
	"""The nozzle face: a dark muzzle with dabs of paint around it."""
	im = casing(19)
	rect(im, 4, 4, 11, 11, (60, 62, 68))
	rect(im, 6, 6, 9, 9, (20, 20, 22))
	for (x, y), c in zip(((3, 3), (12, 4), (4, 12), (11, 12), (13, 9)),
			((220, 60, 60), (60, 120, 220), (240, 200, 50), (80, 190, 90), (200, 90, 200))):
		im.putpixel((x, y), c)
	return im

def paint_gun_side():
	"""The body: beige with three paint canisters showing through a window."""
	im = casing(20)
	rect(im, 3, 4, 12, 11, (40, 40, 46))
	for i, c in enumerate(((220, 60, 60), (60, 120, 220), (240, 200, 50))):
		rect(im, 4 + i * 3, 5, 5 + i * 3, 10, c)
	return im

textures = [(f"tier{t}_screen_{face}", (lambda t=t, fn=fn: fn(t))) for t in (1, 2, 3)
	for face, fn in (("front", tier_screen_front), ("side", screen_side), ("back", screen_back), ("top", screen_top))] + [
	("cable", cable), ("expansion_hub", expansion_hub),
	("robotic_piston_port", robotic_piston_port), ("robotic_piston_plate", robotic_piston_plate),
	("paint_gun_nozzle", paint_gun_nozzle), ("paint_gun_side", paint_gun_side)]
for name, fn in textures:
	fn().save(f"{OUT}/{name}.png")

# Preview sheet, scaled up, for review.
sheet = Image.new("RGB", (20 * len(textures) + 4, 24), (40, 40, 40))
for i, (name, _) in enumerate(textures):
	sheet.paste(Image.open(f"{OUT}/{name}.png"), (4 + i * 20, 4))
sheet.resize((sheet.width * 8, sheet.height * 8), Image.NEAREST).save(sys.argv[2])
