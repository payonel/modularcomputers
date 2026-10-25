package com.payonel.modularcomputers.display;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * How many cells a code point takes on a terminal (wcwidth, as glibc's): 0 for a combining mark or
 * a format character (it joins the cell before), 2 for a wide one (CJK, most emoji), else 1. The
 * table is mcos's own file ({@code /usr/share/wcwidth}, from scripts/data/wcwidth.py), so a
 * program measuring text with it and the screen showing it agree on every width.
 */
public final class CharWidth {
	/** The table, in the mod's Lua files: mcos's {@code /usr/share/wcwidth}. */
	private static final String TABLE = "/data/modularcomputers/lua/mcos/usr/share/wcwidth";
	/** Ranges that aren't width 1: first, last and width, three ints each, sorted. */
	private static final int[] RANGES = load();

	private CharWidth() {
	}

	/** A code point's width in cells: 0, 1 or 2. */
	public static int of(int codePoint) {
		int low = 0;
		int high = RANGES.length / 3 - 1;
		while (low <= high) {
			int middle = (low + high) >>> 1;
			if (codePoint < RANGES[middle * 3]) {
				high = middle - 1;
			} else if (codePoint > RANGES[middle * 3 + 1]) {
				low = middle + 1;
			} else {
				return RANGES[middle * 3 + 2];
			}
		}
		return 1;
	}

	private static int[] load() {
		try (InputStream in = CharWidth.class.getResourceAsStream(TABLE)) {
			if (in == null) {
				throw new IllegalStateException("no width table " + TABLE + " in the mod");
			}
			List<Integer> values = new ArrayList<>();
			for (String line : new String(in.readAllBytes(), StandardCharsets.US_ASCII).split("\n")) {
				if (line.isBlank() || line.startsWith("#")) {
					continue;
				}
				String[] fields = line.trim().split(" ");
				values.add(Integer.parseInt(fields[0], 16));
				values.add(Integer.parseInt(fields[1], 16));
				values.add(Integer.parseInt(fields[2]));
			}
			return values.stream().mapToInt(Integer::intValue).toArray();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
