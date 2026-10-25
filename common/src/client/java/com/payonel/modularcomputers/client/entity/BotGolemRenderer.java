package com.payonel.modularcomputers.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.payonel.modularcomputers.ModularComputers;
import com.payonel.modularcomputers.client.render.TerminalFace;
import com.payonel.modularcomputers.entity.BotGolem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;

/**
 * Draws a bot golem: its model, and on its head's front its screen's glass (the tier 1 screen in
 * its gold slot), as a case's front shows its terminal; with no screen in, an empty bezel; and what
 * it holds, in its right hand.
 */
public final class BotGolemRenderer extends MobRenderer<BotGolem, BotGolemRenderer.State, BotGolemModel> {
	private static final Identifier TEXTURE = ModularComputers.id("textures/entity/bot_golem.png");
	/** No screen in its gold slot: its face an empty bezel. */
	private static final Identifier TEXTURE_BLANK = ModularComputers.id("textures/entity/bot_golem_blank.png");
	/** Its shadow's radius, in blocks. */
	private static final float SHADOW = 0.4f;
	/** Its glass on its face, in pixels: from the face's left edge and the head's top, and its size. */
	private static final float GLASS_LEFT = 1;
	private static final float GLASS_TOP = 1;
	private static final float GLASS_WIDTH = 5;
	private static final float GLASS_HEIGHT = 4;
	/** The head's front's half width, in pixels. */
	private static final float HALF_FACE = 3.5f;
	/** How far the text floats in front of the face, in blocks. */
	private static final float TEXT_OFFSET = 0.002f;
	private static final float PIXEL = 1 / 16f;

	private final Font font;

	public BotGolemRenderer(EntityRendererProvider.Context context) {
		super(context, new BotGolemModel(BotGolemModel.createBodyLayer().bakeRoot()), SHADOW);
		font = context.getFont();
		addLayer(new HeldItem(this));
	}

	/** What it holds, at the end of its right arm, held as a player holds it (the game's third person pose), at half size. */
	private static final class HeldItem extends net.minecraft.client.renderer.entity.layers.RenderLayer<State, BotGolemModel> {
		/** Where its hand is down its arm, and the game's offsets for a held item from there (ItemInHandLayer's), in pixels. */
		private static final float HAND_DOWN = 7;
		private static final float OFFSET_X = 1;
		private static final float OFFSET_Y = 2;
		/** What it holds is drawn at its scale (the user's): it's half a player's height, so its tools are half a player's. */
		private static final float HELD_SCALE = 0.5f;

		HeldItem(net.minecraft.client.renderer.entity.RenderLayerParent<State, BotGolemModel> parent) {
			super(parent);
		}

		@Override
		public void submit(PoseStack pose, SubmitNodeCollector collector, int light, State state, float yRot, float xRot) {
			if (state.rightHandItemState.isEmpty()) {
				return;
			}
			pose.pushPose();
			getParentModel().translateToRightArm(pose);
			pose.rotateDegrees(Axis.XP, -90);
			pose.rotateDegrees(Axis.YP, 180);
			pose.translate(OFFSET_X * PIXEL * HELD_SCALE, OFFSET_Y * PIXEL * HELD_SCALE, -HAND_DOWN * PIXEL);
			pose.scale(HELD_SCALE, HELD_SCALE, HELD_SCALE);
			state.rightHandItemState.submit(pose, collector, light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, state.outlineColor);
			pose.popPose();
		}
	}

	/** What a frame draws of it. */
	public static final class State extends net.minecraft.client.renderer.entity.state.ArmedEntityRenderState {
		/** A screen is in its gold slot. */
		boolean screen;
		/** A crafting card is in: its storage, a chest on its back. */
		boolean chest;
		final TerminalFace.State face = new TerminalFace.State();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(BotGolem golem, State state, float partialTick) {
		super.extractRenderState(golem, state, partialTick);
		net.minecraft.client.renderer.entity.state.ArmedEntityRenderState.extractArmedEntityRenderState(golem, state, itemModelResolver, partialTick);
		state.screen = golem.showsScreen();
		state.chest = golem.hasCraftingCard();
		TerminalFace.extract(golem.terminal(), state.screen, state.face);
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return state.screen ? TEXTURE : TEXTURE_BLANK;
	}

	@Override
	public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		super.submit(state, pose, collector, camera);
		if (!state.screen) {
			return;
		}
		pose.pushPose();
		// Turn as its body does, then to the head's front, and face out of it (its front is -Z).
		pose.rotateDegrees(Axis.YP, 180 - state.bodyRot);
		pose.translate(0, 0, BotGolemModel.HEAD_FRONT * PIXEL - TEXT_OFFSET);
		pose.rotateDegrees(Axis.YP, 180);
		pose.translate((GLASS_LEFT - HALF_FACE) * PIXEL, (BotGolemModel.HEAD_TOP - GLASS_TOP) * PIXEL, 0);
		TerminalFace.submit(state.face, GLASS_WIDTH * PIXEL, GLASS_HEIGHT * PIXEL, font, pose, collector);
		pose.popPose();
	}
}
