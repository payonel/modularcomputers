package com.payonel.modularcomputers.menu;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * Slots kept on an item, as a container: read from and written to the item's components (the slots'
 * one home), wherever the item is (a crafting card in a computer's slot, a golem in a player's hand).
 * The game's slots change stacks in place and then say so: the container works on the item's stacks
 * as last read, read again whenever the item's components aren't what it last wrote, and writes them
 * back at each change. How the slots are kept on the item is its {@link Format}.
 */
public class ItemContents implements Container {
	/** How slots are kept on an item. */
	public interface Format {
		/** What the slots are kept in now, to compare with what was last read or written (equal: unchanged). */
		Object snapshot(ItemStack holder);

		/** The slots, from the item, into {@code into}. */
		void read(ItemStack holder, NonNullList<ItemStack> into);

		/** The slots, onto the item. */
		void write(ItemStack holder, NonNullList<ItemStack> from);
	}

	/** The game's contents component, as a container block's item keeps its slots. */
	public static final Format CONTAINER = in(DataComponents.CONTAINER);

	/** Slots kept in a contents component; none when all are empty (so an empty item stacks with a new one). */
	public static Format in(net.minecraft.core.component.DataComponentType<ItemContainerContents> component) {
		return new Format() {
			@Override
			public Object snapshot(ItemStack holder) {
				return holder.getOrDefault(component, ItemContainerContents.EMPTY);
			}

			@Override
			public void read(ItemStack holder, NonNullList<ItemStack> into) {
				holder.getOrDefault(component, ItemContainerContents.EMPTY).copyInto(into);
			}

			@Override
			public void write(ItemStack holder, NonNullList<ItemStack> from) {
				ItemContainerContents contents = ItemContainerContents.fromItems(from);
				if (contents.equals(ItemContainerContents.EMPTY)) {
					holder.remove(component);
				} else {
					holder.set(component, contents);
				}
			}
		};
	}

	private final Supplier<ItemStack> holder;
	private final int size;
	private final Format format;
	private final Runnable changed;
	private NonNullList<ItemStack> items;
	/** What it last read or wrote on the item (the stack, and its format's snapshot), to know when it changed elsewhere. */
	private ItemStack lastHolder = ItemStack.EMPTY;
	private Object lastSnapshot;

	/** {@code size} slots on the item {@code holder} gives, kept as {@code format} says, {@code changed} told when they change. */
	public ItemContents(Supplier<ItemStack> holder, int size, Format format, Runnable changed) {
		this.holder = holder;
		this.size = size;
		this.format = format;
		this.changed = changed;
		this.items = NonNullList.withSize(size, ItemStack.EMPTY);
	}

	/** Whether there's an item to hold the slots (any, by default). */
	public boolean present() {
		return !holder.get().isEmpty();
	}

	/** The slots, as on the item now. */
	protected NonNullList<ItemStack> items() {
		ItemStack stack = holder.get();
		Object now = present() ? format.snapshot(stack) : null;
		if (stack != lastHolder || !Objects.equals(now, lastSnapshot)) {
			items = NonNullList.withSize(size, ItemStack.EMPTY);
			if (present()) {
				format.read(stack, items);
			}
			lastHolder = stack;
			lastSnapshot = now;
		}
		return items;
	}

	/** Its slots, in order (a copy of the list: the stacks are the slots'). */
	public List<ItemStack> stacks() {
		return List.copyOf(items());
	}

	@Override
	public int getContainerSize() {
		return size;
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
			return; // no item: nowhere to put it
		}
		items().set(slot, stack);
		setChanged();
	}

	/** Writes its slots back onto the item, and tells who listens. */
	@Override
	public void setChanged() {
		if (!present()) {
			return;
		}
		ItemStack stack = holder.get();
		format.write(stack, items());
		lastSnapshot = format.snapshot(stack);
		changed.run();
	}

	@Override
	public boolean stillValid(Player player) {
		return true;
	}

	@Override
	public void clearContent() {
		for (int slot = 0; slot < size; slot++) {
			items().set(slot, ItemStack.EMPTY);
		}
		setChanged();
	}
}
