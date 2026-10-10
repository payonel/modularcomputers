package com.payonel.modularcomputers.display;

import java.util.Arrays;

/** The characters shown on a text display, one per cell. Blank cells hold a space. */
public final class TextBuffer {
	private final Resolution resolution;
	private final char[][] rows;

	public TextBuffer(Resolution resolution) {
		this.resolution = resolution;
		this.rows = new char[resolution.rows()][resolution.columns()];
		clear();
	}

	public Resolution resolution() {
		return resolution;
	}

	public void clear() {
		for (char[] row : rows) {
			Arrays.fill(row, ' ');
		}
	}

	/** Writes {@code text} starting at the given cell. Text past the right edge is cut off. */
	public void write(int column, int row, String text) {
		if (row < 0 || row >= rows.length) {
			return;
		}
		for (int i = 0; i < text.length(); i++) {
			int x = column + i;
			if (x >= 0 && x < resolution.columns()) {
				rows[row][x] = text.charAt(i);
			}
		}
	}

	public char get(int column, int row) {
		return rows[row][column];
	}

	public String row(int row) {
		return new String(rows[row]);
	}

	public boolean isBlank() {
		for (char[] row : rows) {
			for (char c : row) {
				if (c != ' ') {
					return false;
				}
			}
		}
		return true;
	}
}
