#!/usr/bin/env python3
# Draws the bot golem (needs Pillow): its entity textures, an iron golem's look a block high (brushed
# warm iron, rivets, vines grown over it), its head's front a screen's bezel and glass (or, with no
# screen in, an empty bezel, its connector's pins showing), and its item's icon; and writes the item's
# models. Each box's texels follow BotGolemModel's layout (texOffs and sizes there). The PNGs and JSON
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

# Each box: its name, texOffs (u, v), and size (w, h, d), as BotGolemModel's.
BOXES = {
	"head": ((0, 0), (7, 6, 6)),
	"body": ((0, 12), (8, 6, 5)),
	"right_arm": ((28, 0), (2, 8, 3)),
	"left_arm": ((40, 0), (2, 8, 3)),
	"right_leg": ((28, 12), (3, 3, 3)),
	"left_leg": ((42, 12), (3, 3, 3)),
	"chest": ((0, 24), (5, 4, 3)),  # on its back, with a crafting card: sunk into its frame
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

def iron(im, rect, rnd):
	"""Brushed iron, rows of one shade, a light top edge and a shaded bottom one."""
	x0, y0, x1, y1 = rect
	for y in range(y0, y1):
		row = rnd.choice(IRON)
		for x in range(x0, x1):
			put(im, x, y, row if rnd.random() < 0.75 else rnd.choice(IRON))
	fill(im, x0, y0, x1, y0 + 1, IRON_LIGHT)
	fill(im, x0, y1 - 1, x1, y1, IRON_SHADE)

def golem(screen):
	rnd = random.Random(31)
	im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
	for box in BOXES:
		for rect in faces(box).values():
			iron(im, rect, rnd)
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
	# hands and feet: darker iron at the ends
	for arm in ("right_arm", "left_arm"):
		for side in ("right", "front", "left", "back"):
			x0, y0, x1, y1 = faces(arm)[side]
			fill(im, x0, y1 - 2, x1, y1, IRON_SHADE)
		fill(im, *faces(arm)["bottom"], IRON_DARK)
	for leg in ("right_leg", "left_leg"):
		for side in ("right", "front", "left", "back"):
			x0, y0, x1, y1 = faces(leg)[side]
			fill(im, x0, y1 - 1, x1, y1, IRON_DARK)
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

def icon():
	"""Its item: a little golem, from the front, as blocky as its model (its head near its body's
	size), its face lit."""
	rnd = random.Random(41)
	im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
	iron(im, (4, 0, 12, 7), rnd)        # head: 8 by 7, as the model's 7 by 6 to its body's 8 by 6
	fill(im, 5, 1, 11, 6, BEZEL)
	fill(im, 6, 2, 10, 5, (60, 220, 90))  # its glass, lit: a tier 1 screen's green
	fill(im, 4, 6, 12, 7, IRON_SHADE)   # its chin
	iron(im, (3, 7, 13, 12), rnd)       # body
	iron(im, (1, 7, 3, 13), rnd)        # arms, long, to its knees
	iron(im, (13, 7, 15, 13), rnd)
	fill(im, 1, 12, 3, 13, IRON_SHADE); fill(im, 13, 12, 15, 13, IRON_SHADE)
	iron(im, (4, 12, 7, 16), rnd)       # legs
	iron(im, (9, 12, 12, 16), rnd)
	put(im, 4, 8, VINE); put(im, 4, 9, VINE_DARK); put(im, 5, 10, LEAF); put(im, 3, 9, VINE)
	return im

images = {"bot_golem": golem(True), "bot_golem_blank": golem(False)}
for name, im in images.items():
	im.save(f"{ASSETS}/textures/entity/{name}.png")
icon().save(f"{ASSETS}/textures/item/bot_golem.png")

def write(path, data):
	with open(f"{ASSETS}/{path}", "w") as f:
		json.dump(data, f, indent="\t")
		f.write("\n")

write("models/item/bot_golem.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "modularcomputers:item/bot_golem"}})
write("items/bot_golem.json", {"model": {"type": "minecraft:model", "model": "modularcomputers:item/bot_golem"}})

if PREVIEW:
	sheet = Image.new("RGBA", (W * 2 + 24 + 16, H), (40, 40, 40, 255))
	sheet.paste(images["bot_golem"], (0, 0))
	sheet.paste(images["bot_golem_blank"], (W + 8, 0))
	sheet.paste(icon(), (2 * W + 16, 0))
	sheet.resize((sheet.width * 8, sheet.height * 8), Image.NEAREST).save(PREVIEW)
