package com.payonel.modularcomputers.menu;

import com.payonel.modularcomputers.block.ExpansionHubBlockEntity;
import com.payonel.modularcomputers.block.ModBlocks;
import com.payonel.modularcomputers.item.ModItems;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * The card slots of an expansion hub (four, any card) or a robotic piston (one, a Bluetooth
 * card), above the player's inventory.
 */
public final class CardHolderMenu extends AbstractContainerMenu {
	public static final int SLOT_Y = 35;
	public static final int INVENTORY_Y = 84;
	private static final int SLOT_SPACING = 22;

	private final int cardSlots;
	private final BlockPos pos;
	private final ContainerLevelAccess access;
	private final Block block;

	private CardHolderMenu(MenuType<?> type, int id, Inventory inventory, Container cards, BlockPos pos, ContainerLevelAccess access,
		Block block, Predicate<ItemStack> accepts) {
		super(type, id);
		this.cardSlots = cards.getContainerSize();
		this.pos = pos;
		this.access = access;
		this.block = block;
		int left = 88 - (cardSlots * SLOT_SPACING - 4) / 2;
		for (int slot = 0; slot < cardSlots; slot++) {
			addSlot(new Slot(cards, slot, left + slot * SLOT_SPACING + 1, SLOT_Y) {
				@Override
				public boolean mayPlace(ItemStack stack) {
					return accepts.test(stack);
				}

				@Override
				public int getMaxStackSize() {
					return 1;
				}
			});
		}
		addStandardInventorySlots(inventory, 8, INVENTORY_Y);
	}

	public static CardHolderMenu hub(int id, Inventory inventory, Container cards, BlockPos pos, ContainerLevelAccess access) {
		return new CardHolderMenu(ModMenus.EXPANSION_HUB, id, inventory, cards, pos, access, ModBlocks.EXPANSION_HUB,
			stack -> ModItems.cardKind(stack).isPresent());
	}

	/** Client side: the slots are filled in by the server's sync. */
	public static CardHolderMenu hub(int id, Inventory inventory, BlockPos pos) {
		return hub(id, inventory, new SimpleContainer(ExpansionHubBlockEntity.SLOTS), pos, ContainerLevelAccess.NULL);
	}

	public static CardHolderMenu piston(int id, Inventory inventory, Container cards, BlockPos pos, ContainerLevelAccess access) {
		return new CardHolderMenu(ModMenus.ROBOTIC_PISTON, id, inventory, cards, pos, access, ModBlocks.ROBOTIC_PISTON,
			stack -> stack.is(ModItems.BLUETOOTH_CARD));
	}

	/** Client side: the slot is filled in by the server's sync. */
	public static CardHolderMenu piston(int id, Inventory inventory, BlockPos pos) {
		return piston(id, inventory, new SimpleContainer(1), pos, ContainerLevelAccess.NULL);
	}

	public BlockPos pos() {
		return pos;
	}

	public int cardSlots() {
		return cardSlots;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		boolean moved = index < cardSlots
			? moveItemStackTo(stack, cardSlots, slots.size(), true)
			: moveItemStackTo(stack, 0, cardSlots, false);
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
		return stillValid(access, player, block);
	}
}
