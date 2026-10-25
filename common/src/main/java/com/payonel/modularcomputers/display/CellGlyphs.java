package com.payonel.modularcomputers.display;

/**
 * Characters a terminal draws itself, as rectangles filling their cell, rather than as a font's
 * glyphs (kitty's, WezTerm's, Windows Terminal's): box drawing (U+2500 to U+257F: lines, corners,
 * tees and crosses, light, heavy, double, dashed, rounded and diagonal), so lines join from cell to
 * cell; block elements (U+2580 to U+259F: halves, eighths, quadrants, the three shades); and braille
 * (U+2800 to U+28FF: a 2 by 4 grid of dots, terminals' graphics). A box character's shape is read
 * from its Unicode name ("BOX DRAWINGS DOWN LIGHT AND RIGHT HEAVY"). Drawn in a cell's own pixels,
 * whole ones, so a line is the same thickness in every cell and meets the next.
 */
public final class CellGlyphs {
	private static final int BOX_FIRST = 0x2500;
	private static final int BOX_LAST = 0x257F;
	private static final int BLOCK_FIRST = 0x2580;
	private static final int BLOCK_LAST = 0x259F;
	private static final int BRAILLE_FIRST = 0x2800;
	private static final int BRAILLE_LAST = 0x28FF;
	/** A full shade, and the three shades' (light, medium, dark: a quarter, half, three quarters). */
	public static final int OPAQUE = 255;
	private static final int[] SHADES = {64, 128, 191};
	/** A light line is a cell's width over this, at least a pixel; a heavy one twice it. */
	private static final int LIGHT_PER_CELL = 8;
	/** A braille dot's side, as a share of its place in the cell's grid. */
	private static final double DOT = 0.6;

	/** An arm's weight. */
	private static final int NONE = 0;
	private static final int LIGHT = 1;
	private static final int HEAVY = 2;
	private static final int DOUBLE = 3;
	/** Arms' order in a shape: up, right, down, left. */
	private static final int UP = 0;
	private static final int RIGHT = 1;
	private static final int DOWN = 2;
	private static final int LEFT = 3;

	/** A box character's shape: its arms' weights, or its dashes or diagonal. */
	private record Shape(int[] arms, int dashes, boolean vertical, int diagonal, boolean arc) {
	}

	/** Diagonals: none, '/' (upper right to lower left), '\\' (upper left to lower right), both. */
	private static final int SLASH = 1;
	private static final int BACKSLASH = 2;

	private static final Shape[] BOXES = boxes();

	private CellGlyphs() {
	}

	/** Where a drawn character's rectangles go: pixels from its cell's top left, and how opaque (0 to 255). */
	public interface Sink {
		void rect(int x0, int y0, int x1, int y1, int alpha);
	}

	/** Whether it draws {@code c} itself. */
	public static boolean draws(int c) {
		return c >= BOX_FIRST && c <= BOX_LAST && BOXES[c - BOX_FIRST] != null || c >= BLOCK_FIRST && c <= BLOCK_LAST
			|| c >= BRAILLE_FIRST && c <= BRAILLE_LAST;
	}

	/** Draws {@code c} in a cell {@code width} by {@code height} pixels. */
	public static void draw(int c, int width, int height, Sink sink) {
		if (width <= 0 || height <= 0) {
			return;
		}
		if (c >= BOX_FIRST && c <= BOX_LAST && BOXES[c - BOX_FIRST] != null) {
			box(BOXES[c - BOX_FIRST], width, height, sink);
		} else if (c >= BLOCK_FIRST && c <= BLOCK_LAST) {
			block(c, width, height, sink);
		} else if (c >= BRAILLE_FIRST && c <= BRAILLE_LAST) {
			braille(c - BRAILLE_FIRST, width, height, sink);
		}
	}

	// ---- box drawing ----------------------------------------------------------------------

	private static Shape[] boxes() {
		Shape[] shapes = new Shape[BOX_LAST - BOX_FIRST + 1];
		for (int c = BOX_FIRST; c <= BOX_LAST; c++) {
			String name = Character.getName(c);
			if (name != null && name.startsWith("BOX DRAWINGS ")) {
				shapes[c - BOX_FIRST] = parse(name.substring("BOX DRAWINGS ".length()));
			}
		}
		return shapes;
	}

	private static int weight(String word) {
		return switch (word) {
			case "LIGHT", "SINGLE" -> LIGHT;
			case "HEAVY" -> HEAVY;
			case "DOUBLE" -> DOUBLE;
			default -> NONE;
		};
	}

	/** A box character's shape, from its name after "BOX DRAWINGS". */
	private static Shape parse(String name) {
		int[] arms = new int[4];
		if (name.contains("DIAGONAL")) {
			int diagonal = name.endsWith("CROSS") ? SLASH | BACKSLASH : name.contains("UPPER RIGHT TO LOWER LEFT") ? SLASH : BACKSLASH;
			return new Shape(arms, 0, false, diagonal, false);
		}
		if (name.contains("DASH")) {
			int dashes = name.contains("QUADRUPLE") ? 4 : name.contains("TRIPLE") ? 3 : 2;
			boolean vertical = name.endsWith("VERTICAL");
			int weight = name.startsWith("HEAVY") ? HEAVY : LIGHT;
			arms[vertical ? UP : LEFT] = weight;
			arms[vertical ? DOWN : RIGHT] = weight;
			return new Shape(arms, dashes, vertical, 0, false);
		}
		String[] segments = name.replace("ARC ", "").split(" AND ");
		int[] weights = new int[segments.length];
		for (int i = 0; i < segments.length; i++) {
			for (String word : segments[i].split(" ")) {
				weights[i] = Math.max(weights[i], weight(word));
			}
		}
		for (int i = 0; i < segments.length; i++) {
			if (weights[i] == NONE) {
				// (a segment with no weight takes the one before's: "LIGHT DOWN AND RIGHT"; the first, the next's)
				for (int j = i - 1; j >= 0 && weights[i] == NONE; j--) {
					weights[i] = weights[j];
				}
				for (int j = i + 1; j < segments.length && weights[i] == NONE; j++) {
					weights[i] = weights[j];
				}
			}
			for (String word : segments[i].split(" ")) {
				switch (word) {
					case "UP" -> arms[UP] = weights[i];
					case "DOWN" -> arms[DOWN] = weights[i];
					case "LEFT" -> arms[LEFT] = weights[i];
					case "RIGHT" -> arms[RIGHT] = weights[i];
					case "HORIZONTAL" -> {
						arms[LEFT] = weights[i];
						arms[RIGHT] = weights[i];
					}
					case "VERTICAL" -> {
						arms[UP] = weights[i];
						arms[DOWN] = weights[i];
					}
					default -> {
					}
				}
			}
		}
		return new Shape(arms, 0, false, 0, name.contains("ARC "));
	}

	/** The strokes an arm of {@code weight} is across, in a cell {@code size} pixels across it: pairs of first, end. */
	private static int[] strokes(int weight, int size, int light) {
		return switch (weight) {
			case LIGHT -> {
				int at = (size - light) / 2;
				yield new int[] {at, at + light};
			}
			case HEAVY -> {
				int heavy = Math.min(size, light * 2);
				int at = (size - heavy) / 2;
				yield new int[] {at, at + heavy};
			}
			case DOUBLE -> {
				int at = (size - light * 3) / 2;
				yield new int[] {at, at + light, at + light * 2, at + light * 3};
			}
			default -> new int[0];
		};
	}

	private static void box(Shape shape, int width, int height, Sink sink) {
		int light = Math.max(1, Math.round((float) Math.min(width, height * 0.5f) / LIGHT_PER_CELL));
		if (shape.diagonal() != 0) {
			diagonals(shape.diagonal(), width, height, light, sink);
			return;
		}
		int[] arms = shape.arms();
		if (shape.arc()) {
			arc(arms, width, height, light, sink);
			return;
		}
		if (shape.dashes() > 0) {
			// a dashed line, across the cell, whole
			boolean vertical = shape.vertical();
			int[] across = strokes(arms[vertical ? UP : LEFT], vertical ? width : height, light);
			dashes(vertical, across[0], across[1], vertical ? height : width, shape.dashes(), sink);
			return;
		}
		if (arms[UP] == DOUBLE || arms[DOWN] == DOUBLE || arms[LEFT] == DOUBLE || arms[RIGHT] == DOUBLE) {
			doubles(arms, width, height, light, sink);
			return;
		}
		// The hub, where the arms meet: across the vertical arms' strokes, and the horizontal ones'.
		int hubLeft = width;
		int hubRight = 0;
		int hubTop = height;
		int hubBottom = 0;
		for (int arm : new int[] {UP, DOWN}) {
			int[] across = strokes(arms[arm], width, light);
			for (int i = 0; i < across.length; i += 2) {
				hubLeft = Math.min(hubLeft, across[i]);
				hubRight = Math.max(hubRight, across[i + 1]);
			}
		}
		for (int arm : new int[] {LEFT, RIGHT}) {
			int[] across = strokes(arms[arm], height, light);
			for (int i = 0; i < across.length; i += 2) {
				hubTop = Math.min(hubTop, across[i]);
				hubBottom = Math.max(hubBottom, across[i + 1]);
			}
		}
		if (hubLeft > hubRight) {
			int[] middle = strokes(LIGHT, width, light);
			hubLeft = middle[0];
			hubRight = middle[1];
		}
		if (hubTop > hubBottom) {
			int[] middle = strokes(LIGHT, height, light);
			hubTop = middle[0];
			hubBottom = middle[1];
		}
		for (int arm = 0; arm < 4; arm++) {
			boolean vertical = arm == UP || arm == DOWN;
			int[] across = strokes(arms[arm], vertical ? width : height, light);
			int from = switch (arm) {
				case UP -> 0;
				case DOWN -> hubTop;
				case LEFT -> 0;
				default -> hubLeft;
			};
			int to = switch (arm) {
				case UP -> hubBottom;
				case DOWN -> height;
				case LEFT -> hubRight;
				default -> width;
			};
			for (int i = 0; i < across.length; i += 2) {
				if (vertical) {
					sink.rect(across[i], from, across[i + 1], to, OPAQUE);
				} else {
					sink.rect(from, across[i], to, across[i + 1], OPAQUE);
				}
			}
		}
	}

	/**
	 * A box character with double lines: each stroke ends where the stroke it meets is, as a double
	 * line's junction is drawn (╬'s four corners, an open channel between). Strokes, by their two
	 * edges across: the vertical line's left one [v0, v1) and right [v2, v3), the horizontal's top
	 * [h0, h1) and bottom [h2, h3) (a single line's are its one, twice). A double arm's stroke on a
	 * side with an arm across ends at that arm's near stroke, else at its far one (the outer corner); a
	 * single arm meeting a double line ends at its near stroke when arms go both ways, else its far.
	 */
	private static void doubles(int[] arms, int width, int height, int light, Sink sink) {
		int vertical = arms[UP] == DOUBLE || arms[DOWN] == DOUBLE ? DOUBLE : Math.max(Math.max(arms[UP], arms[DOWN]), LIGHT);
		int horizontal = arms[LEFT] == DOUBLE || arms[RIGHT] == DOUBLE ? DOUBLE : Math.max(Math.max(arms[LEFT], arms[RIGHT]), LIGHT);
		int[] v = twice(strokes(vertical, width, light));
		int[] h = twice(strokes(horizontal, height, light));
		boolean up = arms[UP] != NONE;
		boolean down = arms[DOWN] != NONE;
		boolean left = arms[LEFT] != NONE;
		boolean right = arms[RIGHT] != NONE;
		// Up: from the top to the stroke it meets; down: from it to the bottom.
		if (up) {
			if (arms[UP] == DOUBLE) {
				sink.rect(v[0], 0, v[1], left ? h[1] : h[3], OPAQUE);
				sink.rect(v[2], 0, v[3], right ? h[1] : h[3], OPAQUE);
			} else {
				sink.rect(v[0], 0, v[1], left && right ? h[1] : h[3], OPAQUE);
			}
		}
		if (down) {
			if (arms[DOWN] == DOUBLE) {
				sink.rect(v[0], left ? h[2] : h[0], v[1], height, OPAQUE);
				sink.rect(v[2], right ? h[2] : h[0], v[3], height, OPAQUE);
			} else {
				sink.rect(v[0], left && right ? h[2] : h[0], v[1], height, OPAQUE);
			}
		}
		if (left) {
			if (arms[LEFT] == DOUBLE) {
				sink.rect(0, h[0], up ? v[1] : v[3], h[1], OPAQUE);
				sink.rect(0, h[2], down ? v[1] : v[3], h[3], OPAQUE);
			} else {
				sink.rect(0, h[0], up && down ? v[1] : v[3], h[1], OPAQUE);
			}
		}
		if (right) {
			if (arms[RIGHT] == DOUBLE) {
				sink.rect(up ? v[2] : v[0], h[0], width, h[1], OPAQUE);
				sink.rect(down ? v[2] : v[0], h[2], width, h[3], OPAQUE);
			} else {
				sink.rect(up && down ? v[2] : v[0], h[0], width, h[1], OPAQUE);
			}
		}
	}

	/**
	 * A rounded corner (╭╮╰╯): a quarter circle from one arm's line to the other's, its radius half
	 * the cell's narrower side, then each line straight on to its edge; a pixel is drawn where its
	 * middle lies on the circle, within the line's thickness.
	 */
	private static void arc(int[] arms, int width, int height, int light, Sink sink) {
		int[] across = strokes(LIGHT, width, light);
		int[] down = strokes(LIGHT, height, light);
		float lineX = (across[0] + across[1]) / 2f;
		float lineY = (down[0] + down[1]) / 2f;
		int dx = arms[RIGHT] != NONE ? 1 : -1;
		int dy = arms[DOWN] != NONE ? 1 : -1;
		float radius = Math.min(width, height) / 2f;
		float centerX = lineX + dx * radius;
		float centerY = lineY + dy * radius;
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				float px = x + 0.5f;
				float py = y + 0.5f;
				if ((px - centerX) * dx > 0 || (py - centerY) * dy > 0) {
					continue; // (the circle's other quarters)
				}
				float distance = (float) Math.hypot(px - centerX, py - centerY);
				if (Math.abs(distance - radius) <= light / 2f) {
					sink.rect(x, y, x + 1, y + 1, OPAQUE);
				}
			}
		}
		// each line on from where the arc meets it, to its edge
		int fromX = Math.round(centerX);
		if (dx > 0) {
			sink.rect(fromX, down[0], width, down[1], OPAQUE);
		} else {
			sink.rect(0, down[0], fromX, down[1], OPAQUE);
		}
		int fromY = Math.round(centerY);
		if (dy > 0) {
			sink.rect(across[0], fromY, across[1], height, OPAQUE);
		} else {
			sink.rect(across[0], 0, across[1], fromY, OPAQUE);
		}
	}

	/** A line's strokes as a double line's four edges: a single one's, twice. */
	private static int[] twice(int[] strokes) {
		return strokes.length == 4 ? strokes : new int[] {strokes[0], strokes[1], strokes[0], strokes[1]};
	}

	/** A dashed line along a cell {@code length} pixels long: {@code count} dashes, a gap after each. */
	private static void dashes(boolean vertical, int a, int b, int length, int count, Sink sink) {
		for (int i = 0; i < count; i++) {
			int start = Math.round((float) length * i / count);
			int end = Math.round((float) length * (i + 1) / count);
			int gap = Math.max(1, (end - start) / 3);
			if (vertical) {
				sink.rect(a, start, b, end - gap, OPAQUE);
			} else {
				sink.rect(start, a, end - gap, b, OPAQUE);
			}
		}
	}

	/** Diagonal lines corner to corner, a step a pixel row. */
	private static void diagonals(int which, int width, int height, int light, Sink sink) {
		for (int y = 0; y < height; y++) {
			int x = Math.round((float) (y + 0.5f) * width / height - light / 2f);
			if ((which & BACKSLASH) != 0) {
				sink.rect(Math.max(0, x), y, Math.min(width, x + light), y + 1, OPAQUE);
			}
			if ((which & SLASH) != 0) {
				int mirrored = width - x - light;
				sink.rect(Math.max(0, mirrored), y, Math.min(width, mirrored + light), y + 1, OPAQUE);
			}
		}
	}

	// ---- block elements -------------------------------------------------------------------

	/** Quadrants' bits: upper left, upper right, lower left, lower right. */
	private static final int UL = 1;
	private static final int UR = 2;
	private static final int LL = 4;
	private static final int LR = 8;
	/** U+2596 to U+259F's quadrants. */
	private static final int[] QUADRANTS = {LL, LR, UL, UL | LL | LR, UL | LR, UL | UR | LL, UL | UR | LR, UR, UR | LL, UR | LL | LR};

	private static void block(int c, int width, int height, Sink sink) {
		int halfWidth = Math.round(width / 2f);
		int halfHeight = Math.round(height / 2f);
		if (c == 0x2580) {
			sink.rect(0, 0, width, halfHeight, OPAQUE); // upper half
		} else if (c >= 0x2581 && c <= 0x2588) {
			sink.rect(0, height - Math.round(height * (c - 0x2580) / 8f), width, height, OPAQUE); // lower eighths, to full
		} else if (c >= 0x2589 && c <= 0x258F) {
			sink.rect(0, 0, Math.round(width * (0x2590 - c) / 8f), height, OPAQUE); // left eighths
		} else if (c == 0x2590) {
			sink.rect(halfWidth, 0, width, height, OPAQUE); // right half
		} else if (c >= 0x2591 && c <= 0x2593) {
			sink.rect(0, 0, width, height, SHADES[c - 0x2591]); // light, medium, dark shade
		} else if (c == 0x2594) {
			sink.rect(0, 0, width, Math.max(1, Math.round(height / 8f)), OPAQUE); // upper one eighth
		} else if (c == 0x2595) {
			sink.rect(width - Math.max(1, Math.round(width / 8f)), 0, width, height, OPAQUE); // right one eighth
		} else if (c >= 0x2596) {
			int quadrants = QUADRANTS[c - 0x2596];
			if ((quadrants & UL) != 0) {
				sink.rect(0, 0, halfWidth, halfHeight, OPAQUE);
			}
			if ((quadrants & UR) != 0) {
				sink.rect(halfWidth, 0, width, halfHeight, OPAQUE);
			}
			if ((quadrants & LL) != 0) {
				sink.rect(0, halfHeight, halfWidth, height, OPAQUE);
			}
			if ((quadrants & LR) != 0) {
				sink.rect(halfWidth, halfHeight, width, height, OPAQUE);
			}
		}
	}

	// ---- braille --------------------------------------------------------------------------

	/** Each dot's bit, by its column (0, 1) and row (0 to 3): dots 1-2-3-7 down the left, 4-5-6-8 the right. */
	private static final int[][] DOTS = {{0, 1, 2, 6}, {3, 4, 5, 7}};

	private static void braille(int dots, int width, int height, Sink sink) {
		float placeWidth = width / 2f;
		float placeHeight = height / 4f;
		int side = Math.max(1, Math.round(Math.min(placeWidth, placeHeight) * (float) DOT));
		for (int column = 0; column < 2; column++) {
			for (int row = 0; row < 4; row++) {
				if ((dots & 1 << DOTS[column][row]) != 0) {
					int x = Math.round(placeWidth * column + (placeWidth - side) / 2);
					int y = Math.round(placeHeight * row + (placeHeight - side) / 2);
					sink.rect(x, y, x + side, y + side, OPAQUE);
				}
			}
		}
	}
}
