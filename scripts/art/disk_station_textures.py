#!/usr/bin/env python3
# Draws the Disk Station (needs Pillow): its faces in iron with emerald trim (the user's pick: iron
# and emerald, as its recipe), a chip board on top, its front with the two drive bays (the target's,
# with its activity light; the source's, with a lock: only read), a cable socket on its back, and the
# slot drawn on its eject face; and writes its block and item models and blockstate: the casing turned
# to face its placer, the slot put on whichever face ejects (a small part over that face, so one
# model serves every face). The PNGs and JSON are committed; rerun after editing this, then check
# the preview.
#   scripts/art/disk_station_textures.py common/src/main/resources/assets/modularcomputers preview.png
import json, random, sys
from PIL import Image

ASSETS = sys.argv[1]
PREVIEW = sys.argv[2] if len(sys.argv) > 2 else None

IRON = [(184, 186, 190), (176, 178, 183), (191, 193, 197), (170, 172, 177)]
IRON_LIGHT = (214, 216, 220)
IRON_SHADE = (128, 130, 136)
IRON_DARK = (96, 98, 104)
EMERALD = (23, 197, 96)
EMERALD_DARK = (12, 128, 62)
EMERALD_LIGHT = (130, 240, 172)
BAY = (34, 36, 40)
BAY_EDGE = (62, 64, 70)
SOCKET = (40, 40, 46)
PIN = (90, 92, 100)
BOARD = [(40, 104, 58), (42, 108, 60), (44, 112, 62)]  # the case view's green board
TRACE = (66, 146, 84)
GOLD = (204, 166, 74)
CHIP = (30, 30, 34)
CHIP_TOP = (46, 46, 52)
LED = (255, 176, 40)
LOCK = (204, 166, 74)

def rect(im, x0, y0, x1, y1, c):
	for y in range(y0, y1 + 1):
		for x in range(x0, x1 + 1):
			im.putpixel((x, y), c)

def iron(seed):
	"""Brushed iron, bevelled: light top and left, dark bottom and right."""
	rnd = random.Random(seed)
	im = Image.new("RGB", (16, 16))
	for y in range(16):
		row = rnd.choice(IRON)
		for x in range(16):
			im.putpixel((x, y), row if rnd.random() < 0.7 else rnd.choice(IRON))  # brushed: rows of one shade
	for i in range(16):
		im.putpixel((i, 0), IRON_LIGHT); im.putpixel((0, i), IRON_LIGHT)
		im.putpixel((i, 15), IRON_SHADE); im.putpixel((15, i), IRON_SHADE)
	return im

def emerald_trim(im, y):
	"""A strip of emerald inlay across the face."""
	rect(im, 1, y, 14, y, EMERALD)
	rect(im, 1, y + 1, 14, y + 1, EMERALD_DARK)
	im.putpixel((3, y), EMERALD_LIGHT); im.putpixel((10, y), EMERALD_LIGHT)

def bay(im, y):
	"""A drive bay: a dark recess with a lit edge below, a disk's width across."""
	rect(im, 2, y, 13, y + 3, BAY)
	rect(im, 2, y + 4, 13, y + 4, BAY_EDGE)
	rect(im, 3, y + 1, 12, y + 1, (20, 20, 24))  # the disk's mouth

def front():
	im = iron(41)
	emerald_trim(im, 1)
	bay(im, 4)          # the target's bay
	im.putpixel((12, 7), LED)  # its activity light
	bay(im, 10)         # the source's bay: only read
	rect(im, 11, 12, 12, 13, LOCK)  # its lock
	im.putpixel((11, 11), LOCK); im.putpixel((12, 11), LOCK)
	return im

def side():
	im = iron(42)
	emerald_trim(im, 7)
	return im

def back():
	im = iron(43)
	emerald_trim(im, 2)
	rect(im, 6, 8, 9, 11, SOCKET)  # the cable socket
	rect(im, 7, 9, 8, 10, PIN)
	return im

def top():
	"""Its chip board: green, gold traces, three chips in a row (its recipe's), in an iron frame."""
	rnd = random.Random(44)
	im = Image.new("RGB", (16, 16))
	for y in range(16):
		for x in range(16):
			im.putpixel((x, y), rnd.choice(BOARD))
	for i in range(16):
		for x, y in ((i, 0), (0, i), (i, 15), (15, i)):
			im.putpixel((x, y), IRON_SHADE)
	for x in range(2, 14):
		im.putpixel((x, 12), TRACE)
	for x in (3, 7, 11):
		rect(im, x, 4, x + 2, 9, CHIP)
		rect(im, x, 4, x + 2, 4, CHIP_TOP)
		for y in (5, 7, 9):
			im.putpixel((x - 1, y), GOLD); im.putpixel((x + 3, y), GOLD)
		im.putpixel((x + 1, 10), TRACE); im.putpixel((x + 1, 11), TRACE)
	im.putpixel((2, 2), GOLD); im.putpixel((13, 2), GOLD); im.putpixel((2, 13), GOLD); im.putpixel((13, 13), GOLD)
	return im

def bottom():
	return iron(45)

def slot():
	"""The eject slot: a dark slit with a lit lip, where disks come out (the model shows only its middle)."""
	im = Image.new("RGB", (16, 16), (20, 20, 24))
	rect(im, 0, 0, 15, 15, (20, 20, 24))
	rect(im, 4, 7, 11, 7, (8, 8, 10))
	rect(im, 4, 8, 11, 8, EMERALD_DARK)
	return im

TEXTURES = {"disk_station_front": front, "disk_station_side": side, "disk_station_back": back,
	"disk_station_top": top, "disk_station_bottom": bottom, "disk_station_slot": slot}
images = {}
for name, fn in TEXTURES.items():
	images[name] = fn()
	images[name].save(f"{ASSETS}/textures/block/{name}.png")

def tex(name):
	return f"modularcomputers:block/{name}"

def write(path, data):
	with open(f"{ASSETS}/{path}", "w") as f:
		json.dump(data, f, indent="\t")
		f.write("\n")

# The casing, its front to the north (turned by the blockstate).
write("models/block/disk_station.json", {
	"parent": "minecraft:block/cube",
	"textures": {"particle": tex("disk_station_side"), "north": tex("disk_station_front"), "south": tex("disk_station_back"),
		"east": tex("disk_station_side"), "west": tex("disk_station_side"), "up": tex("disk_station_top"), "down": tex("disk_station_bottom")},
})
# The eject slot: a thin part just over the north face (turned to the eject face), 8 by 2 pixels.
SLOT = [4, 7, 12, 9]
write("models/block/disk_station_slot.json", {
	"textures": {"particle": tex("disk_station_slot"), "slot": tex("disk_station_slot")},
	"elements": [{"from": [SLOT[0], 16 - SLOT[3], -0.05], "to": [SLOT[2], 16 - SLOT[1], 0],
		"faces": {"north": {"uv": [16 - SLOT[2], SLOT[1], 16 - SLOT[0], SLOT[3]], "texture": "#slot", "cullface": "north"}}}],
})
# Its front faces its placer (four turns); the slot goes on the eject face (any of six).
FRONT_Y = {"north": 0, "east": 90, "south": 180, "west": 270}
SLOT_TURN = {"north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270}, "up": {"x": 270}, "down": {"x": 90}}
parts = [{"when": {"facing": face}, "apply": {"model": "modularcomputers:block/disk_station", **({"y": y} if y else {})}} for face, y in FRONT_Y.items()]
parts += [{"when": {"eject": face}, "apply": {"model": "modularcomputers:block/disk_station_slot", **turn}} for face, turn in SLOT_TURN.items()]
write("blockstates/disk_station.json", {"multipart": parts})
# The item: the casing with its slot on its front, as it's placed.
write("items/disk_station.json", {"model": {"type": "minecraft:composite", "models": [
	{"type": "minecraft:model", "model": "modularcomputers:block/disk_station"},
	{"type": "minecraft:model", "model": "modularcomputers:block/disk_station_slot"}]}})

if PREVIEW:
	order = ["disk_station_front", "disk_station_side", "disk_station_back", "disk_station_top", "disk_station_bottom", "disk_station_slot"]
	sheet = Image.new("RGB", (20 * len(order) + 4, 24), (40, 40, 40))
	for i, name in enumerate(order):
		sheet.paste(images[name], (4 + 20 * i, 4))
	sheet.resize((sheet.width * 8, sheet.height * 8), Image.NEAREST).save(PREVIEW)
