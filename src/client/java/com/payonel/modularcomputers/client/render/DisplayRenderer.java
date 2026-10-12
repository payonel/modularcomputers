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
 * Draws a screen's display (cell backgrounds and text) onto the glass of its block's front
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
		if (display.isBlank() || !host.showsScreen()) {
			state.rows = null; // nothing on it, or no screen on the front (a case without one)
			return;
		}
		state.rows = display.rows().toArray(String[]::new);
		state.foregrounds = display.foregrounds();
		state.backgrounds = display.backgrounds();
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
		TextGrid grid = TextGrid.of(width, height, state.resolution, font);

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
					if (background != TextBuffer.DEFAULT_BACKGROUND) {
						collector.submitTextBackground(pose, start * cellWidth, row * cellHeight, column * cellWidth, (row + 1) * cellHeight,
							0xFF000000 | background, Font.DisplayMode.POLYGON_OFFSET, LightCoordsUtil.FULL_BRIGHT);
					}
					start = column;
				}
			}
		}
		for (int row = 0; row < state.rows.length; row++) {
			String text = state.rows[row];
			for (int column = 0; column < text.length(); column++) {
				char c = text.charAt(column);
				if (c != ' ') {
					collector.submitText(pose, grid.x(column, c, font), grid.y(row), FormattedCharSequence.codepoint(c, Style.EMPTY), false,
						Font.DisplayMode.POLYGON_OFFSET, LightCoordsUtil.FULL_BRIGHT, 0xFF000000 | state.foregrounds[row * columns + column], 0, 0);
				}
			}
		}
		pose.popPose();
	}
}
