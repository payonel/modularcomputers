#!/usr/bin/env python3
# Draws the all-in-one computer's 16x16 block textures and the GUI power button (needs Pillow).
# The PNGs are committed; rerun after editing this to regenerate them, then check the preview.
#   scripts/art/all_in_one_textures.py common/src/main/resources/assets/modularcomputers/textures/block preview.png
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
	# Glass: unlit, its built-in screen's (tier 3's, as ColorDepth's). Display text is drawn over x 2..13, y 2..9.
	rect(im, 2, 2, 13, 9, (17, 19, 21))
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

def case_front():
	"""A case's front with no screen in it: a blank panel where the screen would go, two drive
	bay slots and vents below, and the power LED in the same place as on the all-in-one."""
	im = casing(2)
	rect(im, 2, 2, 13, 9, SHADE)  # the blank where a screen goes
	rect(im, 2, 2, 13, 2, DARK); rect(im, 2, 2, 2, 9, DARK)
	rect(im, 3, 9, 13, 9, LIGHT); rect(im, 13, 3, 13, 9, LIGHT)
	for y in (11, 13):  # drive bay slots
		rect(im, 2, y, 8, y, (66, 64, 59))
	for x in range(10, 14, 2):  # vents
		rect(im, x, 11, x, 14, DARK)
	im.putpixel((13, 11), (74, 44, 40))
	return im

def case_front_plain():
	"""A tier 1 case's front: it can't take a screen, so no recess for one, just the drive bays,
	vents and the power LED where the others have them, and a plain panel above."""
	im = casing(3)
	for y in (11, 13):  # drive bay slots
		rect(im, 2, y, 8, y, (66, 64, 59))
	for x in range(10, 14, 2):  # vents
		rect(im, x, 11, x, 14, DARK)
	for y in (3, 5, 7):  # a few more vents up top
		rect(im, 4, y, 11, y, SHADE)
	im.putpixel((13, 11), (74, 44, 40))
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
	# On, its CPU halted (the OS called halt): amber, like a standby light.
	"halted": [(255, 236, 150), (236, 190, 40), (176, 132, 20)],
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

GUI_STATES = ("off", "on", "off_faded", "on_faded", "halted")

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

def cpu_item(lid=(168, 172, 180, 255), lid_light=(206, 210, 216, 255), lid_dark=(120, 124, 132, 255)):
	"""A square chip: dark package with a metal lid, gold pins on all four sides. The lid tells the tier."""
	im = item_canvas()
	pin, pin_dark = (222, 182, 70, 255), (170, 132, 40, 255)
	for i in range(3, 13, 2):
		for x, y in [(i, 1), (i, 2), (i, 13), (i, 14), (1, i), (2, i), (13, i), (14, i)]:
			im.putpixel((x, y), pin if (x + y) % 4 else pin_dark)
	rect(im, 3, 3, 12, 12, (44, 46, 52, 255))  # package
	rect(im, 5, 5, 10, 10, lid)  # lid
	rect(im, 5, 5, 10, 5, lid_light)
	rect(im, 5, 5, 5, 10, lid_light)
	rect(im, 6, 10, 10, 10, lid_dark)
	rect(im, 10, 6, 10, 10, lid_dark)
	im.putpixel((4, 4), (222, 182, 70, 255))  # pin-1 mark
	return im

def tier1_cpu_item():
	"""An iron lid."""
	return cpu_item()

def tier2_cpu_item():
	"""A gold lid."""
	return cpu_item((214, 170, 40, 255), (250, 222, 100, 255), (160, 120, 30, 255))

def tier3_cpu_item():
	"""A diamond lid."""
	return cpu_item((70, 200, 200, 255), (160, 240, 236, 255), (40, 140, 140, 255))

FONT_3X3 = {
	"J": ["..x", "x.x", ".x."],
	"A": [".x.", "xxx", "x.x"],
	"V": ["x.x", "x.x", ".x."],
}

def java_cpu_item():
	"""The Java CPU (creative only): no lid, its package printed JAVA, in two lines."""
	im = cpu_item()
	rect(im, 4, 4, 11, 11, (44, 46, 52, 255))  # no lid: the package shows
	ink = (236, 150, 60, 255)
	for word, y in (("JA", 4), ("VA", 8)):
		for i, letter in enumerate(word):
			for dy, line in enumerate(FONT_3X3[letter]):
				for dx, c in enumerate(line):
					if c == "x":
						im.putpixel((4 + i * 5 + dx, y + dy), ink)
	return im

def java_eeprom_item(writing=(220, 120, 40, 255)):
	"""The Java EEPROM (creative only): the EEPROM, its label written in orange."""
	return eeprom_item(writing)

def eeprom_item(writing=(90, 110, 170, 255)):
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
	rect(im, 5, 7, 9, 7, writing)  # writing
	rect(im, 5, 8, 7, 8, writing)
	return im

def ram_item(tier=1):
	"""A memory stick: green board, black chips, gold contacts along the bottom; a tier mark on the chips."""
	im = item_canvas()
	rect(im, 0, 5, 15, 11, (40, 120, 66, 255))
	rect(im, 0, 5, 15, 5, (60, 150, 86, 255))
	for x in (1, 5, 9, 13):
		rect(im, x, 6, x + 2, 9, (30, 30, 34, 255))
	for x in range(1, 15, 2):
		im.putpixel((x, 11), (222, 182, 70, 255))
	im.putpixel((7, 11), (40, 120, 66, 255))  # key notch
	mark = [(196, 200, 206, 255), (250, 222, 100, 255), (160, 240, 236, 255)][tier - 1]
	for x in (1, 5, 9, 13)[:tier + 1]:
		im.putpixel((x + 1, 7), mark)
	return im

def hard_disk_item(tier=1):
	"""A drive: metal case, label, connector, activity light. The label stripe tells the tier."""
	im = item_canvas()
	rect(im, 1, 3, 14, 12, (150, 154, 162, 255))
	rect(im, 1, 3, 14, 3, (196, 200, 208, 255))
	rect(im, 1, 12, 14, 12, (100, 104, 112, 255))
	rect(im, 3, 5, 9, 9, (236, 232, 214, 255))  # label
	rect(im, 4, 6, 8, 6, [(196, 200, 206, 255), (214, 170, 40, 255), (70, 200, 200, 255)][tier - 1])
	rect(im, 11, 10, 13, 11, (40, 40, 46, 255))  # connector
	im.putpixel((12, 5), (90, 230, 110, 255))
	return im

def wlan_card_item(tier):
	"""An expansion card: green board, metal bracket, gold fingers, and an antenna: tier 1's short
	(16 blocks, as OpenComputers'), tier 2's long, with a gold stripe on its chip (400)."""
	im = item_canvas()
	rect(im, 2, 6, 12, 11, (46, 116, 64, 255))
	rect(im, 13, 4, 13, 13, (190, 194, 202, 255))  # bracket
	top = 4 if tier == 1 else 0
	rect(im, 14, top + 1, 14, 6, (40, 40, 46, 255))  # antenna
	im.putpixel((14, top), (40, 40, 46, 255))
	rect(im, 4, 7, 7, 9, (30, 30, 34, 255))  # chip
	if tier == 2:
		rect(im, 4, 8, 7, 8, (214, 170, 40, 255))
	for x in range(3, 12, 2):
		im.putpixel((x, 12), (222, 182, 70, 255))
	return im

def card_base():
	"""What every expansion card shares: green board, metal bracket, gold fingers."""
	im = item_canvas()
	rect(im, 2, 6, 12, 11, (46, 116, 64, 255))
	rect(im, 13, 4, 13, 13, (190, 194, 202, 255))  # bracket
	for x in range(3, 12, 2):
		im.putpixel((x, 12), (222, 182, 70, 255))
	return im

def internet_card_item():
	"""A network chip, and a little globe on the bracket: blue sea, green land (the internet)."""
	im = card_base()
	rect(im, 4, 7, 7, 9, (30, 30, 34, 255))
	for (x, y) in ((14, 4), (15, 4), (13, 5), (14, 5), (15, 5), (13, 6), (14, 6), (15, 6), (14, 7), (15, 7)):
		im.putpixel((x, y), (60, 120, 220, 255))
	for (x, y) in ((14, 5), (13, 6), (15, 7)):
		im.putpixel((x, y), (80, 190, 90, 255))
	return im

def operator_card_item():
	"""A command block's look on the bracket (orange, its arrow), and a gold chip: it runs the server's commands."""
	im = card_base()
	rect(im, 4, 7, 7, 9, (214, 170, 40, 255))  # a gold chip
	rect(im, 13, 4, 15, 8, (186, 112, 58, 255))  # the command block
	im.putpixel((14, 5), (240, 220, 190, 255))
	im.putpixel((14, 6), (240, 220, 190, 255))
	im.putpixel((15, 6), (240, 220, 190, 255))
	return im

def network_card_item():
	"""A network chip and an Ethernet jack (with its link light) on the bracket."""
	im = card_base()
	rect(im, 4, 7, 7, 9, (30, 30, 34, 255))
	im.putpixel((5, 8), (90, 200, 220, 255))
	rect(im, 14, 6, 15, 10, (40, 40, 46, 255))  # the jack
	for y in (7, 8, 9):
		im.putpixel((15, y), (222, 182, 70, 255))  # its contacts
	im.putpixel((14, 5), (90, 230, 110, 255))  # link light
	return im

def redstone_card_item():
	"""Red: a redstone chip and a little torch on the bracket."""
	im = card_base()
	rect(im, 4, 7, 7, 9, (150, 20, 16, 255))
	im.putpixel((5, 8), (255, 70, 50, 255))
	rect(im, 14, 5, 14, 8, (120, 84, 50, 255))  # torch stick
	im.putpixel((14, 4), (255, 60, 40, 255))
	return im

def sound_card_item():
	"""Three colored jacks on the bracket, like a sound card."""
	im = card_base()
	rect(im, 4, 7, 7, 9, (30, 30, 34, 255))
	for y, c in ((5, (90, 200, 90, 255)), (8, (230, 120, 170, 255)), (11, (90, 140, 230, 255))):
		im.putpixel((14, y), c)
		im.putpixel((15, y), (30, 30, 34, 255))
	return im

def motion_sensor_card_item():
	"""A white dome (a PIR sensor) on the board."""
	im = card_base()
	rect(im, 5, 6, 8, 9, (238, 238, 232, 255))
	rect(im, 6, 5, 7, 5, (238, 238, 232, 255))
	im.putpixel((6, 6), (255, 255, 255, 255))
	rect(im, 5, 9, 8, 9, (200, 200, 194, 255))
	return im

def audio_sensor_card_item():
	"""A microphone grille: a dark disc with holes."""
	im = card_base()
	rect(im, 4, 6, 8, 10, (60, 62, 68, 255))
	for x, y in ((5, 7), (7, 7), (6, 8), (5, 9), (7, 9)):
		im.putpixel((x, y), (20, 20, 22, 255))
	return im

def weather_sensor_card_item():
	"""A thermometer and a sky-blue sensor on the board."""
	im = card_base()
	rect(im, 4, 5, 4, 10, (230, 230, 230, 255))
	rect(im, 4, 8, 4, 10, (220, 50, 40, 255))
	rect(im, 7, 7, 10, 9, (120, 190, 240, 255))
	im.putpixel((8, 7), (240, 250, 255, 255))
	return im

def crafting_card_item():
	"""A crafting table's top on the board (planks, its grid), and a chest's latch on the bracket: its grid and its storage."""
	im = card_base()
	rect(im, 3, 6, 9, 10, (160, 118, 68, 255))  # the table's top
	for x in (5, 7):
		rect(im, x, 6, x, 10, (110, 78, 42, 255))  # its grid
	for y in (7, 9):
		rect(im, 3, y, 9, y, (110, 78, 42, 255))
	rect(im, 14, 6, 15, 9, (150, 104, 52, 255))  # the chest
	im.putpixel((14, 7), (200, 200, 206, 255))  # its latch
	return im

def bluetooth_card_item():
	"""A blue radio chip and a short antenna stub."""
	im = card_base()
	rect(im, 4, 7, 7, 9, (40, 80, 200, 255))
	im.putpixel((5, 8), (150, 190, 255, 255))
	rect(im, 14, 3, 14, 5, (40, 80, 200, 255))
	return im

def battery_item(cap, cap_light, body, body_light, stripe):
	"""A battery pack: a body, a metal cap with two terminals, a charge stripe."""
	im = item_canvas()
	rect(im, 2, 4, 13, 13, body)
	rect(im, 2, 13, 13, 13, body_light)
	rect(im, 2, 4, 13, 5, cap)
	rect(im, 2, 4, 13, 4, cap_light)
	rect(im, 4, 2, 5, 3, (196, 60, 50, 255))  # + terminal
	rect(im, 10, 2, 11, 3, (40, 40, 46, 255))  # - terminal
	rect(im, 4, 8, 11, 9, stripe)
	return im

def tier1_battery_item():
	"""Copper cap on a dark iron body."""
	return battery_item((180, 100, 60, 255), (226, 150, 100, 255), (66, 70, 78, 255), (84, 88, 98, 255), (90, 230, 110, 255))

def tier2_battery_item():
	"""Gold cap, a blaze-orange stripe."""
	return battery_item((214, 170, 40, 255), (250, 222, 100, 255), (66, 70, 78, 255), (84, 88, 98, 255), (255, 170, 40, 255))

def tier3_battery_item():
	"""Diamond cap on a netherite body, a star-white core with ender-green ends."""
	im = battery_item((70, 200, 200, 255), (160, 240, 236, 255), (58, 50, 54, 255), (78, 68, 72, 255), (236, 240, 255, 255))
	im.putpixel((4, 8), (60, 160, 120, 255))
	im.putpixel((11, 9), (60, 160, 120, 255))
	return im

for name, fn in [("front", front), ("case_front", case_front), ("case_front_plain", case_front_plain), ("side", side), ("top", top), ("back", back), ("bottom", bottom)]:
	fn().save(f"{OUT}/all_in_one_{name}.png")
for state in LIT:
	power(state).save(f"{OUT}/all_in_one_power_{state}.png")
gui_button().save(f"{OUT}/../gui/power_button.png")
case_button().save(f"{OUT}/../gui/case_button.png")
for name, fn in [("tier1_cpu", tier1_cpu_item), ("tier2_cpu", tier2_cpu_item), ("tier3_cpu", tier3_cpu_item), ("eeprom", eeprom_item),
		("java_cpu", java_cpu_item), ("java_eeprom", java_eeprom_item),
		("tier1_ram", lambda: ram_item(1)), ("tier2_ram", lambda: ram_item(2)), ("tier3_ram", lambda: ram_item(3)),
		("tier1_hard_disk", lambda: hard_disk_item(1)), ("tier2_hard_disk", lambda: hard_disk_item(2)), ("tier3_hard_disk", lambda: hard_disk_item(3)),
		("tier1_wlan_card", lambda: wlan_card_item(1)), ("tier2_wlan_card", lambda: wlan_card_item(2)), ("network_card", network_card_item), ("tier1_battery", tier1_battery_item), ("tier2_battery", tier2_battery_item), ("tier3_battery", tier3_battery_item), ("redstone_card", redstone_card_item),
		("sound_card", sound_card_item), ("internet_card", internet_card_item), ("operator_card", operator_card_item), ("motion_sensor_card", motion_sensor_card_item), ("audio_sensor_card", audio_sensor_card_item),
		("weather_sensor_card", weather_sensor_card_item), ("bluetooth_card", bluetooth_card_item), ("crafting_card", crafting_card_item)]:
	fn().save(f"{OUT}/../item/{name}.png")

# Preview sheet, scaled up, for review.
sheet = Image.new("RGB", (16 * 5 + 4 * 6, 24), (40, 40, 40))
for i, name in enumerate(["front", "side", "top", "back", "bottom"]):
	sheet.paste(Image.open(f"{OUT}/all_in_one_{name}.png"), (4 + i * 20, 4))
sheet.resize((sheet.width * 8, sheet.height * 8), Image.NEAREST).save(sys.argv[2])
