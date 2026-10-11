#!/usr/bin/env python3
# Synthesizes the sounds of putting parts into the motherboard and taking them out, as Ogg
# Vorbis (see oggwrite.py). The .ogg files are committed; rerun after changing a sound.
#   scripts/art/part_sounds.py src/main/resources/assets/modularcomputers/sounds
#
# Everything is soft and low-passed: plastic and metal seating into sockets, not a UI click.
# Building blocks: a slide (filtered noise that swells), a click (a short, damped knock), a
# thump (a low decaying tone) and, for the battery, a tiny zap (a falling crackle).
import math, os, random, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from oggwrite import write_ogg, RATE

OUT = sys.argv[1]
rnd = random.Random(42)

def silence(seconds):
	return [0.0] * int(RATE * seconds)

def lowpass(samples, cutoff):
	"""One-pole low-pass: rounds off harsh edges."""
	a = math.exp(-2 * math.pi * cutoff / RATE)
	out, y = [], 0.0
	for x in samples:
		y = (1 - a) * x + a * y
		out.append(y)
	return out

def highpass(samples, cutoff):
	low = lowpass(samples, cutoff)
	return [x - l for x, l in zip(samples, low)]

def slide(seconds, volume=0.35, cutoff=1800, rising=True):
	"""Something sliding along a guide: noise that swells (or fades), softened."""
	n = int(RATE * seconds)
	noise = [rnd.uniform(-1, 1) for _ in range(n)]
	noise = lowpass(highpass(noise, 300), cutoff)
	out = []
	for i, x in enumerate(noise):
		t = i / n
		env = (t if rising else 1 - t) ** 1.5 * min(1, i / (0.004 * RATE), (n - i) / (0.004 * RATE))
		out.append(volume * 4 * env * x)
	return out

def click(volume=0.6, tone=2200, decay=0.006, cutoff=5000):
	"""A small, damped knock: a latch closing or a contact seating."""
	n = int(RATE * decay * 6)
	out = []
	for i in range(n):
		t = i / RATE
		body = math.sin(2 * math.pi * tone * t) * 0.6 + rnd.uniform(-1, 1) * 0.4
		out.append(volume * body * math.exp(-t / decay))
	return lowpass(out, cutoff)

def thump(volume=0.7, freq=140, decay=0.025):
	"""A low, short thud: something settling into place."""
	n = int(RATE * decay * 5)
	out = []
	for i in range(n):
		t = i / RATE
		f = freq * (1 + 0.6 * math.exp(-t / 0.006))  # a quick drop in pitch gives it weight
		out.append(volume * math.sin(2 * math.pi * f * t) * math.exp(-t / decay))
	return out

def metal_tick(volume=0.35, decay=0.012):
	"""A light metallic tick: a socket lever or a clip."""
	n = int(RATE * decay * 5)
	partials = [(1870, 1.0), (2940, 0.6), (4130, 0.35)]
	out = []
	for i in range(n):
		t = i / RATE
		out.append(volume * sum(a * math.sin(2 * math.pi * f * t) for f, a in partials) / 2 * math.exp(-t / decay))
	return lowpass(out, 6000)

def zap(seconds=0.075, volume=0.32):
	"""A tiny electric zap: a crackling buzz that falls in pitch."""
	n = int(RATE * seconds)
	out, phase = [], 0.0
	for i in range(n):
		t = i / n
		f = 2600 * (1 - t) + 500 * t
		phase += f / RATE
		saw = 2 * (phase % 1) - 1
		crackle = rnd.uniform(-1, 1) if rnd.random() < 0.35 else 0.0
		env = min(1, i / (0.002 * RATE)) * (1 - t) ** 2
		out.append(volume * env * (0.7 * saw + 0.5 * crackle))
	return lowpass(out, 7000)

def mix(*parts):
	"""Each part is (start seconds, samples)."""
	length = max(int(start * RATE) + len(s) for start, s in parts)
	out = [0.0] * length
	for start, s in parts:
		offset = int(start * RATE)
		for i, x in enumerate(s):
			out[offset + i] += x
	return out + silence(0.01)

SOUNDS = {
	# RAM: slides down the guides, the module seats, and both latches snap shut.
	"ram_insert": mix((0, slide(0.07)), (0.07, thump(0.45, 170)), (0.072, click(0.45, 2400)), (0.088, click(0.4, 2100))),
	"ram_remove": mix((0, click(0.4, 2300)), (0.012, click(0.35, 2000)), (0.03, slide(0.06, rising=False))),
	# CPU: dropped into the socket, then the retention lever closes.
	"cpu_insert": mix((0, thump(0.5, 220, 0.02)), (0.004, click(0.25, 1600, 0.004)), (0.12, metal_tick())),
	"cpu_remove": mix((0, metal_tick(0.3)), (0.1, click(0.3, 1700, 0.004))),
	# EEPROM: a chip pressed into its socket.
	"eeprom_insert": mix((0, click(0.4, 1300, 0.005, 3000)), (0.028, click(0.35, 1150, 0.005, 3000))),
	"eeprom_remove": mix((0, click(0.35, 1250, 0.005, 3000))),
	# Disk: a longer slide into the bay, then a solid clunk as the connector mates.
	"disk_insert": mix((0, slide(0.13, 0.3, 1400)), (0.13, thump(0.7, 110, 0.03)), (0.132, click(0.3, 1500))),
	"disk_remove": mix((0, click(0.3, 1500)), (0.01, thump(0.4, 120, 0.02)), (0.03, slide(0.11, 0.25, 1400, rising=False))),
	# Expansion card: a short slide, a firm seat, the bracket clip.
	"card_insert": mix((0, slide(0.05, 0.3)), (0.05, thump(0.55, 160)), (0.065, metal_tick(0.25, 0.008))),
	"card_remove": mix((0, metal_tick(0.25, 0.008)), (0.02, slide(0.05, 0.28, rising=False))),
	# UPS battery: a tiny zap as it connects; a soft click when it's pulled.
	"battery_insert": mix((0, click(0.3, 1400, 0.004)), (0.006, zap())),
	"battery_remove": mix((0, click(0.35, 1300, 0.005, 3000))),
}

for name, samples in SOUNDS.items():
	peak = max(abs(x) for x in samples)
	if peak > 0.95:
		samples = [x * 0.95 / peak for x in samples]
	write_ogg(f"{OUT}/{name}.ogg", samples)
	print(f"{name}: {len(samples) / RATE * 1000:.0f} ms, peak {min(peak, 0.95):.2f}")
