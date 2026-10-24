package com.payonel.modularcomputers.menu;

import com.payonel.modularcomputers.block.DiskStationBlock;
import com.payonel.modularcomputers.block.DiskStationBlockEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A Disk Station's slots (its disk, the source disk, a battery, its radio) above the player's
 * inventory, and its buttons: each is a menu button (vanilla's, as the enchanting table's), so the
 * server does the action as the station's device does it.
 */
public final class DiskStationMenu extends AbstractContainerMenu {
	public static final int INVENTORY_Y = 114;
	/** The buttons, by their menu button ids: the actions, in this order. */
	public static final List<String> ACTIONS = List.of("format", "wipe", "install", "update", "clone");
	/** Where each slot is. */
	public static final int TARGET_X = 98;
	public static final int TARGET_Y = 22;
	public static final int SOURCE_X = 98;
	public static final int SOURCE_Y = 64;
	public static final int BATTERY_X = 154;
	public static final int BATTERY_Y = 18;
	public static final int RADIO_X = 154;
	public static final int RADIO_Y = 46;

	private final ContainerData data;
	private final BlockPos pos;
	private final ContainerLevelAccess access;

	/** Client side: the slots and data are filled in by the server's sync. */
	public DiskStationMenu(int id, Inventory inventory, BlockPos pos) {
		this(id, inventory, new SimpleContainer(DiskStationBlockEntity.SLOTS), new SimpleContainerData(DiskStationBlockEntity.DATA), pos,
			ContainerLevelAccess.NULL);
	}

	public DiskStationMenu(int id, Inventory inventory, Container items, ContainerData data, BlockPos pos, ContainerLevelAccess access) {
		super(ModMenus.DISK_STATION, id);
		checkContainerSize(items, DiskStationBlockEntity.SLOTS);
		this.data = data;
		this.pos = pos;
		this.access = access;
		addSlot(new Bay(items, DiskStationBlockEntity.TARGET, TARGET_X, TARGET_Y));
		addSlot(new Bay(items, DiskStationBlockEntity.SOURCE, SOURCE_X, SOURCE_Y));
		addSlot(new Bay(items, DiskStationBlockEntity.BATTERY, BATTERY_X, BATTERY_Y));
		addSlot(new Bay(items, DiskStationBlockEntity.RADIO, RADIO_X, RADIO_Y));
		addDataSlots(data);
		addStandardInventorySlots(inventory, 8, INVENTORY_Y);
	}

	public BlockPos pos() {
		return pos;
	}

	/** How its last action went. */
	public DiskStationBlockEntity.Outcome outcome() {
		return DiskStationBlockEntity.Outcome.values()[Math.clamp(data.get(0), 0, DiskStationBlockEntity.Outcome.values().length - 1)];
	}

	/** Its battery's charge, in percent. */
	public int charge() {
		return data.get(1);
	}

	/** Server: a button was pressed. */
	@Override
	public boolean clickMenuButton(Player player, int id) {
		return access.evaluate((level, at) -> {
			if (!(level.getBlockEntity(at) instanceof DiskStationBlockEntity station)) {
				return false;
			}
			if (id < 0 || id >= ACTIONS.size()) {
				return false;
			}
			station.act(ACTIONS.get(id));
			return true;
		}, false);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		boolean moved = index < DiskStationBlockEntity.SLOTS
			? moveItemStackTo(stack, DiskStationBlockEntity.SLOTS, slots.size(), true)
			: moveItemStackTo(stack, 0, DiskStationBlockEntity.SLOTS, false);
		if (!moved) {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return original;
	}

	@Override
	public boolean stillValid(Player player) {
		return access.evaluate((level, at) -> level.getBlockState(at).getBlock() instanceof DiskStationBlock
			&& player.isWithinBlockInteractionRange(at, 4.0), true);
	}

	/** One of its slots: one item, of the kind it takes. */
	private static final class Bay extends Slot {
		Bay(Container container, int index, int x, int y) {
			super(container, index, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return DiskStationBlockEntity.takes(getContainerSlot(), stack);
		}

		@Override
		public int getMaxStackSize() {
			return 1;
		}
	}
}
