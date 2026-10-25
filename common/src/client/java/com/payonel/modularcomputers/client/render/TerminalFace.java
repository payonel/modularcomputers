package com.payonel.modularcomputers.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.payonel.modularcomputers.device.Terminal;
import com.payonel.modularcomputers.display.Resolution;
import com.payonel.modularcomputers.display.TextBuffer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;

/**
 * A terminal's glass as the world shows it (cell backgrounds, its pictures, its text and cursor),
 * on whatever face holds it: a case's or a screen block's front, a golem's head. Its state is taken
 * from the terminal each frame; it's drawn at the glass's top-left corner, facing +Z.
 */
public final class TerminalFace {
	/** How far its pictures float in front of the cells' backgrounds, in blocks (under the text). */
	private static final float PIXEL_OFFSET = 0.0005f;

	/** What a frame draws of a terminal. */
	public static final class State {
		Resolution resolution = Resolution.DEFAULT;
		/** Rows of the display, or null when there's nothing to draw. */
		String[] rows;
		/** Its cells' characters and combining marks ("" for none), by row and column. */
		int[][] cells;
		String[][] marks;
		int[] foregrounds;
		int[] backgrounds;
		/** The tier's font ({@link TerminalFonts}). */
		Style style = Style.EMPTY;
		/** Which cells' text is bold (drawn in the bold font). */
		boolean[] bold;
		/** The glass's own color, where no background is drawn. */
		int glass;
		/** Its pictures' texture (sixel), or null: none. */
		net.minecraft.resources.Identifier pixels;
		/** The cursor's cell when it's lit now (-1: not drawn), and its shape. */
		int cursorColumn = -1;
		int cursorRow;
		com.payonel.modularcomputers.device.Terminal.Cursor cursor;
	}

	private TerminalFace() {
	}

	/** Takes what {@code state} draws from {@code terminal}; nothing when it isn't shown ({@code shown}: a screen there). */
	public static void extract(Terminal terminal, boolean shown, State state) {
		TextBuffer display = terminal.buffer();
		state.resolution = display.resolution();
		var cursor = terminal.shownCursor();
		boolean lit = CursorBlink.lit(cursor) && cursor.row() < display.resolution().rows();
		if ((display.isBlank() && !lit) || !shown) {
			state.rows = null; // nothing on it, or no screen on the front (a case without one)
			return;
		}
		state.rows = display.rows().toArray(String[]::new);
		int columnsNow = display.resolution().columns();
		state.cells = new int[state.rows.length][columnsNow];
		state.marks = new String[state.rows.length][columnsNow];
		for (int row = 0; row < state.rows.length; row++) {
			for (int column = 0; column < columnsNow; column++) {
				state.cells[row][column] = display.get(column, row);
				state.marks[row][column] = display.marks(column, row);
			}
		}
		state.pixels = PixelLayers.of(display);
		// A monochrome terminal's phosphor has one color: the glass glows green (ColorDepth.glow).
		var depth = terminal.tier().depth();
		state.style = TerminalFonts.of(terminal.tier());
		int[] foregrounds = display.foregrounds();
		state.foregrounds = java.util.Arrays.stream(foregrounds).map(depth::glow).toArray();
		state.bold = new boolean[foregrounds.length];
		for (int i = 0; i < foregrounds.length; i++) {
			state.bold[i] = TextBuffer.bold(foregrounds[i]);
		}
		state.backgrounds = java.util.Arrays.stream(display.backgrounds()).map(depth::glow).toArray(); // (reverse video lights a monochrome cell)
		state.glass = depth.glow(TextBuffer.DEFAULT_BACKGROUND);
		state.cursor = cursor;
		state.cursorColumn = -1;
		if (lit) {
			int cell = cursor.row() * display.resolution().columns() + cursor.column();
			state.cursorColumn = cursor.column();
			state.cursorRow = cursor.row();
			if (cursor.block()) {
				// a block cursor: its cell in reverse video
				int foreground = state.foregrounds[cell];
				state.foregrounds[cell] = state.backgrounds[cell];
				state.backgrounds[cell] = foreground;
			}
		}
	}

	/**
	 * Draws it into the glass {@code glassWidth} by {@code glassHeight} (in blocks), its grid fit
	 * inside keeping its aspect ratio, centered; {@code pose} at the glass's top-left corner, X right,
	 * Y up, facing +Z.
	 */
	public static void submit(State state, float glassWidth, float glassHeight, Font font, PoseStack pose, SubmitNodeCollector collector) {
		if (state.rows == null) {
			return;
		}
		// Fit the text grid inside the glass, keeping its aspect ratio, centered.
		float width = Math.min(glassWidth, glassHeight * (float) state.resolution.aspectRatio());
		float height = width / (float) state.resolution.aspectRatio();
		float left = (glassWidth - width) / 2;
		float top = (glassHeight - height) / 2;
		TextGrid grid = TextGrid.of(width, height, state.resolution, font, state.style);

		pose.pushPose();
		// To the grid's top-left corner. Font coordinates grow right and down, so flip Y.
		pose.translate(left, -top, 0);
		pose.scale(grid.fontScale(), -grid.fontScale(), grid.fontScale());
		int columns = state.resolution.columns();
		float cellWidth = grid.cellWidth() / grid.fontScale();
		float cellHeight = grid.cellHeight() / grid.fontScale();
		// Backgrounds, in runs of equal color; the glass is already black.
		for (int row = 0; row < state.rows.length; row++) {
			int start = 0;
			for (int column = 1; column <= columns; column++) {
				if (column == columns || state.backgrounds[row * columns + column] != state.backgrounds[row * columns + start]) {
					int background = state.backgrounds[row * columns + start];
					boolean cursorCell = state.cursorColumn >= 0 && state.cursor.block() && row == state.cursorRow && start == state.cursorColumn;
					if (background != state.glass || cursorCell) {
						collector.submitTextBackground(pose, start * cellWidth, row * cellHeight, column * cellWidth, (row + 1) * cellHeight,
							0xFF000000 | background, Font.DisplayMode.POLYGON_OFFSET, LightCoordsUtil.FULL_BRIGHT);
					}
					start = column;
				}
			}
		}
		if (state.pixels != null) {
			// Its pictures: one quad over the whole grid, its texture transparent where there are none.
			float right = columns * cellWidth;
			float down = state.rows.length * cellHeight;
			pose.pushPose();
			pose.translate(0, 0, PIXEL_OFFSET / grid.fontScale());
			collector.submitCustomGeometry(pose, net.minecraft.client.renderer.rendertype.RenderTypes.textPolygonOffset(state.pixels), (at, vertices) -> {
				vertices.addVertex(at, 0, 0, 0).setColor(0xFFFFFFFF).setUv(0, 0).setLight(LightCoordsUtil.FULL_BRIGHT);
				vertices.addVertex(at, 0, down, 0).setColor(0xFFFFFFFF).setUv(0, 1).setLight(LightCoordsUtil.FULL_BRIGHT);
				vertices.addVertex(at, right, down, 0).setColor(0xFFFFFFFF).setUv(1, 1).setLight(LightCoordsUtil.FULL_BRIGHT);
				vertices.addVertex(at, right, 0, 0).setColor(0xFFFFFFFF).setUv(1, 0).setLight(LightCoordsUtil.FULL_BRIGHT);
			});
			pose.popPose();
		}
		for (int row = 0; row < state.cells.length; row++) {
			for (int column = 0; column < state.cells[row].length; column++) {
				int c = state.cells[row][column];
				if (c != ' ' && c != com.payonel.modularcomputers.display.TextBuffer.WIDE) {
					Style style = state.style.withBold(state.bold[row * columns + column]);
					float x = grid.x(column, c, font, style);
					int color = 0xFF000000 | state.foregrounds[row * columns + column];
					collector.submitText(pose, x, grid.y(row), FormattedCharSequence.codepoint(c, style), false,
						Font.DisplayMode.POLYGON_OFFSET, LightCoordsUtil.FULL_BRIGHT, color, 0, 0);
					String marks = state.marks[row][column];
					for (int i = 0; i < marks.length(); i = marks.offsetByCodePoints(i, 1)) {
						// a combining mark over its character, where it is
						collector.submitText(pose, x, grid.y(row), FormattedCharSequence.codepoint(marks.codePointAt(i), style), false,
							Font.DisplayMode.POLYGON_OFFSET, LightCoordsUtil.FULL_BRIGHT, color, 0, 0);
					}
				}
			}
		}
		if (state.cursorColumn >= 0 && !state.cursor.block()) {
			// an underline along the cell's bottom, or a bar at its left, in the text's color
			float cellLeft = state.cursorColumn * cellWidth;
			float cellTop = state.cursorRow * cellHeight;
			int color = 0xFF000000 | state.foregrounds[state.cursorRow * columns + state.cursorColumn];
			if (state.cursor.underline()) {
				collector.submitTextBackground(pose, cellLeft, cellTop + cellHeight * (1 - CursorBlink.STROKE), cellLeft + cellWidth, cellTop + cellHeight,
					color, Font.DisplayMode.POLYGON_OFFSET, LightCoordsUtil.FULL_BRIGHT);
			} else {
				collector.submitTextBackground(pose, cellLeft, cellTop, cellLeft + cellWidth * CursorBlink.STROKE, cellTop + cellHeight,
					color, Font.DisplayMode.POLYGON_OFFSET, LightCoordsUtil.FULL_BRIGHT);
			}
		}
		pose.popPose();
	}
}
