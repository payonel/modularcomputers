package com.payonel.modularcomputers.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.payonel.modularcomputers.ModularComputers;
import com.payonel.modularcomputers.block.PhotomaskBlock;
import com.payonel.modularcomputers.block.PhotomaskBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;

/**
 * A photomask's gear, turning in the recess on its front as it's cranked or runs on its battery: a
 * solid brass spur gear (16 teeth on a rim, four spokes, a hub on its axle), flush with the block's
 * face, with a steel crank peg on its top spoke to grab, and to see it turn. The item model holds the
 * same gear, standing still (`scripts/art/photomask_textures.py` writes it from the same numbers).
 */
public final class PhotomaskRenderer implements BlockEntityRenderer<PhotomaskBlockEntity, PhotomaskRenderer.State> {
	private static final Identifier GEAR = ModularComputers.id("textures/block/photomask_gear.png");
	/** How deep the gear is, in texels, back from the face into the recess (3 deep). */
	private static final float DEPTH = 2.5f;
	/** Where each part samples the gear texture: brass, its shadowed axle, the peg's steel. */
	private static final int BRASS = 0;
	private static final int AXLE = 12;
	private static final int STEEL = 14;
	/** The gear, in texels around its axle (z out of the face, 0 at the face), built once. */
	private static final List<Box> PARTS = gear();

	/** A box turned about the axle by {@code angle} degrees, sampling the texture from column {@code u}. */
	record Box(float x0, float y0, float z0, float x1, float y1, float z1, float angle, int u) {
	}

	private static List<Box> gear() {
		List<Box> parts = new ArrayList<>();
		for (int i = 0; i < 16; i++) {
			float angle = i * 22.5f;
			parts.add(new Box(-0.75f, 5.75f, -DEPTH, 0.75f, 6.9f, 0, angle, BRASS)); // a tooth
			parts.add(new Box(-1.15f, 4.75f, -DEPTH, 1.15f, 5.75f, 0, angle, BRASS)); // the rim
		}
		for (int i = 0; i < 2; i++) {
			parts.add(new Box(-0.6f, -4.9f, -DEPTH + 0.3f, 0.6f, 4.9f, -0.4f, i * 90, BRASS)); // two spokes across
		}
		for (int i = 0; i < 2; i++) {
			parts.add(new Box(-1.6f, -1.6f, -DEPTH, 1.6f, 1.6f, 0, i * 45, BRASS)); // the hub, an octagon
		}
		parts.add(new Box(-0.6f, -0.6f, -DEPTH, 0.6f, 0.6f, 0.2f, 0, AXLE));
		parts.add(new Box(-0.6f, 3.3f, 0, 0.6f, 4.5f, 1, 0, STEEL)); // the crank peg, standing out of the face
		return parts;
	}

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
		// Light the gear as its face is lit, from the block in front.
		if (mask.getLevel() != null) {
			state.lightCoords = LightCoordsUtil.getLightCoords(mask.getLevel(), mask.getBlockPos().relative(state.facing));
		}
	}

	@Override
	public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		pose.pushPose();
		// Turn so the front face is at +Z (as DisplayRenderer does), then onto its middle, in texels, and turn the gear.
		pose.translate(0.5f, 0.5f, 0.5f);
		pose.rotateDegrees(Axis.YP, -state.facing.toYRot());
		pose.translate(0, 0, 0.5f);
		pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
		pose.rotateDegrees(Axis.ZP, state.angle);
		int light = state.lightCoords;
		for (Box box : PARTS) {
			pose.pushPose();
			pose.rotateDegrees(Axis.ZP, box.angle());
			collector.submitCustomGeometry(pose, RenderTypes.entityCutout(GEAR), (at, vertices) -> box(vertices, at, box, light));
			pose.popPose();
		}
		pose.popPose();
	}

	/** A box's six faces, each sampling its texture column by the face's own size. */
	private static void box(VertexConsumer v, PoseStack.Pose at, Box b, int light) {
		float x0 = b.x0(), y0 = b.y0(), z0 = b.z0(), x1 = b.x1(), y1 = b.y1(), z1 = b.z1();
		quad(v, at, b.u(), light, 0, 0, 1, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1); // front
		quad(v, at, b.u(), light, 0, 0, -1, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0); // back
		quad(v, at, b.u(), light, 1, 0, 0, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1);
		quad(v, at, b.u(), light, -1, 0, 0, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
		quad(v, at, b.u(), light, 0, 1, 0, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0);
		quad(v, at, b.u(), light, 0, -1, 0, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
	}

	private static void quad(VertexConsumer v, PoseStack.Pose at, int u, int light, float nx, float ny, float nz, float... corners) {
		// The column is 2 texels wide (12 for brass); a face samples a patch of it as big as itself.
		float width = u == BRASS ? 12 : 2;
		float du = Math.min(width, Math.max(Math.abs(corners[3] - corners[0]) + Math.abs(corners[5] - corners[2]), 0.5f));
		float dv = Math.min(16, Math.max(Math.abs(corners[7] - corners[4]) + Math.abs(corners[8] - corners[5]), 0.5f));
		float[][] uv = {{u, dv}, {u + du, dv}, {u + du, 0}, {u, 0}};
		for (int i = 0; i < 4; i++) {
			v.addVertex(at, corners[i * 3], corners[i * 3 + 1], corners[i * 3 + 2]).setColor(0xFFFFFFFF)
				.setUv(uv[i][0] / 16, uv[i][1] / 16).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(at, nx, ny, nz);
		}
	}
}
