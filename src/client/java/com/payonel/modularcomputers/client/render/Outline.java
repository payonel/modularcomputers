package com.payonel.modularcomputers.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.LightCoordsUtil;

/** A wireframe along every edge of a block, seen through walls: "this one". */
final class Outline {
	/** The bus order panel's bulb. */
	static final int IDENTIFY = 0xE6C83C;
	/** How far the frame sits outside the block, in blocks. */
	private static final float OFFSET = 0.02f;

	private Outline() {
	}

	/** Draws the frame around the block at the pose's origin, in {@code rgb} at {@code opacity} (0..1). */
	static void submit(float opacity, PoseStack pose, SubmitNodeCollector collector) {
		submit(opacity, IDENTIFY, pose, collector);
	}

	static void submit(float opacity, int rgb, PoseStack pose, SubmitNodeCollector collector) {
		int color = Math.round(opacity * 255) << 24 | rgb;
		float edge = 1f; // texels: just the casing's bevel, clear of a screen's glass
		for (int face = 0; face < 6; face++) {
			pose.pushPose();
			pose.translate(0.5f, 0.5f, 0.5f);
			if (face < 4) {
				pose.rotateDegrees(Axis.YP, 90 * face);
			} else {
				pose.rotateDegrees(Axis.XP, face == 4 ? 90 : -90);
			}
			pose.translate(-0.5f - OFFSET, 0.5f + OFFSET, 0.5f + OFFSET);
			float scale = (1 + 2 * OFFSET) / 16f;
			pose.scale(scale, -scale, scale);
			collector.submitTextBackground(pose, 0, 0, 16, edge, color, Font.DisplayMode.SEE_THROUGH, LightCoordsUtil.FULL_BRIGHT);
			collector.submitTextBackground(pose, 0, 16 - edge, 16, 16, color, Font.DisplayMode.SEE_THROUGH, LightCoordsUtil.FULL_BRIGHT);
			collector.submitTextBackground(pose, 0, edge, edge, 16 - edge, color, Font.DisplayMode.SEE_THROUGH, LightCoordsUtil.FULL_BRIGHT);
			collector.submitTextBackground(pose, 16 - edge, edge, 16, 16 - edge, color, Font.DisplayMode.SEE_THROUGH, LightCoordsUtil.FULL_BRIGHT);
			pose.popPose();
		}
	}
}
