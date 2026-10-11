package com.payonel.modularcomputers.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.payonel.modularcomputers.ModularComputers;
import com.payonel.modularcomputers.block.AllInOneBlockEntity;
import com.payonel.modularcomputers.display.Viewport;
import com.payonel.modularcomputers.item.ModComponents;
import com.payonel.modularcomputers.menu.CaseMenu;
import com.payonel.modularcomputers.network.ReorderBusPayload;
import com.payonel.modularcomputers.power.ChargeMeter;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The case view: the computer's motherboard with its part slots, above the player's inventory.
 * Left of it, the bus order panel lists the devices on the bus in discovery order; its arrows
 * move a device up or down. Right of it, the power button and the toggle back to the screen
 * view, as in the screen view.
 * <p>
 * The board, the bus panel's backing and the icons are textures (scripts/art/case_gui_textures.py);
 * labels, numbers and everything that reacts to the parts (seated RAM, plugged drives, the
 * battery meter, ...) are drawn here on top.
 */
public final class CaseScreen extends AbstractContainerScreen<CaseMenu> {
	private static final int WIDTH = 176;
	private static final int HEIGHT = 230;
	private static final Identifier BOARD = ModularComputers.id("textures/gui/case_board.png");
	private static final int BOARD_W = 168;
	private static final int BOARD_H = 132;
	private static final Identifier BUS_PANEL = ModularComputers.id("textures/gui/bus_panel.png");
	private static final int BUS_W = 176;
	private static final int BUS_H = 230;
	private static final int BUS_GAP = 6;
	public static final int BUS_ROWS = 10;
	private static final int BUS_ROW_Y = 30;
	private static final int BUS_ROW_H = 19;
	private static final Identifier ICONS = ModularComputers.id("textures/gui/case_icons.png");
	private static final int ICON_CPU = 0, ICON_ROM = 1, ICON_RAM = 2, ICON_DISK = 3, ICON_CARD = 4, ICON_UPS = 5;

	private static final int PANEL = 0xFFC6C6C6;
	private static final int LIGHT = 0xFFFFFFFF;
	private static final int SHADOW = 0xFF555555;
	private static final int OUTLINE = 0xFF000000;
	private static final int SLOT_DARK = 0xFF373737;
	private static final int SLOT_FACE = 0xFF8B8B8B;
	private static final int LABEL = 0xFF404040;
	private static final int SILK = 0xFFEAEADE;
	private static final int SILK_DIM = 0xFF96C4A0;
	private static final int LIT = 0xFF6EF08C;
	private static final int LIT_CORE = 0xFFC8FFD2;
	private static final int SILVER = 0xFFB0B4BC;
	private static final int SATA_RED = 0xFFC4342C;
	private static final int RAM_BOARD = 0xFF287842;
	private static final int CHIP = 0xFF1E1E22;
	private static final int METER_LOW = 0xFFDC3C32;
	private static final int METER_MEDIUM = 0xFFE6C83C;
	private static final int METER_HIGH = 0xFF5AE66E;

	private boolean powerHeld;
	private boolean showBus;

	public CaseScreen(CaseMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, WIDTH, HEIGHT);
		inventoryLabelY = CaseMenu.INVENTORY_Y - 10;
	}

	@Override
	protected void init() {
		super.init();
		// Bus panel | case | buttons, centered together. On a window too narrow for all of
		// it, the bus panel is left out.
		int buttons = ComputerButtons.GAP + ComputerButtons.SIZE;
		showBus = width >= BUS_W + BUS_GAP + imageWidth + buttons;
		int total = (showBus ? BUS_W + BUS_GAP : 0) + imageWidth + buttons;
		leftPos = (width - total) / 2 + (showBus ? BUS_W + BUS_GAP : 0);
	}

	public Viewport powerButton() {
		return new Viewport(leftPos + imageWidth + ComputerButtons.GAP, topPos, ComputerButtons.SIZE, ComputerButtons.SIZE);
	}

	public Viewport viewToggle() {
		Viewport power = powerButton();
		return new Viewport(power.x(), power.bottom() + ComputerButtons.GAP, ComputerButtons.SIZE, ComputerButtons.SIZE);
	}

	/** A slot's 16x16 item area on screen. */
	public Viewport slotArea(int index) {
		Slot slot = menu.slots.get(index);
		return new Viewport(leftPos + slot.x, topPos + slot.y, 16, 16);
	}

	/** Where the board texture is drawn; indicator positions are relative to it. */
	public Viewport board() {
		return new Viewport(leftPos + CaseMenu.BOARD_X, topPos + CaseMenu.BOARD_Y, BOARD_W, BOARD_H);
	}

	public boolean busPanelShown() {
		return showBus;
	}

	public Viewport busPanel() {
		return new Viewport(leftPos - BUS_GAP - BUS_W, topPos, BUS_W, BUS_H);
	}

	/** The up or down arrow of bus order row {@code row}. */
	public Viewport busArrow(int row, boolean up) {
		Viewport panel = busPanel();
		return new Viewport(panel.x() + BUS_W - 34 + (up ? 0 : 12), panel.y() + BUS_ROW_Y + row * BUS_ROW_H + 4, 11, 11);
	}

	private List<AllInOneBlockEntity.BusEntry> busEntries() {
		return minecraft.level != null && minecraft.level.getBlockEntity(menu.pos()) instanceof AllInOneBlockEntity computer
			? computer.busEntries() : List.of();
	}

	// ---- drawing -------------------------------------------------------------------------

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		int x = leftPos;
		int y = topPos;
		graphics.fill(x, y, x + imageWidth, y + imageHeight, OUTLINE);
		graphics.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, LIGHT);
		graphics.fill(x + 2, y + 2, x + imageWidth - 1, y + imageHeight - 1, SHADOW);
		graphics.fill(x + 2, y + 2, x + imageWidth - 2, y + imageHeight - 2, PANEL);
		graphics.blit(RenderPipelines.GUI_TEXTURED, BOARD, x + CaseMenu.BOARD_X, y + CaseMenu.BOARD_Y, 0, 0, BOARD_W, BOARD_H, BOARD_W, BOARD_H);
		extractBoardLabels(graphics, x, y);
		extractIndicators(graphics);
		for (int slot = 0; slot < CaseMenu.PART_SLOTS; slot++) {
			if (!menu.slots.get(slot).hasItem()) {
				Viewport area = slotArea(slot);
				extractIcon(graphics, ghostIcon(slot), area.x(), area.y());
			}
		}
		for (int slot = CaseMenu.PART_SLOTS; slot < menu.slots.size(); slot++) {
			extractInventorySlot(graphics, x + menu.slots.get(slot).x, y + menu.slots.get(slot).y);
		}
		if (showBus) {
			extractBusPanel(graphics);
		}
		if (minecraft.level != null && minecraft.level.getBlockEntity(menu.pos()) instanceof AllInOneBlockEntity computer) {
			ComputerButtons.extract(graphics, powerButton(), viewToggle(), ComputerButtons.Target.SCREEN, computer.getBlockState(), powerHeld);
		}
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		// No title: the board fills the top. Just the inventory's label.
		graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, LABEL, false);
	}

	/** Silkscreen labels, above each part. Positions are the board's, shifted by the board offset. */
	private void extractBoardLabels(GuiGraphicsExtractor graphics, int x, int y) {
		graphics.text(font, "CPU", x + 19, y + 7, SILK, false);
		graphics.text(font, "ROM", x + 19, y + 56, SILK, false);
		graphics.text(font, "UPS", x + 19, y + 92, SILK, false);
		graphics.text(font, "RAM", x + 80, y + 6, SILK, false);
		graphics.text(font, "SATA", x + 138, y + 8, SILK, false);
		graphics.text(font, "EXP", x + 54, y + 103, SILK, false);
	}

	/** What the board shows about its parts. All coordinates are board pixels (see the art script). */
	private void extractIndicators(GuiGraphicsExtractor graphics) {
		int bx = leftPos + CaseMenu.BOARD_X;
		int by = topPos + CaseMenu.BOARD_Y;
		// CPU: the four power-stage dots light up.
		if (hasPart(CaseMenu.CPU_SLOT)) {
			for (int i = 0; i < 4; i++) {
				int cx = bx + 2;
				int cy = by + 18 + 7 * i;
				graphics.fill(cx + 1, cy + 1, cx + 4, cy + 4, LIT);
				graphics.fill(cx + 2, cy + 2, cx + 3, cy + 3, LIT_CORE);
			}
		}
		for (int lane = 0; lane < CaseMenu.LANES; lane++) {
			// RAM: a seated stick fills its connector, held by closed latches.
			if (hasPart(CaseMenu.RAM_SLOTS + lane)) {
				int rx = bx + 50 + 20 * lane;
				int ry = by + 36;
				graphics.fill(rx + 5, ry - 21, rx + 11, ry + 37, RAM_BOARD);
				for (int chip = ry - 18; chip < ry + 34; chip += 8) {
					graphics.fill(rx + 6, chip, rx + 10, chip + 4, CHIP);
				}
				graphics.fill(rx + 5, ry - 23, rx + 11, ry - 21, LIGHT);
				graphics.fill(rx + 5, ry + 37, rx + 11, ry + 39, LIGHT);
			}
			// SATA: a cable plugged in, with an activity light.
			if (hasPart(CaseMenu.SATA_SLOTS + lane)) {
				int sx = bx + 134;
				int sy = by + 15 + 20 * lane;
				graphics.fill(sx + 18, sy + 4, sx + 24, sy + 12, SATA_RED);
				graphics.fill(sx + 18, sy + 4, sx + 24, sy + 5, 0xFFE05A50);
				graphics.fill(sx + 25, sy + 1, sx + 27, sy + 3, LIT);
			}
			// Expansion: the card's retention clip engages.
			if (hasPart(CaseMenu.EXP_SLOTS + lane)) {
				int ex = bx + 52 + 27 * lane;
				int ey = by + 110;
				graphics.fill(ex - 6, ey + 5, ex - 1, ey + 11, SILVER);
				graphics.fill(ex - 5, ey + 7, ex - 2, ey + 9, 0xFF6E727A);
			}
		}
		// UPS battery: the charge meter. No battery: all bars off (the texture's own look).
		ItemStack battery = menu.slots.get(CaseMenu.UPS_SLOT).getItem();
		if (!battery.isEmpty()) {
			int lit = ChargeMeter.litBars(battery.getOrDefault(ModComponents.CHARGE, 0));
			int color = switch (ChargeMeter.level(lit)) {
				case LOW -> METER_LOW;
				case MEDIUM -> METER_MEDIUM;
				default -> METER_HIGH;
			};
			int mx = bx + 12;
			int my = by + 119;
			for (int i = 0; i < lit; i++) {
				graphics.fill(mx + 1 + 4 * i, my + 1, mx + 4 + 4 * i, my + 5, color);
			}
		}
	}

	private boolean hasPart(int slot) {
		return menu.slots.get(slot).hasItem();
	}

	private static int ghostIcon(int slot) {
		if (slot == CaseMenu.CPU_SLOT) {
			return ICON_CPU;
		} else if (slot == CaseMenu.EEPROM_SLOT) {
			return ICON_ROM;
		} else if (slot == CaseMenu.UPS_SLOT) {
			return ICON_UPS;
		} else if (slot < CaseMenu.SATA_SLOTS) {
			return ICON_RAM;
		}
		return slot < CaseMenu.EXP_SLOTS ? ICON_DISK : ICON_CARD;
	}

	private void extractIcon(GuiGraphicsExtractor graphics, int icon, int x, int y) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, ICONS, x, y, icon * 16, 0, 16, 16, 128, 16);
	}

	/** A vanilla-style slot for the player's inventory: 18x18, shadowed top-left, lit bottom-right. */
	private static void extractInventorySlot(GuiGraphicsExtractor graphics, int itemX, int itemY) {
		int x = itemX - 1;
		int y = itemY - 1;
		graphics.fill(x, y, x + 18, y + 18, LIGHT);
		graphics.fill(x, y, x + 17, y + 17, SLOT_DARK);
		graphics.fill(x + 1, y + 1, x + 17, y + 17, SLOT_FACE);
	}

	private void extractBusPanel(GuiGraphicsExtractor graphics) {
		Viewport panel = busPanel();
		graphics.blit(RenderPipelines.GUI_TEXTURED, BUS_PANEL, panel.x(), panel.y(), 0, 0, BUS_W, BUS_H, BUS_W, BUS_H);
		graphics.text(font, Component.translatable("gui.modularcomputers.bus.title"), panel.x() + 10, panel.y() + 6, SILK, false);
		graphics.text(font, Component.translatable("gui.modularcomputers.bus.subtitle"), panel.x() + 10, panel.y() + 17, SILK_DIM, false);
		List<AllInOneBlockEntity.BusEntry> entries = busEntries();
		for (int row = 0; row < BUS_ROWS; row++) {
			int ry = panel.y() + BUS_ROW_Y + row * BUS_ROW_H;
			// The position is etched into the board: it stays put when devices move.
			String number = String.valueOf(row + 1);
			graphics.text(font, number, panel.x() + 11 - font.width(number) / 2, ry + 5, SILK, false);
			if (row >= entries.size()) {
				continue;
			}
			AllInOneBlockEntity.BusEntry entry = entries.get(row);
			if (entry.item().isEmpty()) {
				extractIcon(graphics, entry.builtInIcon(), panel.x() + 19, ry + 1);
			} else {
				graphics.item(entry.item(), panel.x() + 19, ry + 1);
			}
			graphics.text(font, entry.name(), panel.x() + 41, ry + 1, LIGHT, false);
			graphics.text(font, entry.location(), panel.x() + 41, ry + 10, SILK_DIM, false);
			extractArrow(graphics, busArrow(row, true), true, row > 0);
			extractArrow(graphics, busArrow(row, false), false, row < entries.size() - 1);
		}
	}

	/** An 11x11 button with a triangle; greyed out when the device can't move that way. */
	private static void extractArrow(GuiGraphicsExtractor graphics, Viewport b, boolean up, boolean enabled) {
		graphics.fill(b.x(), b.y(), b.right(), b.bottom(), OUTLINE);
		graphics.fill(b.x() + 1, b.y() + 1, b.right() - 1, b.bottom() - 1, enabled ? 0xFFAAAAAA : 0xFF6E6E6E);
		if (enabled) {
			graphics.fill(b.x() + 1, b.bottom() - 2, b.right() - 1, b.bottom() - 1, 0xFF646464);
			graphics.fill(b.x() + 1, b.y() + 1, b.x() + 2, b.bottom() - 1, 0xFFDCDCDC);
		}
		int ink = enabled ? LIGHT : 0xFF969696;
		for (int i = 0; i < 3; i++) {
			int row = b.y() + (up ? 3 + i : 7 - i);
			graphics.fill(b.x() + 5 - i, row, b.x() + 6 + i, row + 1, ink);
		}
	}

	// ---- input ---------------------------------------------------------------------------

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		var hit = ComputerButtons.click(event, powerButton(), viewToggle(), ComputerButtons.Target.SCREEN, menu.pos());
		powerHeld = hit == ComputerButtons.Hit.POWER;
		if (hit != ComputerButtons.Hit.NONE) {
			return true;
		}
		boolean leftOrRight = event.button() == InputConstants.MOUSE_BUTTON_LEFT || event.button() == InputConstants.MOUSE_BUTTON_RIGHT;
		if (showBus && leftOrRight) {
			int devices = busEntries().size();
			for (int row = 0; row < devices; row++) {
				for (boolean up : new boolean[] {true, false}) {
					boolean enabled = up ? row > 0 : row < devices - 1;
					if (enabled && busArrow(row, up).contains(event.x(), event.y())) {
						ClientPlayNetworking.send(new ReorderBusPayload(menu.pos(), row, up));
						return true;
					}
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		powerHeld = false;
		return super.mouseReleased(event);
	}

	@Override
	protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top) {
		// The bus panel and the buttons are part of this screen: clicking them must not drop
		// the carried item.
		boolean onBus = showBus && busPanel().contains(mouseX, mouseY);
		boolean onButtons = powerButton().contains(mouseX, mouseY) || viewToggle().contains(mouseX, mouseY);
		return super.hasClickedOutside(mouseX, mouseY, left, top) && !onBus && !onButtons;
	}
}
