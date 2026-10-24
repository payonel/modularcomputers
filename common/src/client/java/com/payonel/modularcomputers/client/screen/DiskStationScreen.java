package com.payonel.modularcomputers.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.payonel.modularcomputers.block.DiskStationBlockEntity;
import com.payonel.modularcomputers.device.Face;
import com.payonel.modularcomputers.display.Viewport;
import com.payonel.modularcomputers.item.ModItems;
import com.payonel.modularcomputers.menu.DiskStationMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A Disk Station's panel, as the machine itself (the mod's look): an iron plate with an emerald
 * inlay, its actions as raised switches down the left, silkscreened; its two drive bays, the source's
 * marked SOURCE with a lock (only read, and optional: faint while empty) and an arrow up to the
 * target beside its Clone switch; Eject beside the target; the battery and the radio; and, under them,
 * the station drawn as an unfolded box (its faces around its front), the eject face lit, a click on
 * another moving it there. Each switch is a menu button: the server does it as the station's device
 * does. The inventory below is vanilla's.
 */
public final class DiskStationScreen extends AbstractContainerScreen<DiskStationMenu> {
	private static final int WIDTH = 176;
	private static final int HEIGHT = 196;
	private static final int PANEL = 0xFFC6C6C6;
	private static final int LIGHT = 0xFFFFFFFF;
	private static final int SHADOW = 0xFF555555;
	private static final int OUTLINE = 0xFF000000;
	private static final int SLOT_DARK = 0xFF373737;
	private static final int SLOT = 0xFF8B8B8B;
	/** The plate: brushed iron, as the block's casing. */
	private static final int IRON = 0xFFB4B6BA;
	private static final int IRON_LIGHT = 0xFFD6D8DC;
	private static final int IRON_DARK = 0xFF7E8086;
	private static final int EMERALD = 0xFF17C560;
	private static final int EMERALD_DARK = 0xFF0C803E;
	private static final int RIVET = 0xFFE2E4E8;
	/** A switch: raised steel, sunk while the mouse is over it. */
	private static final int SWITCH = 0xFF8E9298;
	private static final int SWITCH_HOVER = 0xFFA4A8AE;
	private static final int SILK = 0xFFF6F7F2;
	/** Lettering engraved in the plate. */
	private static final int ENGRAVED = 0xFF3A3C42;
	private static final int LOCK = 0xFFCCA64A;
	private static final int ARROW = 0xFF4E5258;
	/** An empty source bay: faint, as an optional one. */
	private static final int FAINT = 0x70FFFFFF;
	/** A ghost over an empty slot's item: the slot's own gray, mostly opaque. */
	private static final int GHOST = 0xB08B8B8B;
	private static final int TITLE = 0xFF2A2C30;
	private static final int LABEL = 0xFF404040;
	private static final int STATUS = 0xFF1E5E36;
	private static final int STATUS_BAD = 0xFF8A1E1E;
	/** The machine's part of the window, above the inventory. */
	private static final int PLATE_BOTTOM = DiskStationMenu.INVENTORY_Y - 12;
	private static final int SWITCH_HEIGHT = 14;
	/** The action switches down the left, by menu button id: where each is. */
	private static final int SWITCH_X = 8;
	private static final int SWITCH_WIDTH = 66;
	private static final int SWITCH_TOP = 18;
	private static final int SWITCH_STEP = 17;
	/** The eject face picker: cells of an unfolded box, its front in the middle. */
	private static final int CELL = 5;
	private static final int PICKER_X = 148;
	private static final int PICKER_Y = 68;
	/** Where each face sits in the unfolded box (column, row), named from the station's front. */
	private static final Map<String, int[]> UNFOLDED = Map.of(
		"up", new int[] {1, 0}, "left", new int[] {0, 1}, "front", new int[] {1, 1}, "right", new int[] {2, 1}, "back", new int[] {3, 1},
		"down", new int[] {1, 2});
	/** What an empty slot shows, and its name. */
	private static final Map<Integer, ItemStack> GHOSTS = Map.of(
		DiskStationBlockEntity.TARGET, new ItemStack(ModItems.TIER1_HARD_DISK),
		DiskStationBlockEntity.SOURCE, new ItemStack(ModItems.TIER1_HARD_DISK),
		DiskStationBlockEntity.BATTERY, new ItemStack(ModItems.TIER1_BATTERY),
		DiskStationBlockEntity.RADIO, new ItemStack(ModItems.BLUETOOTH_CARD));

	public DiskStationScreen(DiskStationMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, WIDTH, HEIGHT);
		inventoryLabelY = DiskStationMenu.INVENTORY_Y - 10;
	}

	/** A switch, by its menu button id: the four actions down the left, Clone by the source, Eject by the target. */
	public Viewport switchArea(int id) {
		String action = DiskStationMenu.ACTIONS.get(id);
		return switch (action) {
			case "clone" -> new Viewport(leftPos + DiskStationMenu.SOURCE_X + 20, topPos + DiskStationMenu.SOURCE_Y + 1, 28, SWITCH_HEIGHT);
			case "eject" -> new Viewport(leftPos + DiskStationMenu.TARGET_X + 20, topPos + DiskStationMenu.TARGET_Y + 1, 32, SWITCH_HEIGHT);
			default -> new Viewport(leftPos + SWITCH_X, topPos + SWITCH_TOP + id * SWITCH_STEP, SWITCH_WIDTH, SWITCH_HEIGHT);
		};
	}

	/** A face's cell in the eject face picker (named from the front). */
	public Viewport faceCell(String relative) {
		int[] at = UNFOLDED.get(relative);
		return new Viewport(leftPos + PICKER_X + at[0] * (CELL + 1), topPos + PICKER_Y + at[1] * (CELL + 1), CELL, CELL);
	}

	/** A slot's 16x16 item area on screen. */
	public Viewport slotArea(int index) {
		Slot slot = menu.slots.get(index);
		return new Viewport(leftPos + slot.x, topPos + slot.y, 16, 16);
	}

	/** The world's face a relative name means, for this station (its front from its block). */
	private Face face(String relative) {
		var state = minecraft.level == null ? null : minecraft.level.getBlockState(menu.pos());
		if (state == null || !state.hasProperty(com.payonel.modularcomputers.block.DiskStationBlock.FACING)) {
			return Face.NORTH;
		}
		Face front = Face.of(state.getValue(com.payonel.modularcomputers.block.DiskStationBlock.FACING).getName());
		return Face.named(relative, front);
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
		for (int id = 0; id < DiskStationMenu.ACTIONS.size(); id++) {
			Viewport area = switchArea(id);
			extractSwitch(graphics, area, Component.translatable("gui.modularcomputers.disk_station." + DiskStationMenu.ACTIONS.get(id)),
				area.contains(mouseX, mouseY));
		}
		// The arrow from the source bay up to the target's: what Clone copies.
		int ax = x + DiskStationMenu.SOURCE_X + 7;
		for (int ay = y + DiskStationMenu.TARGET_Y + 20; ay < y + DiskStationMenu.SOURCE_Y - 3; ay++) {
			graphics.fill(ax, ay, ax + 2, ay + 1, ARROW);
		}
		int tip = y + DiskStationMenu.TARGET_Y + 19;
		graphics.fill(ax - 2, tip + 2, ax + 4, tip + 3, ARROW);
		graphics.fill(ax - 1, tip + 1, ax + 3, tip + 2, ARROW);
		graphics.fill(ax, tip, ax + 2, tip + 1, ARROW);
		for (Slot slot : menu.slots) {
			if (slot.index >= DiskStationBlockEntity.SLOTS) {
				extractSlot(graphics, x + slot.x - 1, y + slot.y - 1);
				continue;
			}
			extractSlot(graphics, x + slot.x - 1, y + slot.y - 1);
			ItemStack ghost = GHOSTS.get(slot.index);
			if (ghost != null && !slot.hasItem()) {
				graphics.item(ghost, x + slot.x, y + slot.y);
				graphics.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, GHOST);
			}
		}
		// The source bay: read only (a lock), and optional (faint while it's empty).
		int sx = x + DiskStationMenu.SOURCE_X;
		int sy = y + DiskStationMenu.SOURCE_Y;
		graphics.text(font, Component.literal("SOURCE"), sx - 2, sy + 19, ENGRAVED, false);
		graphics.fill(sx - 7, sy + 5, sx - 3, sy + 9, LOCK);
		graphics.fill(sx - 6, sy + 2, sx - 4, sy + 5, LOCK);
		graphics.fill(sx - 6, sy + 2, sx - 5, sy + 3, IRON);
		if (!menu.slots.get(DiskStationBlockEntity.SOURCE).hasItem()) {
			graphics.fill(sx - 1, sy - 1, sx + 17, sy + 17, FAINT);
		}
		graphics.text(font, Component.literal("DISK"), x + DiskStationMenu.TARGET_X + 2, y + DiskStationMenu.TARGET_Y - 10, ENGRAVED, false);
		ChargeBars.extract(graphics, x + DiskStationMenu.BATTERY_X + 8 - ChargeBars.WIDTH / 2, y + DiskStationMenu.BATTERY_Y + 19, menu.charge());
		extractPicker(graphics, mouseX, mouseY);
		SlotHints.extract(graphics, menu, hoveredSlot, DiskStationBlockEntity.SLOTS, this::slotArea);
	}

	private void extractSlot(GuiGraphicsExtractor graphics, int sx, int sy) {
		graphics.fill(sx, sy, sx + 18, sy + 18, LIGHT);
		graphics.fill(sx, sy, sx + 17, sy + 17, SLOT_DARK);
		graphics.fill(sx + 1, sy + 1, sx + 17, sy + 17, SLOT);
	}

	/** The iron plate, riveted, with its emerald inlay along the bottom. */
	private void extractPlate(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1) {
		graphics.fill(x0, y0, x1, y1, IRON_DARK);
		graphics.fill(x0, y0, x1 - 1, y1 - 1, IRON_LIGHT);
		graphics.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, IRON);
		graphics.fill(x0 + 1, y1 - 8, x1 - 1, y1 - 7, EMERALD);
		graphics.fill(x0 + 1, y1 - 7, x1 - 1, y1 - 6, EMERALD_DARK);
		for (int rx : new int[] {x0 + 3, x1 - 5}) {
			for (int ry : new int[] {y0 + 3, y1 - 5}) {
				graphics.fill(rx, ry, rx + 2, ry + 2, RIVET);
				graphics.fill(rx + 1, ry + 1, rx + 2, ry + 2, IRON_DARK);
			}
		}
	}

	/** A switch: raised (light above and left, dark below and right), its label silkscreened on it. */
	private void extractSwitch(GuiGraphicsExtractor graphics, Viewport area, Component label, boolean hover) {
		int x0 = area.x();
		int y0 = area.y();
		int x1 = x0 + area.width();
		int y1 = y0 + area.height();
		graphics.fill(x0, y0, x1, y1, OUTLINE);
		graphics.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, hover ? SHADOW : LIGHT);
		graphics.fill(x0 + 2, y0 + 2, x1 - 1, y1 - 1, hover ? LIGHT : SHADOW);
		graphics.fill(x0 + 2, y0 + 2, x1 - 2, y1 - 2, hover ? SWITCH_HOVER : SWITCH);
		graphics.centeredText(font, label, x0 + area.width() / 2, y0 + (area.height() - font.lineHeight) / 2 + 1, SILK);
	}

	/** The station as an unfolded box: the eject face lit, the front marked. */
	private void extractPicker(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		Face eject = menu.ejectFace();
		for (String relative : UNFOLDED.keySet()) {
			Viewport cell = faceCell(relative);
			boolean lit = face(relative) == eject;
			boolean hover = cell.contains(mouseX, mouseY);
			graphics.fill(cell.x() - 1, cell.y() - 1, cell.x() + cell.width() + 1, cell.y() + cell.height() + 1, OUTLINE);
			graphics.fill(cell.x(), cell.y(), cell.x() + cell.width(), cell.y() + cell.height(), lit ? EMERALD : hover ? SWITCH_HOVER : IRON_DARK);
			if (relative.equals("front")) {
				graphics.fill(cell.x() + 2, cell.y() + 3, cell.x() + 5, cell.y() + 4, lit ? EMERALD_DARK : IRON_LIGHT); // its bays
			}
		}
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		graphics.text(font, title, titleLabelX, titleLabelY, TITLE, false);
		graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, LABEL, false);
		DiskStationBlockEntity.Outcome outcome = menu.outcome();
		if (outcome != DiskStationBlockEntity.Outcome.NONE) {
			boolean done = outcome == DiskStationBlockEntity.Outcome.DONE;
			graphics.text(font, Component.translatable("gui.modularcomputers.disk_station.outcome." + outcome.name()), SWITCH_X,
				SWITCH_TOP + 4 * SWITCH_STEP, done ? STATUS : STATUS_BAD, false);
		}
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		for (int id = 0; id < DiskStationMenu.ACTIONS.size(); id++) {
			if (switchArea(id).contains(mouseX, mouseY)) {
				graphics.setTooltipForNextFrame(font,
					Component.translatable("gui.modularcomputers.disk_station." + DiskStationMenu.ACTIONS.get(id) + ".tip"), mouseX, mouseY);
				return;
			}
		}
		for (String relative : UNFOLDED.keySet()) {
			if (faceCell(relative).contains(mouseX, mouseY)) {
				graphics.setTooltipForNextFrame(font, Component.translatable("gui.modularcomputers.disk_station.eject_face",
					Component.translatable("gui.modularcomputers.disk_station.face." + relative)), mouseX, mouseY);
				return;
			}
		}
		Slot battery = menu.slots.get(DiskStationBlockEntity.BATTERY);
		if (hoveredSlot == battery && battery.hasItem()) {
			return; // (the battery's own tooltip says its charge)
		}
		// An empty slot says what goes in it.
		if (hoveredSlot != null && !hoveredSlot.hasItem() && hoveredSlot.index < DiskStationBlockEntity.SLOTS && menu.getCarried().isEmpty()) {
			List<Component> lines = new ArrayList<>();
			lines.add(Component.translatable("hint.modularcomputers.disk_station.slot." + hoveredSlot.index));
			graphics.setTooltipForNextFrame(font, lines.getFirst(), mouseX, mouseY);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && minecraft.gameMode != null) {
			for (int id = 0; id < DiskStationMenu.ACTIONS.size(); id++) {
				if (switchArea(id).contains(event.x(), event.y())) {
					press(id);
					return true;
				}
			}
			for (String relative : UNFOLDED.keySet()) {
				if (faceCell(relative).contains(event.x(), event.y())) {
					press(DiskStationMenu.EJECT_FACE + face(relative).ordinal());
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	/** A button pressed: the menu's button, as vanilla's menus send theirs. */
	private void press(int id) {
		minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
		minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
			net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0f));
	}
}
