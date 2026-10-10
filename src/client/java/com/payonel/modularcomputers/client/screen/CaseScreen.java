package com.payonel.modularcomputers.client.screen;

import com.payonel.modularcomputers.block.AllInOneBlockEntity;
import com.payonel.modularcomputers.display.Viewport;
import com.payonel.modularcomputers.menu.CaseMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * The case view: the computer's part slots (CPU, EEPROM) above the player's inventory, drawn in
 * vanilla's container style. The power button and the toggle back to the screen view sit to
 * the right of the panel, as in the screen view.
 */
public final class CaseScreen extends AbstractContainerScreen<CaseMenu> {
	private static final int WIDTH = 176;
	private static final int HEIGHT = 166;
	private static final int PANEL = 0xFFC6C6C6;
	private static final int LIGHT = 0xFFFFFFFF;
	private static final int SHADOW = 0xFF555555;
	private static final int OUTLINE = 0xFF000000;
	private static final int SLOT_DARK = 0xFF373737;
	private static final int SLOT_FACE = 0xFF8B8B8B;
	private static final int LABEL = 0xFF404040;

	private boolean powerHeld;

	public CaseScreen(CaseMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, WIDTH, HEIGHT);
		inventoryLabelY = CaseMenu.INVENTORY_Y - 11;
	}

	public Viewport powerButton() {
		return new Viewport(leftPos + imageWidth + ComputerButtons.GAP, topPos, ComputerButtons.SIZE, ComputerButtons.SIZE);
	}

	/** A slot's 16x16 item area on screen. */
	public Viewport slotArea(int index) {
		Slot slot = menu.slots.get(index);
		return new Viewport(leftPos + slot.x, topPos + slot.y, 16, 16);
	}

	public Viewport viewToggle() {
		Viewport power = powerButton();
		return new Viewport(power.x(), power.bottom() + ComputerButtons.GAP, ComputerButtons.SIZE, ComputerButtons.SIZE);
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
			extractSlotBox(graphics, x + slot.x, y + slot.y);
		}
		extractCenteredLabel(graphics, "CPU", x + CaseMenu.CPU_X + 8, y + CaseMenu.PARTS_Y + 20);
		extractCenteredLabel(graphics, "EEPROM", x + CaseMenu.EEPROM_X + 8, y + CaseMenu.PARTS_Y + 20);
		if (minecraft.level != null && minecraft.level.getBlockEntity(menu.pos()) instanceof AllInOneBlockEntity computer) {
			ComputerButtons.extract(graphics, powerButton(), viewToggle(), ComputerButtons.Target.SCREEN, computer.getBlockState(), powerHeld);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		var hit = ComputerButtons.click(event, powerButton(), viewToggle(), ComputerButtons.Target.SCREEN, menu.pos());
		powerHeld = hit == ComputerButtons.Hit.POWER;
		return hit != ComputerButtons.Hit.NONE || super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		powerHeld = false;
		return super.mouseReleased(event);
	}

	/** A vanilla-style slot: 18x18, shadowed top-left, lit bottom-right. */
	private static void extractSlotBox(GuiGraphicsExtractor graphics, int itemX, int itemY) {
		int x = itemX - 1;
		int y = itemY - 1;
		graphics.fill(x, y, x + 18, y + 18, LIGHT);
		graphics.fill(x, y, x + 17, y + 17, SLOT_DARK);
		graphics.fill(x + 1, y + 1, x + 17, y + 17, SLOT_FACE);
	}

	private void extractCenteredLabel(GuiGraphicsExtractor graphics, String text, int centerX, int y) {
		graphics.text(font, text, centerX - font.width(text) / 2, y, LABEL, false);
	}
}
