package com.payonel.modularcomputers.menu;

import com.payonel.modularcomputers.device.CraftingCard;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * A crafting card's slots, as a container: read from and written to its item (its contents
 * component, the slots' one home), wherever the card is (a computer's slot), so the card takes them
 * where it goes. The game's slots change stacks in place and then say so: the container works on the
 * card's stacks as last read, read again whenever the card's contents aren't what it last wrote, and
 * writes them back at each change. Its first {@link CraftingCard#GRID} slots are the crafting grid
 * ({@link #grid}).
 */
public final class CraftingStorage implements Container {
	private final Supplier<ItemStack> card;
	private final Runnable changed;
	private final List<Runnable> listeners = new ArrayList<>();
	private NonNullList<ItemStack> items = NonNullList.withSize(CraftingCard.SLOTS, ItemStack.EMPTY);
	/** What it last read or wrote on the card (the stack, and its contents), to know when it changed elsewhere. */
	private ItemStack lastCard = ItemStack.EMPTY;
	private ItemContainerContents lastContents;

	/** The slots on {@code card} (an empty stack: no card, no slots), {@code changed} told when they change. */
	public CraftingStorage(Supplier<ItemStack> card, Runnable changed) {
		this.card = card;
		this.changed = changed;
	}

	/** Told whenever its slots change (a menu working out what its grid makes). */
	public void listen(Runnable listener) {
		listeners.add(listener);
	}

	/** Whether there's a card to hold the slots. */
	public boolean present() {
		ItemStack stack = card.get();
		return !stack.isEmpty() && com.payonel.modularcomputers.item.ModItems.cardKind(stack).orElse(null) == com.payonel.modularcomputers.device.CardKind.CRAFTING;
	}

	private NonNullList<ItemStack> items() {
		ItemStack stack = card.get();
		ItemContainerContents now = present() ? stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY) : ItemContainerContents.EMPTY;
		if (stack != lastCard || !now.equals(lastContents)) {
			items = NonNullList.withSize(CraftingCard.SLOTS, ItemStack.EMPTY);
			now.copyInto(items);
			lastCard = stack;
			lastContents = now;
		}
		return items;
	}

	@Override
	public int getContainerSize() {
		return CraftingCard.SLOTS;
	}

	@Override
	public boolean isEmpty() {
		return items().stream().allMatch(ItemStack::isEmpty);
	}

	@Override
	public ItemStack getItem(int slot) {
		return items().get(slot);
	}

	@Override
	public ItemStack removeItem(int slot, int count) {
		ItemStack taken = items().get(slot).split(count);
		if (!taken.isEmpty()) {
			setChanged();
		}
		return taken;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		ItemStack taken = items().set(slot, ItemStack.EMPTY);
		setChanged();
		return taken;
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		if (!present()) {
			return; // no card: nowhere to put it
		}
		items().set(slot, stack);
		setChanged();
	}

	/** Writes its slots back onto the card, and tells who listens. */
	@Override
	public void setChanged() {
		if (!present()) {
			return;
		}
		ItemStack stack = card.get();
		NonNullList<ItemStack> now = items();
		lastContents = ItemContainerContents.fromItems(now);
		if (lastContents.equals(ItemContainerContents.EMPTY)) {
			stack.remove(DataComponents.CONTAINER); // (an empty card stacks with a new one)
		} else {
			stack.set(DataComponents.CONTAINER, lastContents);
		}
		lastContents = stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
		changed.run();
		for (Runnable listener : listeners) {
			listener.run();
		}
	}

	@Override
	public boolean stillValid(Player player) {
		return true;
	}

	@Override
	public void clearContent() {
		for (int slot = 0; slot < CraftingCard.SLOTS; slot++) {
			items().set(slot, ItemStack.EMPTY);
		}
		setChanged();
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
