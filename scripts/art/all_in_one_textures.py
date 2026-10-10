#!/usr/bin/env python3
# Draws the all-in-one computer's 16x16 block textures and the GUI power button (needs Pillow).
# The PNGs are committed; rerun after editing this to regenerate them, then check the preview.
#   scripts/art/all_in_one_textures.py src/main/resources/assets/modularcomputers/textures/block preview.png
import random, sys
from PIL import Image

OUT = sys.argv[1]
BEIGE = [(206, 197, 168), (199, 190, 160), (212, 203, 175), (195, 186, 156)]
LIGHT = (226, 218, 192)
SHADE = (172, 163, 135)
DARK = (148, 139, 113)

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

def front():
	im = casing(1)
	# Recessed bezel: shadow on top/left, light on bottom/right.
	rect(im, 1, 1, 14, 1, DARK); rect(im, 1, 1, 1, 10, DARK)
	rect(im, 2, 10, 14, 10, LIGHT); rect(im, 14, 2, 14, 10, LIGHT)
	# Glass: black. Display text is drawn over x 2..13, y 2..9.
	rect(im, 2, 2, 13, 9, (10, 10, 12))
	# Power LED (off).
	im.putpixel((13, 11), (74, 44, 40))
	# Keyboard tray with keys.
	rect(im, 1, 12, 14, 14, (66, 64, 59))
	key, key_dark = (196, 191, 176), (168, 163, 149)
	for x in range(2, 14, 2):
		im.putpixel((x, 12), key)
	for x in range(3, 14, 2):
		im.putpixel((x, 13), key)
	im.putpixel((2, 14), key_dark); im.putpixel((13, 14), key_dark)
	rect(im, 4, 14, 11, 14, key)
	return im

def side():
	im = casing(2)
	rect(im, 1, 11, 14, 11, SHADE)  # seam where the keyboard deck meets the case
	return im

def top():
	return casing(3)

# Power button: a dark housing (5x5) at the back's bottom right, with a 3x3 lit button in it.
# The button itself is a separate emissive model element using all_in_one_power_{off,on}.
HOUSING = (9, 9)  # top-left texel; keep in sync with PowerButton.java and the block model
RING = (56, 56, 60)
LIT = {
	"off": [(255, 128, 110), (222, 44, 34), (168, 26, 20)],  # highlight, face, shadow
	"on": [(170, 255, 160), (64, 214, 64), (32, 150, 42)],
}

def button_face(state):
	"""3x3 lit button: highlight top-left, shadow bottom-right."""
	hl, face, dark = LIT[state]
	return [[hl, face, face], [face, face, face], [face, face, dark]]

def back():
	im = casing(4)
	for y in (3, 5, 7):  # vents
		rect(im, 3, y, 12, y, (92, 86, 70))
	rect(im, 2, 12, 4, 13, (60, 60, 64))  # port
	hx, hy = HOUSING
	rect(im, hx, hy, hx + 4, hy + 4, RING)
	return im

def power(state):
	"""Texture for the button element: the 3x3 face sits at the same texels as on the back."""
	im = Image.new("RGB", (16, 16), LIT[state][1])
	for dy, row in enumerate(button_face(state)):
		for dx, c in enumerate(row):
			im.putpixel((HOUSING[0] + 1 + dx, HOUSING[1] + 1 + dy), c)
	return im

def gui_button():
	"""GUI power button, off then on, 5x5 each: housing ring plus the same lit face."""
	im = Image.new("RGBA", (10, 5), RING + (255,))
	for i, state in enumerate(("off", "on")):
		for dy, row in enumerate(button_face(state)):
			for dx, c in enumerate(row):
				im.putpixel((i * 5 + 1 + dx, 1 + dy), c + (255,))
	return im

def bottom():
	return casing(5, [(178, 169, 141), (171, 162, 134), (184, 175, 148), (166, 157, 130)])

for name, fn in [("front", front), ("side", side), ("top", top), ("back", back), ("bottom", bottom)]:
	fn().save(f"{OUT}/all_in_one_{name}.png")
for state in LIT:
	power(state).save(f"{OUT}/all_in_one_power_{state}.png")
gui_button().save(f"{OUT}/../gui/power_button.png")

# Preview sheet, scaled up, for review.
sheet = Image.new("RGB", (16 * 5 + 4 * 6, 24), (40, 40, 40))
for i, name in enumerate(["front", "side", "top", "back", "bottom"]):
	sheet.paste(Image.open(f"{OUT}/all_in_one_{name}.png"), (4 + i * 20, 4))
sheet.resize((sheet.width * 8, sheet.height * 8), Image.NEAREST).save(sys.argv[2])
