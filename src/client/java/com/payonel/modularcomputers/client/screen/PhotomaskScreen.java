package com.payonel.modularcomputers.client.screen;

import com.payonel.modularcomputers.block.PhotomaskBlockEntity;
import com.payonel.modularcomputers.menu.PhotomaskMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** A photomask's slots: silicon and a battery on the left, an arrow that fills as it works, chips on the right. */
public final class PhotomaskScreen extends AbstractContainerScreen<PhotomaskMenu> {
	private static final int WIDTH = 176;
	private static final int HEIGHT = 166;
	private static final int PANEL = 0xFFC6C6C6;
	private static final int LIGHT = 0xFFFFFFFF;
	private static final int SHADOW = 0xFF555555;
	private static final int OUTLINE = 0xFF000000;
	private static final int SLOT_DARK = 0xFF373737;
	private static final int SLOT = 0xFF8B8B8B;
	private static final int ARROW = 0xFF8B8B8B;
	private static final int ARROW_DONE = 0xFFFFFFFF;
	private static final int LABEL = 0xFF404040;

	public PhotomaskScreen(PhotomaskMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, WIDTH, HEIGHT);
		inventoryLabelY = PhotomaskMenu.INVENTORY_Y - 10;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		int x = leftPos;
		int y = topPos;
		graphics.fill(x, y, x + imageWidth, y + imageHeight, OUTLINE);
		graphics.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, LIGHT);
		graphics.fill(x + 2, y + 2, x + imageWidth - 1, y + imageHeight - 1, SHADOW);
		graphics.fill(x + 2, y + 2, x + imageWidth - 2, y + imageHeight - 2, PANEL);
		for (Slot slot : menu.slots) {
			int sx = x + slot.x - 1;
			int sy = y + slot.y - 1;
			int size = slot.index == PhotomaskBlockEntity.OUTPUT ? 26 : 18;
			int pad = (size - 18) / 2;
			graphics.fill(sx - pad, sy - pad, sx - pad + size, sy - pad + size, LIGHT);
			graphics.fill(sx - pad, sy - pad, sx - pad + size - 1, sy - pad + size - 1, SLOT_DARK);
			graphics.fill(sx - pad + 1, sy - pad + 1, sx - pad + size - 1, sy - pad + size - 1, SLOT);
		}
		// The arrow from the silicon to the chips, filling as the wafer is printed.
		int ax = x + 72;
		int ay = y + 39;
		int done = Math.round(26f * menu.progress() / PhotomaskBlockEntity.WORK);
		for (int i = 0; i < 26; i++) {
			int half = i < 20 ? 2 : 6 - (i - 20);
			graphics.fill(ax + i, ay - half, ax + i + 1, ay + half + 1, i < done ? ARROW_DONE : ARROW);
		}
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		graphics.text(font, title, titleLabelX, titleLabelY, LABEL, false);
		graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, LABEL, false);
		Component battery = Component.translatable("gui.modularcomputers.photomask.battery");
		graphics.text(font, battery, 44 - 4 - font.width(battery), 54, LABEL, false);
		Component silicon = Component.translatable("gui.modularcomputers.photomask.silicon");
		graphics.text(font, silicon, 44 - 4 - font.width(silicon), 30, LABEL, false);
	}
}
