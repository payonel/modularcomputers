package com.payonel.modularcomputers.menu;

import com.payonel.modularcomputers.block.ModBlocks;
import com.payonel.modularcomputers.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** The case view: the computer's part slots above the player's inventory. */
public final class CaseMenu extends AbstractContainerMenu {
	public static final int CPU_SLOT = 0;
	public static final int EEPROM_SLOT = 1;
	public static final int PART_SLOTS = 2;
	/** Slot positions inside the case view, in GUI units (top-left of the 16x16 item). */
	public static final int CPU_X = 53;
	public static final int EEPROM_X = 107;
	public static final int PARTS_Y = 26;
	public static final int INVENTORY_Y = 84;

	private final Container parts;
	private final BlockPos pos;
	private final ContainerLevelAccess access;

	/** Client side: the slots are filled in by the server's sync. */
	public CaseMenu(int id, Inventory inventory, BlockPos pos) {
		this(id, inventory, new SimpleContainer(PART_SLOTS), pos, ContainerLevelAccess.NULL);
	}

	public CaseMenu(int id, Inventory inventory, Container parts, BlockPos pos, ContainerLevelAccess access) {
		super(ModMenus.CASE, id);
		checkContainerSize(parts, PART_SLOTS);
		this.parts = parts;
		this.pos = pos;
		this.access = access;
		addSlot(new PartSlot(parts, CPU_SLOT, CPU_X, PARTS_Y, ModItems.CPU));
		addSlot(new PartSlot(parts, EEPROM_SLOT, EEPROM_X, PARTS_Y, ModItems.EEPROM));
		addStandardInventorySlots(inventory, 8, INVENTORY_Y);
	}

	public BlockPos pos() {
		return pos;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		boolean moved = index < PART_SLOTS
			? moveItemStackTo(stack, PART_SLOTS, slots.size(), true)
			: moveItemStackTo(stack, 0, PART_SLOTS, false); // part slots only take their own item
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
		return stillValid(access, player, ModBlocks.ALL_IN_ONE);
	}

	/** A slot for one kind of part, holding one item. */
	private static final class PartSlot extends Slot {
		private final Item accepts;

		PartSlot(Container container, int index, int x, int y, Item accepts) {
			super(container, index, x, y);
			this.accepts = accepts;
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return stack.is(accepts);
		}

		@Override
		public int getMaxStackSize() {
			return 1;
		}
	}
}
