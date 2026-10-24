#!/usr/bin/env python3
# Writes the table of which subtitle each of Minecraft's sounds shows (sound id -> subtitle key),
# for the audio sensor: the game keeps that only in its sounds.json, an asset the launcher
# downloads for clients and a server never has (the subtitles' English text is in the server's own
# jar). Reads it from Loom's asset cache (a Fabric build downloads it). Sounds with no subtitle are
# left out: players get none for them, so the sensor reports none. Rerun when Minecraft changes:
#   scripts/data/sound_subtitles.py 26.3 common/src/main/resources/data/modularcomputers/sound_subtitles.json
import glob, json, os, sys

VERSION, OUT = sys.argv[1], sys.argv[2]
CACHE = os.path.expanduser("~/.gradle/caches/fabric-loom/assets")

indexes = sorted(glob.glob(f"{CACHE}/indexes/{VERSION}-*.json"), key=os.path.getmtime)
if not indexes:
    sys.exit(f"no asset index for {VERSION} in {CACHE}: build the Fabric project first")
objects = json.load(open(indexes[-1]))["objects"]
digest = objects["minecraft/sounds.json"]["hash"]
sounds = json.load(open(f"{CACHE}/objects/{digest[:2]}/{digest}"))
table = {f"minecraft:{sound}": event["subtitle"] for sound, event in sorted(sounds.items()) if "subtitle" in event}
with open(OUT, "w") as f:
    json.dump(table, f, indent="\t", sort_keys=True)
    f.write("\n")
print(f"{len(table)} of {len(sounds)} sounds have subtitles")
