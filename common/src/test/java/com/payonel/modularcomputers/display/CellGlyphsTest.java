package com.payonel.modularcomputers.display;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The characters a terminal draws itself: lines meet their cell's edges where they go on, and nowhere else. */
class CellGlyphsTest {
	private static final int WIDTH = 8;
	private static final int HEIGHT = 16;

	/** A character drawn as a grid of alphas. */
	private static int[][] draw(int c) {
		int[][] pixels = new int[HEIGHT][WIDTH];
		CellGlyphs.draw(c, WIDTH, HEIGHT, (x0, y0, x1, y1, alpha) -> {
			for (int y = Math.max(0, y0); y < Math.min(HEIGHT, y1); y++) {
				for (int x = Math.max(0, x0); x < Math.min(WIDTH, x1); x++) {
					pixels[y][x] = Math.max(pixels[y][x], alpha);
				}
			}
		});
		return pixels;
	}

	private static boolean top(int[][] p) {
		for (int x = 0; x < WIDTH; x++) {
			if (p[0][x] > 0) {
				return true;
			}
		}
		return false;
	}

	private static boolean bottom(int[][] p) {
		for (int x = 0; x < WIDTH; x++) {
			if (p[HEIGHT - 1][x] > 0) {
				return true;
			}
		}
		return false;
	}

	private static boolean left(int[][] p) {
		for (int y = 0; y < HEIGHT; y++) {
			if (p[y][0] > 0) {
				return true;
			}
		}
		return false;
	}

	private static boolean right(int[][] p) {
		for (int y = 0; y < HEIGHT; y++) {
			if (p[y][WIDTH - 1] > 0) {
				return true;
			}
		}
		return false;
	}

	/** Which edges it reaches: up, right, down, left. */
	private static boolean[] edges(int c) {
		int[][] p = draw(c);
		return new boolean[] {top(p), right(p), bottom(p), left(p)};
	}

	@Test
	void linesReachTheEdgesTheyGoOnTo() {
		assertThat(edges('─')).containsExactly(false, true, false, true);
		assertThat(edges('│')).containsExactly(true, false, true, false);
		assertThat(edges('┌')).containsExactly(false, true, true, false);
		assertThat(edges('┘')).containsExactly(true, false, false, true);
		assertThat(edges('┼')).containsExactly(true, true, true, true);
		assertThat(edges('╋')).as("heavy").containsExactly(true, true, true, true);
		assertThat(edges('╔')).as("double").containsExactly(false, true, true, false);
		assertThat(edges('╭')).as("an arc, as a corner").containsExactly(false, true, true, false);
		assertThat(edges('╴')).as("a half line").containsExactly(false, false, false, true);
		assertThat(edges('┢')).as("mixed: up light, down and right heavy").containsExactly(true, true, true, false);
	}

	@Test
	void aHorizontalLineIsAtTheSameHeightInEveryCellSoLinesJoin() {
		int[][] light = draw('─');
		int[][] corner = draw('┐');
		for (int y = 0; y < HEIGHT; y++) {
			assertThat(corner[y][0] > 0).as("row " + y).isEqualTo(light[y][0] > 0);
		}
	}

	@Test
	void heavyIsThickerThanLightAndDoubleIsTwoLines() {
		int light = 0;
		int heavy = 0;
		for (int y = 0; y < HEIGHT; y++) {
			light += draw('─')[y][0] > 0 ? 1 : 0;
			heavy += draw('━')[y][0] > 0 ? 1 : 0;
		}
		assertThat(heavy).isGreaterThan(light);
		int runs = 0;
		int[][] twin = draw('═');
		for (int y = 1; y < HEIGHT; y++) {
			if (twin[y][0] > 0 && twin[y - 1][0] == 0) {
				runs++;
			}
		}
		assertThat(runs).as("two lines").isEqualTo(2);
	}

	@Test
	void aDoubleCrossIsFourCornersWithAnOpenChannel() {
		int[][] cross = draw('╬');
		int middleX = WIDTH / 2 - (WIDTH % 2 == 0 ? 1 : 0);
		int middleY = HEIGHT / 2 - 1;
		// down the middle of the vertical line's gap, and across the horizontal's: nothing drawn
		boolean open = true;
		for (int y = 0; y < HEIGHT; y++) {
			open &= cross[y][middleX] == 0 || cross[y][middleX + 1] == 0;
		}
		assertThat(open).as("the channel between the vertical strokes stays open").isTrue();
		assertThat(edges('╬')).containsExactly(true, true, true, true);
		assertThat(edges('╦')).containsExactly(false, true, true, true);
		assertThat(edges('╒')).as("down single, right double").containsExactly(false, true, true, false);
	}

	@Test
	void blocksAndShadesFillTheirShare() {
		int[][] upper = draw('▀');
		assertThat(upper[0][0]).isEqualTo(CellGlyphs.OPAQUE);
		assertThat(upper[HEIGHT - 1][0]).isZero();
		assertThat(draw('█')[HEIGHT - 1][WIDTH - 1]).isEqualTo(CellGlyphs.OPAQUE);
		assertThat(draw('░')[0][0]).as("light shade").isBetween(1, 127);
		int[][] quadrant = draw('▗'); // lower right
		assertThat(quadrant[HEIGHT - 1][WIDTH - 1]).isEqualTo(CellGlyphs.OPAQUE);
		assertThat(quadrant[0][0]).isZero();
	}

	@Test
	void brailleDotsAreWhereTheirBitsSay() {
		int[][] first = draw(0x2801); // dot 1: top left
		int[][] eighth = draw(0x2880); // dot 8: bottom right
		assertThat(first[HEIGHT / 8][WIDTH / 4]).isEqualTo(CellGlyphs.OPAQUE);
		assertThat(first[HEIGHT - 2][WIDTH - 2]).isZero();
		assertThat(eighth[HEIGHT - HEIGHT / 8][WIDTH - WIDTH / 4]).isEqualTo(CellGlyphs.OPAQUE);
	}

	@Test
	void everyBoxCharacterHasAShape() {
		for (int c = 0x2500; c <= 0x257F; c++) {
			assertThat(CellGlyphs.draws(c)).as(Integer.toHexString(c)).isTrue();
		}
		assertThat(CellGlyphs.draws('a')).isFalse();
	}
}
