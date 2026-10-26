package com.payonel.modularcomputers.client.entity;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

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
 * its gold slot), as a case's front shows its terminal; with no screen in, an empty bezel; what
 * it holds, in its right hand; and while its thrusters fire, their beams: glowing red columns down
 * from its leg sockets, tapering and flickering.
 */
public final class BotGolemRenderer extends MobRenderer<BotGolem, BotGolemRenderer.State, BotGolemModel> {
	private static final Identifier TEXTURE = ModularComputers.id("textures/entity/tier1_bot_golem.png");
	/** No screen in its gold slot: its face an empty bezel. */
	private static final Identifier TEXTURE_BLANK = ModularComputers.id("textures/entity/tier1_bot_golem_blank.png");
	/** A tier 2's skin: the same model, more advanced plating (scripts/art/bot_golem_textures.py). */
	private static final Identifier TIER2_TEXTURE = ModularComputers.id("textures/entity/tier2_bot_golem.png");
	private static final Identifier TIER2_TEXTURE_BLANK = ModularComputers.id("textures/entity/tier2_bot_golem_blank.png");
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
	/** Its beams: the field's shimmer (scripts/art/energy_field_textures.py), tinted red, glowing. */
	private static final RenderType BEAM = RenderTypes.entityTranslucentEmissive(ModularComputers.id("textures/entity/energy_field.png"));
	private static final int BEAM_COLOR = 0xD0FF2A1E;
	/** Its hot core, inside it: brighter, narrower, a little shorter. */
	private static final int CORE_COLOR = 0xF0FFB89A;
	private static final float CORE_WIDTH = 0.4f;
	private static final float CORE_LENGTH = 0.75f;
	/** Each socket's beam: how far to its side of its middle, how long, how wide at the socket and at the tip (half widths), in blocks. */
	private static final float SOCKET_SIDE = BotGolem.SOCKET_SIDE;
	private static final float BEAM_LENGTH = 0.5f;
	private static final float BEAM_ROOT = 0.07f;
	private static final float BEAM_TIP = 0.015f;
	/** How much its length flickers (a share of it), and how fast (a tick's phase). */
	private static final float FLICKER = 0.2f;
	private static final float FLICKER_SPEED = 1.7f;
	/** How fast its shimmer runs down it, a tick (of its texture's height). */
	private static final float BEAM_FLOW = 0.08f;
	private static final int FULL_BRIGHT = 0xF000F0;

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
		/** It stands on its legs (else its torso sits on the ground, lower). */
		boolean legs = true;
		/** Its thrusters fire: their beams show. */
		boolean thrusting;
		/** It has thrusters (a tier 2): its skin is a tier 2's. */
		boolean thrusters;
		/** Its age this frame, in ticks: what its beams flicker by. */
		float time;
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
		state.legs = golem.legs();
		state.thrusting = golem.thrusting();
		state.thrusters = golem.tier().thrusters();
		state.time = golem.tickCount + partialTick;
		TerminalFace.extract(golem.terminal(), state.screen, state.face);
	}

	@Override
	public Identifier getTextureLocation(State state) {
		if (state.thrusters) {
			return state.screen ? TIER2_TEXTURE : TIER2_TEXTURE_BLANK;
		}
		return state.screen ? TEXTURE : TEXTURE_BLANK;
	}

	@Override
	public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		super.submit(state, pose, collector, camera);
		if (state.thrusting) {
			pose.pushPose();
			pose.rotateDegrees(Axis.YP, 180 - state.bodyRot);
			for (int side = -1; side <= 1; side += 2) {
				float x = side * SOCKET_SIDE;
				float length = BEAM_LENGTH * (1 - FLICKER * (0.5f + 0.5f * Mth.sin(state.time * FLICKER_SPEED + side)));
				float flow = state.time * BEAM_FLOW;
				collector.submitCustomGeometry(pose, BEAM, (at, buffer) -> {
					beam(at, buffer, x, length * CORE_LENGTH, flow, CORE_WIDTH, CORE_COLOR);
					beam(at, buffer, x, length, flow, 1, BEAM_COLOR);
				});
			}
			pose.popPose();
		}
		if (!state.screen) {
			return;
		}
		pose.pushPose();
		// Turn as its body does, then to the head's front, and face out of it (its front is -Z).
		pose.rotateDegrees(Axis.YP, 180 - state.bodyRot);
		pose.translate(0, 0, BotGolemModel.HEAD_FRONT * PIXEL - TEXT_OFFSET);
		pose.rotateDegrees(Axis.YP, 180);
		float top = BotGolemModel.HEAD_TOP - (state.legs ? 0 : BotGolemModel.LEG_PIXELS);
		pose.translate((GLASS_LEFT - HALF_FACE) * PIXEL, (top - GLASS_TOP) * PIXEL, 0);
		TerminalFace.submit(state.face, GLASS_WIDTH * PIXEL, GLASS_HEIGHT * PIXEL, font, pose, collector);
		pose.popPose();
	}

	/** A beam: four tapering sides, from its socket (at its feet's height) down {@code length}, {@code width} times a beam's wide, its shimmer flowing down it. */
	private static void beam(PoseStack.Pose pose, VertexConsumer buffer, float x, float length, float flow, float width, int color) {
		float root = BEAM_ROOT * width;
		float tip = BEAM_TIP * width;
		float[][] corners = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
		for (int side = 0; side < corners.length; side++) {
			float[] a = corners[side];
			float[] b = corners[(side + 1) % corners.length];
			float u0 = side / 4f;
			float u1 = (side + 1) / 4f;
			beamVertex(pose, buffer, x + a[0] * root, 0, a[1] * root, u0, flow, color);
			beamVertex(pose, buffer, x + a[0] * tip, -length, a[1] * tip, u0, flow + 1, color);
			beamVertex(pose, buffer, x + b[0] * tip, -length, b[1] * tip, u1, flow + 1, color);
			beamVertex(pose, buffer, x + b[0] * root, 0, b[1] * root, u1, flow, color);
		}
	}

	private static void beamVertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, float u, float v, int color) {
		buffer.addVertex(pose, x, y, z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(pose, 0, 1, 0);
	}
}
