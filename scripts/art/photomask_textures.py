#!/usr/bin/env python3
# Draws the chip-making textures (needs Pillow): the silicon and chip items, the photomask
# block's faces, and its gear's brass (drawn by PhotomaskRenderer, turning as it's cranked); and the
# block and item models: a full cube with a recess in its front for the gear (the item's holds the
# gear too, built from the same numbers as PhotomaskRenderer's). The PNGs and models are committed;
# rerun after editing this to regenerate them, then check the preview.
#   scripts/art/photomask_textures.py common/src/main/resources/assets/modularcomputers/textures preview.png
import json, math, os, random, sys
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
	"""The front's frame around the recess (only its edge shows)."""
	return casing(32)

def well():
	"""The recess the gear sits in: dark steel walls, and a round well behind the gear."""
	rnd = random.Random(35)
	im = Image.new("RGBA", (16, 16))
	for y in range(16):
		for x in range(16):
			d = math.hypot(x - 7.5, y - 7.5)
			c = (40, 42, 48) if d <= 6.6 else (58, 62, 70)
			n = rnd.randint(-3, 3)
			im.putpixel((x, y), (c[0] + n, c[1] + n, c[2] + n, 255))
	return im

def side():
	im = casing(33)
	for y in (4, 6, 8, 10):
		rect(im, 4, y, 11, y, (78, 82, 90, 255))
	return im

def bottom():
	return casing(34)

def gear():
	"""The gear's materials, in columns: brass (0-11), the shadowed axle (12-13), the peg's steel (14-15)."""
	rnd = random.Random(36)
	im = Image.new("RGBA", (16, 16))
	brass = [(204, 158, 70), (196, 150, 64), (212, 168, 80), (188, 142, 58)]
	for y in range(16):
		for x in range(16):
			if x < 12:
				c = rnd.choices(brass, weights=[5, 4, 2, 2])[0]
			elif x < 14:
				c = (70, 56, 30)
			else:
				c = (200, 204, 212) if y % 4 else (170, 174, 182)
			im.putpixel((x, y), c + (255,))
	return im

# The gear, as PhotomaskRenderer builds it: boxes in texels around the axle (z out of the face, 0 at
# the face), each turned about the axle, sampling the texture from column u.
DEPTH = 2.5
G_BRASS, G_AXLE, G_STEEL = 0, 12, 14
def gear_parts():
	parts = []
	for i in range(16):
		parts.append((-0.75, 5.75, -DEPTH, 0.75, 6.9, 0, i * 22.5, G_BRASS))   # a tooth
		parts.append((-1.15, 4.75, -DEPTH, 1.15, 5.75, 0, i * 22.5, G_BRASS))  # the rim
	for i in range(2):
		parts.append((-0.6, -4.9, -DEPTH + 0.3, 0.6, 4.9, -0.4, i * 90, G_BRASS))  # two spokes across
	for i in range(2):
		parts.append((-1.6, -1.6, -DEPTH, 1.6, 1.6, 0, i * 45, G_BRASS))     # the hub, an octagon
	parts.append((-0.6, -0.6, -DEPTH, 0.6, 0.6, 0.2, 0, G_AXLE))
	parts.append((-0.6, 3.3, 0, 0.6, 4.5, 1, 0, G_STEEL))                 # the crank peg
	return parts

def r(v):
	return round(v, 4)

def gear_elements():
	"""The gear as model elements on the north face. A model turns an element at most 45 degrees, so a
	part at 22.5k degrees is first swung round by quarter turns (exact), then turned the rest."""
	out = []
	for (x0, y0, z0, x1, y1, z1, angle, u) in gear_parts():
		quarter = int(round(angle / 90)) % 4
		rest = angle - round(angle / 90) * 90
		for _ in range(quarter):          # (x, y) -> (-y, x)
			x0, y0, x1, y1 = -y1, x0, -y0, x1
		width = 12 if u == G_BRASS else 2
		def face(a, b):
			return {"uv": [u, 0, r(u + min(width, max(a, 0.5))), r(min(16, max(b, 0.5)))], "texture": "#gear"}
		w, h, d = x1 - x0, y1 - y0, z1 - z0
		el = {
			# North is the front: model x runs the other way seen from the front, and z goes into the block.
			"from": [r(8 - x1), r(8 + y0), r(-z1)], "to": [r(8 - x0), r(8 + y1), r(-z0)],
			"faces": {"north": face(w, h), "south": face(w, h), "east": face(d, h), "west": face(d, h), "up": face(w, d), "down": face(w, d)},
		}
		if rest:
			el["rotation"] = {"origin": [8, 8, 0], "axis": "z", "angle": rest}
		out.append(el)
	return out

def block_elements():
	"""A full cube but for its front: a 1-texel frame around a 3-deep recess, the well at its back."""
	def faces(sides, wall):
		f = {}
		for side, tex in sides.items():
			f[side] = {"texture": tex}
			if not wall.get(side):
				f[side]["cullface"] = side
		return f
	outer = {"south": "#side", "east": "#side", "west": "#side", "up": "#top", "down": "#bottom"}
	els = [{"from": [0, 0, 3], "to": [16, 16, 16], "faces": faces({**outer, "north": "#well"}, {"north": True})}]
	for (fx, fy, tx, ty, inner) in ((0, 15, 16, 16, "down"), (0, 0, 16, 1, "up"), (15, 1, 16, 15, "west"), (0, 1, 1, 15, "east")):
		sides = {"north": "#front", inner: "#well"}
		for side in ("east", "west", "up", "down"):
			if side != inner and ((side == "up" and ty == 16) or (side == "down" and fy == 0) or (side == "east" and tx == 16) or (side == "west" and fx == 0)):
				sides[side] = outer[side]
		els.append({"from": [fx, fy, 0], "to": [tx, ty, 3], "faces": faces(sides, {inner: True})})
	return els

def gui_gear():
	"""The gear as a picture (16x16, the item hint's turning gear): face on, peg up."""
	im = Image.new("RGBA", (16, 16), CLEAR)
	brass, light, dark = (204, 158, 70, 255), (232, 196, 110, 255), (150, 108, 40, 255)
	for y in range(16):
		for x in range(16):
			dx, dy = x - 7.5, y - 7.5
			d = math.hypot(dx, dy)
			a = math.degrees(math.atan2(dx, -dy)) % 360          # 0 straight up, clockwise
			c = None
			if d <= 0.8:
				c = (70, 56, 30, 255)                             # axle
			elif d <= 2.0:
				c = brass                                         # hub
			elif 4.6 <= d <= 5.8:
				c = brass                                         # rim
			elif 5.8 < d <= 7.4 and (a + 5.625) % 22.5 < 11.25:
				c = brass                                         # teeth
			elif d < 4.6 and min(abs(dx), abs(dy)) <= 0.6:
				c = brass                                         # spokes
			if c == brass and dx + dy < -3:
				c = light
			elif c == brass and dx + dy > 4:
				c = dark
			if c:
				im.putpixel((x, y), c)
	for (x, y) in ((7, 3), (8, 3), (7, 4), (8, 4)):                # the crank peg, on the top spoke
		im.putpixel((x, y), (214, 218, 226, 255))
	return im

def models(root):
	textures = {"particle": "modularcomputers:block/photomask_side", "front": "modularcomputers:block/photomask_front",
		"well": "modularcomputers:block/photomask_well", "side": "modularcomputers:block/photomask_side",
		"top": "modularcomputers:block/photomask_top", "bottom": "modularcomputers:block/photomask_bottom"}
	block = {"parent": "minecraft:block/block", "textures": textures, "elements": block_elements()}
	item = {"parent": "minecraft:block/block", "textures": {**textures, "gear": "modularcomputers:block/photomask_gear"},
		"elements": block_elements() + gear_elements()}
	for path, model in ((f"{root}/block/photomask.json", block), (f"{root}/item/photomask.json", item)):
		with open(path, "w") as f:
			json.dump(model, f, indent="\t")
			f.write("\n")

items = {"silicon": silicon, "chip": chip}
blocks = {"photomask_top": top, "photomask_front": front, "photomask_well": well, "photomask_side": side, "photomask_bottom": bottom, "photomask_gear": gear}
for name, fn in items.items():
	fn().save(f"{OUT}/item/{name}.png")
for name, fn in blocks.items():
	fn().save(f"{OUT}/block/{name}.png")

models(os.path.join(OUT, "..", "models"))
gui_gear().save(f"{OUT}/gui/photomask_gear.png")

sheet = Image.new("RGBA", (20 * 8 + 4, 24), (40, 40, 40, 255))
for i, (folder, name) in enumerate([("item", n) for n in items] + [("block", n) for n in blocks]):
	sheet.alpha_composite(Image.open(f"{OUT}/{folder}/{name}.png").convert("RGBA"), (4 + i * 20, 4))
sheet.resize((sheet.width * 8, sheet.height * 8), Image.NEAREST).save(sys.argv[2])
