package com.payonel.modularcomputers.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.payonel.modularcomputers.client.Identify;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/** For cabled blocks without a screen (expansion hubs, robotic pistons): just the bulb's outline. */
public final class OutlineRenderer<T extends BlockEntity> implements BlockEntityRenderer<T, OutlineRenderer.State> {
	public static final class State extends BlockEntityRenderState {
		float identify;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(T block, State state, float partialTick, Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(block, state, partialTick, cameraPos, breakProgress);
		state.identify = Identify.pulse(block.getBlockPos());
	}

	@Override
	public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.identify > 0) {
			Outline.submit(state.identify, pose, collector);
		}
	}
}
