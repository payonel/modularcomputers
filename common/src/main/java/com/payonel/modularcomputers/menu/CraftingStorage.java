package com.payonel.modularcomputers.menu;

import com.payonel.modularcomputers.device.CraftingCard;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;

/**
 * A crafting card's slots, as a container: kept on its item (its contents component, the slots'
 * one home: {@link ItemContents}), wherever the card is (a computer's slot), so the card takes them
 * where it goes. Its first {@link CraftingCard#GRID} slots are the crafting grid ({@link #grid}).
 */
public final class CraftingStorage extends ItemContents {
	private final Supplier<ItemStack> card;
	/** Told whenever its slots change, after {@code changed} (its change callback runs them: made before it, so it can). */
	private final List<Runnable> listeners;

	/** The slots on {@code card} (an empty stack: no card, no slots), {@code changed} told when they change. */
	public CraftingStorage(Supplier<ItemStack> card, Runnable changed) {
		this(card, changed, new ArrayList<>());
	}

	private CraftingStorage(Supplier<ItemStack> card, Runnable changed, List<Runnable> listeners) {
		super(card, CraftingCard.SLOTS, CONTAINER, () -> {
			changed.run();
			listeners.forEach(Runnable::run);
		});
		this.card = card;
		this.listeners = listeners;
	}

	/** Told whenever its slots change (a menu working out what its grid makes). */
	public void listen(Runnable listener) {
		listeners.add(listener);
	}

	/** Whether there's a card to hold the slots. */
	@Override
	public boolean present() {
		ItemStack stack = card.get();
		return !stack.isEmpty() && com.payonel.modularcomputers.item.ModItems.cardKind(stack).orElse(null) == com.payonel.modularcomputers.device.CardKind.CRAFTING;
	}

	/** Its crafting grid, as the game's crafting containers are: 3 by 3, its first slots. */
	public CraftingContainer grid() {
		return new Grid();
	}

	private final class Grid implements CraftingContainer {
		private static final int SIDE = 3;

		@Override
		public int getWidth() {
			return SIDE;
		}

		@Override
		public int getHeight() {
			return SIDE;
		}

		@Override
		public List<ItemStack> getItems() {
			return List.copyOf(items().subList(0, CraftingCard.GRID));
		}

		@Override
		public int getContainerSize() {
			return CraftingCard.GRID;
		}

		@Override
		public boolean isEmpty() {
			return getItems().stream().allMatch(ItemStack::isEmpty);
		}

		@Override
		public ItemStack getItem(int slot) {
			return CraftingStorage.this.getItem(slot);
		}

		@Override
		public ItemStack removeItem(int slot, int count) {
			return CraftingStorage.this.removeItem(slot, count);
		}

		@Override
		public ItemStack removeItemNoUpdate(int slot) {
			return CraftingStorage.this.removeItemNoUpdate(slot);
		}

		@Override
		public void setItem(int slot, ItemStack stack) {
			CraftingStorage.this.setItem(slot, stack);
		}

		@Override
		public void setChanged() {
			CraftingStorage.this.setChanged();
		}

		@Override
		public boolean stillValid(Player player) {
			return true;
		}

		@Override
		public void clearContent() {
			for (int slot = 0; slot < CraftingCard.GRID; slot++) {
				CraftingStorage.this.setItem(slot, ItemStack.EMPTY);
			}
		}

		@Override
		public void fillStackedContents(StackedItemContents contents) {
			for (ItemStack stack : getItems()) {
				contents.accountSimpleStack(stack);
			}
		}
	}
}
