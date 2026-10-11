package com.payonel.modularcomputers.client.screen;

import com.payonel.modularcomputers.ModularComputers;
import com.payonel.modularcomputers.display.Viewport;
import com.payonel.modularcomputers.menu.CardHolderMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * The card slots of a block on the network: a strip of circuit board with a socket per slot,
 * above the player's inventory. The pairing slot (for a Bluetooth card) has a blue socket and
 * its own label; an expansion hub's four expansion slots sit left of it.
 */
public final class CardHolderScreen extends AbstractContainerScreen<CardHolderMenu> {
	private static final int WIDTH = 176;
	private static final int HEIGHT = 166;
	private static final Identifier ICONS = ModularComputers.id("textures/gui/case_icons.png");
	private static final int ICON_CARD = 4;
	private static final int PANEL = 0xFFC6C6C6;
	private static final int LIGHT = 0xFFFFFFFF;
	private static final int SHADOW = 0xFF555555;
	private static final int OUTLINE = 0xFF000000;
	private static final int BOARD = 0xFF2A6C3C;
	private static final int BOARD_EDGE = 0xFF1E5430;
	private static final int SOCKET = 0xFF1A1A1E;
	private static final int SOCKET_FACE = 0xFF34343A;
	private static final int LABEL = 0xFF404040;
	private static final int PAIRING_SOCKET = 0xFF28468C;
	private static final int PAIRING_FACE = 0xFF3A5AA8;
	private static final int SILK = 0xFFEAEADE;

	public CardHolderScreen(CardHolderMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, WIDTH, HEIGHT);
		inventoryLabelY = CardHolderMenu.INVENTORY_Y - 10;
	}

	/** A card slot's 16x16 item area on screen. */
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
		// The board strip with a socket per card slot.
		graphics.fill(x + 7, y + 17, x + imageWidth - 7, y + 63, BOARD_EDGE);
		graphics.fill(x + 8, y + 18, x + imageWidth - 8, y + 62, BOARD);
		for (int slot = 0; slot < menu.cardSlots(); slot++) {
			Viewport area = slotArea(slot);
			boolean pairing = slot == menu.pairingSlot();
			graphics.fill(area.x() - 2, area.y() - 2, area.right() + 2, area.bottom() + 2, pairing ? PAIRING_SOCKET : SOCKET);
			graphics.fill(area.x() - 1, area.y() - 1, area.right() + 1, area.bottom() + 1, pairing ? PAIRING_FACE : SOCKET_FACE);
			if (pairing) {
				Component label = Component.translatable("gui.modularcomputers.pairing");
				graphics.text(font, label, area.x() + 8 - font.width(label) / 2, area.y() - 12, SILK, false);
			}
			if (!menu.slots.get(slot).hasItem()) {
				graphics.blit(RenderPipelines.GUI_TEXTURED, ICONS, area.x(), area.y(), ICON_CARD * 16, 0, 16, 16, 128, 16);
			}
		}
		for (int slot = menu.cardSlots(); slot < menu.slots.size(); slot++) {
			int sx = x + menu.slots.get(slot).x - 1;
			int sy = y + menu.slots.get(slot).y - 1;
			graphics.fill(sx, sy, sx + 18, sy + 18, LIGHT);
			graphics.fill(sx, sy, sx + 17, sy + 17, 0xFF373737);
			graphics.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF8B8B8B);
		}
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		graphics.text(font, title, titleLabelX, titleLabelY, LABEL, false);
		graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, LABEL, false);
	}
}
