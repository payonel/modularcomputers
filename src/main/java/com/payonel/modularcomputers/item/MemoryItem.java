package com.payonel.modularcomputers.item;

import com.payonel.modularcomputers.hint.Hint;
import com.payonel.modularcomputers.hint.HintedItem;
import com.payonel.modularcomputers.machine.MemoryTier;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** A RAM stick of a tier. */
public final class MemoryItem extends HintedItem {
	private final MemoryTier tier;

	public MemoryItem(Properties properties, MemoryTier tier) {
		super(properties);
		this.tier = tier;
	}

	public MemoryTier tier() {
		return tier;
	}

	@Override
	public void summary(ItemStack stack, Consumer<Hint.Element> hint) {
		hint.accept(Hint.text(Component.translatable("item.modularcomputers.memory.size", Bytes.format(tier.bytes()))));
	}

	/** With Shift: the ring memory it adds, for each device (pieces of device data the firmware keeps for the program). */
	@Override
	public void details(ItemStack stack, Consumer<Hint.Element> hint) {
		hint.accept(Hint.text(tier.inbox() == 0 ? Component.translatable("item.modularcomputers.memory.no_ring")
			: Component.translatable("item.modularcomputers.memory.ring", tier.inbox())));
	}
}
