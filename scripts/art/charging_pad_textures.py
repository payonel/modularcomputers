#!/usr/bin/env python3
# Draws the charging pad (needs Pillow): a plate three pixels high in the bot golem's iron, its top
# an induction coil (gold, wound round its middle) with a green lamp at each corner, its front its
# battery hatch, its sides cable sockets; and writes its block and item models and blockstate: the
# plate turned to face its placer. The PNGs and JSON are committed; rerun after editing this, then
# check the preview.
#   scripts/art/charging_pad_textures.py common/src/main/resources/assets/modularcomputers preview.png
import json, random, sys
from PIL import Image

ASSETS = sys.argv[1]
PREVIEW = sys.argv[2] if len(sys.argv) > 2 else None

HEIGHT = 3  # ChargingPadBlock.HEIGHT, in pixels
IRON = [(201, 194, 181), (197, 190, 177), (205, 198, 185)]  # the golem's iron (bot_golem_textures.py)
IRON_SHADE = (138, 131, 119)
IRON_DARK = (104, 98, 88)
IRON_LIGHT = (228, 222, 210)
RIVET = (112, 106, 96)
COIL_BED = (40, 38, 36)
GOLD = (204, 166, 74)
GOLD_LIGHT = (236, 206, 120)
LAMP = (60, 220, 90)
LAMP_DIM = (30, 110, 46)
SOCKET = (40, 40, 46)
PIN = (90, 92, 100)

def put(im, x, y, c):
	im.putpixel((x, y), c)

def rect(im, x0, y0, x1, y1, c):
	for y in range(y0, y1 + 1):
		for x in range(x0, x1 + 1):
			put(im, x, y, c)

def iron(seed):
	rnd = random.Random(seed)
	im = Image.new("RGB", (16, 16))
	for y in range(16):
		row = rnd.choice(IRON)
		for x in range(16):
			put(im, x, y, row if rnd.random() < 0.75 else rnd.choice(IRON))
	return im

def top():
	"""The coil it charges through: gold wound round its middle on a dark bed, a lamp in each corner, in an iron rim."""
	im = iron(51)
	for i in range(16):
		put(im, i, 0, IRON_LIGHT); put(im, 0, i, IRON_LIGHT)
		put(im, i, 15, IRON_SHADE); put(im, 15, i, IRON_SHADE)
	rect(im, 2, 2, 13, 13, COIL_BED)
	for ring in range(3):
		a, b = 3 + 2 * ring, 12 - 2 * ring
		for i in range(a, b + 1):
			for x, y in ((i, a), (i, b), (a, i), (b, i)):
				put(im, x, y, GOLD if (x + y) % 3 else GOLD_LIGHT)
	rect(im, 7, 7, 8, 8, IRON_DARK)  # the coil's core
	for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
		put(im, x, y, LAMP)
	return im

def side_strip(im, y0):
	"""Its edge, three pixels high, at the bottom of a side texture (the model takes the bottom rows)."""
	for x in range(16):
		put(im, x, y0, IRON_LIGHT)
		put(im, x, 15, IRON_DARK)
	for x in (2, 13):
		put(im, x, y0 + 1, RIVET)

def side():
	im = iron(52)
	y0 = 16 - HEIGHT
	side_strip(im, y0)
	rect(im, 6, y0 + 1, 9, y0 + 1, SOCKET)  # the cable socket
	put(im, 7, y0 + 1, PIN); put(im, 8, y0 + 1, PIN)
	return im

def front():
	"""Its battery hatch, and a lamp: lit while it charges is its top's (this one, dim, says it's there)."""
	im = iron(53)
	y0 = 16 - HEIGHT
	side_strip(im, y0)
	rect(im, 5, y0 + 1, 10, y0 + 1, IRON_SHADE)  # the hatch's seam
	put(im, 12, y0 + 1, LAMP_DIM)
	return im

def bottom():
	return iron(54)

TEXTURES = {"charging_pad_top": top, "charging_pad_side": side, "charging_pad_front": front, "charging_pad_bottom": bottom}
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

# The plate, three pixels high, its front to the north (turned by the blockstate); its sides take
# their textures' bottom rows.
SIDE_UV = [0, 16 - HEIGHT, 16, 16]
write("models/block/charging_pad.json", {
	"parent": "minecraft:block/block",
	"textures": {"particle": tex("charging_pad_side"), "top": tex("charging_pad_top"), "side": tex("charging_pad_side"),
		"front": tex("charging_pad_front"), "bottom": tex("charging_pad_bottom")},
	"elements": [{
		"from": [0, 0, 0], "to": [16, HEIGHT, 16],
		"faces": {
			"up": {"uv": [0, 0, 16, 16], "texture": "#top"},
			"down": {"uv": [0, 0, 16, 16], "texture": "#bottom", "cullface": "down"},
			"north": {"uv": SIDE_UV, "texture": "#front", "cullface": "north"},
			"south": {"uv": SIDE_UV, "texture": "#side", "cullface": "south"},
			"east": {"uv": SIDE_UV, "texture": "#side", "cullface": "east"},
			"west": {"uv": SIDE_UV, "texture": "#side", "cullface": "west"},
		},
	}],
})
FRONT_Y = {"north": 0, "east": 90, "south": 180, "west": 270}
write("blockstates/charging_pad.json", {"variants": {f"facing={face}": {"model": "modularcomputers:block/charging_pad", **({"y": y} if y else {})}
	for face, y in FRONT_Y.items()}})
write("items/charging_pad.json", {"model": {"type": "minecraft:model", "model": "modularcomputers:block/charging_pad"}})

if PREVIEW:
	order = list(TEXTURES)
	sheet = Image.new("RGB", (20 * len(order) + 4, 24), (40, 40, 40))
	for i, name in enumerate(order):
		sheet.paste(images[name], (4 + 20 * i, 4))
	sheet.resize((sheet.width * 8, sheet.height * 8), Image.NEAREST).save(PREVIEW)
