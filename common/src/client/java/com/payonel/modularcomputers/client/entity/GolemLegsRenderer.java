package com.payonel.modularcomputers.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.payonel.modularcomputers.ModularComputers;
import com.payonel.modularcomputers.entity.GolemLegs;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/** Draws a golem's legs standing alone: its model's legs, its texture, turned as they were left. */
public final class GolemLegsRenderer extends EntityRenderer<GolemLegs, GolemLegsRenderer.State> {
	private static final Identifier TEXTURE = ModularComputers.id("textures/entity/tier2_bot_golem.png");
	/** Model space to the world's: flipped, as every entity model is, its ground (24 pixels) at the feet. */
	private static final float MODEL_GROUND = 1.501f;

	private final EntityModel<State> model;

	public GolemLegsRenderer(EntityRendererProvider.Context context) {
		super(context);
		model = new EntityModel<>(BotGolemModel.createLegsLayer().bakeRoot()) {
		};
	}

	/** What a frame draws of them: which way they face. */
	public static final class State extends EntityRenderState {
		float yaw;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(GolemLegs legs, State state, float partialTick) {
		super.extractRenderState(legs, state, partialTick);
		state.yaw = legs.getYRot(partialTick);
	}

	@Override
	public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		pose.pushPose();
		pose.rotateDegrees(Axis.YP, 180 - state.yaw);
		pose.scale(-1, -1, 1);
		pose.translate(0, -MODEL_GROUND, 0);
		collector.submitModel(model, state, pose, TEXTURE, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
		pose.popPose();
		super.submit(state, pose, collector, camera);
	}
}
