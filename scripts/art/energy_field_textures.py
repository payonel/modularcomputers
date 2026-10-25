#!/usr/bin/env python3
# Draws an energy field's shimmer (needs Pillow): a soft white with brighter wisps across it and a
# few sparks, tinted blue by the renderer (EnergyFieldRenderer) and drawn round a sphere, drifting.
# Seamless left to right (it wraps round). The PNG is committed; rerun after editing this.
#   scripts/art/energy_field_textures.py common/src/main/resources/assets/modularcomputers
import math, random, sys
from PIL import Image

ASSETS = sys.argv[1]
W, H = 64, 32
BASE = 120  # the field's own glow, out of 255
WISP = 110  # how much brighter a wisp is

rnd = random.Random(43)
im = Image.new("RGBA", (W, H))
waves = [(rnd.uniform(0, math.tau), rnd.choice((1, 2, 3)), rnd.uniform(2, 6), rnd.uniform(0, H)) for _ in range(5)]
for y in range(H):
	for x in range(W):
		glow = BASE
		for phase, turns, width, center in waves:
			at = center + math.sin(x / W * math.tau * turns + phase) * H / 4
			glow += WISP * math.exp(-((y - at) ** 2) / (2 * width * width))
		v = min(255, int(glow))
		im.putpixel((x, y), (v, v, v, min(255, 90 + v // 2)))
for _ in range(14):
	x, y = rnd.randrange(W), rnd.randrange(H)
	im.putpixel((x, y), (255, 255, 255, 255))
im.save(f"{ASSETS}/textures/entity/energy_field.png")
