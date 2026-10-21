#!/usr/bin/env python3
# Synthesizes the sound of a computer dying when its case runs out of power, as Ogg Vorbis (see
# oggwrite.py). The .ogg is committed; rerun after changing the sound.
#   scripts/art/power_down.py common/src/main/resources/assets/modularcomputers/sounds/power_down.ogg
#
# The sound: the error beep's buzzy square wave (error_beep.py) starting at its pitch, then sagging
# deeper and fading out as the power drains away, like a speaker on a dying supply. The upper
# harmonics go first, so it dulls as it falls.
import math, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from oggwrite import write_ogg

RATE = 44100
START = 180.0       # Hz: the error beep's pitch
END = 45.0          # Hz: where it ends, a low growl
DURATION = 1.4      # seconds
HOLD = 0.12         # seconds at the beep's pitch before it sags
ATTACK = 0.004
VOLUME = 0.5

def frequency(t):
	if t < HOLD:
		return START
	# Sags fast at first, then slower: an exponential glide, like a capacitor draining.
	x = (t - HOLD) / (DURATION - HOLD)
	return END + (START - END) * math.exp(-4.0 * x)

def main(path):
	frames = int(RATE * DURATION)
	phase = 0.0
	samples = []
	for i in range(frames):
		t = i / RATE
		f = frequency(t)
		phase += 2 * math.pi * f / RATE
		x = max(0.0, (t - HOLD) / (DURATION - HOLD))
		cutoff = 5000 * math.exp(-3.0 * x) + 400  # the harmonics fade first
		value = 0.0
		n = 1
		while n * f < 8000:
			# A soft rolloff, so no harmonic switches off with a click.
			value += math.sin(n * phase) / n / (1 + (n * f / cutoff) ** 4)
			n += 2
		value *= 4 / math.pi
		fade = (1 - x) ** 2  # the fade out
		envelope = min(1.0, t / ATTACK) * fade
		samples.append(max(-1.0, min(1.0, VOLUME * envelope * value)))
	write_ogg(path, samples)

main(sys.argv[1])
