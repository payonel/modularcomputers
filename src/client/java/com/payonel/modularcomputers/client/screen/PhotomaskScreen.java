package com.payonel.modularcomputers.client.screen;

import com.payonel.modularcomputers.block.PhotomaskBlockEntity;
import com.payonel.modularcomputers.display.Viewport;
import com.payonel.modularcomputers.item.ModItems;
import com.payonel.modularcomputers.menu.PhotomaskMenu;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A photomask's slots, on the machine itself: a riveted steel plate with the mask's glass set in it
 * (chrome circuit traces, like the block's top), silicon and battery slots on the glass, an arrow
 * that lights up as a wafer is printed, and the chips slot. Empty slots show a ghost of what goes in
 * them, named by a tooltip (user, 2026-10-12: pictures, not words). The inventory below is vanilla's.
 */
public final class PhotomaskScreen extends AbstractContainerScreen<PhotomaskMenu> {
	private static final int WIDTH = 176;
	private static final int HEIGHT = 166;
	private static final int PANEL = 0xFFC6C6C6;
	private static final int LIGHT = 0xFFFFFFFF;
	private static final int SHADOW = 0xFF555555;
	private static final int OUTLINE = 0xFF000000;
	private static final int SLOT_DARK = 0xFF373737;
	private static final int SLOT = 0xFF8B8B8B;
	/** The machine's plate: brushed steel, as the block's casing. */
	private static final int STEEL = 0xFF7A8088;
	private static final int STEEL_LIGHT = 0xFFA2A8B0;
	private static final int STEEL_DARK = 0xFF4E525A;
	private static final int RIVET = 0xFFC4C9D0;
	/** The mask: pale glass with chrome traces, in a dark frame. */
	private static final int GLASS = 0xFF8FBFCC;
	private static final int GLASS_DEEP = 0xFF7AAFBE;
	private static final int CHROME = 0xFFE2E8EE;
	private static final int FRAME = 0xFF3A3E46;
	private static final int ARROW = 0xFF4E6E78;
	private static final int ARROW_LIT = 0xFFFFF4C0;
	/** A ghost over the slot's item: the slot's own gray, mostly opaque. */
	private static final int GHOST = 0xB08B8B8B;
	private static final int TITLE = 0xFFF2F4F6;
	private static final int LABEL = 0xFF404040;
	/** The machine's part of the window, above the inventory. */
	private static final int PLATE_BOTTOM = PhotomaskMenu.INVENTORY_Y - 12;
	/** What an empty slot shows, and its name. */
	private static final Map<Integer, ItemStack> GHOSTS = Map.of(
		PhotomaskBlockEntity.INPUT, new ItemStack(ModItems.SILICON),
		PhotomaskBlockEntity.BATTERY, new ItemStack(ModItems.TIER1_BATTERY),
		PhotomaskBlockEntity.OUTPUT, new ItemStack(ModItems.CHIP));

	public PhotomaskScreen(PhotomaskMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, WIDTH, HEIGHT);
		inventoryLabelY = PhotomaskMenu.INVENTORY_Y - 10;
	}

	/** The charge meter, under the battery's slot. */
	public Viewport meter() {
		Slot battery = menu.slots.get(PhotomaskBlockEntity.BATTERY);
		return new Viewport(leftPos + battery.x + 8 - ChargeBars.WIDTH / 2, topPos + battery.y + 19, ChargeBars.WIDTH, ChargeBars.HEIGHT);
	}

	/** A slot's 16x16 item area on screen. */
	public Viewport slotArea(int index) {
		Slot slot = menu.slots.get(index);
		return new Viewport(leftPos + slot.x, topPos + slot.y, 16, 16);
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
		extractPlate(graphics, x + 4, y + 4, x + imageWidth - 4, y + PLATE_BOTTOM);
		for (Slot slot : menu.slots) {
			int sx = x + slot.x - 1;
			int sy = y + slot.y - 1;
			int size = slot.index == PhotomaskBlockEntity.OUTPUT ? 26 : 18;
			int pad = (size - 18) / 2;
			graphics.fill(sx - pad, sy - pad, sx - pad + size, sy - pad + size, LIGHT);
			graphics.fill(sx - pad, sy - pad, sx - pad + size - 1, sy - pad + size - 1, SLOT_DARK);
			graphics.fill(sx - pad + 1, sy - pad + 1, sx - pad + size - 1, sy - pad + size - 1, SLOT);
			ItemStack ghost = GHOSTS.get(slot.index);
			if (ghost != null && slot.index < PhotomaskBlockEntity.SLOTS && !slot.hasItem()) {
				graphics.item(ghost, x + slot.x, y + slot.y);
				graphics.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, GHOST);
			}
		}
		// The light through the mask: an arrow from the silicon to the chips, lit as the wafer is printed.
		int ax = x + 76;
		int ay = y + 40;
		int done = Math.round(26f * menu.progress() / PhotomaskBlockEntity.WORK);
		for (int i = 0; i < 26; i++) {
			int half = i < 20 ? 2 : 6 - (i - 20);
			graphics.fill(ax + i, ay - half, ax + i + 1, ay + half + 1, i < done ? ARROW_LIT : ARROW);
		}
		Viewport meter = meter();
		ChargeBars.extract(graphics, meter.x(), meter.y(), menu.charge());
		// Where things go: silicon and batteries to their slots, and the other way round (as the case view does).
		SlotHints.extract(graphics, menu, hoveredSlot, PhotomaskBlockEntity.SLOTS, this::slotArea);
	}

	/** The steel plate with rivets, and the mask's glass set into it. */
	private void extractPlate(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1) {
		graphics.fill(x0, y0, x1, y1, STEEL_DARK);
		graphics.fill(x0, y0, x1 - 1, y1 - 1, STEEL_LIGHT);
		graphics.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, STEEL);
		for (int sx : new int[] {x0 + 3, x1 - 5}) {
			for (int sy : new int[] {y0 + 3, y1 - 5}) {
				graphics.fill(sx, sy, sx + 2, sy + 2, RIVET);
				graphics.fill(sx + 1, sy + 1, sx + 2, sy + 2, STEEL_DARK);
			}
		}
		// The glass, under the slots: from left of the silicon to right of the chips.
		int gx0 = leftPos + 30;
		int gy0 = topPos + 17;
		int gx1 = leftPos + 146;
		int gy1 = topPos + 66;
		graphics.fill(gx0 - 2, gy0 - 2, gx1 + 2, gy1 + 2, FRAME);
		graphics.fill(gx0, gy0, gx1, gy1, GLASS);
		graphics.fill(gx0, gy0 + (gy1 - gy0) / 2, gx1, gy1, GLASS_DEEP);
		// Chrome traces, the circuit the mask prints.
		int[][] traces = {
			{4, 4, 110, 5}, {4, 4, 5, 44}, {4, 43, 70, 44}, {12, 12, 13, 36}, {12, 36, 108, 37}, {107, 12, 108, 37},
			{40, 24, 76, 25}, {64, 10, 65, 24}, {84, 41, 112, 42}, {111, 41, 112, 46},
		};
		for (int[] t : traces) {
			graphics.fill(gx0 + t[0], gy0 + t[1], gx0 + t[2], gy0 + t[3], CHROME);
		}
		graphics.fill(gx0, gy0, gx1, gy0 + 1, 0x60FFFFFF); // a glint along the top edge
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		graphics.text(font, title, titleLabelX, titleLabelY, TITLE, true);
		graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, LABEL, false);
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		Viewport meter = meter();
		if (mouseX >= meter.x() && mouseX < meter.x() + meter.width() && mouseY >= meter.y() - 1 && mouseY < meter.y() + meter.height() + 1) {
			graphics.setTooltipForNextFrame(font, java.util.List.of(
				Component.translatable("gui.modularcomputers.photomask.charge", menu.charge()).getVisualOrderText(),
				Component.translatable("gui.modularcomputers.photomask.crank").withStyle(net.minecraft.ChatFormatting.GRAY).getVisualOrderText()),
				mouseX, mouseY);
			return;
		}
		// An empty slot says what goes in it.
		if (hoveredSlot != null && !hoveredSlot.hasItem() && hoveredSlot.index < PhotomaskBlockEntity.SLOTS && menu.getCarried().isEmpty()) {
			graphics.setTooltipForNextFrame(font, Component.translatable("hint.modularcomputers.photomask.slot." + hoveredSlot.index), mouseX, mouseY);
		}
	}
}
