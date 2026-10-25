package com.payonel.modularcomputers.client.screen;

import com.payonel.modularcomputers.display.Viewport;
import com.payonel.modularcomputers.menu.CaseMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

/**
 * A robot's hands in its views: its hand's slot and its off hand's under it, in an iron plate (its
 * arm's gripper), under the views' buttons, its tabs under it. The parts view's slots are live; the
 * screen view shows what it holds, and a click there opens the parts view.
 */
final class HandPlate {
	/** A golem's iron (scripts/art/case_gui_textures.py's). */
	private static final int IRON = 0xFFC9C2B5;
	private static final int IRON_SHADE = 0xFF8A8377;
	private static final int IRON_LIGHT = 0xFFE4DED2;
	private static final int IRON_RIVET = 0xFF706A60;
	private static final int LIGHT = 0xFFFFFFFF;
	private static final int SLOT_DARK = 0xFF373737;
	private static final int SLOT_FACE = 0xFF8B8B8B;
	/** How far the plate reaches round its slots. */
	static final int MARGIN = 3;
	/** Its off hand's slot, below its hand's. */
	static final int OFF_HAND_DOWN = CaseMenu.OFF_HAND_Y - CaseMenu.HAND_Y;
	static final int WIDTH = 16 + 2 * MARGIN;
	static final int HEIGHT = OFF_HAND_DOWN + 16 + 2 * MARGIN;
	/** How far below the view's buttons it starts (as the parts view's slots are placed). */
	static final int BELOW_BUTTONS = CaseMenu.HAND_Y - MARGIN - ((ComputerButtons.VIEW_ROWS + 1) * ComputerButtons.SIZE + ComputerButtons.VIEW_ROWS * ComputerButtons.GAP);

	private HandPlate() {
	}

	/** The plate whose hand's slot's item is at {@code itemX, itemY}. */
	static Viewport at(int itemX, int itemY) {
		return new Viewport(itemX - MARGIN, itemY - MARGIN, WIDTH, HEIGHT);
	}

	/** The plate under the view buttons, {@code parts} the last of them (the screen view's). */
	static Viewport under(Viewport parts) {
		return new Viewport(parts.x() + (parts.width() - WIDTH) / 2, parts.bottom() + BELOW_BUTTONS, WIDTH, HEIGHT);
	}

	/** The plate and its slots' sockets. */
	static void extract(GuiGraphicsExtractor graphics, Viewport plate) {
		graphics.fill(plate.x(), plate.y(), plate.right(), plate.bottom(), IRON_SHADE);
		graphics.fill(plate.x(), plate.y(), plate.right() - 1, plate.bottom() - 1, IRON_LIGHT);
		graphics.fill(plate.x() + 1, plate.y() + 1, plate.right() - 1, plate.bottom() - 1, IRON);
		for (int[] rivet : new int[][] {{1, 1}, {plate.width() - 3, 1}, {1, plate.height() - 3}, {plate.width() - 3, plate.height() - 3}}) {
			graphics.fill(plate.x() + rivet[0], plate.y() + rivet[1], plate.x() + rivet[0] + 2, plate.y() + rivet[1] + 2, IRON_RIVET);
		}
		socket(graphics, plate.x() + MARGIN, plate.y() + MARGIN);
		socket(graphics, plate.x() + MARGIN, plate.y() + MARGIN + OFF_HAND_DOWN);
	}

	/** What it holds in each hand, drawn in their slots (the screen view's: not live slots). */
	static void extractItems(GuiGraphicsExtractor graphics, Viewport plate, ItemStack main, ItemStack off, net.minecraft.client.gui.Font font) {
		graphics.item(main, plate.x() + MARGIN, plate.y() + MARGIN);
		graphics.itemDecorations(font, main, plate.x() + MARGIN, plate.y() + MARGIN);
		graphics.item(off, plate.x() + MARGIN, plate.y() + MARGIN + OFF_HAND_DOWN);
		graphics.itemDecorations(font, off, plate.x() + MARGIN, plate.y() + MARGIN + OFF_HAND_DOWN);
	}

	private static void socket(GuiGraphicsExtractor graphics, int itemX, int itemY) {
		int x = itemX - 1;
		int y = itemY - 1;
		graphics.fill(x, y, x + 18, y + 18, LIGHT);
		graphics.fill(x, y, x + 17, y + 17, SLOT_DARK);
		graphics.fill(x + 1, y + 1, x + 17, y + 17, SLOT_FACE);
	}
}
