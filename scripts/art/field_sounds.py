#!/usr/bin/env python3
# Synthesizes an energy field's sounds, as Ogg Vorbis (see oggwrite.py): its start, an electric hum
# rising up to its pitch with a crackle; its end, the hum sagging down and out (power_down.py's
# glide); and the soft hum round what it holds while it's on, a loop (whole cycles of every part, so
# it repeats without a seam). The .ogg files are committed; rerun after changing them.
#   scripts/art/field_sounds.py common/src/main/resources/assets/modularcomputers/sounds
import math, os, random, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from oggwrite import write_ogg

RATE = 44100
PITCH = 110.0       # Hz: the hum's (mains hum's neighbourhood: an electric one)
LOW = 40.0          # Hz: where the start rises from and the end sags to
HARMONICS = [1.0, 0.5, 0.35, 0.2, 0.12, 0.08]  # each harmonic's weight: a buzzy, transformer-like tone
START_SECONDS = 0.9
END_SECONDS = 1.1
LOOP_SECONDS = 2.0  # whole cycles of PITCH and of the warble below
WARBLE = 1.5        # Hz: how fast the loop's hum breathes (3 breaths in its 2 s)
VOLUME = 0.45
HUM_VOLUME = 0.25   # the loop's, softer
CRACKLE = 0.15      # the start's crackle, its loudest

def tone(phase, brightness):
	"""The hum at a phase: its harmonics, the higher ones scaled by brightness (0 to 1)."""
	value = 0.0
	for n, weight in enumerate(HARMONICS, start=1):
		value += weight * (brightness ** (n - 1)) * math.sin(n * phase)
	return value / sum(HARMONICS)

def start():
	rnd = random.Random(5)
	samples, phase = [], 0.0
	frames = int(RATE * START_SECONDS)
	for i in range(frames):
		x = i / frames
		f = LOW + (PITCH - LOW) * (1 - math.exp(-5 * x))  # rising, settling at its pitch
		phase += 2 * math.pi * f / RATE
		envelope = min(1.0, x * 6) * (1 - max(0.0, x - 0.8) / 0.2 * 0.5)  # (ends at the hum's level, half)
		crackle = CRACKLE * (rnd.random() * 2 - 1) * (1 - x) if rnd.random() < 0.02 else 0.0
		samples.append(max(-1.0, min(1.0, VOLUME * envelope * tone(phase, 0.3 + 0.7 * x) + crackle)))
	return samples

def end():
	samples, phase = [], 0.0
	frames = int(RATE * END_SECONDS)
	for i in range(frames):
		x = i / frames
		f = LOW + (PITCH - LOW) * math.exp(-3.5 * x)  # sagging, as a supply draining
		phase += 2 * math.pi * f / RATE
		envelope = 0.5 * (1 - x) ** 2
		samples.append(max(-1.0, min(1.0, VOLUME * envelope * tone(phase, 1 - 0.8 * x))))
	return samples

def hum():
	samples = []
	frames = int(RATE * LOOP_SECONDS)
	for i in range(frames):
		t = i / RATE
		phase = 2 * math.pi * PITCH * t
		breath = 0.85 + 0.15 * math.sin(2 * math.pi * WARBLE * t)
		samples.append(HUM_VOLUME * breath * tone(phase, 0.6))
	return samples

OUT = sys.argv[1]
write_ogg(f"{OUT}/field_on.ogg", start())
write_ogg(f"{OUT}/field_off.ogg", end())
write_ogg(f"{OUT}/field_hum.ogg", hum())
