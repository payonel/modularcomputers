package com.payonel.modularcomputers.display;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * What a text display shows: one character per cell, each with a foreground and background
 * color (0xRRGGBB). Blank cells hold a space in the default colors, white on black.
 */
public final class TextBuffer {
	public static final int DEFAULT_FOREGROUND = 0xFFFFFF;
	public static final int DEFAULT_BACKGROUND = 0x000000;

	private final Resolution resolution;
	private final char[][] chars;
	private final int[][] foregrounds;
	private final int[][] backgrounds;
	/** Counts changes, so whoever shows the buffer can tell when it needs sending again. */
	private int changes;

	public TextBuffer(Resolution resolution) {
		this.resolution = resolution;
		this.chars = new char[resolution.rows()][resolution.columns()];
		this.foregrounds = new int[resolution.rows()][resolution.columns()];
		this.backgrounds = new int[resolution.rows()][resolution.columns()];
		clear();
	}

	public Resolution resolution() {
		return resolution;
	}

	/** Goes up whenever a cell changes (or might have). */
	public int changes() {
		return changes;
	}

	public void clear() {
		fill(0, 0, resolution.columns(), resolution.rows(), DEFAULT_FOREGROUND, DEFAULT_BACKGROUND);
	}

	/** Blanks a rectangle of cells (clipped to the buffer) in the given colors. */
	public void fill(int column, int row, int width, int height, int foreground, int background) {
		for (int y = Math.max(0, row); y < Math.min(resolution.rows(), row + height); y++) {
			for (int x = Math.max(0, column); x < Math.min(resolution.columns(), column + width); x++) {
				set(x, y, ' ', foreground, background);
			}
		}
	}

	/** Writes {@code text} in the default colors starting at the given cell. Text past the right edge is cut off. */
	public void write(int column, int row, String text) {
		for (int i = 0; i < text.length(); i++) {
			set(column + i, row, text.charAt(i));
		}
	}

	public char get(int column, int row) {
		return chars[row][column];
	}

	public int foreground(int column, int row) {
		return foregrounds[row][column];
	}

	public int background(int column, int row) {
		return backgrounds[row][column];
	}

	/** Sets one cell in the default colors. Cells outside the buffer are ignored. */
	public void set(int column, int row, char c) {
		set(column, row, c, DEFAULT_FOREGROUND, DEFAULT_BACKGROUND);
	}

	/** Sets one cell. Cells outside the buffer are ignored. */
	public void set(int column, int row, char c, int foreground, int background) {
		if (row >= 0 && row < resolution.rows() && column >= 0 && column < resolution.columns()) {
			changes++;
			chars[row][column] = c;
			foregrounds[row][column] = foreground;
			backgrounds[row][column] = background;
		}
	}

	/**
	 * Moves rows 1..count-1 up by one and blanks row count-1 in the given colors. Rows from
	 * count on don't move.
	 */
	public void scrollUp(int count, int foreground, int background) {
		int last = Math.min(count, resolution.rows()) - 1;
		if (last < 0) {
			return;
		}
		changes++;
		for (int row = 0; row < last; row++) {
			System.arraycopy(chars[row + 1], 0, chars[row], 0, resolution.columns());
			System.arraycopy(foregrounds[row + 1], 0, foregrounds[row], 0, resolution.columns());
			System.arraycopy(backgrounds[row + 1], 0, backgrounds[row], 0, resolution.columns());
		}
		fill(0, last, resolution.columns(), 1, foreground, background);
	}

	public String row(int row) {
		return new String(chars[row]);
	}

	public List<String> rows() {
		List<String> result = new ArrayList<>(resolution.rows());
		for (char[] row : chars) {
			result.add(new String(row));
		}
		return result;
	}

	/** Replaces the characters, e.g. from a save. Rows and text that don't fit are dropped. */
	public void setRows(List<String> contents) {
		changes++;
		for (int row = 0; row < resolution.rows(); row++) {
			Arrays.fill(chars[row], ' ');
			if (row < contents.size()) {
				String text = contents.get(row);
				for (int x = 0; x < Math.min(text.length(), resolution.columns()); x++) {
					chars[row][x] = text.charAt(x);
				}
			}
		}
	}

	/** All foreground colors, row by row, e.g. for saving. */
	public int[] foregrounds() {
		return flatten(foregrounds);
	}

	/** All background colors, row by row, e.g. for saving. */
	public int[] backgrounds() {
		return flatten(backgrounds);
	}

	/** Replaces the colors from {@link #foregrounds()} and {@link #backgrounds()}; ignored if the sizes don't match. */
	public void setColors(int[] foreground, int[] background) {
		int cells = resolution.rows() * resolution.columns();
		if (foreground.length != cells || background.length != cells) {
			return;
		}
		changes++;
		for (int row = 0; row < resolution.rows(); row++) {
			System.arraycopy(foreground, row * resolution.columns(), foregrounds[row], 0, resolution.columns());
			System.arraycopy(background, row * resolution.columns(), backgrounds[row], 0, resolution.columns());
		}
	}

	/** True when there is nothing to draw: only spaces on the default background. */
	public boolean isBlank() {
		for (int row = 0; row < resolution.rows(); row++) {
			for (int x = 0; x < resolution.columns(); x++) {
				if (chars[row][x] != ' ' || backgrounds[row][x] != DEFAULT_BACKGROUND) {
					return false;
				}
			}
		}
		return true;
	}

	private int[] flatten(int[][] grid) {
		int[] result = new int[resolution.rows() * resolution.columns()];
		for (int row = 0; row < resolution.rows(); row++) {
			System.arraycopy(grid[row], 0, result, row * resolution.columns(), resolution.columns());
		}
		return result;
	}
}
