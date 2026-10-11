package com.payonel.modularcomputers.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.payonel.modularcomputers.ModularComputers;
import com.payonel.modularcomputers.block.AllInOneBlockEntity;
import com.payonel.modularcomputers.client.Identify;
import com.payonel.modularcomputers.display.ScrollWindow;
import com.payonel.modularcomputers.display.Viewport;
import com.payonel.modularcomputers.item.BatteryItem;
import com.payonel.modularcomputers.menu.CaseMenu;
import com.payonel.modularcomputers.network.ConnectDevicePayload;
import com.payonel.modularcomputers.network.ReorderBusPayload;
import com.payonel.modularcomputers.power.ChargeMeter;
import com.payonel.modularcomputers.power.Durations;
import com.payonel.modularcomputers.power.PowerBuffer;
import java.util.ArrayList;
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
 * Left of it, the bus order panel lists every device on the bus in discovery order, one row
 * each; its arrows move a device up or down, and it scrolls when the devices don't all fit.
 * Cabled devices also get a bulb (outline the device in the world for a while) and a plug
 * (connect or disconnect it). Right of it, the power button and the toggle back to the screen
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
	private static final Identifier BUS_ROW = ModularComputers.id("textures/gui/bus_row.png");
	private static final int BUS_ROW_X = 19;
	private static final int BUS_ROW_W = 141;
	private static final int BUS_ROW_Y = 30;
	private static final int BUS_ROW_H = 19;
	/** Rows that fit in the panel; more devices than this scroll. */
	public static final int BUS_VISIBLE_ROWS = (BUS_H - 8 - BUS_ROW_Y) / BUS_ROW_H;
	private static final int SCROLLBAR_X = BUS_W - 13;
	private static final int SCROLLBAR_W = 6;
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
	private static final int GOLD = 0xFFCCA64A;
	/**
	 * Outlines a part slot and its bus order row together while either is hovered, and the
	 * slots an item would fit while it's carried or hovered in the inventory.
	 */
	private static final int HIGHLIGHT = SlotHints.COLOR;
	/** Closed latches: the open ones' white, pushed in against the module. */
	private static final int LATCH_CLOSED = 0xFFE6E6E6;
	private static final int BOARD_GREEN = 0xFF2A6C3C;
	private static final int SATA_PLUG = 0xFF3CB45A;
	private static final int SATA_PLUG_LIGHT = 0xFF6EDC82;
	private static final int METER_LOW = 0xFFDC3C32;
	private static final int METER_MEDIUM = 0xFFE6C83C;
	private static final int METER_HIGH = 0xFF5AE66E;
	private static final int BULB_LIT = 0xFFFFE066;
	private static final int BULB_OFF = 0xFFE8E8E0;
	private static final int BULB_BASE = 0xFF505058;
	private static final int PLUG_IN = 0xFF3CB45A;
	private static final int PLUG_OUT = 0xFF8C8C8C;

	private boolean powerHeld;
	private boolean showBus;
	/** The first device shown in the bus order panel. */
	private int busScroll;

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

	/** The case's power, as the menu last sent it: up to date every tick. */
	private PowerBuffer casePower() {
		PowerBuffer power = new PowerBuffer();
		power.set(menu.powerLevel());
		return power;
	}

	/** The case power meter under the battery slot. */
	public Viewport chargeMeter() {
		Viewport board = board();
		return new Viewport(board.x() + 12, board.y() + 119, 4 * ChargeMeter.SEGMENTS + 1, 6);
	}

	/**
	 * The meter's hover hint: the case's power and the battery's, each with how long it runs
	 * the computer (DD:MM:SS).
	 */
	public List<Component> powerTooltip() {
		PowerBuffer power = casePower();
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("gui.modularcomputers.power", power.percent(), Durations.clock(power.level())));
		ItemStack stack = menu.slots.get(CaseMenu.UPS_SLOT).getItem();
		if (stack.getItem() instanceof BatteryItem battery) {
			lines.add(battery.tier().infinite()
				? Component.translatable("gui.modularcomputers.power.battery_infinite")
				: Component.translatable("gui.modularcomputers.power.battery", BatteryItem.percent(stack), Durations.clock(BatteryItem.charge(stack).ticks())));
		}
		return lines;
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		if (menu.getCarried().isEmpty() && chargeMeter().contains(mouseX, mouseY)) {
			graphics.setComponentTooltipForNextFrame(font, powerTooltip(), mouseX, mouseY);
		}
	}

	public boolean busPanelShown() {
		return showBus;
	}

	public Viewport busPanel() {
		return new Viewport(leftPos - BUS_GAP - BUS_W, topPos, BUS_W, BUS_H);
	}

	/** The up or down arrow of the bus order panel's {@code row}-th visible row. */
	public Viewport busArrow(int row, boolean up) {
		Viewport panel = busPanel();
		return new Viewport(panel.x() + BUS_ROW_X + BUS_ROW_W - 26 + (up ? 0 : 12), panel.y() + BUS_ROW_Y + row * BUS_ROW_H + 4, 11, 11);
	}

	/** The bulb of a cabled device's row: outlines the device in the world. */
	public Viewport busBulb(int row) {
		Viewport up = busArrow(row, true);
		return new Viewport(up.x() - 26, up.y(), 11, 11);
	}

	/** The plug of a cabled device's row: connects or disconnects it. */
	public Viewport busPlug(int row) {
		Viewport up = busArrow(row, true);
		return new Viewport(up.x() - 14, up.y(), 11, 11);
	}

	/** The scrollbar's track, shown only when the devices don't all fit. */
	public Viewport busScrollbar() {
		Viewport panel = busPanel();
		return new Viewport(panel.x() + SCROLLBAR_X, panel.y() + BUS_ROW_Y, SCROLLBAR_W, BUS_VISIBLE_ROWS * BUS_ROW_H - 1);
	}

	private ScrollWindow busWindow(int devices) {
		return new ScrollWindow(devices, BUS_VISIBLE_ROWS);
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
		extractLinkedHighlight(graphics, mouseX, mouseY);
		for (Slot slot : SlotHints.fitting(menu, hoveredSlot, CaseMenu.PART_SLOTS)) {
			SlotHints.outline(graphics, slotArea(slot.index), 2, HIGHLIGHT);
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
			// RAM: the connector stays black; its contacts close (a thin gold line down the
			// middle) and the latches swing shut against the module.
			if (hasPart(CaseMenu.RAM_SLOTS + lane)) {
				int rx = bx + 50 + 20 * lane;
				int ry = by + 36;
				graphics.fill(rx + 7, ry - 20, rx + 9, ry + 36, GOLD);
				// Open latches stick out (8 wide); closed ones hug the connector (6 wide).
				graphics.fill(rx + 4, ry - 25, rx + 12, ry - 22, BOARD_GREEN);
				graphics.fill(rx + 4, ry + 38, rx + 12, ry + 41, BOARD_GREEN);
				graphics.fill(rx + 5, ry - 24, rx + 11, ry - 21, LATCH_CLOSED);
				graphics.fill(rx + 5, ry + 37, rx + 11, ry + 40, LATCH_CLOSED);
			}
			// SATA: a small green cable plug in the connector.
			if (hasPart(CaseMenu.SATA_SLOTS + lane)) {
				int sx = bx + 134;
				int sy = by + 15 + 20 * lane;
				graphics.fill(sx + 19, sy + 6, sx + 23, sy + 11, SATA_PLUG);
				graphics.fill(sx + 19, sy + 6, sx + 23, sy + 7, SATA_PLUG_LIGHT);
			}
			// Expansion: the card's retention clip engages.
			if (hasPart(CaseMenu.EXP_SLOTS + lane)) {
				int ex = bx + 52 + 27 * lane;
				int ey = by + 110;
				graphics.fill(ex - 6, ey + 5, ex - 1, ey + 11, SILVER);
				graphics.fill(ex - 5, ey + 7, ex - 2, ey + 9, 0xFF6E727A);
			}
		}
		// The charge meter under the battery shows the case's power buffer, not the battery.
		// Empty: all bars off (the texture's own look).
		int lit = ChargeMeter.litBars(casePower().percent());
		int color = switch (ChargeMeter.level(lit)) {
			case LOW -> METER_LOW;
			case MEDIUM -> METER_MEDIUM;
			default -> METER_HIGH;
		};
		int mx = chargeMeter().x();
		int my = chargeMeter().y();
		for (int i = 0; i < lit; i++) {
			graphics.fill(mx + 1 + 4 * i, my + 1, mx + 4 + 4 * i, my + 5, color);
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

	/** A visible bus order row (socket and display) on screen. */
	public Viewport busRow(int row) {
		Viewport panel = busPanel();
		return new Viewport(panel.x() + BUS_ROW_X, panel.y() + BUS_ROW_Y + row * BUS_ROW_H, BUS_ROW_W, 18);
	}

	/** The visible bus row under the mouse, or -1. */
	private int busRowAt(double mouseX, double mouseY, int devices) {
		if (!showBus) {
			return -1;
		}
		for (int row = 0; row < busWindow(devices).rowsShown(busScroll); row++) {
			if (busRow(row).contains(mouseX, mouseY)) {
				return row;
			}
		}
		return -1;
	}

	/**
	 * A part slot and its bus order row belong together: hovering either one outlines both.
	 * Built-in devices have a row but no slot; RAM, the CPU and such have a slot but no row.
	 */
	private void extractLinkedHighlight(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		List<AllInOneBlockEntity.BusEntry> entries = busEntries();
		int row = busRowAt(mouseX, mouseY, entries.size());
		int index = row >= 0 ? busScroll + row : -1;
		if (index < 0 && hoveredSlot != null && hoveredSlot.index < CaseMenu.PART_SLOTS) {
			for (int i = 0; i < entries.size(); i++) {
				if (entries.get(i).slot() == hoveredSlot.index) {
					index = i;
				}
			}
		}
		if (index < 0) {
			return;
		}
		int shownRow = index - busScroll;
		if (showBus && shownRow >= 0 && shownRow < busWindow(entries.size()).rowsShown(busScroll)) {
			outline(graphics, busRow(shownRow), 1);
		}
		int slot = entries.get(index).slot();
		if (slot >= 0) {
			outline(graphics, slotArea(slot), 2);
		}
	}

	/** A 1-pixel highlight around an area, {@code gap} pixels out. */
	private static void outline(GuiGraphicsExtractor graphics, Viewport area, int gap) {
		SlotHints.outline(graphics, area, gap, HIGHLIGHT);
	}

	private void extractBusPanel(GuiGraphicsExtractor graphics) {
		Viewport panel = busPanel();
		graphics.blit(RenderPipelines.GUI_TEXTURED, BUS_PANEL, panel.x(), panel.y(), 0, 0, BUS_W, BUS_H, BUS_W, BUS_H);
		graphics.text(font, Component.translatable("gui.modularcomputers.bus.title"), panel.x() + 10, panel.y() + 6, SILK, false);
		graphics.text(font, Component.translatable("gui.modularcomputers.bus.subtitle"), panel.x() + 10, panel.y() + 17, SILK_DIM, false);
		List<AllInOneBlockEntity.BusEntry> entries = busEntries();
		ScrollWindow window = busWindow(entries.size());
		busScroll = window.clamp(busScroll);
		// One row per device on the bus, however many there are.
		for (int row = 0; row < window.rowsShown(busScroll); row++) {
			int index = busScroll + row;
			int ry = panel.y() + BUS_ROW_Y + row * BUS_ROW_H;
			// The position number is etched into the board beside the row; it stays with the
			// position, not the device.
			String number = String.valueOf(index + 1);
			graphics.text(font, number, panel.x() + 11 - font.width(number) / 2, ry + 5, SILK, false);
			graphics.blit(RenderPipelines.GUI_TEXTURED, BUS_ROW, panel.x() + BUS_ROW_X, ry, 0, 0, BUS_ROW_W, 18, BUS_ROW_W, 18);
			AllInOneBlockEntity.BusEntry entry = entries.get(index);
			if (entry.item().isEmpty()) {
				extractIcon(graphics, entry.builtInIcon(), panel.x() + BUS_ROW_X + 1, ry + 1);
			} else {
				graphics.item(entry.item(), panel.x() + BUS_ROW_X + 1, ry + 1);
			}
			graphics.text(font, entry.name(), panel.x() + 41, ry + 1, entry.connected() ? LIGHT : SILK_DIM, false);
			graphics.text(font, entry.location(), panel.x() + 41, ry + 10, SILK_DIM, false);
			if (entry.cabled()) {
				extractBulb(graphics, busBulb(row), Identify.isOn(entry.device().get()));
				extractPlug(graphics, busPlug(row), entry.connected());
			}
			extractArrow(graphics, busArrow(row, true), true, index > 0);
			extractArrow(graphics, busArrow(row, false), false, index < entries.size() - 1);
		}
		if (window.scrolls()) {
			Viewport track = busScrollbar();
			graphics.fill(track.x(), track.y(), track.right(), track.bottom(), 0xFF1A1A1E);
			int start = window.thumbStart(busScroll, track.height() - 2);
			int length = window.thumbLength(track.height() - 2);
			graphics.fill(track.x() + 1, track.y() + 1 + start, track.right() - 1, track.y() + 1 + start + length, SILK_DIM);
		}
	}

	/** The face of an 11x11 button; greyed out when disabled. */
	private static void extractButton(GuiGraphicsExtractor graphics, Viewport b, boolean enabled) {
		graphics.fill(b.x(), b.y(), b.right(), b.bottom(), OUTLINE);
		graphics.fill(b.x() + 1, b.y() + 1, b.right() - 1, b.bottom() - 1, enabled ? 0xFFAAAAAA : 0xFF6E6E6E);
		if (enabled) {
			graphics.fill(b.x() + 1, b.bottom() - 2, b.right() - 1, b.bottom() - 1, 0xFF646464);
			graphics.fill(b.x() + 1, b.y() + 1, b.x() + 2, b.bottom() - 1, 0xFFDCDCDC);
		}
	}

	/** A light bulb: lit yellow while the device is outlined in the world. */
	private static void extractBulb(GuiGraphicsExtractor graphics, Viewport b, boolean lit) {
		extractButton(graphics, b, true);
		int glass = lit ? BULB_LIT : BULB_OFF;
		int x = b.x();
		int y = b.y();
		graphics.fill(x + 4, y + 2, x + 7, y + 3, glass);
		graphics.fill(x + 3, y + 3, x + 8, y + 6, glass);
		graphics.fill(x + 4, y + 6, x + 7, y + 7, glass);
		graphics.fill(x + 4, y + 7, x + 7, y + 9, BULB_BASE);
	}

	/**
	 * A plug over its socket: pushed in (green) while the device is connected, pulled out (grey,
	 * with a gap) when it isn't.
	 */
	private static void extractPlug(GuiGraphicsExtractor graphics, Viewport b, boolean connected) {
		extractButton(graphics, b, true);
		int x = b.x();
		int y = b.y();
		graphics.fill(x + 2, y + 8, x + 9, y + 9, OUTLINE); // the socket
		int body = connected ? PLUG_IN : PLUG_OUT;
		int top = connected ? y + 3 : y + 1;
		graphics.fill(x + 3, top, x + 8, top + 3, body);
		graphics.fill(x + 4, top + 3, x + 5, top + 5, BULB_BASE);
		graphics.fill(x + 6, top + 3, x + 7, top + 5, BULB_BASE);
	}

	/** An 11x11 button with a triangle; greyed out when the device can't move that way. */
	private static void extractArrow(GuiGraphicsExtractor graphics, Viewport b, boolean up, boolean enabled) {
		extractButton(graphics, b, enabled);
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
			List<AllInOneBlockEntity.BusEntry> entries = busEntries();
			int devices = entries.size();
			ScrollWindow window = busWindow(devices);
			for (int row = 0; row < window.rowsShown(busScroll); row++) {
				int index = busScroll + row;
				AllInOneBlockEntity.BusEntry entry = entries.get(index);
				if (entry.cabled() && busBulb(row).contains(event.x(), event.y())) {
					Identify.toggle(entry.device().get());
					return true;
				}
				if (entry.cabled() && busPlug(row).contains(event.x(), event.y())) {
					ClientPlayNetworking.send(new ConnectDevicePayload(menu.pos(), entry.key(), !entry.connected()));
					return true;
				}
				for (boolean up : new boolean[] {true, false}) {
					boolean enabled = up ? index > 0 : index < devices - 1;
					if (enabled && busArrow(row, up).contains(event.x(), event.y())) {
						ClientPlayNetworking.send(new ReorderBusPayload(menu.pos(), index, up));
						return true;
					}
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (showBus && busPanel().contains(mouseX, mouseY) && scrollY != 0) {
			busScroll = busWindow(busEntries().size()).clamp(busScroll - (int) Math.signum(scrollY));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
