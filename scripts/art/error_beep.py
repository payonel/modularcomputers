#!/usr/bin/env python3
# Synthesizes the computer's error beep as Ogg Vorbis (see oggwrite.py). The .ogg is committed;
# rerun after changing the sound.
#   scripts/art/error_beep.py common/src/main/resources/assets/modularcomputers/sounds/error_beep.ogg
#
# The sound: a short, low, buzzy square wave at a steady pitch, like a PC speaker's error beep.
# No vibrato or pitch bend (that reads as musical); a hard but click-free start and stop.
import math, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from oggwrite import write_ogg

RATE = 44100
FREQUENCY = 180.0   # Hz: low, but still a "beep" on small speakers
DURATION = 0.17     # seconds
ATTACK = 0.004
RELEASE = 0.03
VOLUME = 0.5

def sample(t):
	# Band-limited square wave: odd harmonics up to ~5 kHz, so it buzzes without aliasing.
	value = 0.0
	n = 1
	while n * FREQUENCY < 5000:
		value += math.sin(2 * math.pi * n * FREQUENCY * t) / n
		n += 2
	value *= 4 / math.pi
	envelope = min(1.0, t / ATTACK, (DURATION - t) / RELEASE)
	return max(-1.0, min(1.0, VOLUME * envelope * value))

def main(path):
	frames = int(RATE * DURATION)
	write_ogg(path, [sample(i / RATE) for i in range(frames)])

main(sys.argv[1])
