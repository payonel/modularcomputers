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
	# No CPU: the same colors, faded and not glowing (the model drops the emission too).
	"off_faded": [(150, 104, 98), (124, 72, 66), (100, 58, 54)],
	"on_faded": [(118, 150, 114), (84, 124, 84), (64, 100, 66)],
}

def button_face(state):
	"""3x3 lit button: highlight top-left, shadow bottom-right."""
	hl, face, dark = LIT[state]
	return [[hl, face, face], [face, face, face], [face, face, dark]]

# Motherboard colors, shared by the back's case window and the GUI case button.
PCB = (46, 116, 64)
PCB_DARK = (32, 88, 48)
TRACE = (196, 160, 72)
SOCKET = (40, 40, 46)
SOCKET_LID = (150, 154, 162)
RAM_SLOT = (214, 212, 200)

# Case window: an opening into the case at the top of the back, showing the motherboard.
# Clicking it opens the case view. Keep in sync with BackPanel.java.
WINDOW = (3, 2, 12, 7)  # x0, y0, x1, y1 texels, inclusive (frame included)

def back():
	im = casing(4)
	x0, y0, x1, y1 = WINDOW
	# Recessed frame: deep shadow top/left, the case's lit edge bottom/right.
	rect(im, x0, y0, x1, y0, (34, 32, 28))
	rect(im, x0, y0, x0, y1, (34, 32, 28))
	rect(im, x0 + 1, y1, x1, y1, LIGHT)
	rect(im, x1, y0 + 1, x1, y1, LIGHT)
	# The board, in shade near the top edge.
	rect(im, x0 + 1, y0 + 1, x1 - 1, y1 - 1, PCB)
	rect(im, x0 + 1, y0 + 1, x1 - 1, y0 + 1, PCB_DARK)
	rect(im, x0 + 2, y0 + 2, x0 + 3, y0 + 3, SOCKET)  # CPU socket
	im.putpixel((x0 + 2, y0 + 2), SOCKET_LID)
	rect(im, x0 + 6, y0 + 1, x0 + 6, y1 - 1, RAM_SLOT)  # RAM slots
	rect(im, x0 + 8, y0 + 1, x0 + 8, y1 - 1, RAM_SLOT)
	rect(im, x0 + 1, y1 - 1, x0 + 4, y1 - 1, TRACE)  # traces
	im.putpixel((x0 + 4, y0 + 2), TRACE)
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

GUI_STATES = ("off", "on", "off_faded", "on_faded")

def gui_button():
	"""GUI power button, one 5x5 frame per state in GUI_STATES: housing ring plus the same face."""
	im = Image.new("RGBA", (5 * len(GUI_STATES), 5), RING + (255,))
	for i, state in enumerate(GUI_STATES):
		for dy, row in enumerate(button_face(state)):
			for dx, c in enumerate(row):
				im.putpixel((i * 5 + 1 + dx, 1 + dy), c + (255,))
	return im

def bottom():
	return casing(5, [(178, 169, 141), (171, 162, 134), (184, 175, 148), (166, 157, 130)])

def case_button():
	"""GUI case-view button: the same motherboard as the back's window, at 16x16."""
	im = Image.new("RGBA", (16, 16), PCB + (255,))
	px = lambda x, y, c: im.putpixel((x, y), c + (255,))
	box = lambda x0, y0, x1, y1, c: [px(x, y, c) for x in range(x0, x1 + 1) for y in range(y0, y1 + 1)]
	box(0, 0, 15, 0, PCB_DARK); box(0, 0, 0, 15, PCB_DARK)
	box(0, 15, 15, 15, PCB_DARK); box(15, 0, 15, 15, PCB_DARK)
	box(2, 2, 7, 7, SOCKET)  # CPU socket with its lid
	box(3, 3, 6, 6, SOCKET_LID)
	box(3, 3, 6, 3, (196, 200, 206))
	for x in (10, 12):  # RAM slots
		box(x, 2, x, 13, RAM_SLOT)
	box(2, 10, 7, 10, TRACE)  # traces
	box(7, 8, 7, 10, TRACE)
	box(2, 12, 5, 12, TRACE)
	box(2, 13, 3, 13, (60, 60, 64))  # a chip
	px(14, 14, TRACE)
	return im

def item_canvas():
	return Image.new("RGBA", (16, 16), (0, 0, 0, 0))

def cpu_item():
	"""A square chip: dark package with a metal lid, gold pins on all four sides."""
	im = item_canvas()
	pin, pin_dark = (222, 182, 70, 255), (170, 132, 40, 255)
	for i in range(3, 13, 2):
		for x, y in [(i, 1), (i, 2), (i, 13), (i, 14), (1, i), (2, i), (13, i), (14, i)]:
			im.putpixel((x, y), pin if (x + y) % 4 else pin_dark)
	rect(im, 3, 3, 12, 12, (44, 46, 52, 255))  # package
	rect(im, 5, 5, 10, 10, (168, 172, 180, 255))  # lid
	rect(im, 5, 5, 10, 5, (206, 210, 216, 255))
	rect(im, 5, 5, 5, 10, (206, 210, 216, 255))
	rect(im, 6, 10, 10, 10, (120, 124, 132, 255))
	rect(im, 10, 6, 10, 10, (120, 124, 132, 255))
	im.putpixel((4, 4), (222, 182, 70, 255))  # pin-1 mark
	return im

def eeprom_item():
	"""A small DIP chip: black body with silver legs and a paper label."""
	im = item_canvas()
	leg = (196, 200, 206, 255)
	for x in range(4, 12, 2):
		for y in (3, 12):
			im.putpixel((x, y), leg)
	rect(im, 2, 4, 13, 11, (30, 30, 34, 255))  # body
	rect(im, 2, 4, 13, 4, (56, 56, 62, 255))
	im.putpixel((2, 7), (12, 12, 14, 255))  # notch
	im.putpixel((2, 8), (12, 12, 14, 255))
	rect(im, 4, 6, 11, 9, (236, 232, 214, 255))  # label
	rect(im, 5, 7, 9, 7, (90, 110, 170, 255))  # writing
	rect(im, 5, 8, 7, 8, (90, 110, 170, 255))
	return im

def ram_item():
	"""A memory stick: green board, black chips, gold contacts along the bottom."""
	im = item_canvas()
	rect(im, 0, 5, 15, 11, (40, 120, 66, 255))
	rect(im, 0, 5, 15, 5, (60, 150, 86, 255))
	for x in (1, 5, 9, 13):
		rect(im, x, 6, x + 2, 9, (30, 30, 34, 255))
	for x in range(1, 15, 2):
		im.putpixel((x, 11), (222, 182, 70, 255))
	im.putpixel((7, 11), (40, 120, 66, 255))  # key notch
	return im

def hard_disk_item():
	"""A drive: metal case, label, connector, activity light."""
	im = item_canvas()
	rect(im, 1, 3, 14, 12, (150, 154, 162, 255))
	rect(im, 1, 3, 14, 3, (196, 200, 208, 255))
	rect(im, 1, 12, 14, 12, (100, 104, 112, 255))
	rect(im, 3, 5, 9, 9, (236, 232, 214, 255))  # label
	rect(im, 4, 6, 8, 6, (90, 110, 170, 255))
	rect(im, 11, 10, 13, 11, (40, 40, 46, 255))  # connector
	im.putpixel((12, 5), (90, 230, 110, 255))
	return im

def wlan_card_item():
	"""An expansion card: green board, metal bracket, antenna, gold fingers."""
	im = item_canvas()
	rect(im, 2, 6, 12, 11, (46, 116, 64, 255))
	rect(im, 13, 4, 13, 13, (190, 194, 202, 255))  # bracket
	rect(im, 14, 1, 14, 6, (40, 40, 46, 255))  # antenna
	im.putpixel((14, 0), (40, 40, 46, 255))
	rect(im, 4, 7, 7, 9, (30, 30, 34, 255))  # chip
	for x in range(3, 12, 2):
		im.putpixel((x, 12), (222, 182, 70, 255))
	return im

def ups_battery_item():
	"""A backup battery pack: dark block, two terminals, a charge stripe."""
	im = item_canvas()
	rect(im, 2, 4, 13, 13, (54, 58, 66, 255))
	rect(im, 2, 4, 13, 4, (84, 88, 98, 255))
	rect(im, 4, 2, 5, 3, (196, 60, 50, 255))  # + terminal
	rect(im, 10, 2, 11, 3, (40, 40, 46, 255))  # - terminal
	rect(im, 4, 8, 11, 9, (90, 230, 110, 255))  # charge stripe
	return im

for name, fn in [("front", front), ("side", side), ("top", top), ("back", back), ("bottom", bottom)]:
	fn().save(f"{OUT}/all_in_one_{name}.png")
for state in LIT:
	power(state).save(f"{OUT}/all_in_one_power_{state}.png")
gui_button().save(f"{OUT}/../gui/power_button.png")
case_button().save(f"{OUT}/../gui/case_button.png")
for name, fn in [("cpu", cpu_item), ("eeprom", eeprom_item), ("ram", ram_item), ("hard_disk", hard_disk_item),
		("wlan_card", wlan_card_item), ("ups_battery", ups_battery_item)]:
	fn().save(f"{OUT}/../item/{name}.png")

# Preview sheet, scaled up, for review.
sheet = Image.new("RGB", (16 * 5 + 4 * 6, 24), (40, 40, 40))
for i, name in enumerate(["front", "side", "top", "back", "bottom"]):
	sheet.paste(Image.open(f"{OUT}/all_in_one_{name}.png"), (4 + i * 20, 4))
sheet.resize((sheet.width * 8, sheet.height * 8), Image.NEAREST).save(sys.argv[2])
