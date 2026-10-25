package com.payonel.modularcomputers.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.payonel.modularcomputers.ModularComputers;
import com.payonel.modularcomputers.entity.EnergyField;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Draws an energy field: a sphere of translucent blue energy round what it holds, wherever that is
 * this frame (the held thing's own movement, smoothed), shimmering, glowing in the dark.
 */
public final class EnergyFieldRenderer extends EntityRenderer<EnergyField, EnergyFieldRenderer.State> {
	/** Its shimmer (scripts/art/energy_field_textures.py), tinted blue. */
	private static final Identifier TEXTURE = ModularComputers.id("textures/entity/energy_field.png");
	private static final RenderType RENDER_TYPE = RenderTypes.entityTranslucentEmissive(TEXTURE);
	/** Its blue, and how much of what's behind it shows. */
	private static final int COLOR = 0x6A3FA0FF;
	/** Its sphere's slices round and stacks top to bottom. */
	private static final int SLICES = 24;
	private static final int STACKS = 16;
	/** How fast its shimmer drifts round it, a tick (of its texture's width). */
	private static final float DRIFT = 0.004f;
	/** Full light: it glows. */
	private static final int FULL_BRIGHT = 0xF000F0;

	public EnergyFieldRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	/** What a frame draws of it. */
	public static final class State extends EntityRenderState {
		float radius;
		/** Where what it holds is, from the field's own place, this frame. */
		Vec3 offset = Vec3.ZERO;
		float time;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(EnergyField field, State state, float partialTick) {
		super.extractRenderState(field, state, partialTick);
		state.radius = field.radius();
		state.time = field.tickCount + partialTick;
		Entity held = field.level().getEntity(field.heldId());
		if (held != null) {
			Vec3 center = held.getPosition(partialTick).add(0, held.getBbHeight() / 2, 0);
			state.offset = center.subtract(field.getPosition(partialTick));
		} else {
			state.offset = Vec3.ZERO;
		}
	}

	/** Never culled by its own small box: it's drawn round what it holds, which is bigger. */
	@Override
	protected boolean affectedByCulling(EnergyField field) {
		return false;
	}

	@Override
	public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		pose.pushPose();
		pose.translate(state.offset.x, state.offset.y, state.offset.z);
		float drift = state.time * DRIFT;
		collector.submitCustomGeometry(pose, RENDER_TYPE, (at, buffer) -> sphere(at, buffer, state.radius, drift));
		pose.popPose();
		super.submit(state, pose, collector, camera);
	}

	/** A UV sphere of {@code radius}, its texture drifting round it by {@code drift}. */
	private static void sphere(PoseStack.Pose pose, VertexConsumer buffer, float radius, float drift) {
		for (int stack = 0; stack < STACKS; stack++) {
			float v0 = (float) stack / STACKS;
			float v1 = (float) (stack + 1) / STACKS;
			for (int slice = 0; slice < SLICES; slice++) {
				float u0 = (float) slice / SLICES;
				float u1 = (float) (slice + 1) / SLICES;
				vertex(pose, buffer, radius, u0, v0, drift);
				vertex(pose, buffer, radius, u0, v1, drift);
				vertex(pose, buffer, radius, u1, v1, drift);
				vertex(pose, buffer, radius, u1, v0, drift);
			}
		}
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float radius, float u, float v, float drift) {
		float theta = u * Mth.TWO_PI;
		float phi = v * Mth.PI;
		float x = Mth.sin(phi) * Mth.cos(theta);
		float y = Mth.cos(phi);
		float z = Mth.sin(phi) * Mth.sin(theta);
		buffer.addVertex(pose, x * radius, y * radius, z * radius).setColor(COLOR).setUv(u * 2 + drift, v).setOverlay(OverlayTexture.NO_OVERLAY)
			.setLight(FULL_BRIGHT).setNormal(pose, x, y, z);
	}
}
