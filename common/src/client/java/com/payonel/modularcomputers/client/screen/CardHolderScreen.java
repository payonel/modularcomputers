package com.payonel.modularcomputers.client.screen;

import com.payonel.modularcomputers.ModularComputers;
import com.payonel.modularcomputers.display.Viewport;
import com.payonel.modularcomputers.menu.CardHolderMenu;
import com.payonel.modularcomputers.menu.ModMenus;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * The card slots of a block on the network: a strip of circuit board with a socket per slot,
 * above the player's inventory. The radio slot (for a Bluetooth card) has a blue socket and
 * its own label; an expansion hub's four expansion slots sit left of it. A screen's slot view
 * also has the toggle back to its screen view, where the case view has it.
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
	private static final int RADIO_SOCKET = 0xFF28468C;
	private static final int RADIO_FACE = 0xFF3A5AA8;
	private static final int SILK = 0xFFEAEADE;

	public CardHolderScreen(CardHolderMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, WIDTH, HEIGHT);
		inventoryLabelY = CardHolderMenu.INVENTORY_Y - 10;
	}

	/** A screen block's slot view has a way back to the display; other blocks have no other view. */
	private boolean hasScreenView() {
		return menu.getType() == ModMenus.SCREEN;
	}

	/** The screen view's button (the column every view has: {@link ComputerButtons}), or an empty area for blocks without one. */
	public Viewport screenButton() {
		return hasScreenView() ? ComputerButtons.row(leftPos + imageWidth + ComputerButtons.GAP, topPos, 0) : new Viewport(0, 0, 0, 0);
	}

	/** This view's button (ringed), under the screen view's; an empty area for blocks with only this view. */
	public Viewport partsButton() {
		return hasScreenView() ? ComputerButtons.row(leftPos + imageWidth + ComputerButtons.GAP, topPos, 1) : new Viewport(0, 0, 0, 0);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		boolean leftOrRightButton = event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT
			|| event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT;
		if (leftOrRightButton && screenButton().contains(event.x(), event.y())) {
			ComputerButtons.open(com.payonel.modularcomputers.network.HostRef.of(menu.pos()), ComputerButtons.View.SCREEN);
			return true;
		}
		if (leftOrRightButton && partsButton().contains(event.x(), event.y())) {
			return true; // (this view)
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top) {
		// The buttons are part of this screen: clicking them must not drop the carried item.
		return super.hasClickedOutside(mouseX, mouseY, left, top) && !screenButton().contains(mouseX, mouseY) && !partsButton().contains(mouseX, mouseY);
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
			boolean pairing = slot == menu.radioSlot();
			graphics.fill(area.x() - 2, area.y() - 2, area.right() + 2, area.bottom() + 2, pairing ? RADIO_SOCKET : SOCKET);
			graphics.fill(area.x() - 1, area.y() - 1, area.right() + 1, area.bottom() + 1, pairing ? RADIO_FACE : SOCKET_FACE);
			if (pairing) {
				Component label = Component.translatable("gui.modularcomputers.radio");
				graphics.text(font, label, area.x() + 8 - font.width(label) / 2, area.y() - 12, SILK, false);
			}
			if (!menu.slots.get(slot).hasItem()) {
				graphics.blit(RenderPipelines.GUI_TEXTURED, ICONS, area.x(), area.y(), ICON_CARD * 16, 0, 16, 16, 128, 16);
			}
		}
		if (hasScreenView()) {
			ComputerButtons.extractView(graphics, screenButton(), ComputerButtons.View.SCREEN, true, false);
			ComputerButtons.extractView(graphics, partsButton(), ComputerButtons.View.PARTS, true, true);
		}
		for (int slot = menu.cardSlots(); slot < menu.slots.size(); slot++) {
			int sx = x + menu.slots.get(slot).x - 1;
			int sy = y + menu.slots.get(slot).y - 1;
			graphics.fill(sx, sy, sx + 18, sy + 18, LIGHT);
			graphics.fill(sx, sy, sx + 17, sy + 17, 0xFF373737);
			graphics.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF8B8B8B);
		}
		SlotHints.extract(graphics, menu, hoveredSlot, menu.cardSlots(), this::slotArea);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		graphics.text(font, title, titleLabelX, titleLabelY, LABEL, false);
		graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, LABEL, false);
	}
}
