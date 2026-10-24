package com.payonel.modularcomputers.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.payonel.modularcomputers.block.DisplayHost;
import com.payonel.modularcomputers.client.BluetoothTarget;
import com.payonel.modularcomputers.client.Identify;
import com.payonel.modularcomputers.display.Resolution;
import com.payonel.modularcomputers.display.TextBuffer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a screen's display (cell backgrounds, its pictures, and text) onto the glass of its block's front
 * face: the all-in-one's or a screen block's. While the player has the bus order panel's bulb
 * on for it, it also outlines the whole block, through walls, so it's easy to find.
 */
public final class DisplayRenderer<T extends BlockEntity & DisplayHost> implements BlockEntityRenderer<T, DisplayRenderer.State> {
	/** The all-in-one's glass, above its keyboard, in texels from the face's top-left corner. */
	public static final Glass ALL_IN_ONE = new Glass(2, 2, 12, 8);
	/** The screen block's glass: a 14x9 pane across the middle of the face. */
	public static final Glass SCREEN = new Glass(1, 3, 14, 9);
	/** How far the text floats in front of the face, in blocks. */
	private static final float TEXT_OFFSET = 0.002f;
	/** How far its pictures float in front of the cells' backgrounds, in blocks (under the text). */
	private static final float PIXEL_OFFSET = 0.0005f;

	/** Where the glass is on the front texture, in texels. */
	public record Glass(float x, float y, float width, float height) {
	}

	private final Font font;
	private final Glass glass;

	public DisplayRenderer(BlockEntityRendererProvider.Context context, Glass glass) {
		this.font = context.font();
		this.glass = glass;
	}

	public static final class State extends BlockEntityRenderState {
		Direction facing = Direction.NORTH;
		Resolution resolution = Resolution.DEFAULT;
		/** The identify outline's opacity (0..1), or 0 when it's off. */
		float identify;
		/** The Bluetooth card in hand links to a card in here. */
		boolean bluetooth;
		/** Rows of the display, or null when there's nothing to draw. */
		String[] rows;
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

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(T host, State state, float partialTick, Vec3 cameraPos,
		ModelFeatureRenderer.CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(host, state, partialTick, cameraPos, breakProgress);
		state.facing = host.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
		state.identify = Identify.pulse(host.getBlockPos());
		state.bluetooth = BluetoothTarget.isTarget(host.getBlockPos());
		TextBuffer display = host.terminal().buffer();
		state.resolution = display.resolution();
		var cursor = host.terminal().shownCursor();
		boolean lit = CursorBlink.lit(cursor) && cursor.row() < display.resolution().rows();
		if ((display.isBlank() && !lit) || !host.showsScreen()) {
			state.rows = null; // nothing on it, or no screen on the front (a case without one)
			return;
		}
		state.rows = display.rows().toArray(String[]::new);
		state.pixels = PixelLayers.of(display);
		// A monochrome terminal's phosphor has one color: the glass glows green (ColorDepth.glow).
		var depth = host.terminal().tier().depth();
		state.style = TerminalFonts.of(host.terminal().tier());
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

	@Override
	public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.identify > 0) {
			Outline.submit(state.identify, pose, collector);
		} else if (state.bluetooth) {
			Outline.submit(0.9f, Outline.BLUETOOTH, pose, collector);
		}
		if (state.rows == null) {
			return;
		}
		// Fit the text grid inside the glass, keeping its aspect ratio, centered.
		float glassWidth = glass.width() / 16f;
		float glassHeight = glass.height() / 16f;
		float width = Math.min(glassWidth, glassHeight * (float) state.resolution.aspectRatio());
		float height = width / (float) state.resolution.aspectRatio();
		float left = glass.x() / 16f + (glassWidth - width) / 2;
		float top = glass.y() / 16f + (glassHeight - height) / 2;
		TextGrid grid = TextGrid.of(width, height, state.resolution, font, state.style);

		pose.pushPose();
		// Turn so the front face is at +Z, then move to the glass's top-left corner. Font
		// coordinates grow right and down, so flip Y.
		pose.translate(0.5f, 0.5f, 0.5f);
		pose.rotateDegrees(Axis.YP, -state.facing.toYRot());
		pose.translate(left - 0.5f, 0.5f - top, 0.5f + TEXT_OFFSET);
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
		for (int row = 0; row < state.rows.length; row++) {
			String text = state.rows[row];
			for (int column = 0; column < text.length(); column++) {
				char c = text.charAt(column);
				if (c != ' ') {
					Style style = state.style.withBold(state.bold[row * columns + column]);
					collector.submitText(pose, grid.x(column, c, font, style), grid.y(row), FormattedCharSequence.codepoint(c, style), false,
						Font.DisplayMode.POLYGON_OFFSET, LightCoordsUtil.FULL_BRIGHT, 0xFF000000 | state.foregrounds[row * columns + column], 0, 0);
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
