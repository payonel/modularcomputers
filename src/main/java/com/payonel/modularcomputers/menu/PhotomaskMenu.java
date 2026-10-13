package com.payonel.modularcomputers.menu;

import com.payonel.modularcomputers.block.PhotomaskBlock;
import com.payonel.modularcomputers.block.PhotomaskBlockEntity;
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

/** A photomask's slots: silicon in, chips out, a battery, above the player's inventory. */
public final class PhotomaskMenu extends AbstractContainerMenu {
	public static final int INVENTORY_Y = 84;
	private final ContainerData data;
	private final BlockPos pos;
	private final ContainerLevelAccess access;

	/** Client side: the slots and progress are filled in by the server's sync. */
	public PhotomaskMenu(int id, Inventory inventory, BlockPos pos) {
		this(id, inventory, new SimpleContainer(PhotomaskBlockEntity.SLOTS), new SimpleContainerData(1), pos, ContainerLevelAccess.NULL);
	}

	public PhotomaskMenu(int id, Inventory inventory, Container items, ContainerData data, BlockPos pos, ContainerLevelAccess access) {
		super(ModMenus.PHOTOMASK, id);
		checkContainerSize(items, PhotomaskBlockEntity.SLOTS);
		this.data = data;
		this.pos = pos;
		this.access = access;
		addSlot(new Part(items, PhotomaskBlockEntity.INPUT, 44, 26));
		addSlot(new Part(items, PhotomaskBlockEntity.OUTPUT, 116, 35));
		addSlot(new Part(items, PhotomaskBlockEntity.BATTERY, 44, 50));
		addDataSlots(data);
		addStandardInventorySlots(inventory, 8, INVENTORY_Y);
	}

	public BlockPos pos() {
		return pos;
	}

	/** Turns done on the wafer in it, of {@link PhotomaskBlockEntity#WORK}. */
	public int progress() {
		return data.get(0);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		boolean moved = index < PhotomaskBlockEntity.SLOTS
			? moveItemStackTo(stack, PhotomaskBlockEntity.SLOTS, slots.size(), true)
			: moveItemStackTo(stack, PhotomaskBlockEntity.INPUT, PhotomaskBlockEntity.INPUT + 1, false)
				|| moveItemStackTo(stack, PhotomaskBlockEntity.BATTERY, PhotomaskBlockEntity.BATTERY + 1, false);
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
		return access.evaluate((level, at) -> level.getBlockState(at).getBlock() instanceof PhotomaskBlock
			&& player.isWithinBlockInteractionRange(at, 4.0), true);
	}

	private static final class Part extends Slot {
		Part(Container container, int index, int x, int y) {
			super(container, index, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return PhotomaskBlockEntity.accepts(getContainerSlot(), stack);
		}

		@Override
		public int getMaxStackSize() {
			return getContainerSlot() == PhotomaskBlockEntity.BATTERY ? 1 : super.getMaxStackSize();
		}
	}
}
