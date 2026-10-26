#!/usr/bin/env python3
# Draws the bot golems (needs Pillow): their entity textures, an iron golem's look a block high
# (brushed warm iron, rivets, vines grown over it), its head's front a screen's bezel and glass (or,
# with no screen in, an empty bezel, its connector's pins showing), and their items' icons; and writes
# the items' models. A tier 2 (it flies) has the same model in a more advanced skin: dark machined
# steel instead of weathered iron (no vines), panel seams, cyan status lights (its chest's vent, its
# shoulders) and a circuit's traces on its chest plate. Each box's texels follow BotGolemModel's layout (texOffs and sizes there). The PNGs and JSON
# are committed; rerun after editing this, then check the preview.
#   scripts/art/bot_golem_textures.py common/src/main/resources/assets/modularcomputers preview.png
import json, random, sys
from PIL import Image

ASSETS = sys.argv[1]
PREVIEW = sys.argv[2] if len(sys.argv) > 2 else None

W, H = 64, 32  # BotGolemModel.TEXTURE_WIDTH, TEXTURE_HEIGHT
IRON = [(201, 194, 181), (197, 190, 177), (205, 198, 185)]  # the golem GUI's iron (case_gui_textures.py)
IRON_SHADE = (138, 131, 119)
IRON_DARK = (104, 98, 88)
IRON_LIGHT = (228, 222, 210)
RIVET = (112, 106, 96)
VINE = (58, 110, 40)
VINE_DARK = (40, 82, 28)
LEAF = (88, 148, 56)
BEZEL = (58, 56, 54)
GLASS = (10, 12, 14)  # a tier 1 screen's glass, off
PIN = (204, 166, 74)
# Where its legs clamp onto its torso (the user's): emerald and redstone, the legs' clamps on top
# (the male side), the torso's sockets underneath (the female side), the same colours.
EMERALD = (40, 200, 110)
EMERALD_DARK = (20, 120, 64)
REDSTONE = (210, 36, 30)
REDSTONE_DARK = (120, 16, 12)
SOCKET = (26, 24, 24)
# A tier 2's skin: machined dark steel with a blue cast, its seams, cyan lights, a circuit's copper.
STEEL = [(82, 90, 102), (78, 86, 98), (86, 94, 106)]
STEEL_SHADE = (52, 58, 68)
STEEL_DARK = (36, 40, 48)
STEEL_LIGHT = (126, 136, 150)
SEAM = (30, 34, 40)
LIGHT = (70, 230, 240)
LIGHT_DIM = (30, 130, 150)
TRACE = (196, 140, 72)

# Each box: its name, texOffs (u, v), and size (w, h, d), as BotGolemModel's.
BOXES = {
	"head": ((0, 0), (7, 6, 6)),
	"body": ((0, 12), (8, 6, 5)),
	"right_arm": ((28, 0), (2, 8, 3)),
	"left_arm": ((40, 0), (2, 8, 3)),
	"right_leg": ((28, 12), (3, 3, 3)),
	"left_leg": ((42, 12), (3, 3, 3)),
	"chest": ((0, 24), (5, 4, 3)),  # on its back, with a crafting card: sunk into its frame
	"nozzle": ((20, 24), (4, 1, 4)),  # a tier 2's: a collar round each leg's socket, under its body
}
CHEST_WOOD = [(150, 104, 52), (142, 98, 48), (156, 108, 56)]
CHEST_DARK = (92, 62, 30)
LATCH = (200, 200, 206)

def faces(box):
	"""A box's faces on the texture, as (x0, y0, x1, y1), the way Minecraft lays a cube out."""
	(u, v), (w, h, d) = BOXES[box]
	return {
		"top": (u + d, v, u + d + w, v + d),
		"bottom": (u + d + w, v, u + d + 2 * w, v + d),
		"right": (u, v + d, u + d, v + d + h),
		"front": (u + d, v + d, u + d + w, v + d + h),
		"left": (u + d + w, v + d, u + 2 * d + w, v + d + h),
		"back": (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),
	}

def put(im, x, y, c):
	if 0 <= x < im.width and 0 <= y < im.height:
		im.putpixel((x, y), c + (255,))

def fill(im, x0, y0, x1, y1, c):
	for y in range(y0, y1):
		for x in range(x0, x1):
			put(im, x, y, c)

def iron(im, rect, rnd, metal=None):
	"""Brushed iron (or a tier 2's steel: metal), rows of one shade, a light top edge and a shaded
	bottom one."""
	shades, light, shade = metal or (IRON, IRON_LIGHT, IRON_SHADE)
	x0, y0, x1, y1 = rect
	for y in range(y0, y1):
		row = rnd.choice(shades)
		for x in range(x0, x1):
			put(im, x, y, row if rnd.random() < 0.75 else rnd.choice(shades))
	fill(im, x0, y0, x1, y0 + 1, light)
	fill(im, x0, y1 - 1, x1, y1, shade)

STEEL_METAL = (STEEL, STEEL_LIGHT, STEEL_SHADE)

def golem(screen, tier2=False):
	rnd = random.Random(31)
	im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
	for box in BOXES:
		if box == "nozzle":
			continue
		for rect in faces(box).values():
			iron(im, rect, rnd, STEEL_METAL if tier2 and box != "chest" else None)
	# the head: rivets on top, its face a bezel round the glass (or an empty socket)
	head = faces("head")
	tx0, ty0, tx1, ty1 = head["top"]
	for x, y in [(tx0 + 1, ty0 + 1), (tx1 - 2, ty0 + 1), (tx0 + 1, ty1 - 2), (tx1 - 2, ty1 - 2)]:
		put(im, x, y, RIVET)
	fx0, fy0, fx1, fy1 = head["front"]
	fill(im, fx0, fy0, fx1, fy1 - 1, BEZEL)
	fill(im, fx0, fy1 - 1, fx1, fy1, IRON_SHADE)  # the chin plate
	# the glass: 5 by 4, a texel in from the left and the top (BotGolemRenderer's GLASS_*)
	if screen:
		fill(im, fx0 + 1, fy0 + 1, fx0 + 6, fy0 + 5, GLASS)
	else:
		fill(im, fx0 + 1, fy0 + 1, fx0 + 6, fy0 + 5, (24, 24, 26))  # the empty socket
		for x in range(fx0 + 1, fx0 + 6, 2):
			put(im, x, fy0 + 4, PIN)  # its connector's pins
	# the body: a chest plate riveted at its corners, a vent; a hatch on its back
	bx0, by0, bx1, by1 = faces("body")["front"]
	for x, y in [(bx0, by0 + 1), (bx1 - 1, by0 + 1), (bx0, by1 - 2), (bx1 - 1, by1 - 2)]:
		put(im, x, y, RIVET)
	for x in range(bx0 + 2, bx1 - 2):
		put(im, x, by0 + 3, IRON_DARK)
	kx0, ky0, kx1, ky1 = faces("body")["back"]
	fill(im, kx0 + 2, ky0 + 1, kx1 - 2, ky1 - 1, IRON_SHADE)
	fill(im, kx0 + 3, ky0 + 2, kx1 - 3, ky1 - 2, IRON_DARK)
	# hands and feet: darker metal at the ends
	shade, dark = (STEEL_SHADE, STEEL_DARK) if tier2 else (IRON_SHADE, IRON_DARK)
	for arm in ("right_arm", "left_arm"):
		for side in ("right", "front", "left", "back"):
			x0, y0, x1, y1 = faces(arm)[side]
			fill(im, x0, y1 - 2, x1, y1, shade)
		fill(im, *faces(arm)["bottom"], dark)
	for leg in ("right_leg", "left_leg"):
		for side in ("right", "front", "left", "back"):
			x0, y0, x1, y1 = faces(leg)[side]
			fill(im, x0, y1 - 1, x1, y1, dark)
	# the tiny chest on its back (drawn only with a crafting card in): planks, a dark lid seam, its latch
	for side, (x0, y0, x1, y1) in faces("chest").items():
		for y in range(y0, y1):
			for x in range(x0, x1):
				put(im, x, y, rnd.choice(CHEST_WOOD))
		fill(im, x0, y1 - 1, x1, y1, CHEST_DARK)
		if side in ("right", "left", "back", "front"):
			fill(im, x0, y0 + 1, x1, y0 + 2, CHEST_DARK)  # the lid's seam
	bx0, by0, bx1, by1 = faces("chest")["back"]
	put(im, (bx0 + bx1) // 2, by0 + 2, LATCH)
	# the legs' clamps on their tops, an emerald one and a redstone one each; the torso's sockets under it
	for leg, colors in (("right_leg", (EMERALD, REDSTONE)), ("left_leg", (REDSTONE, EMERALD))):
		x0, y0, x1, y1 = faces(leg)["top"]
		put(im, x0, y0 + 1, colors[0]); put(im, x0 + 2, y0 + 1, colors[1])
		put(im, x0 + 1, y0 + 1, IRON_DARK)
	ux0, uy0, ux1, uy1 = faces("body")["bottom"]
	for i, c in enumerate((EMERALD_DARK, REDSTONE_DARK, REDSTONE_DARK, EMERALD_DARK)):
		x = ux0 + 1 + i * 2
		put(im, x, uy0 + 2, SOCKET); put(im, x, uy0 + 1, c)
	if tier2:
		tech(im)
		return im
	# vines, as on an iron golem: over a shoulder, down the body's side, round a leg
	vr = random.Random(37)
	def vine(points):
		for x, y in points:
			put(im, x, y, VINE if vr.random() < 0.65 else VINE_DARK)
			if vr.random() < 0.3:
				put(im, x + 1, y, LEAF)
	sx0, sy0, sx1, sy1 = faces("body")["right"]
	vine([(sx0 + 1, sy0 + k) for k in range(sy1 - sy0)])
	vine([(bx0 + 1, by0), (bx0 + 1, by0 + 1), (bx0 + 2, by0 + 2)])
	ax0, ay0, ax1, ay1 = faces("right_arm")["front"]
	vine([(ax0, ay0 + k) for k in range(3)])
	lx0, ly0, lx1, ly1 = faces("left_leg")["front"]
	vine([(lx0 + 1, ly0), (lx0 + 2, ly0 + 1)])
	return im

def tech(im):
	"""A tier 2's details over its steel: seams between panels, its chest's vent and its shoulders'
	status lights lit cyan, a circuit's traces across its chest plate, its head's sensor strip."""
	bx0, by0, bx1, by1 = faces("body")["front"]
	for x, y in [(bx0, by0 + 1), (bx1 - 1, by0 + 1), (bx0, by1 - 2), (bx1 - 1, by1 - 2)]:
		put(im, x, y, SEAM)  # its screws, flush
	for x in range(bx0 + 2, bx1 - 2):
		put(im, x, by0 + 3, LIGHT)  # the vent, lit
	put(im, bx0 + 1, by0 + 2, TRACE); put(im, bx0 + 1, by0 + 3, TRACE); put(im, bx0 + 1, by0 + 4, TRACE)
	put(im, bx1 - 2, by0 + 2, TRACE); put(im, bx1 - 2, by0 + 1, TRACE)
	put(im, bx0 + 2, by0 + 4, TRACE); put(im, bx1 - 3, by0 + 4, TRACE); put(im, bx1 - 3, by0 + 5, TRACE)
	for side in ("right", "left"):
		x0, y0, x1, y1 = faces("body")[side]
		fill(im, x0 + 1, y0, x0 + 2, y1, SEAM)  # a panel's seam down its side
	kx0, ky0, kx1, ky1 = faces("body")["back"]
	fill(im, kx0 + 2, ky0 + 1, kx1 - 2, ky1 - 1, STEEL_SHADE)
	fill(im, kx0 + 3, ky0 + 2, kx1 - 3, ky1 - 2, SEAM)
	put(im, kx0 + 3, ky0 + 2, LIGHT_DIM)  # its hatch's indicator
	for arm in ("right_arm", "left_arm"):
		x0, y0, x1, y1 = faces(arm)["front"]
		put(im, x0, y0 + 1, LIGHT)  # a shoulder's status light
		fill(im, x0, y0 + 3, x1, y0 + 4, SEAM)  # its elbow's joint
		for side in ("right", "left", "back"):
			x0, y0, x1, y1 = faces(arm)[side]
			fill(im, x0, y0 + 3, x1, y0 + 4, SEAM)
	fx0, fy0, fx1, fy1 = faces("head")["front"]
	for x in range(fx0 + 1, fx1 - 1):
		put(im, x, fy1 - 1, LIGHT_DIM)  # its sensor strip, under the glass
	tx0, ty0, tx1, ty1 = faces("head")["top"]
	fill(im, tx0 + 1, ty0 + 2, tx1 - 1, ty0 + 3, SEAM)
	put(im, tx1 - 2, ty0 + 1, LIGHT)  # its antenna's light
	for leg in ("right_leg", "left_leg"):
		for side in ("right", "front", "left", "back"):
			x0, y0, x1, y1 = faces(leg)[side]
			fill(im, x0, y0 + 1, x1, y0 + 2, SEAM)
	for side, (x0, y0, x1, y1) in faces("nozzle").items():
		fill(im, x0, y0, x1, y1, STEEL_LIGHT if side != "bottom" else STEEL_DARK)  # its collar's rim
		if side == "bottom":
			fill(im, x0 + 1, y0 + 1, x1 - 1, y1 - 1, LIGHT_DIM)  # its mouth, glowing
		elif side == "front":
			put(im, x0 + 1, y0, LIGHT)

def icon(tier2=False):
	"""Its item: a little golem, from the front, as blocky as its model (its head near its body's
	size), its face lit; a tier 2's in its steel, its lights lit."""
	rnd = random.Random(41)
	im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
	m = STEEL_METAL if tier2 else None
	iron(im, (4, 0, 12, 7), rnd, m)        # head: 8 by 7, as the model's 7 by 6 to its body's 8 by 6
	fill(im, 5, 1, 11, 6, BEZEL)
	fill(im, 6, 2, 10, 5, (60, 220, 90))  # its glass, lit: a tier 1 screen's green
	fill(im, 4, 6, 12, 7, STEEL_SHADE if tier2 else IRON_SHADE)   # its chin
	iron(im, (3, 7, 13, 12), rnd, m)       # body
	iron(im, (1, 7, 3, 13), rnd, m)        # arms, long, to its knees
	iron(im, (13, 7, 15, 13), rnd, m)
	shade = STEEL_SHADE if tier2 else IRON_SHADE
	fill(im, 1, 12, 3, 13, shade); fill(im, 13, 12, 15, 13, shade)
	iron(im, (4, 12, 7, 16), rnd, m)       # legs
	iron(im, (9, 12, 12, 16), rnd, m)
	if tier2:
		for x in range(5, 11):
			put(im, x, 9, LIGHT)  # its vent, lit
		put(im, 1, 8, LIGHT); put(im, 14, 8, LIGHT)  # its shoulders' lights
		fill(im, 5, 6, 11, 7, LIGHT_DIM)  # its sensor strip
		put(im, 4, 10, TRACE); put(im, 4, 11, TRACE); put(im, 11, 10, TRACE)
	else:
		put(im, 4, 8, VINE); put(im, 4, 9, VINE_DARK); put(im, 5, 10, LEAF); put(im, 3, 9, VINE)
	return im

def torso_icon():
	"""Its torso alone: the golem's icon without its legs, set a little lower, the sockets its legs
	clamp into showing under it (emerald and redstone rims, dark holes)."""
	whole = icon(tier2=True)
	im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
	im.alpha_composite(whole.crop((0, 0, 16, 12)), (0, 2))
	for x, rim in ((4, EMERALD), (6, REDSTONE), (9, REDSTONE), (11, EMERALD)):
		put(im, x, 13, rim); put(im, x, 14, SOCKET)
	return im

def legs_icon():
	"""A tier 2's legs alone: two stubby steel legs under a hip plate, a panel seam round each."""
	rnd = random.Random(47)
	im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
	iron(im, (3, 6, 13, 8), rnd, STEEL_METAL)   # the hip plate
	fill(im, 3, 7, 13, 8, STEEL_SHADE)
	iron(im, (4, 8, 7, 13), rnd, STEEL_METAL)   # legs
	iron(im, (9, 8, 12, 13), rnd, STEEL_METAL)
	fill(im, 4, 12, 7, 13, STEEL_DARK); fill(im, 9, 12, 12, 13, STEEL_DARK)
	fill(im, 4, 9, 7, 10, SEAM); fill(im, 9, 9, 12, 10, SEAM)
	for x, clamp, dark in ((4, EMERALD, EMERALD_DARK), (6, REDSTONE, REDSTONE_DARK), (9, REDSTONE, REDSTONE_DARK), (11, EMERALD, EMERALD_DARK)):
		put(im, x, 4, clamp); put(im, x, 5, dark)  # its clamps, up into its torso's sockets
	return im

images = {"tier1_bot_golem": golem(True), "tier1_bot_golem_blank": golem(False),
	"tier2_bot_golem": golem(True, tier2=True), "tier2_bot_golem_blank": golem(False, tier2=True)}
for name, im in images.items():
	im.save(f"{ASSETS}/textures/entity/{name}.png")
icon().save(f"{ASSETS}/textures/item/tier1_bot_golem.png")
icon(tier2=True).save(f"{ASSETS}/textures/item/tier2_bot_golem.png")
torso_icon().save(f"{ASSETS}/textures/item/tier2_bot_golem_torso.png")
legs_icon().save(f"{ASSETS}/textures/item/tier2_bot_golem_legs.png")

def write(path, data):
	with open(f"{ASSETS}/{path}", "w") as f:
		json.dump(data, f, indent="\t")
		f.write("\n")

for part in ("tier1_bot_golem", "tier2_bot_golem", "tier2_bot_golem_torso", "tier2_bot_golem_legs"):
	write(f"models/item/{part}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"modularcomputers:item/{part}"}})
	write(f"items/{part}.json", {"model": {"type": "minecraft:model", "model": f"modularcomputers:item/{part}"}})

if PREVIEW:
	sheet = Image.new("RGBA", (W * 2 + 24 + 16, H * 2 + 8), (40, 40, 40, 255))
	sheet.paste(images["tier1_bot_golem"], (0, 0))
	sheet.paste(images["tier1_bot_golem_blank"], (W + 8, 0))
	sheet.paste(icon(), (2 * W + 16, 0))
	sheet.paste(images["tier2_bot_golem"], (0, H + 8))
	sheet.paste(images["tier2_bot_golem_blank"], (W + 8, H + 8))
	sheet.paste(icon(tier2=True), (2 * W + 16, H + 8))
	sheet.resize((sheet.width * 8, sheet.height * 8), Image.NEAREST).save(PREVIEW)
