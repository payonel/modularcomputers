package com.payonel.modularcomputers.display;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

/**
 * What a text display shows: one character per cell (a code point: any of Unicode's), each with a
 * foreground and background color (0xRRGGBB). Blank cells hold a space in the default colors, white
 * on black. A wide character ({@link CharWidth}: CJK, most emoji) takes two cells: it's in the first,
 * the second holds {@link #WIDE} (its right half); writing over either half blanks the other, as
 * xterm does. A cell can also carry combining marks (an accent over its letter): they're its own.
 * A row as text ({@link #row}, saves, players' copies) is its cells in order, each its character
 * (WIDE, U+0000, for a right half) and then its marks: read back by the same widths.
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

	/** A wide character's right half, in the cell after it. */
	public static final int WIDE = 0;

	private Resolution resolution;
	private int[][] chars;
	/** Each cell's combining marks (code points, as a string), or null: none. */
	private String[][] marks;
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
		this.chars = new int[resolution.rows()][resolution.columns()];
		this.marks = new String[resolution.rows()][resolution.columns()];
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
		int[][] oldChars = chars;
		String[][] oldMarks = marks;
		int[][] oldForegrounds = foregrounds;
		int[][] oldBackgrounds = backgrounds;
		int[][][] oldTiles = tiles;
		Resolution old = resolution;
		resolution = size;
		chars = new int[size.rows()][size.columns()];
		marks = new String[size.rows()][size.columns()];
		foregrounds = new int[size.rows()][size.columns()];
		backgrounds = new int[size.rows()][size.columns()];
		tiles = new int[size.rows()][size.columns()][];
		clear();
		int columns = Math.min(old.columns(), size.columns());
		for (int row = 0; row < Math.min(old.rows(), size.rows()); row++) {
			System.arraycopy(oldChars[row], 0, chars[row], 0, columns);
			System.arraycopy(oldMarks[row], 0, marks[row], 0, columns);
			mend(columns - 1, row); // (a wide character cut in half at the new edge)
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

	/** Blanks a rectangle of cells (clipped to the buffer) in the given colors (a wide character half in it goes whole). */
	public void fill(int column, int row, int width, int height, int foreground, int background) {
		int left = Math.max(0, column);
		int right = Math.min(resolution.columns(), column + width);
		for (int y = Math.max(0, row); y < Math.min(resolution.rows(), row + height); y++) {
			for (int x = left; x < right; x++) {
				set(x, y, ' ', foreground, background);
			}
			if (left < right) {
				mend(left, y);
				mend(right - 1, y);
			}
		}
	}

	/** Writes {@code text} in the default colors starting at the given cell, a code point a cell. Text past the right edge is cut off. */
	public void write(int column, int row, String text) {
		int x = column;
		for (int i = 0; i < text.length(); i = text.offsetByCodePoints(i, 1)) {
			set(x++, row, text.codePointAt(i));
		}
	}

	/** A cell's character: a code point, {@link #WIDE} for a wide character's right half. */
	public int get(int column, int row) {
		return chars[row][column];
	}

	/** A cell's combining marks, as a string ("" for none). */
	public String marks(int column, int row) {
		String mark = marks[row][column];
		return mark == null ? "" : mark;
	}

	/** Adds a combining mark to a cell (its character's: it's drawn over it). */
	public void addMark(int column, int row, int mark) {
		if (row >= 0 && row < resolution.rows() && column >= 0 && column < resolution.columns()) {
			changes++;
			dirty.set(row);
			marks[row][column] = marks(column, row) + Character.toString(mark);
		}
	}

	/**
	 * Mends the wide characters about a cell just written: a right half whose left isn't a wide
	 * character, or a wide character whose right half isn't beside it, becomes a space (xterm's:
	 * writing over half a wide character blanks the other).
	 */
	public void mend(int column, int row) {
		if (row < 0 || row >= resolution.rows()) {
			return;
		}
		for (int x = Math.max(0, column - 1); x <= Math.min(resolution.columns() - 1, column + 1); x++) {
			int c = chars[row][x];
			boolean orphan = c == WIDE ? x == 0 || chars[row][x - 1] == WIDE || CharWidth.of(chars[row][x - 1]) != 2
				: CharWidth.of(c) == 2 && (x + 1 >= resolution.columns() || chars[row][x + 1] != WIDE);
			if (orphan) {
				chars[row][x] = ' ';
				marks[row][x] = null;
				changes++;
				dirty.set(row);
			}
		}
	}

	/** Mends every wide character on a row (after its cells moved: inserting, deleting). */
	public void mendRow(int row) {
		for (int x = 0; x < resolution.columns(); x += 2) {
			mend(x, row);
		}
	}

	public int foreground(int column, int row) {
		return foregrounds[row][column];
	}

	public int background(int column, int row) {
		return backgrounds[row][column];
	}

	/** Sets one cell in the default colors. Cells outside the buffer are ignored. */
	public void set(int column, int row, int c) {
		set(column, row, c, DEFAULT_FOREGROUND, DEFAULT_BACKGROUND);
	}

	/** Sets one cell (its tile and marks go: text replaces a picture). Cells outside the buffer are ignored. */
	public void set(int column, int row, int c, int foreground, int background) {
		if (row >= 0 && row < resolution.rows() && column >= 0 && column < resolution.columns()) {
			changes++;
			dirty.set(row);
			chars[row][column] = c;
			marks[row][column] = null;
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
			marks[row][column] = null;
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
		marks[toRow][toColumn] = marks[fromRow][fromColumn];
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
			System.arraycopy(marks[row + 1], 0, marks[row], 0, resolution.columns());
			System.arraycopy(foregrounds[row + 1], 0, foregrounds[row], 0, resolution.columns());
			System.arraycopy(backgrounds[row + 1], 0, backgrounds[row], 0, resolution.columns());
			System.arraycopy(tiles[row + 1], 0, tiles[row], 0, resolution.columns());
		}
		tileChanges++;
		fill(0, last, resolution.columns(), 1, foreground, background);
	}

	/** A row as text: its cells, each its character ({@link #WIDE} for a right half) and then its marks. */
	public String row(int row) {
		StringBuilder text = new StringBuilder(resolution.columns());
		for (int x = 0; x < resolution.columns(); x++) {
			text.appendCodePoint(chars[row][x]);
			if (marks[row][x] != null) {
				text.append(marks[row][x]);
			}
		}
		return text.toString();
	}

	/**
	 * A row's text, as {@link #row} writes it, as its cells: their characters and their marks (null
	 * where none), however many there are (a mark before any character is a cell of its own).
	 */
	public record Cells(int[] chars, String[] marks) {
		public int count() {
			return chars.length;
		}
	}

	public static Cells cells(String text) {
		List<Integer> chars = new ArrayList<>();
		List<String> marks = new ArrayList<>();
		for (int i = 0; i < text.length(); i = text.offsetByCodePoints(i, 1)) {
			int c = text.codePointAt(i);
			if (c != WIDE && CharWidth.of(c) == 0 && !chars.isEmpty()) {
				int last = marks.size() - 1;
				marks.set(last, (marks.get(last) == null ? "" : marks.get(last)) + Character.toString(c));
			} else {
				chars.add(c);
				marks.add(null);
			}
		}
		return new Cells(chars.stream().mapToInt(Integer::intValue).toArray(), marks.toArray(String[]::new));
	}

	/** A row's text made {@code columns} cells: cut, or padded with spaces (a wide character cut in half goes). */
	public static String fit(String text, int columns) {
		Cells cells = cells(text);
		StringBuilder out = new StringBuilder();
		for (int x = 0; x < columns; x++) {
			if (x < cells.count()) {
				int c = cells.chars()[x];
				boolean cut = CharWidth.of(c) == 2 && x == columns - 1 && c != WIDE;
				out.appendCodePoint(cut ? ' ' : c);
				if (!cut && cells.marks()[x] != null) {
					out.append(cells.marks()[x]);
				}
			} else {
				out.append(' ');
			}
		}
		return out.toString();
	}

	/** Sets a row's cells from its text ({@link #row}'s); false if it isn't the row's width. */
	private boolean putRow(int row, String text) {
		Cells cells = cells(text);
		if (cells.count() != resolution.columns()) {
			return false;
		}
		System.arraycopy(cells.chars(), 0, chars[row], 0, cells.count());
		System.arraycopy(cells.marks(), 0, marks[row], 0, cells.count());
		return true;
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
		if (row < 0 || row >= resolution.rows() || foreground.length != columns || background.length != columns || !putRow(row, text)) {
			return;
		}
		changes++;
		dirty.set(row);
		java.util.Arrays.fill(tiles[row], null); // (its tiles come with them: setRowTiles)
		tileChanges++;
		System.arraycopy(foreground, 0, foregrounds[row], 0, columns);
		System.arraycopy(background, 0, backgrounds[row], 0, columns);
	}

	/** Every row as text ({@link #row}'s). */
	public List<String> rows() {
		List<String> result = new ArrayList<>(resolution.rows());
		for (int row = 0; row < resolution.rows(); row++) {
			result.add(row(row));
		}
		return result;
	}

	/** Replaces the characters, e.g. from a save. Rows and text that don't fit are dropped. */
	public void setRows(List<String> contents) {
		changes++;
		dirty.set(0, resolution.rows());
		for (int row = 0; row < resolution.rows(); row++) {
			putRow(row, fit(row < contents.size() ? contents.get(row) : "", resolution.columns()));
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
