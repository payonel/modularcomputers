package com.payonel.modularcomputers.display;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

	/** Sets one cell. Cells outside the buffer are ignored. */
	public void set(int column, int row, char c) {
		if (row >= 0 && row < rows.length && column >= 0 && column < resolution.columns()) {
			rows[row][column] = c;
		}
	}

	/** Moves rows 1..count-1 up by one and blanks row count-1. Rows from count on don't move. */
	public void scrollUp(int count) {
		int last = Math.min(count, rows.length) - 1;
		if (last < 0) {
			return;
		}
		for (int row = 0; row < last; row++) {
			System.arraycopy(rows[row + 1], 0, rows[row], 0, rows[row].length);
		}
		Arrays.fill(rows[last], ' ');
	}

	public String row(int row) {
		return new String(rows[row]);
	}

	public List<String> rows() {
		List<String> result = new ArrayList<>(rows.length);
		for (char[] row : rows) {
			result.add(new String(row));
		}
		return result;
	}

	/** Replaces the contents, e.g. from a save. Rows and text that don't fit are dropped. */
	public void setRows(List<String> contents) {
		clear();
		for (int row = 0; row < contents.size(); row++) {
			write(0, row, contents.get(row));
		}
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
