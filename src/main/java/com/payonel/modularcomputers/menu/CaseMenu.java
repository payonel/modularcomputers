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
import net.minecraft.world.item.ItemStack;

/**
 * The case view: the motherboard's part slots above the player's inventory. Slot positions
 * match the sockets in textures/gui/case_board.png (drawn at panel offset {@link #BOARD_X},
 * {@link #BOARD_Y}); CaseArtTest checks that they line up.
 */
public final class CaseMenu extends AbstractContainerMenu {
	public static final int CPU_SLOT = 0;
	public static final int EEPROM_SLOT = 1;
	public static final int UPS_SLOT = 2;
	public static final int RAM_SLOTS = 3;
	public static final int SATA_SLOTS = 7;
	public static final int EXP_SLOTS = 11;
	public static final int LANES = 4;
	public static final int PART_SLOTS = 15;

	/** Where the board texture is drawn inside the panel. */
	public static final int BOARD_X = 4;
	public static final int BOARD_Y = 4;
	public static final int INVENTORY_Y = 150;

	/** Slot positions in the panel, in GUI units (the item's top-left), by slot index. */
	public static final int[][] SLOT_POSITIONS = {
		{18, 26}, {20, 68}, {20, 103},
		{54, 40}, {74, 40}, {94, 40}, {114, 40},
		{138, 19}, {138, 39}, {138, 59}, {138, 79},
		{56, 114}, {83, 114}, {110, 114}, {137, 114},
	};

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
		for (int slot = 0; slot < PART_SLOTS; slot++) {
			addSlot(new PartSlot(parts, slot, SLOT_POSITIONS[slot][0], SLOT_POSITIONS[slot][1]));
		}
		addStandardInventorySlots(inventory, 8, INVENTORY_Y);
	}

	/** Whether a slot takes this item: each takes its own part, and expansion slots take any card. */
	public static boolean accepts(int slot, ItemStack stack) {
		if (slot == CPU_SLOT) {
			return stack.is(ModItems.CPU);
		} else if (slot == EEPROM_SLOT) {
			return stack.is(ModItems.EEPROM);
		} else if (slot == UPS_SLOT) {
			return stack.is(ModItems.UPS_BATTERY);
		} else if (slot < SATA_SLOTS) {
			return stack.is(ModItems.RAM);
		} else if (slot < EXP_SLOTS) {
			return stack.is(ModItems.HARD_DISK);
		}
		return ModItems.cardKind(stack).isPresent();
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
		PartSlot(Container container, int index, int x, int y) {
			super(container, index, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return accepts(getContainerSlot(), stack);
		}

		@Override
		public int getMaxStackSize() {
			return 1;
		}
	}
}
