package com.payonel.modularcomputers.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.payonel.modularcomputers.machine.Motherboard;
import com.payonel.modularcomputers.ModularComputers;
import com.payonel.modularcomputers.block.CaseBlockEntity;
import com.payonel.modularcomputers.block.BoardHolder;
import com.payonel.modularcomputers.bus.BusStatus;
import com.payonel.modularcomputers.client.Identify;
import com.payonel.modularcomputers.display.ScrollWindow;
import com.payonel.modularcomputers.display.Viewport;
import com.payonel.modularcomputers.item.BatteryItem;
import com.payonel.modularcomputers.menu.CaseMenu;
import com.payonel.modularcomputers.network.ConnectDevicePayload;
import com.payonel.modularcomputers.network.ForgetDevicePayload;
import com.payonel.modularcomputers.network.ReorderBusPayload;
import com.payonel.modularcomputers.power.ChargeMeter;
import com.payonel.modularcomputers.power.Durations;
import com.payonel.modularcomputers.power.PowerBuffer;
import com.payonel.modularcomputers.power.PowerUnits;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import com.payonel.modularcomputers.client.ModNetworkClient;
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
 * (connect or disconnect it). Right of it, the button column every view has ({@link ComputerButtons}):
 * power, the screen view, this view (ringed while the bus order shows), a robot's hands, the cards' tabs.
 * <p>
 * The board, the bus panel's backing and the icons are textures (scripts/art/case_gui_textures.py);
 * labels, numbers and everything that reacts to the parts (seated RAM, plugged drives, the
 * battery meter, ...) are drawn here on top.
 */
public final class CaseScreen extends AbstractContainerScreen<CaseMenu> {
	private static final int WIDTH = 176;
	private static final int HEIGHT = 230;
	private static final Identifier BOARD = ModularComputers.id("textures/gui/case_board.png");
	/** The same board with every part's footprint left unpopulated: what a lower tier shows where it has no slot. */
	private static final Identifier BOARD_BARE = ModularComputers.id("textures/gui/case_board_bare.png");
	private static final int BOARD_W = 168;
	private static final int BOARD_H = 132;
	/** A golem's: the same board, its robotics device soldered on, seen through its iron chest (golem_panel.png). */
	private static final Identifier GOLEM_BOARD = ModularComputers.id("textures/gui/golem_board.png");
	private static final Identifier GOLEM_BOARD_BARE = ModularComputers.id("textures/gui/golem_board_bare.png");
	private static final Identifier GOLEM_PANEL = ModularComputers.id("textures/gui/golem_panel.png");
	private static final Identifier BUS_PANEL = ModularComputers.id("textures/gui/bus_panel.png");
	static final int BUS_W = 176;
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
	/** The icon sheet's width: nine 16-pixel icons. */
	private static final int ICONS_WIDTH = 16 * 9;
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
	private static final int GOLD_TRIM = 0xFFD4AA28;
	private static final int METER_MEDIUM = 0xFFE6C83C;
	private static final int METER_HIGH = 0xFF5AE66E;
	private static final int BULB_LIT = 0xFFFFE066;
	private static final int BULB_OFF = 0xFFE8E8E0;
	private static final int BULB_BASE = 0xFF505058;
	private static final int PLUG_IN = 0xFF3CB45A;
	private static final int PLUG_OUT = 0xFF8C8C8C;
	private static final int LED_WARNING = 0xFFF0A020;
	/** An offline device's row: a gray display, with its name and "offline" in lighter grays. */
	private static final int OFFLINE_DISPLAY = 0xFF4E4E52;
	private static final int OFFLINE_NAME = 0xFFC4C4C4;
	private static final int OFFLINE_LOCATION = 0xFF96969A;
	private static final int LED_GLINT = 0xFFFFE8A0;
	private static final int LED_OFF = 0xFF3A2E1A;

	/** The left button went down on the bus order scrollbar and is still held. */
	private boolean draggingScrollbar;
	private boolean powerHeld;
	private boolean showBus;
	/** The first device shown in the bus order panel. */
	private int busScroll;

	public CaseScreen(CaseMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, WIDTH, HEIGHT);
		inventoryLabelY = CaseMenu.INVENTORY_Y - 10;
		boolean golem = menu.tier() instanceof com.payonel.modularcomputers.machine.GolemLayout;
		board = golem ? GOLEM_BOARD : BOARD;
		boardBare = golem ? GOLEM_BOARD_BARE : BOARD_BARE;
		panel = golem ? GOLEM_PANEL : null;
	}

	/** Its board's art, full and bare, and its panel's (null: the plain panel), by what it's in. */
	private final Identifier board;
	private final Identifier boardBare;
	private final Identifier panel;

	@Override
	protected void init() {
		super.init();
		menu.openTab(tabToOpen);
		tabToOpen = SideTabs.BUS;
		layout();
	}

	/**
	 * The open tab's panel | case | buttons (its tabs under them), centered together. On a window
	 * too narrow for it, an optional panel (the bus order's) is left out.
	 */
	private void layout() {
		int buttons = ComputerButtons.GAP + ComputerButtons.SIZE;
		SideTabs.Tab open = openTab();
		laidOut = open.lane();
		showPanel = !open.optional() || width >= open.width() + BUS_GAP + imageWidth + buttons;
		showBus = showPanel && open.lane() == SideTabs.BUS;
		int panel = showPanel ? open.width() + BUS_GAP : 0;
		leftPos = (width - (panel + imageWidth + buttons)) / 2 + panel;
	}

	/** The tab to open when this view opens (a card's, clicked in the screen view), or none. */
	static int tabToOpen = SideTabs.BUS;
	/** The tab it's laid out for: what's in its slots is sent after it opens, so it's laid out again when the open tab's card comes or goes. */
	private int laidOut = SideTabs.BUS;
	/** Whether the open tab's panel shows (an optional one is left out of a narrow window). */
	private boolean showPanel;

	/** Whether the bus order panel shows (its tab open, and room for it). */
	public boolean showsBus() {
		return showBus;
	}

	/** Its cards' tabs ({@link SideTabs}), by what's in its slots now. */
	private java.util.List<SideTabs.Tab> tabs() {
		return SideTabs.of(lane -> menu.slots.get(CaseMenu.EXP_SLOTS + lane).getItem());
	}

	/** The open panel: the menu's tab (which card's slots show), while that card is still in; else the bus order's. */
	private SideTabs.Tab openTab() {
		for (SideTabs.Tab tab : tabs()) {
			if (tab.lane() == menu.openTab()) {
				return tab;
			}
		}
		return SideTabs.bus();
	}

	/** A card's tab's button, by its lane, under the view buttons (and a robot's hands); empty when it has none. */
	public Viewport tab(int lane) {
		java.util.List<SideTabs.Tab> tabs = tabs();
		int top = menu.hasHand() ? handPlate().bottom() + ComputerButtons.GAP : ComputerButtons.below(partsButton());
		for (int index = 0; index < tabs.size(); index++) {
			if (tabs.get(index).lane() == lane) {
				return SideTabs.button(leftPos + imageWidth + ComputerButtons.GAP, top, index);
			}
		}
		return new Viewport(0, 0, 0, 0);
	}

	/** The open tab's panel, left of the board (empty while it's left out). */
	public Viewport panel() {
		SideTabs.Tab open = openTab();
		return showPanel ? new Viewport(leftPos - BUS_GAP - open.width(), topPos, open.width(), imageHeight) : new Viewport(0, 0, 0, 0);
	}

	/** The button column's row {@code index}, right of the panel ({@link ComputerButtons}). */
	private Viewport row(int index) {
		return ComputerButtons.row(leftPos + imageWidth + ComputerButtons.GAP, topPos, index);
	}

	public Viewport powerButton() {
		return row(0);
	}

	/** The screen view's button: always there, dimmed (and doing nothing) with no screen in its slot. */
	public Viewport screenButton() {
		return row(1);
	}

	/** Whether it has a screen view to go to (a screen in its slot). */
	public boolean hasScreenView() {
		return minecraft != null && computer().map(BoardHolder::showsScreen).orElse(false);
	}

	/** The parts view's button (this view's): it opens the bus order's panel. */
	public Viewport partsButton() {
		return row(2);
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
		PowerBuffer power = new PowerBuffer(menu.tier().bufferCapacity());
		power.set(menu.powerLevel());
		return power;
	}

	/** The case power meter under the battery bays. */
	public Viewport chargeMeter() {
		Viewport board = board();
		return new Viewport(board.x() + 20, board.y() + 119, 4 * ChargeMeter.SEGMENTS + 1, 6);
	}

	/**
	 * The meter's hover hint: the case's power and the batteries' (all the bays as one) in percent;
	 * then what's stored in all of them, the load (what the computer draws now, a tick) and how
	 * long the stored power lasts at that load (DD:MM:SS). Units go without a name.
	 */
	public List<Component> powerTooltip() {
		PowerBuffer power = casePower();
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("gui.modularcomputers.power", power.percent()));
		long units = 0;
		long capacity = 0;
		boolean any = false;
		boolean infinite = false;
		for (int bay = 0; bay < CaseMenu.UPS_BAYS; bay++) {
			ItemStack stack = menu.slots.get(CaseMenu.UPS_SLOT + bay).getItem();
			if (stack.getItem() instanceof BatteryItem battery) {
				any = true;
				infinite |= battery.tier().infinite();
				if (!battery.tier().infinite()) {
					units += BatteryItem.charge(stack).units();
					capacity += battery.tier().capacity();
				}
			}
		}
		if (!any) {
			lines.add(Component.translatable("gui.modularcomputers.power.batteries_none"));
		} else {
			int percent = infinite || capacity == 0 ? 100 : (int) Math.min(100, Math.ceil(units * 100.0 / capacity));
			lines.add(Component.translatable("gui.modularcomputers.power.batteries", percent));
		}
		long stored = power.level() + units;
		lines.add(Component.translatable("gui.modularcomputers.power.stored",
			PowerUnits.amount(stored, infinite)));
		long load = menu.load();
		lines.add(Component.translatable("gui.modularcomputers.power.load", PowerUnits.rate(load)));
		long runtime = PowerUnits.runtime(stored, load);
		if (infinite) {
			lines.add(Component.translatable("gui.modularcomputers.power.time_forever"));
		} else if (runtime < 0) {
			lines.add(Component.translatable("gui.modularcomputers.power.time_idle"));
		} else {
			lines.add(Component.translatable("gui.modularcomputers.power.time", Durations.clock(runtime)));
		}
		return lines;
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		if (menu.getCarried().isEmpty() && chargeMeter().contains(mouseX, mouseY)) {
			graphics.setComponentTooltipForNextFrame(font, powerTooltip(), mouseX, mouseY);
		}
		busButtonTip(mouseX, mouseY).ifPresent(tip -> graphics.setTooltipForNextFrame(font, tip, mouseX, mouseY));
		if (menu.getCarried().isEmpty()) {
			solderedTooltip(mouseX, mouseY).ifPresent(lines -> {
				List<net.minecraft.util.FormattedCharSequence> wrapped = new ArrayList<>();
				for (Component line : lines) {
					wrapped.addAll(font.split(line, TOOLTIP_WIDTH)); // (wrapped, as an item's hint is)
				}
				graphics.setTooltipForNextFrame(font, wrapped, mouseX, mouseY);
			});
		}
	}

	/** Where a soldered device sits on the board (its lane's connector's place), or an empty area for a lane without one. */
	public Viewport soldered(int lane) {
		if (!menu.tier().soldered(lane)) {
			return new Viewport(0, 0, 0, 0);
		}
		Viewport at = slotArea(CaseMenu.EXP_SLOTS + lane);
		return new Viewport(at.x() - SOLDERED_MARGIN, at.y() - SOLDERED_MARGIN, at.width() + 2 * SOLDERED_MARGIN, at.height() + 2 * SOLDERED_MARGIN);
	}

	/** The widest a soldered device's tooltip's lines get before they wrap, in GUI units. */
	private static final int TOOLTIP_WIDTH = 200;
	/** How far round its connector's slot a soldered chip reaches, in GUI units (its art's pins). */
	private static final int SOLDERED_MARGIN = 2;

	/**
	 * What the soldered device under the mouse is (the user's: it's no item, so it has no item's
	 * tooltip): its name, that it's soldered on, and what it does, as an item's hint does.
	 */
	public Optional<List<Component>> solderedTooltip(double mouseX, double mouseY) {
		for (int lane = 0; lane < CaseMenu.LANES; lane++) {
			String kind = menu.tier().solderedKind(lane);
			if (kind != null && soldered(lane).contains(mouseX, mouseY)) {
				String key = "gui.modularcomputers.soldered." + kind;
				return Optional.of(List.of(Component.translatable(key),
					Component.translatable("gui.modularcomputers.soldered").withStyle(net.minecraft.ChatFormatting.GRAY),
					Component.translatable(key + ".about").withStyle(net.minecraft.ChatFormatting.GRAY)));
			}
		}
		return Optional.empty();
	}

	/** The one-word tip for the bus order panel's button under the mouse: View, Disconnect, Up, ... */
	public Optional<Component> busButtonTip(double mouseX, double mouseY) {
		if (!showBus) {
			return Optional.empty();
		}
		List<com.payonel.modularcomputers.board.BoardAssembly.BusEntry> entries = busEntries();
		int firstExternal = firstExternal(entries);
		ScrollWindow window = busWindow(entries.size());
		for (int row = 0; row < window.rowsShown(busScroll); row++) {
			int index = busScroll + row;
			com.payonel.modularcomputers.board.BoardAssembly.BusEntry entry = entries.get(index);
			if (!entry.cabled()) {
				continue; // the case's rows have no buttons
			}
			String tip = null;
			if (busBulb(row).contains(mouseX, mouseY)) {
				tip = "view";
			} else if (busPlug(row).contains(mouseX, mouseY)) {
				tip = !entry.online() ? "forget" : entry.connected() ? "disconnect" : "connect";
			} else if (busArrow(row, true).contains(mouseX, mouseY) && index > firstExternal) {
				tip = "up";
			} else if (busArrow(row, false).contains(mouseX, mouseY) && index < entries.size() - 1) {
				tip = "down";
			}
			if (tip != null) {
				return Optional.of(Component.translatable("gui.modularcomputers.bus.tip." + tip));
			}
		}
		return Optional.empty();
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

	/**
	 * The line under the panel's title: "external devices: 13 of 20", then ", missing 5" when
	 * some are offline, or with an overflow, how many wait (", overflow 2"). Both can't happen at once: a waiting device takes
	 * a missing one's place.
	 */
	public static Component busSummary(BusStatus status) {
		if (status.capacity() == 0) {
			return Component.translatable("gui.modularcomputers.bus.no_cpu"); // no CPU, no bus
		}
		// Everything on the bus counts against the CPU's capacity, the case's own devices too.
		Object[] counts = {status.devices(), status.capacity(), status.internal(), status.external()};
		if (status.overflow()) {
			return Component.translatable("gui.modularcomputers.bus.summary_overflow", counts[0], counts[1], counts[2], counts[3], status.waiting());
		}
		return status.missing() > 0
			? Component.translatable("gui.modularcomputers.bus.summary_missing", counts[0], counts[1], counts[2], counts[3], status.missing())
			: Component.translatable("gui.modularcomputers.bus.summary", counts); // nothing missing: no need to say so
	}

	/** The bus overflow warning light, top right of the bus order panel. */
	public Viewport busOverflowLed() {
		Viewport panel = busPanel();
		return new Viewport(panel.right() - 16, panel.y() + 7, 6, 6);
	}

	/** The display part of a bus order row, right of its icon. */
	public Viewport busRowDisplay(int row) {
		Viewport panel = busPanel();
		return new Viewport(panel.x() + BUS_ROW_X + 19, panel.y() + BUS_ROW_Y + row * BUS_ROW_H + 1, BUS_ROW_W - 19, 17);
	}

	/** The forget button of an offline device's row, where its plug would be. */
	public Viewport busForget(int row) {
		return busPlug(row);
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

	/** What these parts are in: a case, a golem. */
	private Optional<BoardHolder> computer() {
		return menu.host().find(minecraft.level, BoardHolder.class);
	}

	private List<com.payonel.modularcomputers.board.BoardAssembly.BusEntry> busEntries() {
		return computer().map(computer -> computer.board().busEntries()).orElse(List.of());
	}

	// ---- drawing -------------------------------------------------------------------------

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		if (openTab().lane() != laidOut) {
			layout(); // (its card came, or went, after it was laid out)
		}
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		int x = leftPos;
		int y = topPos;
		graphics.fill(x, y, x + imageWidth, y + imageHeight, OUTLINE);
		graphics.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, LIGHT);
		graphics.fill(x + 2, y + 2, x + imageWidth - 1, y + imageHeight - 1, SHADOW);
		graphics.fill(x + 2, y + 2, x + imageWidth - 2, y + imageHeight - 2, PANEL);
		if (panel != null) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, panel, x, y, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);
		}
		graphics.blit(RenderPipelines.GUI_TEXTURED, board, x + CaseMenu.BOARD_X, y + CaseMenu.BOARD_Y, 0, 0, BOARD_W, BOARD_H, BOARD_W, BOARD_H);
		extractBoardLabels(graphics, x, y);
		extractIndicators(graphics);
		extractTierMarks(graphics);
		for (int slot = 0; slot < CaseMenu.PART_SLOTS; slot++) {
			if (!menu.slots.get(slot).hasItem() && CaseMenu.available(menu.tier(), slot)) {
				Viewport area = slotArea(slot);
				extractIcon(graphics, ghostIcon(slot), area.x(), area.y());
			}
		}
		for (int slot = CaseMenu.PART_SLOTS; slot < CaseMenu.PART_SLOTS + CaseMenu.INVENTORY_SLOTS; slot++) {
			extractInventorySlot(graphics, x + menu.slots.get(slot).x, y + menu.slots.get(slot).y);
		}
		if (menu.hasHand()) {
			extractHand(graphics);
		}
		extractTabs(graphics, mouseX, mouseY);
		extractLinkedHighlight(graphics, mouseX, mouseY);
		SlotHints.extract(graphics, menu, hoveredSlot, CaseMenu.PART_SLOTS, this::slotArea);
		if (computer().isPresent()) {
			ComputerButtons.extractPower(graphics, powerButton(), computer().get().powerLight(), powerHeld);
		}
		ComputerButtons.extractView(graphics, screenButton(), ComputerButtons.View.SCREEN, hasScreenView(), false);
		ComputerButtons.extractView(graphics, partsButton(), ComputerButtons.View.PARTS, true, openTab().lane() == SideTabs.BUS);
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
		graphics.text(font, "UPS", x + 28, y + 92, SILK, false);
		graphics.text(font, "RAM", x + 80, y + 6, SILK, false);
		graphics.text(font, "SATA", x + 138, y + 8, SILK, false);
		graphics.text(font, "EXP", x + 74, y + 103, SILK, false);
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
				int ex = bx + 70 + 24 * lane;
				int ey = by + 110;
				graphics.fill(ex - 5, ey + 5, ex - 1, ey + 11, SILVER);
				graphics.fill(ex - 4, ey + 7, ex - 2, ey + 9, 0xFF6E727A);
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

	/**
	 * The case's tier on the one board: where this tier has no slot, the board is as the fab left
	 * it, unpopulated (the bare board's footprint: silkscreen and pads); the screen slot gets its
	 * gold trim.
	 */
	private void extractTierMarks(GuiGraphicsExtractor graphics) {
		int bx = leftPos + CaseMenu.BOARD_X;
		int by = topPos + CaseMenu.BOARD_Y;
		for (int slot = 0; slot < CaseMenu.PART_SLOTS; slot++) {
			if (!CaseMenu.available(menu.tier(), slot)) {
				Viewport part = partArt(slot);
				graphics.blit(RenderPipelines.GUI_TEXTURED, boardBare, bx + part.x(), by + part.y(), part.x(), part.y(),
					part.width(), part.height(), BOARD_W, BOARD_H);
			}
		}
		if (menu.tier().screenSlot()) {
			Viewport slot = slotArea(CaseMenu.EXP_SLOTS);
			int x0 = slot.x() - 2;
			int y0 = slot.y() - 2;
			int x1 = slot.right() + 2;
			int y1 = slot.bottom() + 2;
			graphics.fill(x0, y0, x1, y0 + 1, GOLD_TRIM);
			graphics.fill(x0, y1 - 1, x1, y1, GOLD_TRIM);
			graphics.fill(x0, y0, x0 + 1, y1, GOLD_TRIM);
			graphics.fill(x1 - 1, y0, x1, y1, GOLD_TRIM);
		}
	}

	/** Where a part slot's art is on the board, with its connector (board pixels; see the art script). */
	private static Viewport partArt(int slot) {
		int[] position = CaseMenu.SLOT_POSITIONS[slot];
		int x = position[0] - CaseMenu.BOARD_X;
		int y = position[1] - CaseMenu.BOARD_Y;
		if (slot >= CaseMenu.RAM_SLOTS && slot < CaseMenu.SATA_SLOTS) {
			return new Viewport(x - 1, y - 26, 19, 68); // the upright memory connector and its latches
		} else if (slot >= CaseMenu.SATA_SLOTS && slot < CaseMenu.EXP_SLOTS) {
			return new Viewport(x - 1, y - 1, 26, 19); // the drive and its port
		} else if (slot >= CaseMenu.EXP_SLOTS) {
			return new Viewport(x - 5, y - 1, 24, 19); // the card and its connector
		}
		return new Viewport(x - 1, y - 1, 19, 19);
	}

	private boolean hasPart(int slot) {
		return menu.slots.get(slot).hasItem();
	}

	private static int ghostIcon(int slot) {
		if (slot == CaseMenu.CPU_SLOT) {
			return ICON_CPU;
		} else if (slot == CaseMenu.EEPROM_SLOT) {
			return ICON_ROM;
		} else if (CaseMenu.isBatteryBay(slot)) {
			return ICON_UPS;
		} else if (slot < CaseMenu.SATA_SLOTS) {
			return ICON_RAM;
		}
		return slot < CaseMenu.EXP_SLOTS ? ICON_DISK : ICON_CARD;
	}

	private void extractIcon(GuiGraphicsExtractor graphics, int icon, int x, int y) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, ICONS, x, y, icon * 16, 0, 16, 16, ICONS_WIDTH, 16);
	}

	/** A vanilla-style slot for the player's inventory: 18x18, shadowed top-left, lit bottom-right. */
	/** A robot's hands: their slots (its hand, its off hand under it) in an iron plate, as its arm's gripper, under the buttons. */
	public Viewport handPlate() {
		return HandPlate.at(leftPos + CaseMenu.HAND_X, topPos + CaseMenu.HAND_Y);
	}

	private void extractHand(GuiGraphicsExtractor graphics) {
		HandPlate.extract(graphics, handPlate());
	}

	/** Its tabs' buttons, and the open one's panel. */
	private void extractTabs(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		SideTabs.Tab open = openTab();
		for (SideTabs.Tab tab : tabs()) {
			tab.extractButton(graphics, tab(tab.lane()));
			if (tab.lane() == open.lane()) { // (by lane: each call makes its tabs afresh)
				ComputerButtons.ring(graphics, tab(tab.lane()));
			}
		}
		if (showPanel) {
			open.extractPanel(this, graphics, panel(), mouseX, mouseY);
		}
	}

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
		List<com.payonel.modularcomputers.board.BoardAssembly.BusEntry> entries = busEntries();
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
			// A soldered device's chip under the mouse: its row, by its connector.
			for (int lane = 0; lane < CaseMenu.LANES; lane++) {
				if (soldered(lane).contains(mouseX, mouseY)) {
					for (int i = 0; i < entries.size(); i++) {
						if (entries.get(i).key().equals(com.payonel.modularcomputers.machine.Motherboard.exp(lane))) {
							index = i;
						}
					}
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
		var entry = entries.get(index);
		if (entry.slot() >= 0) {
			outline(graphics, slotArea(entry.slot()), 2);
		} else {
			solderedLane(entry).ifPresent(lane -> outline(graphics, soldered(lane), 1)); // (no slot: its chip on the board)
		}
	}

	/** The lane a bus entry's device is soldered onto, if it's a soldered one. */
	private java.util.Optional<Integer> solderedLane(com.payonel.modularcomputers.board.BoardAssembly.BusEntry entry) {
		for (int lane = 0; lane < CaseMenu.LANES; lane++) {
			if (menu.tier().soldered(lane) && entry.key().equals(com.payonel.modularcomputers.machine.Motherboard.exp(lane))) {
				return java.util.Optional.of(lane);
			}
		}
		return java.util.Optional.empty();
	}

	/** Whether the board's highlight is on a soldered device's chip now (its row, or it, under the mouse): for tests. */
	public boolean solderedHighlighted(int lane, double mouseX, double mouseY) {
		List<com.payonel.modularcomputers.board.BoardAssembly.BusEntry> entries = busEntries();
		int row = busRowAt(mouseX, mouseY, entries.size());
		if (row >= 0) {
			return solderedLane(entries.get(busScroll + row)).map(at -> at == lane).orElse(false);
		}
		return soldered(lane).contains(mouseX, mouseY);
	}

	/** A 1-pixel highlight around an area, {@code gap} pixels out. */
	private static void outline(GuiGraphicsExtractor graphics, Viewport area, int gap) {
		SlotHints.outline(graphics, area, gap, HIGHLIGHT);
	}

	/** The bus order's panel (its tab's: {@link SideTabs}). */
	/**
	 * The disk its EEPROM boots, by its bus entry's key, if it names one on the bus: BootNext's,
	 * else BootOrder's first there (BootVariables.toTry), as the bootloader tries them. The EEPROM's,
	 * read from its item in the slot: no EEPROM, no mark; none named, none (the bootloader's
	 * fallback, the first disk holding /init.lua, is the disks' business, not the chip's).
	 */
	public Optional<String> bootDisk() {
		ItemStack eeprom = menu.slots.get(CaseMenu.EEPROM_SLOT).getItem();
		var vars = eeprom.get(com.payonel.modularcomputers.item.ModComponents.NVRAM);
		if (vars == null) {
			return Optional.empty();
		}
		List<com.payonel.modularcomputers.board.BoardAssembly.BusEntry> entries = busEntries();
		for (var entry : vars.toTry()) {
			for (var bus : entries) {
				if (entry.disk().equals(idOf(bus)) && (!bus.cabled() || bus.online())) {
					return Optional.of(bus.key());
				}
			}
		}
		return Optional.empty();
	}

	/** A bus entry's device's id: a cabled one's key, a part's its item's serial. */
	private static String idOf(com.payonel.modularcomputers.board.BoardAssembly.BusEntry entry) {
		if (entry.key().startsWith("id:")) {
			return entry.key();
		}
		java.util.UUID serial = entry.item().get(com.payonel.modularcomputers.item.ModComponents.SERIAL);
		return serial == null ? "" : "id:" + serial;
	}

	void extractBusPanel(GuiGraphicsExtractor graphics) {
		Viewport panel = busPanel();
		graphics.blit(RenderPipelines.GUI_TEXTURED, BUS_PANEL, panel.x(), panel.y(), 0, 0, BUS_W, BUS_H, BUS_W, BUS_H);
		graphics.text(font, Component.translatable("gui.modularcomputers.bus.title"), panel.x() + 10, panel.y() + 6, SILK, false);
		BusStatus status = computer().map(computer -> computer.board().busStatus()).orElse(BusStatus.EMPTY);
		// How full the external part of the bus is. A wired device that can't join a full bus
		// would otherwise look broken: the warning light comes on, and the line says so.
		// (no computer to ask: a golem held in a hand, its parts on its item: no bus until it's set down)
		Component summary = computer().isPresent() ? busSummary(status) : Component.translatable("gui.modularcomputers.bus.held");
		int room = BUS_W - 10 - 6;
		float scale = Math.min(1f, (float) room / font.width(summary)); // a long line shrinks to fit
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(panel.x() + 10, panel.y() + 17 + (1 - scale) * 4);
		pose.scale(scale, scale);
		graphics.text(font, summary, 0, 0, status.overflow() ? METER_MEDIUM : SILK_DIM, false);
		pose.popMatrix();
		extractLed(graphics, busOverflowLed(), status.overflow());
		List<com.payonel.modularcomputers.board.BoardAssembly.BusEntry> entries = busEntries();
		String bootKey = bootDisk().orElse(null);
		int firstExternal = firstExternal(entries);
		ScrollWindow window = busWindow(entries.size());
		busScroll = window.clamp(busScroll);
		// One row per device on the bus, however many there are.
		for (int row = 0; row < window.rowsShown(busScroll); row++) {
			int index = busScroll + row;
			int ry = panel.y() + BUS_ROW_Y + row * BUS_ROW_H;
			// The position number is etched into the board beside the row: the device's fixed place
			// on the bus, so empty case slots leave gaps (see CaseBlockEntity.busPosition).
			String number = String.valueOf(entries.get(index).position());
			graphics.text(font, number, panel.x() + 11 - font.width(number) / 2, ry + 5, SILK, false);
			graphics.blit(RenderPipelines.GUI_TEXTURED, BUS_ROW, panel.x() + BUS_ROW_X, ry, 0, 0, BUS_ROW_W, 18, BUS_ROW_W, 18);
			com.payonel.modularcomputers.board.BoardAssembly.BusEntry entry = entries.get(index);
			if (entry.item().isEmpty()) {
				extractIcon(graphics, entry.builtInIcon(), panel.x() + BUS_ROW_X + 1, ry + 1);
			} else {
				graphics.item(entry.item(), panel.x() + BUS_ROW_X + 1, ry + 1);
			}
			boolean offline = entry.cabled() && !entry.online();
			if (offline) {
				// An offline device's display is gray, not black: plainly not there right now.
				graphics.fill(panel.x() + BUS_ROW_X + 19, ry + 1, panel.x() + BUS_ROW_X + BUS_ROW_W, ry + 18, OFFLINE_DISPLAY);
			}
			graphics.text(font, entry.name(), panel.x() + 41, ry + 1, offline ? OFFLINE_NAME : entry.connected() ? LIGHT : SILK_DIM, false);
			boolean boots = entry.key().equals(bootKey);
			Component location = boots ? Component.translatable("gui.modularcomputers.bus.boot", entry.location()) : entry.location();
			graphics.text(font, location, panel.x() + 41, ry + 10, offline ? OFFLINE_LOCATION : boots ? GOLD : SILK_DIM, false);
			if (entry.cabled()) {
				extractBulb(graphics, busBulb(row), Identify.isOn(entry.device().get()));
				if (entry.online()) {
					extractPlug(graphics, busPlug(row), entry.connected());
				} else {
					extractForget(graphics, busForget(row));
				}
				// Only external devices move, and only among themselves; the case's are fixed.
				extractArrow(graphics, busArrow(row, true), true, index > firstExternal);
				extractArrow(graphics, busArrow(row, false), false, index < entries.size() - 1);
			}
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

	/** A small round LED: lit amber for a warning, else dark. */
	private static void extractLed(GuiGraphicsExtractor graphics, Viewport led, boolean lit) {
		int color = lit ? LED_WARNING : LED_OFF;
		graphics.fill(led.x() + 1, led.y(), led.right() - 1, led.bottom(), color);
		graphics.fill(led.x(), led.y() + 1, led.right(), led.bottom() - 1, color);
		if (lit) {
			graphics.fill(led.x() + 1, led.y() + 1, led.x() + 3, led.y() + 3, LED_GLINT);
		}
	}

	/** An 11x11 button with an X: forget an offline device. */
	private static void extractForget(GuiGraphicsExtractor graphics, Viewport b) {
		extractButton(graphics, b, true);
		for (int i = 0; i < 5; i++) {
			graphics.fill(b.x() + 3 + i, b.y() + 3 + i, b.x() + 4 + i, b.y() + 4 + i, METER_LOW);
			graphics.fill(b.x() + 7 - i, b.y() + 3 + i, b.x() + 8 - i, b.y() + 4 + i, METER_LOW);
		}
	}

	/** The index of the first external (movable) device, or the size when there's none. */
	private static int firstExternal(List<com.payonel.modularcomputers.board.BoardAssembly.BusEntry> entries) {
		for (int i = 0; i < entries.size(); i++) {
			if (entries.get(i).cabled()) {
				return i;
			}
		}
		return entries.size();
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
		boolean leftOrRightButton = event.button() == InputConstants.MOUSE_BUTTON_LEFT || event.button() == InputConstants.MOUSE_BUTTON_RIGHT;
		powerHeld = leftOrRightButton && powerButton().contains(event.x(), event.y());
		if (powerHeld) {
			ComputerButtons.pressPower(menu.host());
			return true;
		}
		if (leftOrRightButton && screenButton().contains(event.x(), event.y())) {
			if (hasScreenView()) {
				ComputerButtons.open(menu.host(), ComputerButtons.View.SCREEN);
			}
			return true; // (dimmed: nothing)
		}
		if (leftOrRightButton && partsButton().contains(event.x(), event.y())) {
			menu.openTab(SideTabs.BUS); // (this view: its own panel)
			layout();
			return true;
		}
		for (SideTabs.Tab tab : tabs()) {
			if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && tab(tab.lane()).contains(event.x(), event.y())) {
				menu.openTab(tab.lane()); // (a card's slots show while its tab's open: the menu's, on this side)
				layout();
				return true;
			}
		}
		boolean leftOrRight = event.button() == InputConstants.MOUSE_BUTTON_LEFT || event.button() == InputConstants.MOUSE_BUTTON_RIGHT;
		if (showBus && event.button() == InputConstants.MOUSE_BUTTON_LEFT && busWindow(busEntries().size()).scrolls()
			&& busScrollbar().contains(event.x(), event.y())) {
			// Clicking the track jumps the thumb there; holding the button drags it.
			draggingScrollbar = true;
			scrollTo(event.y());
			return true;
		}
		if (showBus && leftOrRight) {
			List<com.payonel.modularcomputers.board.BoardAssembly.BusEntry> entries = busEntries();
			int devices = entries.size();
			ScrollWindow window = busWindow(devices);
			for (int row = 0; row < window.rowsShown(busScroll); row++) {
				int index = busScroll + row;
				com.payonel.modularcomputers.board.BoardAssembly.BusEntry entry = entries.get(index);
				if (entry.cabled() && busBulb(row).contains(event.x(), event.y())) {
					Identify.toggle(entry.device().get());
					return true;
				}
				if (entry.cabled() && !entry.online() && busForget(row).contains(event.x(), event.y())) {
					ModNetworkClient.send(new ForgetDevicePayload(menu.pos(), entry.key()));
					return true;
				}
				if (entry.cabled() && busPlug(row).contains(event.x(), event.y())) {
					ModNetworkClient.send(new ConnectDevicePayload(menu.pos(), entry.key(), !entry.connected()));
					return true;
				}
				for (boolean up : new boolean[] {true, false}) {
					boolean enabled = entry.cabled() && (up ? index > firstExternal(entries) : index < devices - 1);
					if (enabled && busArrow(row, up).contains(event.x(), event.y())) {
						ModNetworkClient.send(new ReorderBusPayload(menu.pos(), entry.key(), up));
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
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (draggingScrollbar) {
			scrollTo(event.y());
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	/** Moves the bus order panel's scrollbar so its thumb is centered at {@code y}. */
	private void scrollTo(double y) {
		Viewport track = busScrollbar();
		busScroll = busWindow(busEntries().size()).offsetAt((int) y - (track.y() + 1), track.height() - 2);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		powerHeld = false;
		draggingScrollbar = false;
		return super.mouseReleased(event);
	}

	@Override
	protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top) {
		// The bus panel and the buttons are part of this screen: clicking them must not drop
		// the carried item.
		boolean onBus = showBus && busPanel().contains(mouseX, mouseY);
		boolean onButtons = powerButton().contains(mouseX, mouseY) || screenButton().contains(mouseX, mouseY) || partsButton().contains(mouseX, mouseY)
			|| menu.hasHand() && handPlate().contains(mouseX, mouseY) || panel().contains(mouseX, mouseY);
		for (SideTabs.Tab tab : tabs()) {
			onButtons |= tab(tab.lane()).contains(mouseX, mouseY);
		}
		return super.hasClickedOutside(mouseX, mouseY, left, top) && !onBus && !onButtons;
	}
}
