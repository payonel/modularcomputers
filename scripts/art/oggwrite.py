# Writes mono float samples as Ogg Vorbis through the system's libsndfile (ctypes; no extra
# Python packages). Shared by the sound scripts in this directory.
import ctypes, ctypes.util, sys

RATE = 44100

class _SfInfo(ctypes.Structure):
	_fields_ = [("frames", ctypes.c_int64), ("samplerate", ctypes.c_int), ("channels", ctypes.c_int),
		("format", ctypes.c_int), ("sections", ctypes.c_int), ("seekable", ctypes.c_int)]

_SF_FORMAT_OGG = 0x200000
_SF_FORMAT_VORBIS = 0x0060
_SFM_WRITE = 0x20
_SFC_SET_VBR_ENCODING_QUALITY = 0x1300

def write_ogg(path, samples, rate=RATE, quality=0.6):
	"""Mono only: Minecraft positions mono sounds in the world."""
	lib = ctypes.CDLL(ctypes.util.find_library("sndfile") or "libsndfile.so.1")
	lib.sf_open.restype = ctypes.c_void_p
	lib.sf_open.argtypes = [ctypes.c_char_p, ctypes.c_int, ctypes.POINTER(_SfInfo)]
	lib.sf_write_float.argtypes = [ctypes.c_void_p, ctypes.POINTER(ctypes.c_float), ctypes.c_int64]
	lib.sf_write_float.restype = ctypes.c_int64
	lib.sf_command.argtypes = [ctypes.c_void_p, ctypes.c_int, ctypes.c_void_p, ctypes.c_int]
	lib.sf_close.argtypes = [ctypes.c_void_p]
	lib.sf_strerror.restype = ctypes.c_char_p
	lib.sf_strerror.argtypes = [ctypes.c_void_p]
	info = _SfInfo(0, rate, 1, _SF_FORMAT_OGG | _SF_FORMAT_VORBIS, 0, 0)
	handle = lib.sf_open(path.encode(), _SFM_WRITE, ctypes.byref(info))
	if not handle:
		sys.exit("sf_open failed: " + lib.sf_strerror(None).decode())
	q = ctypes.c_double(quality)
	lib.sf_command(handle, _SFC_SET_VBR_ENCODING_QUALITY, ctypes.byref(q), ctypes.sizeof(q))
	data = (ctypes.c_float * len(samples))(*[max(-1.0, min(1.0, s)) for s in samples])
	written = lib.sf_write_float(handle, data, len(samples))
	lib.sf_close(handle)
	if written != len(samples):
		sys.exit(f"wrote {written} of {len(samples)} frames")
