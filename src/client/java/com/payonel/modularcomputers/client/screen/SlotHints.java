package com.payonel.modularcomputers.client.screen;

import com.payonel.modularcomputers.display.Viewport;
import java.util.List;
import java.util.function.IntFunction;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Where an item can go: while the player carries an item, or hovers one in their inventory,
 * the block's slots that take it are outlined (the CPU socket for a CPU, every expansion slot
 * for a card, and so on). The other way round too: hovering one of the block's slots outlines
 * the items in the inventory that fit it.
 */
final class SlotHints {
	/** The same light outline as the case view's linked hover. */
	static final int COLOR = 0xFFF2F2E6;

	private SlotHints() {
	}

	/**
	 * The block's slots (the first {@code blockSlots} of the menu) that take the carried item,
	 * or else the item under the mouse in the player's inventory.
	 */
	static List<Slot> fitting(AbstractContainerMenu menu, Slot hovered, int blockSlots) {
		ItemStack stack = menu.getCarried();
		if (stack.isEmpty() && hovered != null && hovered.index >= blockSlots) {
			stack = hovered.getItem();
		}
		if (stack.isEmpty()) {
			return List.of();
		}
		ItemStack item = stack;
		return menu.slots.subList(0, blockSlots).stream().filter(slot -> slot.mayPlace(item)).toList();
	}

	/**
	 * The inventory slots (after the block's first {@code blockSlots}) holding an item that fits
	 * the hovered block slot. Nothing while an item is carried: then the block's slots show.
	 */
	static List<Slot> fittingItems(AbstractContainerMenu menu, Slot hovered, int blockSlots) {
		if (!menu.getCarried().isEmpty() || hovered == null || hovered.index >= blockSlots) {
			return List.of();
		}
		return menu.slots.subList(blockSlots, menu.slots.size()).stream()
			.filter(slot -> slot.hasItem() && hovered.mayPlace(slot.getItem()))
			.toList();
	}

	/** Outlines what fits: the block's slots for the carried or hovered item, or the items for the hovered slot. */
	static void extract(GuiGraphicsExtractor graphics, AbstractContainerMenu menu, Slot hovered, int blockSlots,
		IntFunction<Viewport> area) {
		for (Slot slot : fitting(menu, hovered, blockSlots)) {
			outline(graphics, area.apply(slot.index), 2, COLOR);
		}
		for (Slot slot : fittingItems(menu, hovered, blockSlots)) {
			outline(graphics, area.apply(slot.index), 1, COLOR); // inventory slots sit close: inside the gutter
		}
	}

	/** A 1-pixel outline around an area, {@code gap} pixels out. */
	static void outline(GuiGraphicsExtractor graphics, Viewport area, int gap, int color) {
		int x0 = area.x() - gap;
		int y0 = area.y() - gap;
		int x1 = area.right() + gap;
		int y1 = area.bottom() + gap;
		graphics.fill(x0, y0, x1, y0 + 1, color);
		graphics.fill(x0, y1 - 1, x1, y1, color);
		graphics.fill(x0, y0, x0 + 1, y1, color);
		graphics.fill(x1 - 1, y0, x1, y1, color);
	}
}
