package com.payonel.modularcomputers.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.payonel.modularcomputers.block.DisplayHost;
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
	/** The screen block's glass: nearly the whole face, above a chin. */
	public static final Glass SCREEN = new Glass(1, 1, 14, 9);
	/** How far the text floats in front of the face, in blocks. */
	private static final float TEXT_OFFSET = 0.002f;
	/** How far the identify outline sits outside the block, in blocks. */
	private static final float OUTLINE_OFFSET = 0.02f;
	private static final int OUTLINE = 0xE6C83C;

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
		TextBuffer display = host.display().buffer();
		state.resolution = display.resolution();
		if (display.isBlank()) {
			state.rows = null;
			return;
		}
		state.rows = display.rows().toArray(String[]::new);
		state.foregrounds = display.foregrounds();
		state.backgrounds = display.backgrounds();
	}

	@Override
	public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.identify > 0) {
			submitOutline(state.identify, pose, collector);
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

	/** A frame along every edge of the block, seen through walls: the bulb's "this one". */
	private static void submitOutline(float opacity, PoseStack pose, SubmitNodeCollector collector) {
		int color = Math.round(opacity * 255) << 24 | OUTLINE;
		float edge = 1f; // texels: just the casing's bevel, clear of the glass
		for (int face = 0; face < 6; face++) {
			pose.pushPose();
			pose.translate(0.5f, 0.5f, 0.5f);
			if (face < 4) {
				pose.rotateDegrees(Axis.YP, 90 * face);
			} else {
				pose.rotateDegrees(Axis.XP, face == 4 ? 90 : -90);
			}
			pose.translate(-0.5f - OUTLINE_OFFSET, 0.5f + OUTLINE_OFFSET, 0.5f + OUTLINE_OFFSET);
			float scale = (1 + 2 * OUTLINE_OFFSET) / 16f;
			pose.scale(scale, -scale, scale);
			collector.submitTextBackground(pose, 0, 0, 16, edge, color, Font.DisplayMode.SEE_THROUGH, LightCoordsUtil.FULL_BRIGHT);
			collector.submitTextBackground(pose, 0, 16 - edge, 16, 16, color, Font.DisplayMode.SEE_THROUGH, LightCoordsUtil.FULL_BRIGHT);
			collector.submitTextBackground(pose, 0, edge, edge, 16 - edge, color, Font.DisplayMode.SEE_THROUGH, LightCoordsUtil.FULL_BRIGHT);
			collector.submitTextBackground(pose, 16 - edge, edge, 16, 16 - edge, color, Font.DisplayMode.SEE_THROUGH, LightCoordsUtil.FULL_BRIGHT);
			pose.popPose();
		}
	}
}
