package com.payonel.modularcomputers.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.payonel.modularcomputers.ModularComputers;
import com.payonel.modularcomputers.block.CaseBlock;
import com.payonel.modularcomputers.display.Viewport;
import com.payonel.modularcomputers.network.SwitchViewPayload;
import com.payonel.modularcomputers.network.PressPowerPayload;
import com.payonel.modularcomputers.client.ModNetworkClient;
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
	/** An empty area (no width) leaves its button out; {@code state} is only needed for the power button. */
	static void extract(GuiGraphicsExtractor graphics, Viewport power, Viewport toggle, Target target, com.payonel.modularcomputers.block.PowerLight state, boolean powerHeld) {
		if (power.width() > 0) {
			extractPower(graphics, power, state, powerHeld);
		}
		if (toggle.width() > 0) {
			extractToggle(graphics, toggle, target);
		}
	}

	/** Sends the button's action to the server if the click hit one, and says which it hit. */
	static Hit click(MouseButtonEvent event, Viewport power, Viewport toggle, Target target, com.payonel.modularcomputers.network.HostRef pos) {
		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT && event.button() != InputConstants.MOUSE_BUTTON_RIGHT) {
			return Hit.NONE;
		}
		if (power.contains(event.x(), event.y())) {
			// The server decides (no CPU: it beeps instead); the button follows the synced state.
			ModNetworkClient.send(new PressPowerPayload(pos));
			return Hit.POWER;
		}
		if (toggle.contains(event.x(), event.y())) {
			ModNetworkClient.send(new SwitchViewPayload(pos, target == Target.CASE));
			return Hit.TOGGLE;
		}
		return Hit.NONE;
	}

	/** Crafting cards' tab buttons (scripts/art/case_gui_textures.py): closed and open, side by side. */
	private static final Identifier CRAFT_TABS = ModularComputers.id("textures/gui/crafting_tabs.png");

	/** A crafting card's tab button, open or not. */
	static void extractCraftTab(GuiGraphicsExtractor graphics, Viewport tab, boolean open) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, CRAFT_TABS, tab.x(), tab.y(), open ? SIZE : 0, 0, tab.width(), tab.height(), 2 * SIZE, SIZE);
	}

	/** Which expansion lanes of a board's parts hold a crafting card (each gets a tab). */
	static boolean[] craftingLanes(net.minecraft.world.Container parts) {
		boolean[] lanes = new boolean[com.payonel.modularcomputers.menu.CaseMenu.LANES];
		for (int lane = 0; lane < lanes.length; lane++) {
			lanes[lane] = com.payonel.modularcomputers.item.ModItems.cardKind(parts.getItem(com.payonel.modularcomputers.menu.CaseMenu.EXP_SLOTS + lane))
				.orElse(null) == com.payonel.modularcomputers.device.CardKind.CRAFTING;
		}
		return lanes;
	}

	private static void extractPower(GuiGraphicsExtractor graphics, Viewport button, com.payonel.modularcomputers.block.PowerLight state, boolean held) {
		// Ready (a CPU and a charged battery): lit red or green. Otherwise the machine can't be
		// on: faded red, or faded green while the button is held down and for the moment a
		// refused press lasts. On with its CPU stopped (halted): amber.
		boolean green = state.powered();
		int frame = state.ready() ? (green ? (state.halted() ? 4 : 1) : 0) : (held || green ? 3 : 2);
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(button.x(), button.y());
		pose.scale(SCALE, SCALE);
		graphics.blit(RenderPipelines.GUI_TEXTURED, POWER_TEXTURE, 0, 0, frame * TEXELS, 0, TEXELS, TEXELS, 5 * TEXELS, TEXELS);
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
