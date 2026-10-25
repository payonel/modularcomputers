#!/usr/bin/env python3
# Synthesizes a bot golem's thrusters' sounds, as Ogg Vorbis (see oggwrite.py): their ignition, a
# sputtering cough rising to the roar; and the roar while they fire, a loop, grumbly (the user's: not
# the field's clean hum): deep rumbling noise (heavily low-passed), a growl well below the field's
# pitch (a rough, saw-like tone), and an uneven sputter (the rumble's loudness wandering), made to
# repeat without a seam (its noise faded across the join, its tones whole cycles). The .ogg files
# are committed; rerun after changing them.
#   scripts/art/thruster_sounds.py common/src/main/resources/assets/modularcomputers/sounds
import math, os, random, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from oggwrite import write_ogg

RATE = 44100
GROWL = 55.0         # Hz: the growl's (whole cycles in the loop; the field hums at 110)
GROWL_HARMONICS = 8  # a saw-like tone: each harmonic 1/n, rough
THROB = 3.5          # Hz: how fast the roar lurches (whole cycles in the loop)
SPUTTER = 0.002      # how fast the sputter wanders: a very slow low-pass of noise
SPUTTER_DEPTH = 0.45 # how much the rumble's loudness sputters
LOOP_SECONDS = 2.0
IGNITION_SECONDS = 0.7
SMOOTH = 0.025       # the rumble's low-pass: how much of each new noise sample comes through (deep)
FADE_SECONDS = 0.25  # the loop's join: its end's noise faded into its start's
VOLUME = 0.5
GROWL_VOLUME = 0.28
CRACKLE = 0.3        # the ignition's crackle, its loudest

def rumble(frames, seed, smooth=SMOOTH):
	"""Low-passed noise (twice, for a deeper roll-off): a roar, not a hiss."""
	rnd = random.Random(seed)
	out, first, level = [], 0.0, 0.0
	for _ in range(frames):
		first += smooth * ((rnd.random() * 2 - 1) - first)
		level += smooth * (first - level)
		out.append(level)
	peak = max(abs(v) for v in out) or 1.0
	return [v / peak for v in out]

def growl(t):
	"""A rough, saw-like tone at GROWL."""
	value = sum(math.sin(2 * math.pi * n * GROWL * t) / n for n in range(1, GROWL_HARMONICS + 1))
	return value / 1.8

def ignition():
	rnd = random.Random(11)
	frames = int(RATE * IGNITION_SECONDS)
	noise = rumble(frames, 12)
	samples = []
	for i in range(frames):
		x = i / frames
		t = i / RATE
		swell = min(1.0, x * 4)  # it catches, and swells
		crackle = CRACKLE * (rnd.random() * 2 - 1) * (1 - x) if rnd.random() < 0.03 else 0.0
		cough = 0.6 + 0.4 * abs(math.sin(2 * math.pi * 9 * t * (1 - x)))  # (sputters, then catches)
		value = VOLUME * swell * cough * (noise[i] * 0.8 + GROWL_VOLUME * growl(t) * x) + crackle
		samples.append(max(-1.0, min(1.0, value)))
	return samples

def roar():
	frames = int(RATE * LOOP_SECONDS)
	fade = int(RATE * FADE_SECONDS)
	noise = rumble(frames + fade, 13)
	sputter = rumble(frames + fade, 14, SPUTTER)
	samples = []
	for i in range(frames):
		t = i / RATE
		n, s = noise[i], sputter[i]
		if i < fade:  # the end's faded in under the start's: the loop has no seam
			w = i / fade
			n = n * w + noise[frames + i] * (1 - w)
			s = s * w + sputter[frames + i] * (1 - w)
		lurch = 0.8 + 0.2 * math.sin(2 * math.pi * THROB * t)
		loud = (1 - SPUTTER_DEPTH) + SPUTTER_DEPTH * (0.5 + 0.5 * s)
		samples.append(max(-1.0, min(1.0, VOLUME * 0.8 * lurch * loud * n + VOLUME * GROWL_VOLUME * loud * growl(t))))
	return samples

OUT = sys.argv[1]
write_ogg(f"{OUT}/thruster_on.ogg", ignition())
write_ogg(f"{OUT}/thruster_roar.ogg", roar())
