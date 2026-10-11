package com.payonel.modularcomputers.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.payonel.modularcomputers.ModularComputers;
import com.payonel.modularcomputers.block.AllInOneBlock;
import com.payonel.modularcomputers.display.Viewport;
import com.payonel.modularcomputers.network.SwitchViewPayload;
import com.payonel.modularcomputers.network.PressPowerPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The button column shared by the screen view and the case view: the power button (it looks
 * like the one on the computer's back: red off, green on; faded and unlit without a CPU) and
 * the toggle between the two views (a motherboard to open the case, a little screen to go
 * back). Left or right click presses either.
 */
final class ComputerButtons {
	static final int SIZE = 20;
	static final int GAP = 4;

	/** Power button art, 5x5 texels each: off, on, off without CPU, on without CPU. Drawn 4x. */
	private static final Identifier POWER_TEXTURE = ModularComputers.id("textures/gui/power_button.png");
	private static final int TEXELS = 5;
	private static final int SCALE = SIZE / TEXELS;
	/** The view toggle's motherboard icon, 16x16. */
	private static final Identifier CASE_TEXTURE = ModularComputers.id("textures/gui/case_button.png");
	private static final int RING = 0xFF38383C;
	private static final int FACE = 0xFF8B8B8B;

	/** Which view the toggle switches to. */
	enum Target {
		CASE,
		SCREEN,
	}

	/** What a click hit. */
	enum Hit {
		NONE,
		POWER,
		TOGGLE,
	}

	private ComputerButtons() {
	}

	/** @param powerHeld the mouse is still down on the power button */
	/** An empty area (no width) leaves its button out. */
	static void extract(GuiGraphicsExtractor graphics, Viewport power, Viewport toggle, Target target, BlockState state, boolean powerHeld) {
		if (power.width() > 0) {
			extractPower(graphics, power, state, powerHeld);
		}
		if (toggle.width() > 0) {
			extractToggle(graphics, toggle, target);
		}
	}

	/** Sends the button's action to the server if the click hit one, and says which it hit. */
	static Hit click(MouseButtonEvent event, Viewport power, Viewport toggle, Target target, BlockPos pos) {
		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT && event.button() != InputConstants.MOUSE_BUTTON_RIGHT) {
			return Hit.NONE;
		}
		if (power.contains(event.x(), event.y())) {
			// The server decides (no CPU: it beeps instead); the button follows the synced state.
			ClientPlayNetworking.send(new PressPowerPayload(pos));
			return Hit.POWER;
		}
		if (toggle.contains(event.x(), event.y())) {
			ClientPlayNetworking.send(new SwitchViewPayload(pos, target == Target.CASE));
			return Hit.TOGGLE;
		}
		return Hit.NONE;
	}

	private static void extractPower(GuiGraphicsExtractor graphics, Viewport button, BlockState state, boolean held) {
		// With a CPU: lit red or green. Without one the machine can't be on: faded red, or faded
		// green while the button is held down and for the moment a refused press lasts.
		boolean green = state.getValue(AllInOneBlock.POWERED);
		int frame = state.getValue(AllInOneBlock.CPU) ? (green ? 1 : 0) : (held || green ? 3 : 2);
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(button.x(), button.y());
		pose.scale(SCALE, SCALE);
		graphics.blit(RenderPipelines.GUI_TEXTURED, POWER_TEXTURE, 0, 0, frame * TEXELS, 0, TEXELS, TEXELS, 4 * TEXELS, TEXELS);
		pose.popMatrix();
	}

	private static void extractToggle(GuiGraphicsExtractor graphics, Viewport button, Target target) {
		graphics.fill(button.x(), button.y(), button.right(), button.bottom(), RING);
		int x = button.x() + 2;
		int y = button.y() + 2;
		graphics.fill(x, y, x + 16, y + 16, FACE);
		if (target == Target.CASE) {
			// A motherboard, like the window into the case on the computer's back.
			graphics.blit(RenderPipelines.GUI_TEXTURED, CASE_TEXTURE, x, y, 0, 0, 16, 16, 16, 16);
		} else {
			// A little screen with a line of text.
			graphics.fill(x + 1, y + 3, x + 15, y + 13, RING);
			graphics.fill(x + 2, y + 4, x + 14, y + 12, 0xFF000000);
			graphics.fill(x + 3, y + 5, x + 9, y + 6, 0xFFFFFFFF);
		}
	}
}
