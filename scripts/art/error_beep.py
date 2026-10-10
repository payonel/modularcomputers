#!/usr/bin/env python3
# Synthesizes the computer's error beep and writes it as Ogg Vorbis through the system's
# libsndfile (no extra Python packages). The .ogg is committed; rerun after changing the sound.
#   scripts/art/error_beep.py src/main/resources/assets/modularcomputers/sounds/error_beep.ogg
#
# The sound: a short, low, buzzy square wave at a steady pitch, like a PC speaker's error beep.
# No vibrato or pitch bend (that reads as musical); a hard but click-free start and stop.
import ctypes, ctypes.util, math, sys

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

class SfInfo(ctypes.Structure):
	_fields_ = [("frames", ctypes.c_int64), ("samplerate", ctypes.c_int), ("channels", ctypes.c_int),
		("format", ctypes.c_int), ("sections", ctypes.c_int), ("seekable", ctypes.c_int)]

SF_FORMAT_OGG = 0x200000
SF_FORMAT_VORBIS = 0x0060
SFM_WRITE = 0x20
SFC_SET_VBR_ENCODING_QUALITY = 0x1300

def main(path):
	frames = int(RATE * DURATION)
	samples = (ctypes.c_float * frames)(*(sample(i / RATE) for i in range(frames)))
	lib = ctypes.CDLL(ctypes.util.find_library("sndfile") or "libsndfile.so.1")
	lib.sf_open.restype = ctypes.c_void_p
	lib.sf_open.argtypes = [ctypes.c_char_p, ctypes.c_int, ctypes.POINTER(SfInfo)]
	lib.sf_write_float.argtypes = [ctypes.c_void_p, ctypes.POINTER(ctypes.c_float), ctypes.c_int64]
	lib.sf_write_float.restype = ctypes.c_int64
	lib.sf_command.argtypes = [ctypes.c_void_p, ctypes.c_int, ctypes.c_void_p, ctypes.c_int]
	lib.sf_close.argtypes = [ctypes.c_void_p]
	lib.sf_strerror.restype = ctypes.c_char_p
	lib.sf_strerror.argtypes = [ctypes.c_void_p]
	# Mono: Minecraft only positions mono sounds in the world.
	info = SfInfo(0, RATE, 1, SF_FORMAT_OGG | SF_FORMAT_VORBIS, 0, 0)
	handle = lib.sf_open(path.encode(), SFM_WRITE, ctypes.byref(info))
	if not handle:
		sys.exit("sf_open failed: " + lib.sf_strerror(None).decode())
	quality = ctypes.c_double(0.6)
	lib.sf_command(handle, SFC_SET_VBR_ENCODING_QUALITY, ctypes.byref(quality), ctypes.sizeof(quality))
	written = lib.sf_write_float(handle, samples, frames)
	lib.sf_close(handle)
	if written != frames:
		sys.exit(f"wrote {written} of {frames} frames")

main(sys.argv[1])
