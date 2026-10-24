package com.payonel.modularcomputers.display;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

/**
 * What a text display shows: one character per cell, each with a foreground and background
 * color (0xRRGGBB). Blank cells hold a space in the default colors, white on black.
 * <p>
 * A cell may also hold a tile of pixels ({@link #TILE_WIDTH} by {@link #TILE_HEIGHT}, 0xAARRGGBB,
 * alpha 0 where the cell's background shows through): a picture drawn over it (sixel). A picture
 * lives in the cells it covers, so it scrolls and moves with them, text written over a cell
 * replaces its tile, and a screen never holds more than a screen of pixels. Tiles aren't changed
 * once set: they're shared, never copied.
 */
public final class TextBuffer {
	public static final int DEFAULT_FOREGROUND = 0xFFFFFF;
	public static final int DEFAULT_BACKGROUND = 0x000000;
	/**
	 * A foreground's bold attribute, in its top byte (colors are 24 bits): bold text is drawn in a
	 * bold font. It goes wherever the foreground goes (saves, the scrollback, players' copies).
	 */
	public static final int BOLD = 1 << 24;

	/** A foreground's color, without its attributes. */
	public static int color(int foreground) {
		return foreground & 0xFFFFFF;
	}

	/** Whether a foreground is bold. */
	public static boolean bold(int foreground) {
		return (foreground & BOLD) != 0;
	}
	/** A cell's pixels across, as a picture covers it (about the font's own cell). */
	public static final int TILE_WIDTH = 6;
	/** And down. */
	public static final int TILE_HEIGHT = 12;
	public static final int TILE_PIXELS = TILE_WIDTH * TILE_HEIGHT;

	private Resolution resolution;
	private char[][] chars;
	private int[][] foregrounds;
	private int[][] backgrounds;
	/** Each cell's tile of pixels, or null: none. */
	private int[][][] tiles;
	/** Counts changes to tiles only (a picture shown elsewhere is redrawn when it changes, not for every letter). */
	private int tileChanges;
	/** Counts changes, so whoever shows the buffer can tell when it needs sending again. */
	private int changes;
	/** Rows changed since {@link #takeDirtyRows()} last looked: what players' copies need. */
	private final BitSet dirty = new BitSet();

	public TextBuffer(Resolution resolution) {
		this.resolution = resolution;
		this.chars = new char[resolution.rows()][resolution.columns()];
		this.foregrounds = new int[resolution.rows()][resolution.columns()];
		this.backgrounds = new int[resolution.rows()][resolution.columns()];
		this.tiles = new int[resolution.rows()][resolution.columns()][];
		clear();
	}

	public Resolution resolution() {
		return resolution;
	}

	/**
	 * Another size: the cells that still fit stay where they are (the top left), the rest are
	 * dropped, and new cells are blank (as xterm's resize: nothing is wrapped again). Every row is
	 * changed for whoever shows it.
	 */
	public void resize(Resolution size) {
		if (size.equals(resolution)) {
			return;
		}
		char[][] oldChars = chars;
		int[][] oldForegrounds = foregrounds;
		int[][] oldBackgrounds = backgrounds;
		int[][][] oldTiles = tiles;
		Resolution old = resolution;
		resolution = size;
		chars = new char[size.rows()][size.columns()];
		foregrounds = new int[size.rows()][size.columns()];
		backgrounds = new int[size.rows()][size.columns()];
		tiles = new int[size.rows()][size.columns()][];
		clear();
		int columns = Math.min(old.columns(), size.columns());
		for (int row = 0; row < Math.min(old.rows(), size.rows()); row++) {
			System.arraycopy(oldChars[row], 0, chars[row], 0, columns);
			System.arraycopy(oldForegrounds[row], 0, foregrounds[row], 0, columns);
			System.arraycopy(oldBackgrounds[row], 0, backgrounds[row], 0, columns);
			System.arraycopy(oldTiles[row], 0, tiles[row], 0, columns);
		}
		tileChanges++;
		changes++;
		dirty.clear();
		dirty.set(0, size.rows());
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

	/** Sets one cell (its tile goes: text replaces a picture). Cells outside the buffer are ignored. */
	public void set(int column, int row, char c, int foreground, int background) {
		if (row >= 0 && row < resolution.rows() && column >= 0 && column < resolution.columns()) {
			changes++;
			dirty.set(row);
			chars[row][column] = c;
			foregrounds[row][column] = foreground;
			backgrounds[row][column] = background;
			if (tiles[row][column] != null) {
				tiles[row][column] = null;
				tileChanges++;
			}
		}
	}

	/** A cell's tile of pixels ({@link #TILE_PIXELS}, 0xAARRGGBB, row by row), or null. Not to be changed. */
	public int[] tile(int column, int row) {
		return tiles[row][column];
	}

	/** Puts a tile on a cell (null: none), over its text: the cell's character becomes a space. */
	public void setTile(int column, int row, int[] pixels) {
		if (row >= 0 && row < resolution.rows() && column >= 0 && column < resolution.columns()
			&& (pixels == null || pixels.length == TILE_PIXELS)) {
			changes++;
			tileChanges++;
			dirty.set(row);
			chars[row][column] = ' ';
			tiles[row][column] = pixels;
		}
	}

	/** Goes up whenever a tile changes. */
	public int tileChanges() {
		return tileChanges;
	}

	/** Whether any cell has a tile. */
	public boolean hasTiles() {
		for (int[][] row : tiles) {
			for (int[] tile : row) {
				if (tile != null) {
					return true;
				}
			}
		}
		return false;
	}

	/** Moves one cell's contents (character, colors, tile) onto another, as inserting and deleting do. */
	public void copy(int fromColumn, int fromRow, int toColumn, int toRow) {
		int[] tile = tiles[fromRow][fromColumn];
		set(toColumn, toRow, chars[fromRow][fromColumn], foregrounds[fromRow][fromColumn], backgrounds[fromRow][fromColumn]);
		if (tile != null) {
			tiles[toRow][toColumn] = tile;
			tileChanges++;
		}
	}

	/** One row's tiles (each null where there's none), e.g. to send players. */
	public int[][] rowTiles(int row) {
		return tiles[row].clone();
	}

	/** Replaces one row's tiles, e.g. from the server; ignored if it doesn't fit. */
	public void setRowTiles(int row, int[][] rowTiles) {
		if (row < 0 || row >= resolution.rows() || rowTiles.length != resolution.columns()) {
			return;
		}
		for (int column = 0; column < rowTiles.length; column++) {
			if (rowTiles[column] != null && rowTiles[column].length != TILE_PIXELS) {
				return;
			}
		}
		changes++;
		tileChanges++;
		dirty.set(row);
		System.arraycopy(rowTiles, 0, tiles[row], 0, rowTiles.length);
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
		dirty.set(0, last + 1);
		for (int row = 0; row < last; row++) {
			System.arraycopy(chars[row + 1], 0, chars[row], 0, resolution.columns());
			System.arraycopy(foregrounds[row + 1], 0, foregrounds[row], 0, resolution.columns());
			System.arraycopy(backgrounds[row + 1], 0, backgrounds[row], 0, resolution.columns());
			System.arraycopy(tiles[row + 1], 0, tiles[row], 0, resolution.columns());
		}
		tileChanges++;
		fill(0, last, resolution.columns(), 1, foreground, background);
	}

	public String row(int row) {
		return new String(chars[row]);
	}

	/** The rows changed since the last call (and forgets them): what to send players. */
	public BitSet takeDirtyRows() {
		BitSet rows = (BitSet) dirty.clone();
		dirty.clear();
		return rows;
	}

	/** One row's foreground colors. */
	public int[] rowForegrounds(int row) {
		return foregrounds[row].clone();
	}

	/** One row's background colors. */
	public int[] rowBackgrounds(int row) {
		return backgrounds[row].clone();
	}

	/** Replaces one row's cells, e.g. from the server; ignored if it doesn't fit. */
	public void setRow(int row, String text, int[] foreground, int[] background) {
		int columns = resolution.columns();
		if (row < 0 || row >= resolution.rows() || text.length() != columns || foreground.length != columns || background.length != columns) {
			return;
		}
		changes++;
		dirty.set(row);
		java.util.Arrays.fill(tiles[row], null); // (its tiles come with them: setRowTiles)
		tileChanges++;
		text.getChars(0, columns, chars[row], 0);
		System.arraycopy(foreground, 0, foregrounds[row], 0, columns);
		System.arraycopy(background, 0, backgrounds[row], 0, columns);
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
		dirty.set(0, resolution.rows());
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
		dirty.set(0, resolution.rows());
		for (int row = 0; row < resolution.rows(); row++) {
			System.arraycopy(foreground, row * resolution.columns(), foregrounds[row], 0, resolution.columns());
			System.arraycopy(background, row * resolution.columns(), backgrounds[row], 0, resolution.columns());
		}
	}

	/** True when there is nothing to draw: only spaces on the default background. */
	public boolean isBlank() {
		for (int row = 0; row < resolution.rows(); row++) {
			for (int x = 0; x < resolution.columns(); x++) {
				if (chars[row][x] != ' ' || backgrounds[row][x] != DEFAULT_BACKGROUND || tiles[row][x] != null) {
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
