package com.payonel.modularcomputers.display;

import java.util.Arrays;

/**
 * Cells' tiles of pixels ({@link TextBuffer#tile}) as numbers, for sending players and saving: for
 * each cell that has one, its place, then its pixels in runs of one color (a count, the color),
 * since a picture's pixels run in colors. A row's are placed by column; a screen's by cell (row by
 * row). What doesn't decode (a broken or older form) is dropped.
 */
public final class Tiles {
	private Tiles() {
	}

	/** A row's tiles: [column, runs, (count, color)...] for each cell with one. */
	public static int[] row(int[][] tiles) {
		IntList out = new IntList();
		for (int column = 0; column < tiles.length; column++) {
			if (tiles[column] != null) {
				out.add(column);
				runs(tiles[column], out);
			}
		}
		return out.toArray();
	}

	/** A row's tiles from {@link #row(int[][])}, {@code columns} wide (null where none). */
	public static int[][] row(int[] encoded, int columns) {
		int[][] tiles = new int[columns][];
		int at = 0;
		while (at < encoded.length) {
			int column = encoded[at++];
			int[] tile = new int[TextBuffer.TILE_PIXELS];
			at = unrun(encoded, at, tile);
			if (at < 0 || column < 0 || column >= columns) {
				return new int[columns][]; // (broken: none)
			}
			tiles[column] = tile;
		}
		return tiles;
	}

	/** A screen's tiles: [cell, runs, (count, color)...] for each cell with one, cells numbered row by row. */
	public static int[] screen(TextBuffer buffer) {
		IntList out = new IntList();
		int columns = buffer.resolution().columns();
		for (int row = 0; row < buffer.resolution().rows(); row++) {
			for (int column = 0; column < columns; column++) {
				int[] tile = buffer.tile(column, row);
				if (tile != null) {
					out.add(row * columns + column);
					runs(tile, out);
				}
			}
		}
		return out.toArray();
	}

	/** Puts {@link #screen(TextBuffer)}'s tiles back on a buffer of the same size. */
	public static void screen(int[] encoded, TextBuffer buffer) {
		int columns = buffer.resolution().columns();
		int cells = columns * buffer.resolution().rows();
		int at = 0;
		while (at < encoded.length) {
			int cell = encoded[at++];
			int[] tile = new int[TextBuffer.TILE_PIXELS];
			at = unrun(encoded, at, tile);
			if (at < 0 || cell < 0 || cell >= cells) {
				return;
			}
			int[][] row = buffer.rowTiles(cell / columns);
			row[cell % columns] = tile;
			buffer.setRowTiles(cell / columns, row);
		}
	}

	private static void runs(int[] tile, IntList out) {
		int countAt = out.size();
		out.add(0);
		int runs = 0;
		for (int i = 0; i < tile.length; ) {
			int j = i;
			while (j < tile.length && tile[j] == tile[i]) {
				j++;
			}
			out.add(j - i);
			out.add(tile[i]);
			runs++;
			i = j;
		}
		out.set(countAt, runs);
	}

	/** Reads a tile's runs from {@code at} into {@code tile}: where it ends, or -1 if they don't make a tile. */
	private static int unrun(int[] encoded, int at, int[] tile) {
		if (at >= encoded.length) {
			return -1;
		}
		int runs = encoded[at++];
		int filled = 0;
		for (int r = 0; r < runs; r++) {
			if (at + 1 >= encoded.length) {
				return -1;
			}
			int count = encoded[at++];
			int color = encoded[at++];
			if (count <= 0 || filled + count > tile.length) {
				return -1;
			}
			Arrays.fill(tile, filled, filled + count, color);
			filled += count;
		}
		return filled == tile.length ? at : -1;
	}

	/** A growing list of ints. */
	private static final class IntList {
		private int[] values = new int[64];
		private int size;

		void add(int value) {
			if (size == values.length) {
				values = Arrays.copyOf(values, size * 2);
			}
			values[size++] = value;
		}

		void set(int index, int value) {
			values[index] = value;
		}

		int size() {
			return size;
		}

		int[] toArray() {
			return Arrays.copyOf(values, size);
		}
	}
}
