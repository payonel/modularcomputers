package com.payonel.modularcomputers.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.payonel.modularcomputers.block.AllInOneBlock;
import com.payonel.modularcomputers.block.AllInOneBlockEntity;
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
import net.minecraft.world.phys.Vec3;

/** Draws the all-in-one computer's display text onto the glass of its front face. */
public final class AllInOneRenderer implements BlockEntityRenderer<AllInOneBlockEntity, AllInOneRenderer.State> {
	/** The glass on the front texture, in texels from the face's top-left corner. */
	private static final float GLASS_X = 2, GLASS_Y = 2, GLASS_WIDTH = 12, GLASS_HEIGHT = 8;
	/** How far the text floats in front of the face, in blocks. */
	private static final float TEXT_OFFSET = 0.002f;
	private static final int TEXT_COLOR = 0xFFFFFFFF;

	private final Font font;

	public AllInOneRenderer(BlockEntityRendererProvider.Context context) {
		this.font = context.font();
	}

	public static final class State extends BlockEntityRenderState {
		Direction facing = Direction.NORTH;
		Resolution resolution = Resolution.DEFAULT;
		/** Rows of the display, or null when there's nothing to draw. */
		String[] rows;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(AllInOneBlockEntity computer, State state, float partialTick, Vec3 cameraPos,
		ModelFeatureRenderer.CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(computer, state, partialTick, cameraPos, breakProgress);
		state.facing = computer.getBlockState().getValue(AllInOneBlock.FACING);
		TextBuffer display = computer.machine().display();
		state.resolution = display.resolution();
		if (display.isBlank()) {
			state.rows = null;
			return;
		}
		state.rows = new String[state.resolution.rows()];
		for (int row = 0; row < state.rows.length; row++) {
			state.rows[row] = display.row(row);
		}
	}

	@Override
	public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.rows == null) {
			return;
		}
		// Fit the text grid inside the glass, keeping its aspect ratio, centered.
		float glassWidth = GLASS_WIDTH / 16f;
		float glassHeight = GLASS_HEIGHT / 16f;
		float width = Math.min(glassWidth, glassHeight * (float) state.resolution.aspectRatio());
		float height = width / (float) state.resolution.aspectRatio();
		float left = GLASS_X / 16f + (glassWidth - width) / 2;
		float top = GLASS_Y / 16f + (glassHeight - height) / 2;
		TextGrid grid = TextGrid.of(width, height, state.resolution, font);

		pose.pushPose();
		// Turn so the front face is at +Z, then move to the glass's top-left corner. Font
		// coordinates grow right and down, so flip Y.
		pose.translate(0.5f, 0.5f, 0.5f);
		pose.rotateDegrees(Axis.YP, -state.facing.toYRot());
		pose.translate(left - 0.5f, 0.5f - top, 0.5f + TEXT_OFFSET);
		pose.scale(grid.fontScale(), -grid.fontScale(), grid.fontScale());
		for (int row = 0; row < state.rows.length; row++) {
			String text = state.rows[row];
			for (int column = 0; column < text.length(); column++) {
				char c = text.charAt(column);
				if (c != ' ') {
					collector.submitText(pose, grid.x(column, c, font), grid.y(row), FormattedCharSequence.codepoint(c, Style.EMPTY), false,
						 Font.DisplayMode.POLYGON_OFFSET, LightCoordsUtil.FULL_BRIGHT, TEXT_COLOR, 0, 0);
				}
			}
		}
		pose.popPose();
	}
}
