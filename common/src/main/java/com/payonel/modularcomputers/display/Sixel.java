package com.payonel.modularcomputers.display;

/**
 * A sixel picture as it's decoded (DEC's VT340 graphics: {@code DCS P1;P2;P3 q ... ST}): each data
 * character, {@code ?} to {@code ~}, is six pixels down (its value less 63, the low bit on top), in
 * the color picked; {@code !n} repeats the next one n times; {@code $} goes back to the band's left
 * edge; {@code -} goes to the next band, six pixels down; {@code #c} picks color register c, and
 * {@code #c;2;r;g;b} (percent) or {@code #c;1;h;l;s} (DEC's HLS: hue 0 is blue) sets it first;
 * {@code "a;b;w;h} gives its size. Registers start as the VT340's 16 colors, the rest black, each
 * picture with its own (xterm's private registers). Where nothing is drawn: register 0's color,
 * unless P2 is 1 (then it shows what's beneath). It's at most {@code maxWidth} by
 * {@code maxHeight} pixels (its screen's): what's past that is dropped.
 */
public final class Sixel {
	/** Color registers (xterm and the VT340 have 256 for a picture). */
	public static final int REGISTERS = 256;
	/** The VT340's colors for registers 0-15, as RGB percents (DEC's table). */
	private static final int[][] VT340 = {{0, 0, 0}, {20, 20, 80}, {80, 13, 13}, {20, 80, 20}, {80, 20, 80}, {20, 80, 80}, {80, 80, 20},
		{53, 53, 53}, {26, 26, 26}, {33, 33, 60}, {60, 26, 26}, {33, 60, 33}, {60, 33, 60}, {33, 60, 60}, {60, 60, 33}, {80, 80, 80}};
	/** Pixels a data character draws, down. */
	private static final int BAND = 6;
	private static final int TRANSPARENT = 0;
	private static final int OPAQUE = 0xFF000000;

	private final int maxWidth;
	private final int maxHeight;
	private final boolean transparent;
	private final int[] palette = new int[REGISTERS];
	/** Its pixels so far, row by row at {@code maxWidth} each (0: none drawn). */
	private final int[] pixels;
	private int width;
	private int height;
	private int x;
	private int band;
	private int color;
	private int repeat = 1;
	/** A command it's reading the numbers of: '#', '!', '"', or 0 for none. */
	private char command;
	private final StringBuilder numbers = new StringBuilder();

	/** {@code p2}: the DCS's second parameter (1: transparent where nothing's drawn). */
	public Sixel(int p2, int maxWidth, int maxHeight) {
		this.transparent = p2 == 1;
		this.maxWidth = Math.max(1, maxWidth);
		this.maxHeight = Math.max(1, maxHeight);
		this.pixels = new int[this.maxWidth * this.maxHeight];
		for (int i = 0; i < VT340.length; i++) {
			palette[i] = rgb(VT340[i][0], VT340[i][1], VT340[i][2]);
		}
	}

	/** One character of its data (the DCS's body, past its {@code q}). */
	public void accept(char c) {
		if (command != 0) {
			if (c >= '0' && c <= '9' || c == ';') {
				numbers.append(c);
				return;
			}
			finishCommand();
		}
		switch (c) {
			case '#', '!', '"' -> {
				command = c;
				numbers.setLength(0);
			}
			case '$' -> x = 0;
			case '-' -> {
				x = 0;
				band++;
			}
			default -> {
				if (c >= '?' && c <= '~') {
					draw(c - '?');
				}
				// (anything else: ignored, as a VT340 ignores it)
			}
		}
	}

	private void finishCommand() {
		int[] args = numbers();
		switch (command) {
			case '!' -> repeat = Math.max(1, args.length > 0 ? args[0] : 1);
			case '#' -> {
				if (args.length > 0) {
					int register = Math.clamp(args[0], 0, REGISTERS - 1);
					if (args.length >= 5) {
						palette[register] = args[1] == 1 ? hls(args[2], args[3], args[4]) : rgb(args[2], args[3], args[4]);
					}
					color = register;
				}
			}
			case '"' -> {
				if (args.length >= 4) { // a;b;width;height: its size, filled now (not where it's transparent)
					width = Math.max(width, Math.min(args[2], maxWidth));
					height = Math.max(height, Math.min(args[3], maxHeight));
				}
			}
			default -> {
			}
		}
		command = 0;
	}

	private int[] numbers() {
		String text = numbers.toString();
		if (text.isEmpty()) {
			return new int[0];
		}
		String[] parts = text.split(";", -1);
		int[] values = new int[parts.length];
		for (int i = 0; i < parts.length; i++) {
			try {
				values[i] = parts[i].isEmpty() ? 0 : Integer.parseInt(parts[i]);
			} catch (NumberFormatException e) {
				values[i] = Integer.MAX_VALUE; // (too big: clamped where it's used)
			}
		}
		return values;
	}

	private void draw(int bits) {
		int count = repeat;
		repeat = 1;
		for (int i = 0; i < count && x < maxWidth; i++, x++) {
			for (int bit = 0; bit < BAND; bit++) {
				int y = band * BAND + bit;
				if ((bits & 1 << bit) != 0 && y < maxHeight) {
					pixels[y * maxWidth + x] = OPAQUE | palette[color];
					width = Math.max(width, x + 1);
					height = Math.max(height, y + 1);
				}
			}
		}
	}

	/** Its data ended: what it drew, {@link #width()} by {@link #height()}, 0xAARRGGBB, row by row (alpha 0 where it shows what's beneath). */
	public int[] finish() {
		if (command != 0) {
			finishCommand();
		}
		int[] out = new int[width * height];
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int pixel = pixels[y * maxWidth + x];
				out[y * width + x] = pixel != 0 ? pixel : transparent ? TRANSPARENT : OPAQUE | palette[0];
			}
		}
		return out;
	}

	public int width() {
		return width;
	}

	public int height() {
		return height;
	}

	/**
	 * Pixels ({@code width} across, 0xAARRGGBB, row by row) as a sixel picture, its own colors in
	 * its registers, transparent where alpha is 0 (P2 1): how a picture is sent on to another
	 * terminal. A color is said in percents, so it comes back within a step of itself.
	 */
	public static String encode(int[] pixels, int width) {
		int height = pixels.length / width;
		java.util.Map<Integer, Integer> registers = new java.util.LinkedHashMap<>();
		StringBuilder out = new StringBuilder("\u001BP0;1q\"1;1;").append(width).append(';').append(height);
		for (int pixel : pixels) {
			if ((pixel >>> 24) != 0 && !registers.containsKey(pixel & 0xFFFFFF) && registers.size() < REGISTERS) {
				int color = pixel & 0xFFFFFF;
				registers.put(color, registers.size());
				out.append('#').append(registers.get(color)).append(";2;").append(toPercent(color >> 16)).append(';')
					.append(toPercent(color >> 8)).append(';').append(toPercent(color));
			}
		}
		for (int top = 0; top < height; top += BAND) {
			boolean first = true;
			for (var register : registers.entrySet()) {
				StringBuilder line = new StringBuilder();
				boolean any = false;
				for (int x = 0; x < width; x++) {
					int bits = 0;
					for (int bit = 0; bit < BAND && top + bit < height; bit++) {
						int pixel = pixels[(top + bit) * width + x];
						if ((pixel >>> 24) != 0 && (pixel & 0xFFFFFF) == register.getKey()) {
							bits |= 1 << bit;
						}
					}
					any |= bits != 0;
					line.append((char) ('?' + bits));
				}
				if (any) {
					out.append(first ? "" : "$").append('#').append(register.getValue()).append(line);
					first = false;
				}
			}
			out.append('-');
		}
		return out.append("\u001B\\").toString();
	}

	private static int toPercent(int channel) {
		return Math.round((channel & 0xFF) * 100 / 255f);
	}

	/** A color from RGB percents. */
	private static int rgb(int r, int g, int b) {
		return percent(r) << 16 | percent(g) << 8 | percent(b);
	}

	private static int percent(int value) {
		return Math.round(Math.clamp(value, 0, 100) * 255 / 100f);
	}

	/** A color from DEC's HLS (hue in degrees, 0 blue, 120 red, 240 green; lightness and saturation in percent). */
	static int hls(int hue, int lightness, int saturation) {
		float h = ((Math.floorMod(hue, 360) + 240) % 360) / 360f; // (DEC's blue at 0; the usual red)
		float l = Math.clamp(lightness, 0, 100) / 100f;
		float s = Math.clamp(saturation, 0, 100) / 100f;
		if (s == 0) {
			int gray = Math.round(l * 255);
			return gray << 16 | gray << 8 | gray;
		}
		float q = l < 0.5f ? l * (1 + s) : l + s - l * s;
		float p = 2 * l - q;
		return channel(p, q, h + 1 / 3f) << 16 | channel(p, q, h) << 8 | channel(p, q, h - 1 / 3f);
	}

	private static int channel(float p, float q, float t) {
		t = t < 0 ? t + 1 : t > 1 ? t - 1 : t;
		float v = t < 1 / 6f ? p + (q - p) * 6 * t : t < 0.5f ? q : t < 2 / 3f ? p + (q - p) * (2 / 3f - t) * 6 : p;
		return Math.round(Math.clamp(v, 0, 1) * 255);
	}
}
