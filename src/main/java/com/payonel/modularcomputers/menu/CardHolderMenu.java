package com.payonel.modularcomputers.menu;

import com.payonel.modularcomputers.block.CardHolderBlockEntity;
import com.payonel.modularcomputers.block.ExpansionHubBlockEntity;
import com.payonel.modularcomputers.block.ModBlocks;
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
 * The card slots of a block on the network, above the player's inventory: an expansion hub's
 * four expansion slots, and every holder's radio slot (a Bluetooth card, which pairs the block
 * with one computer). Screens, robotic pistons and paint guns have just the radio slot.
 */
public final class CardHolderMenu extends AbstractContainerMenu {
	public static final int SLOT_Y = 35;
	public static final int INVENTORY_Y = 84;
	private static final int SLOT_SPACING = 22;
	/** Where the radio slot sits when there are expansion slots too: on its own, at the right. */
	private static final int RADIO_X = 141;

	private final int cardSlots;
	private final int radioSlot;
	private final BlockPos pos;
	private final ContainerLevelAccess access;
	private final Block block;

	private CardHolderMenu(MenuType<?> type, int id, Inventory inventory, Container cards, int radioSlot, BlockPos pos,
		ContainerLevelAccess access, Block block) {
		super(type, id);
		this.cardSlots = cards.getContainerSize();
		this.radioSlot = radioSlot;
		this.pos = pos;
		this.access = access;
		this.block = block;
		int expansion = cardSlots - 1;
		int left = (expansion > 0 ? 76 : 88) - (expansion * SLOT_SPACING - 4) / 2;
		for (int slot = 0, column = 0; slot < cardSlots; slot++) {
			int x = slot == radioSlot ? (expansion > 0 ? RADIO_X : 80) : left + column++ * SLOT_SPACING + 1;
			int index = slot;
			addSlot(new Slot(cards, slot, x, SLOT_Y) {
				@Override
				public boolean mayPlace(ItemStack stack) {
					return CardHolderBlockEntity.accepts(index, radioSlot, stack);
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
		return new CardHolderMenu(ModMenus.EXPANSION_HUB, id, inventory, cards, ExpansionHubBlockEntity.RADIO_SLOT, pos, access, ModBlocks.EXPANSION_HUB);
	}

	/** Client side: the slots are filled in by the server's sync. */
	public static CardHolderMenu hub(int id, Inventory inventory, BlockPos pos) {
		return hub(id, inventory, new SimpleContainer(ExpansionHubBlockEntity.SLOTS + 1), pos, ContainerLevelAccess.NULL);
	}

	public static CardHolderMenu piston(int id, Inventory inventory, Container cards, BlockPos pos, ContainerLevelAccess access) {
		return new CardHolderMenu(ModMenus.ROBOTIC_PISTON, id, inventory, cards, 0, pos, access, ModBlocks.ROBOTIC_PISTON);
	}

	public static CardHolderMenu piston(int id, Inventory inventory, BlockPos pos) {
		return piston(id, inventory, new SimpleContainer(1), pos, ContainerLevelAccess.NULL);
	}

	public static CardHolderMenu screen(int id, Inventory inventory, Container cards, BlockPos pos, ContainerLevelAccess access) {
		// Whichever tier of screen is there.
		Block screen = access.evaluate((level, at) -> level.getBlockState(at).getBlock()).orElse(ModBlocks.TIER1_SCREEN);
		return new CardHolderMenu(ModMenus.SCREEN, id, inventory, cards, 0, pos, access, screen);
	}

	public static CardHolderMenu screen(int id, Inventory inventory, BlockPos pos) {
		return screen(id, inventory, new SimpleContainer(1), pos, ContainerLevelAccess.NULL);
	}

	public static CardHolderMenu paintGun(int id, Inventory inventory, Container cards, BlockPos pos, ContainerLevelAccess access) {
		return new CardHolderMenu(ModMenus.PAINT_GUN, id, inventory, cards, 0, pos, access, ModBlocks.PAINT_GUN);
	}

	public static CardHolderMenu paintGun(int id, Inventory inventory, BlockPos pos) {
		return paintGun(id, inventory, new SimpleContainer(1), pos, ContainerLevelAccess.NULL);
	}

	public int radioSlot() {
		return radioSlot;
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
