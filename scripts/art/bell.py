#!/usr/bin/env python3
# Synthesizes the terminal's bell as Ogg Vorbis (see oggwrite.py): what a BEL (\a) rings, as a PC
# speaker's beep rang a terminal's bell. The .ogg is committed; rerun after changing the sound.
#   scripts/art/bell.py common/src/main/resources/assets/modularcomputers/sounds/bell.ogg
#
# The sound: a short, bright beep at a steady pitch, higher and softer than the error beep (a bell
# is a heads-up, not an error); a soft square, so it reads as a speaker's, not a chime's.
import math, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from oggwrite import write_ogg

RATE = 44100
FREQUENCY = 880.0   # Hz: A5, a PC speaker's usual beep is about here
DURATION = 0.12     # seconds
ATTACK = 0.003
RELEASE = 0.04
VOLUME = 0.32
HARMONICS_UP_TO = 4000  # Hz: a few odd harmonics, a square's edge without its harshness

def sample(t):
	value = 0.0
	n = 1
	while n * FREQUENCY < HARMONICS_UP_TO:
		value += math.sin(2 * math.pi * n * FREQUENCY * t) / n
		n += 2
	value *= 4 / math.pi
	envelope = min(1.0, t / ATTACK, (DURATION - t) / RELEASE)
	return max(-1.0, min(1.0, VOLUME * envelope * value))

def main(path):
	frames = int(RATE * DURATION)
	write_ogg(path, [sample(i / RATE) for i in range(frames)])

main(sys.argv[1])
