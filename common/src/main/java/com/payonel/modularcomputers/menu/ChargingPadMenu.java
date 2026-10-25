package com.payonel.modularcomputers.menu;

import com.payonel.modularcomputers.block.ChargingPadBlock;
import com.payonel.modularcomputers.block.ChargingPadBlockEntity;
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

/** A charging pad's slots (its battery, its radio) above the player's inventory, and its buffer's and battery's charge. */
public final class ChargingPadMenu extends AbstractContainerMenu {
	public static final int INVENTORY_Y = 84;
	public static final int BATTERY_X = 44;
	public static final int BATTERY_Y = 35;
	public static final int RADIO_X = 134;
	public static final int RADIO_Y = 35;

	private final ContainerData data;
	private final BlockPos pos;
	private final ContainerLevelAccess access;

	/** Client side: the slots and data are filled in by the server's sync. */
	public ChargingPadMenu(int id, Inventory inventory, BlockPos pos) {
		this(id, inventory, new SimpleContainer(ChargingPadBlockEntity.SLOTS), new SimpleContainerData(ChargingPadBlockEntity.DATA), pos,
			ContainerLevelAccess.NULL);
	}

	public ChargingPadMenu(int id, Inventory inventory, Container items, ContainerData data, BlockPos pos, ContainerLevelAccess access) {
		super(ModMenus.CHARGING_PAD, id);
		checkContainerSize(items, ChargingPadBlockEntity.SLOTS);
		this.data = data;
		this.pos = pos;
		this.access = access;
		addSlot(new Bay(items, ChargingPadBlockEntity.BATTERY, BATTERY_X, BATTERY_Y));
		addSlot(new Bay(items, ChargingPadBlockEntity.RADIO, RADIO_X, RADIO_Y));
		addDataSlots(data);
		addStandardInventorySlots(inventory, 8, INVENTORY_Y);
	}

	public BlockPos pos() {
		return pos;
	}

	/** Its buffer's charge, in percent. */
	public int buffer() {
		return data.get(0);
	}

	/** Its battery's charge, in percent. */
	public int battery() {
		return data.get(1);
	}

	/** It's charging a robot now. */
	public boolean charging() {
		return data.get(2) != 0;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		boolean moved = index < ChargingPadBlockEntity.SLOTS
			? moveItemStackTo(stack, ChargingPadBlockEntity.SLOTS, slots.size(), true)
			: moveItemStackTo(stack, 0, ChargingPadBlockEntity.SLOTS, false);
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
		return access.evaluate((level, at) -> level.getBlockState(at).getBlock() instanceof ChargingPadBlock
			&& player.isWithinBlockInteractionRange(at, 4.0), true);
	}

	/** One of its slots: one item, of the kind it takes. */
	private static final class Bay extends Slot {
		Bay(Container container, int index, int x, int y) {
			super(container, index, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return ChargingPadBlockEntity.takes(getContainerSlot(), stack);
		}

		@Override
		public int getMaxStackSize() {
			return 1;
		}
	}
}
