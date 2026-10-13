package com.payonel.modularcomputers.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.payonel.modularcomputers.ModularComputers;
import com.payonel.modularcomputers.block.PhotomaskBlock;
import com.payonel.modularcomputers.block.PhotomaskBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/** A photomask's gear, turning on its front as it's cranked or runs on its battery. */
public final class PhotomaskRenderer implements BlockEntityRenderer<PhotomaskBlockEntity, PhotomaskRenderer.State> {
	private static final Identifier GEAR = ModularComputers.id("textures/block/photomask_gear.png");
	/** Half the gear's width, in blocks: it fills the front's round recess (11 texels across). */
	private static final float RADIUS = 6f / 16;
	/** Just off the face, so it never fights the block's own front. */
	private static final float OFFSET = 0.002f;

	public static final class State extends BlockEntityRenderState {
		Direction facing = Direction.NORTH;
		float angle;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(PhotomaskBlockEntity mask, State state, float partialTick, Vec3 cameraPos,
		ModelFeatureRenderer.CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(mask, state, partialTick, cameraPos, breakProgress);
		state.facing = mask.getBlockState().getValue(PhotomaskBlock.FACING);
		state.angle = mask.gearAngle(partialTick);
		// Inside a solid block it's dark: light the gear as its face is lit, from the block in front.
		if (mask.getLevel() != null) {
			state.lightCoords = net.minecraft.util.LightCoordsUtil.getLightCoords(mask.getLevel(), mask.getBlockPos().relative(state.facing));
		}
	}

	@Override
	public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		pose.pushPose();
		// Turn so the front face is at +Z (as DisplayRenderer does), then onto its middle, and turn the gear.
		pose.translate(0.5f, 0.5f, 0.5f);
		pose.rotateDegrees(Axis.YP, -state.facing.toYRot());
		pose.translate(0, 0, 0.5f + OFFSET);
		pose.rotateDegrees(Axis.ZP, state.angle);
		int light = state.lightCoords;
		collector.submitCustomGeometry(pose, RenderTypes.entityCutout(GEAR), (at, vertices) -> {
			vertex(vertices, at, -RADIUS, -RADIUS, 0, 1, light);
			vertex(vertices, at, RADIUS, -RADIUS, 1, 1, light);
			vertex(vertices, at, RADIUS, RADIUS, 1, 0, light);
			vertex(vertices, at, -RADIUS, RADIUS, 0, 0, light);
		});
		pose.popPose();
	}

	private static void vertex(com.mojang.blaze3d.vertex.VertexConsumer vertices, PoseStack.Pose at, float x, float y, float u, float v, int light) {
		vertices.addVertex(at, x, y, 0).setColor(0xFFFFFFFF).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
			.setNormal(at, 0, 0, 1);
	}
}
