package com.payonel.modularcomputers.client.screen;

import com.payonel.modularcomputers.ModularComputers;
import com.payonel.modularcomputers.client.ModNetworkClient;
import com.payonel.modularcomputers.display.Viewport;
import com.payonel.modularcomputers.network.HostRef;
import com.payonel.modularcomputers.network.PressPowerPayload;
import com.payonel.modularcomputers.network.SwitchViewPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * The button column right of every view of a computer, a screen or a card holder (the user's: the
 * same buttons in the same places in every view, the same icons, the open view ringed in yellow):
 * from the top, the power button (a computer's only), the screen view (a little screen), the parts
 * view (a motherboard: the board and its bus order, or a block's slots), then a robot's hands, then
 * a tab for each card that has a panel ({@link SideTabs}). A view a block doesn't have shows dimmed
 * and does nothing (a case with no screen), so nothing moves as parts come and go.
 */
final class ComputerButtons {
	static final int SIZE = 20;
	static final int GAP = 4;
	/** The rows the column always has under the power button: the screen view's and the parts view's. */
	static final int VIEW_ROWS = 2;

	/** Power button art, 5x5 texels each: off, on, off without CPU, on without CPU. Drawn 4x. */
	private static final Identifier POWER_TEXTURE = ModularComputers.id("textures/gui/power_button.png");
	private static final int TEXELS = 5;
	private static final int SCALE = SIZE / TEXELS;
	/** The parts view's motherboard icon, 16x16. */
	private static final Identifier CASE_TEXTURE = ModularComputers.id("textures/gui/case_button.png");
	private static final int RING = 0xFF38383C;
	private static final int FACE = 0xFF8B8B8B;
	private static final int SCREEN_GLASS = 0xFF000000;
	private static final int SCREEN_TEXT = 0xFFFFFFFF;
	/** Over a view the block doesn't have: the button shows through it, faded. */
	private static final int DIMMED = 0xA0505054;
	/** The open view's ring, round its button (in the gap between buttons). */
	static final int OPEN = 0xFFFFD23C;
	private static final int OPEN_WIDTH = 2;

	/** The two views every column lists. */
	enum View {
		SCREEN,
		PARTS,
	}

	private ComputerButtons() {
	}

	/** The column's row {@code index} (0 the top), at {@code x}, from {@code top}. */
	static Viewport row(int x, int top, int index) {
		return new Viewport(x, top + index * (SIZE + GAP), SIZE, SIZE);
	}

	/** Where what follows the view buttons (a robot's hands, the cards' tabs) starts: under the parts view's. */
	static int below(Viewport parts) {
		return parts.bottom() + GAP;
	}

	/** The power button's frames (computer_textures.py's GUI_STATES): the aborted light's lit and unlit ones, and how long each shows. */
	private static final int AMBER_FRAME = 4;
	private static final int UNLIT_FRAME = 5;
	private static final int POWER_FRAMES = 6;
	private static final long BLINK_MILLIS = 500;

	/** @param held the mouse is still down on it */
	static void extractPower(GuiGraphicsExtractor graphics, Viewport button, com.payonel.modularcomputers.block.PowerLight state, boolean held) {
		// Ready (a CPU and a charged battery): lit red or green. Otherwise the machine can't be
		// on: faded red, or faded green while the button is held down and for the moment a
		// refused press lasts. Off after a fault (aborted): blinking amber, lit and unlit.
		boolean green = state.powered();
		int frame = state.ready() ? (green ? 1 : 0) : (held || green ? 3 : 2);
		if (state.aborted() && !green) {
			frame = System.currentTimeMillis() / BLINK_MILLIS % 2 == 0 ? AMBER_FRAME : UNLIT_FRAME;
		}
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(button.x(), button.y());
		pose.scale(SCALE, SCALE);
		graphics.blit(RenderPipelines.GUI_TEXTURED, POWER_TEXTURE, 0, 0, frame * TEXELS, 0, TEXELS, TEXELS, POWER_FRAMES * TEXELS, TEXELS);
		pose.popMatrix();
	}

	/** A view's button: its icon always the same; dimmed when the block hasn't the view; ringed when it's the one open. */
	static void extractView(GuiGraphicsExtractor graphics, Viewport button, View view, boolean available, boolean open) {
		graphics.fill(button.x(), button.y(), button.right(), button.bottom(), RING);
		int x = button.x() + 2;
		int y = button.y() + 2;
		graphics.fill(x, y, x + 16, y + 16, FACE);
		if (view == View.PARTS) {
			// A motherboard, like the window into the case on the computer's back.
			graphics.blit(RenderPipelines.GUI_TEXTURED, CASE_TEXTURE, x, y, 0, 0, 16, 16, 16, 16);
		} else {
			// A little screen with a line of text.
			graphics.fill(x + 1, y + 3, x + 15, y + 13, RING);
			graphics.fill(x + 2, y + 4, x + 14, y + 12, SCREEN_GLASS);
			graphics.fill(x + 3, y + 5, x + 9, y + 6, SCREEN_TEXT);
		}
		if (!available) {
			graphics.fill(button.x(), button.y(), button.right(), button.bottom(), DIMMED);
		}
		if (open) {
			ring(graphics, button);
		}
	}

	/** The open view's ring round a button (a view's or a card's tab). */
	static void ring(GuiGraphicsExtractor graphics, Viewport button) {
		int w = OPEN_WIDTH;
		graphics.fill(button.x() - w, button.y() - w, button.right() + w, button.y(), OPEN);
		graphics.fill(button.x() - w, button.bottom(), button.right() + w, button.bottom() + w, OPEN);
		graphics.fill(button.x() - w, button.y(), button.x(), button.bottom(), OPEN);
		graphics.fill(button.right(), button.y(), button.right() + w, button.bottom(), OPEN);
	}

	/** The power button pressed: the server decides (no CPU: it beeps instead); the button follows the synced state. */
	static void pressPower(HostRef host) {
		ModNetworkClient.send(new PressPowerPayload(host));
	}

	/** Goes to a view (the server opens it). */
	static void open(HostRef host, View view) {
		ModNetworkClient.send(new SwitchViewPayload(host, view == View.PARTS));
	}
}
