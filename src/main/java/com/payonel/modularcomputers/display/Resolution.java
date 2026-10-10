package com.payonel.modularcomputers.display;

/** A text display's size in character cells. Each cell is a {@value #GLYPH_WIDTH}x{@value #GLYPH_HEIGHT} glyph. */
public record Resolution(int columns, int rows) {
	public static final int GLYPH_WIDTH = 8;
	public static final int GLYPH_HEIGHT = 16;
	public static final Resolution DEFAULT = new Resolution(50, 16);

	public Resolution {
		if (columns <= 0 || rows <= 0) {
			throw new IllegalArgumentException("resolution must be positive: " + columns + "x" + rows);
		}
	}

	/** Width over height of the rendered display in pixels. */
	public double aspectRatio() {
		return (double) (columns * GLYPH_WIDTH) / (rows * GLYPH_HEIGHT);
	}
}
