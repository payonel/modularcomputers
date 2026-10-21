#!/usr/bin/env python3
# Writes the structure every server game test runs in: 8x8x8 blocks of air, so placing it clears
# the test's area (Fabric's game test API had one built in; NeoForge has none, so the tests bring
# their own, the same on both loaders). Rerun after editing:
#   scripts/data/empty_test_structure.py common/src/gametest/resources/data/modularcomputers/structure/gametest/empty.nbt
import gzip, struct, sys

DATA_VERSION = 5023  # Minecraft 26.3 (version.json's world_version)
SIZE = 8             # x, y and z

END, INT, STRING, LIST, COMPOUND = 0, 3, 8, 9, 10

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

def ints(*values):
	return (LIST, (INT, list(values)))

blocks = [{"pos": ints(x, y, z), "state": (INT, 0)} for y in range(SIZE) for z in range(SIZE) for x in range(SIZE)]
root = {
	"DataVersion": (INT, DATA_VERSION),
	"size": ints(SIZE, SIZE, SIZE),
	"palette": (LIST, (COMPOUND, [{"id": (STRING, "minecraft:air")}])),  # 26.3's name: "id" (was "Name")
	"blocks": (LIST, (COMPOUND, blocks)),
	"entities": (LIST, (COMPOUND, [])),
}
with gzip.open(sys.argv[1], "wb") as f:
	f.write(struct.pack(">b", COMPOUND) + name("") + payload(COMPOUND, root))
print(f"{len(blocks)} blocks")
