#!/usr/bin/env python3
# Draws the tier 2 and 3 case textures from the tier 1 (beige) ones that all_in_one_textures.py
# draws, and writes the case models and blockstates (needs Pillow). Each tier is an era of the PC
# (user's pick, 2026-10-12), matching its screens (cabled_block_textures.py):
#   tier 1: the 80s beige box (the originals, unchanged);
#   tier 2: a 90s platinum tower, with a red MHz display on the front;
#   tier 3: a modern graphite case, with a diamond light bar down the front.
# The sides keep tier 1's line across them (the user likes how it breaks up the color), and show
# no board: the board shows only through the back's window, where the power button is, so a
# player looking for the back isn't misled (user, 2026-10-12).
# The shapes stay the tier 1 ones (the glass the display is drawn on, the back's window and power
# button housing, which BackPanel maps clicks to); only colors and the era's details change.
# Rerun after all_in_one_textures.py, then check the preview.
#   scripts/art/case_tier_textures.py src/main/resources/assets/modularcomputers preview.png
import json, random, sys
from PIL import Image

ROOT = sys.argv[1]
TEX = f"{ROOT}/textures/block"
# The beige ramp: recess shadow (darkest) to bevel light, and each era's colors for it.
BEIGE_DARK, BEIGE_LIGHT = (148, 139, 113), (226, 218, 192)
ERAS = {2: ((141, 136, 124), (222, 218, 208)), 3: ((20, 21, 24), (64, 67, 73))}
DIAMOND = (90, 220, 220)

def lum(c):
	return 0.3 * c[0] + 0.59 * c[1] + 0.11 * c[2]

def beige(c):
	"""The case's plastic: warm, low-saturation pixels of the beige ramp (not the board, keys or LEDs)."""
	r, g, b = c[:3]
	return r >= g >= b and 25 <= r - b <= 45 and lum(c) > 100

def recolor(im, tier):
	dark, light = ERAS[tier]
	lo, hi = lum(BEIGE_DARK), lum(BEIGE_LIGHT)
	out = im.copy()
	for y in range(16):
		for x in range(16):
			c = out.getpixel((x, y))
			if beige(c):
				t = min(1, max(0, (lum(c) - lo) / (hi - lo)))
				out.putpixel((x, y), tuple(round(dark[i] + (light[i] - dark[i]) * t) for i in range(3)) + c[3:])
	return out

def rect(im, x0, y0, x1, y1, c):
	for y in range(y0, y1 + 1):
		for x in range(x0, x1 + 1):
			im.putpixel((x, y), c)

def mhz(im):
	"""A 90s turbo display: "66" in red seven-segment on black, bottom right of the front."""
	rect(im, 9, 11, 13, 13, (18, 8, 8))
	for x, y in ((9, 11), (9, 12), (9, 13), (10, 13), (10, 12), (12, 11), (12, 12), (12, 13), (13, 13), (13, 12)):
		im.putpixel((x, y), (255, 59, 42))

def light_bar(im):
	for y in range(10, 15):
		im.putpixel((14, y), DIAMOND)

def src(name):
	return Image.open(f"{TEX}/{name}.png").convert("RGBA")

made = {}
for tier in (2, 3):
	front = recolor(src("all_in_one_case_front"), tier)
	front_screen = recolor(src("all_in_one_front"), tier)
	side = recolor(src("all_in_one_side"), tier)
	if tier == 2:
		mhz(front)
	else:
		# Dark keys and lit drive bays: beige keys and black bays don't read on graphite.
		for im in (front, front_screen):
			for y in range(16):
				for x in range(16):
					c = im.getpixel((x, y))[:3]
					if c in ((196, 191, 176), (168, 163, 149)):
						im.putpixel((x, y), (62, 66, 72, 255))
					elif c == (66, 64, 59):
						im.putpixel((x, y), (16, 17, 19, 255) if im is front_screen else (88, 93, 101, 255))
		light_bar(front)
		light_bar(front_screen)
	made[f"tier{tier}_case_front"] = front
	made[f"tier{tier}_case_front_screen"] = front_screen
	made[f"tier{tier}_case_side"] = side
	for face in ("top", "bottom", "back"):
		made[f"tier{tier}_case_{face}"] = recolor(src(f"all_in_one_{face}"), tier)
for name, im in made.items():
	im.save(f"{TEX}/{name}.png")

# Models: one per tier, front (with or without a screen in the gold slot), power and readiness.
def faces(tier, screen):
	if tier == 1:
		return {"north": "all_in_one_case_front_plain", "south": "all_in_one_back", "east": "all_in_one_side",
			"west": "all_in_one_side", "up": "all_in_one_top", "down": "all_in_one_bottom"}
	return {"north": f"tier{tier}_case_front" + ("_screen" if screen else ""), "south": f"tier{tier}_case_back",
		"east": f"tier{tier}_case_side", "west": f"tier{tier}_case_side", "up": f"tier{tier}_case_top", "down": f"tier{tier}_case_bottom"}

def write(path, data):
	with open(path, "w") as f:
		json.dump(data, f, indent="\t")
		f.write("\n")

for tier in (1, 2, 3):
	for screen in ((False,) if tier == 1 else (False, True)):
		for on in (False, True):
			for faded in (False, True):
				name = f"tier{tier}_case" + ("_screen" if screen else "") + ("_on" if on else "") + ("_faded" if faded else "")
				textures = {face: f"modularcomputers:block/{tex}" for face, tex in faces(tier, screen).items()}
				textures["particle"] = textures["east"]
				textures["button"] = "modularcomputers:block/all_in_one_power_" + ("on" if on else "off") + ("_faded" if faded else "")
				write(f"{ROOT}/models/block/{name}.json",
					{"parent": "modularcomputers:block/all_in_one_base" + ("_faded" if faded else ""), "textures": textures})
		# On with its CPU halted: the button lit amber (only a ready machine can be on).
		textures = {face: f"modularcomputers:block/{tex}" for face, tex in faces(tier, screen).items()}
		textures["particle"] = textures["east"]
		textures["button"] = "modularcomputers:block/all_in_one_power_halted"
		write(f"{ROOT}/models/block/tier{tier}_case" + ("_screen" if screen else "") + "_halted.json",
			{"parent": "modularcomputers:block/all_in_one_base", "textures": textures})
	variants = {}
	for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
		for halted in (False, True):
			for powered in (False, True):
				for ready in (False, True):
					for screen in (False, True):
						model = f"modularcomputers:block/tier{tier}_case" + ("_screen" if screen and tier > 1 else "") \
							+ (("_halted" if halted and ready else "_on") if powered else "") + ("" if ready else "_faded")
						key = f"facing={facing},halted={str(halted).lower()},powered={str(powered).lower()},ready={str(ready).lower()},screen={str(screen).lower()}"
						variants[key] = {"model": model} | ({"y": y} if y else {})
	states = {"variants": variants}
	write(f"{ROOT}/blockstates/tier{tier}_case.json", states)
	if tier == 3:
		write(f"{ROOT}/blockstates/all_in_one.json", states)  # the dev kit is a tier 3 case
	item = {"model": {"type": "minecraft:model", "model": f"modularcomputers:block/tier{tier}_case_faded"}}
	write(f"{ROOT}/items/tier{tier}_case.json", item)
	if tier == 3:
		write(f"{ROOT}/items/all_in_one.json", item)

# Preview: each tier's faces, scaled up.
names = ["all_in_one_case_front_plain", "all_in_one_side", "all_in_one_back"] + [n for t in (2, 3) for n in (
	f"tier{t}_case_front", f"tier{t}_case_front_screen", f"tier{t}_case_side", f"tier{t}_case_back", f"tier{t}_case_top")]
sheet = Image.new("RGBA", (20 * len(names) + 4, 24), (40, 40, 40, 255))
for i, n in enumerate(names):
	sheet.alpha_composite(src(n), (4 + i * 20, 4))
sheet.resize((sheet.width * 8, sheet.height * 8), Image.NEAREST).save(sys.argv[2])
