package com.payonel.modularcomputers.item;

/** Sizes for players, in binary units: "256 KiB", "16 MiB". */
final class Bytes {
	private Bytes() {
	}

	static String format(long bytes) {
		if (bytes >= 1 << 20 && bytes % (1 << 20) == 0) {
			return (bytes >> 20) + " MiB";
		}
		if (bytes >= 1 << 10 && bytes % (1 << 10) == 0) {
			return (bytes >> 10) + " KiB";
		}
		return bytes + " B";
	}
}
