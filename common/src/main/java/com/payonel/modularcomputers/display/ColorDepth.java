package com.payonel.modularcomputers.display;

/**
 * How many colors a terminal shows. A program's colors (SGR) are decoded the same everywhere;
 * a terminal shows what it can of them, as a real monochrome or 16-color terminal does.
 */
public enum ColorDepth {
	/** Text only: every cell in the default colors. */
	MONOCHROME,
	/** The 16 basic colors: 256-color and 24-bit colors show as the nearest of them. */
	SIXTEEN,
	/** Every 24-bit color. */
	TRUE_COLOR;

	/**
	 * A monochrome terminal's phosphor (user's pick, 2026-10-12: the tier 1 screen is green, as
	 * monochrome terminals were): its glass glows this color wherever text is lit.
	 */
	public static final int PHOSPHOR = 0x33FF66;
	/** A monochrome terminal's glass, unlit: a dark green. */
	public static final int PHOSPHOR_GLASS = 0x06210F;

	/**
	 * The color a cell's text glows on the glass: what the decoder settled on, except that a
	 * monochrome terminal's phosphor has one color. Drawing only; the cells keep their colors.
	 */
	public int glow(int rgb) {
		return this == MONOCHROME ? PHOSPHOR : rgb;
	}

	/** The glass's color where nothing is lit. */
	public int glass() {
		return this == MONOCHROME ? PHOSPHOR_GLASS : 0x000000;
	}

	/** How many colors it shows: 2 (text only), 16, or every 24-bit color. */
	public long colors() {
		return switch (this) {
			case MONOCHROME -> 2;
			case SIXTEEN -> 16;
			case TRUE_COLOR -> 1L << 24;
		};
	}

	/** The color this depth shows for {@code rgb}; {@code fallback} is the default it uses in monochrome. */
	public int show(int rgb, int fallback) {
		return switch (this) {
			case MONOCHROME -> fallback;
			case SIXTEEN -> VtDecoder.nearestBasic(rgb);
			case TRUE_COLOR -> rgb;
		};
	}
}
