package com.payonel.modularcomputers.client.screen;

import com.payonel.modularcomputers.block.ChargingPadBlockEntity;
import com.payonel.modularcomputers.item.ModItems;
import com.payonel.modularcomputers.menu.ChargingPadMenu;
import com.payonel.modularcomputers.power.ChargeMeter;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A charging pad's view: its iron plate (the bot golem's), its battery hatch and radio slot, its
 * buffer's charge meter between, and its lamp, green while it charges a robot.
 */
public final class ChargingPadScreen extends AbstractContainerScreen<ChargingPadMenu> {
	private static final int WIDTH = 176;
	private static final int HEIGHT = 166;
	private static final int OUTLINE = 0xFF000000;
	private static final int LIGHT = 0xFFFFFFFF;
	private static final int SHADOW = 0xFF555555;
	private static final int PANEL = 0xFFC6C6C6;
	/** The golem's iron (scripts/art/bot_golem_textures.py). */
	private static final int IRON = 0xFFC9C2B5;
	private static final int IRON_LIGHT = 0xFFE4DED2;
	private static final int IRON_SHADE = 0xFF8A8377;
	private static final int RIVET = 0xFF706A60;
	private static final int SLOT_DARK = 0xFF373737;
	private static final int SLOT = 0xFF8B8B8B;
	private static final int METER_OFF = 0xFF1E2621;
	private static final int METER_LOW = 0xFFDC3C32;
	private static final int METER_MEDIUM = 0xFFE6B432;
	private static final int METER_HIGH = 0xFF46D25A;
	private static final int LAMP_ON = 0xFF3CDC5A;
	private static final int LAMP_OFF = 0xFF1E6E2E;
	private static final int TITLE = 0xFF2A2C30;
	private static final int LABEL = 0xFF404040;
	private static final int ENGRAVED = 0xFF4A463E;
	/** Ghost items for empty slots. */
	private static final int GHOST = 0xB08B8B8B;
	/** The plate's bottom, above the inventory label. */
	private static final int PLATE_BOTTOM = ChargingPadMenu.INVENTORY_Y - 12;
	/** The meter: its place, and its bars (ChargeMeter's). */
	private static final int METER_X = 70;
	private static final int METER_Y = 39;
	private static final int BAR_W = 4;
	private static final int BAR_H = 8;
	/** The lamp, right of the meter. */
	private static final int LAMP_X = 112;
	private static final int LAMP_Y = 40;
	private static final int LAMP_SIZE = 6;

	public ChargingPadScreen(ChargingPadMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, WIDTH, HEIGHT);
		inventoryLabelY = ChargingPadMenu.INVENTORY_Y - 10;
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
		// Its plate: iron, bevelled, riveted at its corners.
		int px0 = x + 4, py0 = y + 4, px1 = x + imageWidth - 4, py1 = y + PLATE_BOTTOM;
		graphics.fill(px0, py0, px1, py1, IRON);
		graphics.fill(px0, py0, px1, py0 + 1, IRON_LIGHT);
		graphics.fill(px0, py0, px0 + 1, py1, IRON_LIGHT);
		graphics.fill(px0, py1 - 1, px1, py1, IRON_SHADE);
		graphics.fill(px1 - 1, py0, px1, py1, IRON_SHADE);
		for (int[] rivet : new int[][] {{px0 + 3, py0 + 3}, {px1 - 5, py0 + 3}, {px0 + 3, py1 - 5}, {px1 - 5, py1 - 5}}) {
			graphics.fill(rivet[0], rivet[1], rivet[0] + 2, rivet[1] + 2, RIVET);
		}
		for (Slot slot : menu.slots) {
			extractSlot(graphics, x + slot.x - 1, y + slot.y - 1);
		}
		ghost(graphics, ChargingPadBlockEntity.BATTERY, new ItemStack(ModItems.TIER1_BATTERY));
		ghost(graphics, ChargingPadBlockEntity.RADIO, new ItemStack(ModItems.BLUETOOTH_CARD));
		// Its buffer's meter: the case's bars, lit as its charge.
		int lit = ChargeMeter.litBars(menu.buffer());
		int color = switch (ChargeMeter.level(lit)) {
			case LOW -> METER_LOW;
			case MEDIUM -> METER_MEDIUM;
			default -> METER_HIGH;
		};
		for (int bar = 0; bar < ChargeMeter.SEGMENTS; bar++) {
			int bx = x + METER_X + bar * (BAR_W + 1);
			graphics.fill(bx, y + METER_Y, bx + BAR_W, y + METER_Y + BAR_H, bar < lit ? color : METER_OFF);
		}
		// Its lamp: green while it charges a robot.
		graphics.fill(x + LAMP_X - 1, y + LAMP_Y - 1, x + LAMP_X + LAMP_SIZE + 1, y + LAMP_Y + LAMP_SIZE + 1, IRON_SHADE);
		graphics.fill(x + LAMP_X, y + LAMP_Y, x + LAMP_X + LAMP_SIZE, y + LAMP_Y + LAMP_SIZE, menu.charging() ? LAMP_ON : LAMP_OFF);
	}

	/** An empty slot shows, faintly, what goes in it. */
	private void ghost(GuiGraphicsExtractor graphics, int index, ItemStack item) {
		Slot slot = menu.slots.get(index);
		if (!slot.hasItem()) {
			graphics.item(item, leftPos + slot.x, topPos + slot.y);
			graphics.fill(leftPos + slot.x, topPos + slot.y, leftPos + slot.x + 16, topPos + slot.y + 16, GHOST);
		}
	}

	private static void extractSlot(GuiGraphicsExtractor graphics, int x, int y) {
		graphics.fill(x, y, x + 18, y + 18, SLOT_DARK);
		graphics.fill(x + 1, y + 1, x + 18, y + 18, LIGHT);
		graphics.fill(x + 1, y + 1, x + 17, y + 17, SLOT);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		graphics.text(font, title, titleLabelX, titleLabelY, TITLE, false);
		graphics.text(font, Component.translatable("gui.modularcomputers.charging_pad.buffer"), METER_X, METER_Y + BAR_H + 3, ENGRAVED, false);
		graphics.text(font, Component.translatable("gui.modularcomputers.charging_pad.charging"), LAMP_X + LAMP_SIZE + 3, LAMP_Y - 1, ENGRAVED, false);
		graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, LABEL, false);
	}
}
