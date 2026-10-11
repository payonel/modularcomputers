package com.payonel.modularcomputers.hint;

import java.util.function.Consumer;
import net.minecraft.world.item.ItemStack;

/** An item with a {@link Hint}: what its tooltip shows, and in more detail with Shift held. */
public interface Hinted {
	Hinted NONE = new Hinted() {
	};

	/** Always shown: short data, like a charge and the time it lasts. */
	default void summary(ItemStack stack, Consumer<Hint.Element> hint) {
	}

	/** Shown with Shift held, above the crafting grid: how the item works. */
	default void details(ItemStack stack, Consumer<Hint.Element> hint) {
	}
}
