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
		/** The identify outline's opacity (0..1), or 0 when it's off. */
		float identify;
		/** The Bluetooth card in hand links to a card in here. */
		boolean bluetooth;
		/** Its glass. */
		final TerminalFace.State face = new TerminalFace.State();
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
		TerminalFace.extract(host.terminal(), host.showsScreen(), state.face);
	}

	@Override
	public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.identify > 0) {
			Outline.submit(state.identify, pose, collector);
		} else if (state.bluetooth) {
			Outline.submit(0.9f, Outline.BLUETOOTH, pose, collector);
		}
		pose.pushPose();
		// Turn so the front face is at +Z, then move to the glass's top-left corner.
		pose.translate(0.5f, 0.5f, 0.5f);
		pose.rotateDegrees(Axis.YP, -state.facing.toYRot());
		pose.translate(glass.x() / 16f - 0.5f, 0.5f - glass.y() / 16f, 0.5f + TEXT_OFFSET);
		TerminalFace.submit(state.face, glass.width() / 16f, glass.height() / 16f, font, pose, collector);
		pose.popPose();
	}
}
