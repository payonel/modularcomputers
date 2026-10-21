#!/usr/bin/env python3
# Writes the ancient lab's structure template (an .nbt file, gzipped NBT, no Minecraft needed):
# the ruin of a small copper-trimmed lab where someone built computers before you (user design,
# 2026-10-12). Tables with broken computers on them, cables along the floor, a chest of parts,
# and among the ruins a working photomask, the way into making chips. Weathering (moss, cracks,
# missing cables and windows) is added at generation by the processor list
# modularcomputers:ancient_lab, so each site differs. Rerun after editing:
#   scripts/data/ancient_lab_structure.py common/src/main/resources/data/modularcomputers/structure/ancient_lab.nbt
import gzip, struct, sys

DATA_VERSION = 5023  # Minecraft 26.3 (version.json's world_version)
W, H, D = 11, 6, 9   # x, y, z

# ---- a tiny NBT writer -------------------------------------------------------------------------
END, BYTE, INT, STRING, LIST, COMPOUND = 0, 1, 3, 8, 9, 10

def name(s):
	b = s.encode("utf-8")
	return struct.pack(">H", len(b)) + b

def payload(tag, value):
	if tag == INT:
		return struct.pack(">i", value)
	if tag == STRING:
		return name(value)
	if tag == LIST:
		inner, items = value
		return struct.pack(">bi", inner if items else END, len(items)) + b"".join(payload(inner, v) for v in items)
	if tag == COMPOUND:
		return b"".join(struct.pack(">b", t) + name(k) + payload(t, v) for k, (t, v) in value.items()) + b"\x00"
	raise ValueError(tag)

def compound(**fields):
	return (COMPOUND, fields)

def string(s):
	return (STRING, s)

def ints(*values):
	return (LIST, (INT, list(values)))

# ---- the lab ----------------------------------------------------------------------------------
blocks = {}  # (x, y, z) -> (block state string, block entity nbt or None)

def put(x, y, z, state, nbt=None):
	blocks[(x, y, z)] = (state, nbt)

for x in range(W):
	for z in range(D):
		# Floor: polished andesite, a weathered copper strip down the middle.
		put(x, 0, z, "minecraft:oxidized_cut_copper" if z == 4 else "minecraft:polished_andesite")
		edge = x in (0, W - 1) or z in (0, D - 1)
		for y in range(1, 4):
			if edge:
				put(x, y, z, "minecraft:stone_bricks")
			else:
				put(x, y, z, "minecraft:air")
		# Roof: only over the back half; the front has fallen in.
		put(x, 4, z, "minecraft:stone_brick_slab[type=bottom,waterlogged=false]" if z >= 4 else "minecraft:air")
		put(x, 5, z, "minecraft:air")
# Corner pillars, chiseled at the top.
for x, z in ((0, 0), (W - 1, 0), (0, D - 1), (W - 1, D - 1)):
	put(x, 3, z, "minecraft:chiseled_stone_bricks")
	put(x, 4, z, "minecraft:chiseled_stone_bricks")
# The doorway, and copper grate windows.
put(5, 1, 0, "minecraft:air"); put(5, 2, 0, "minecraft:air")
for x in (2, 8):
	put(x, 2, D - 1, "minecraft:oxidized_copper_grate[waterlogged=false]")
for z in (2, 6):
	put(0, 2, z, "minecraft:oxidized_copper_grate[waterlogged=false]")
	put(W - 1, 2, z, "minecraft:oxidized_copper_grate[waterlogged=false]")
# A dead lamp on the back wall.
put(5, 3, D - 1, "minecraft:oxidized_copper_bulb[lit=false,powered=false]")
# Fallen stone where the roof came down.
for x, z, state in ((3, 1, "minecraft:mossy_cobblestone"), (4, 2, "minecraft:cobblestone"), (7, 1, "minecraft:mossy_cobblestone"),
		(8, 2, "minecraft:cobblestone_slab[type=bottom,waterlogged=false]"), (2, 3, "minecraft:stone_brick_slab[type=bottom,waterlogged=false]")):
	put(x, 1, z, state)

# Workbenches along the back: tables (top slabs) with broken computers on them.
for x in (2, 3, 4):
	put(x, 1, 7, "minecraft:spruce_slab[type=top,waterlogged=false]")
put(2, 2, 7, "modularcomputers:broken_case[facing=south]")
put(4, 2, 7, "modularcomputers:broken_case[facing=south]")
# The photomask's bench, on the right: it still works.
for x in (7, 8):
	put(x, 1, 7, "minecraft:spruce_slab[type=top,waterlogged=false]")
put(8, 2, 7, "modularcomputers:photomask[facing=south,working=false]", compound(id=string("modularcomputers:photomask")))
put(7, 2, 7, "minecraft:candle[candles=2,lit=false,waterlogged=false]")
# One more broken computer on the floor, knocked over to face the wall.
put(9, 1, 5, "modularcomputers:broken_case[facing=east]")
# Cables along the floor, running between the benches.
def cable(x, z, **sides):
	props = ",".join(f"{side}={'true' if sides.get(side) else 'false'}" for side in ("down", "east", "north", "south", "up", "west"))
	put(x, 1, z, f"modularcomputers:cable[{props}]")
for x in range(2, 9):
	cable(x, 6, east=x < 8, west=x > 2)
cable(5, 6, east=True, west=True, north=True)   # a branch toward the door (north is -z)
cable(5, 5, south=True)
# The chest of parts, by the door.
put(1, 1, 1, "minecraft:chest[facing=east,type=single,waterlogged=false]",
	compound(id=string("minecraft:chest"), LootTable=string("modularcomputers:chests/ancient_lab")))
# Cobwebs in the roofed corners.
for x, z in ((1, D - 2), (W - 2, D - 2), (1, 4)):
	put(x, 3, z, "minecraft:cobweb")

# ---- write it ---------------------------------------------------------------------------------
def parse(state):
	if "[" not in state:
		return state, {}
	block, rest = state.split("[", 1)
	return block, dict(pair.split("=") for pair in rest.rstrip("]").split(","))

palette = []
entries = []
for pos in sorted(blocks, key=lambda p: (p[1], p[2], p[0])):
	state, nbt = blocks[pos]
	if state not in palette:
		palette.append(state)
	entry = {"pos": ints(*pos), "state": (INT, palette.index(state))}
	if nbt:
		entry["nbt"] = nbt
	entries.append(entry)

def palette_entry(state):
	block, props = parse(state)
	fields = {"id": string(block)}  # 26.3's names: "id" and "properties" (they were "Name" and "Properties")
	if props:
		fields["properties"] = compound(**{k: string(v) for k, v in props.items()})
	return fields

root = {
	"DataVersion": (INT, DATA_VERSION),
	"size": ints(W, H, D),
	"palette": (LIST, (COMPOUND, [palette_entry(s) for s in palette])),
	"blocks": (LIST, (COMPOUND, entries)),
	"entities": (LIST, (COMPOUND, [])),
}
data = struct.pack(">b", COMPOUND) + name("") + payload(COMPOUND, root)
with gzip.open(sys.argv[1], "wb") as f:
	f.write(data)
print(f"{len(entries)} blocks, {len(palette)} states")
