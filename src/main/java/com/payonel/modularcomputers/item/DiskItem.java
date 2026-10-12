package com.payonel.modularcomputers.item;

import com.payonel.modularcomputers.device.DiskTier;
import com.payonel.modularcomputers.hint.Hint;
import com.payonel.modularcomputers.hint.HintedItem;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** A hard disk of a tier: its size and power draw. */
public final class DiskItem extends HintedItem {
	private final DiskTier tier;

	public DiskItem(Properties properties, DiskTier tier) {
		super(properties);
		this.tier = tier;
	}

	public DiskTier tier() {
		return tier;
	}

	@Override
	public void summary(ItemStack stack, Consumer<Hint.Element> hint) {
		hint.accept(Hint.text(Component.translatable("item.modularcomputers.disk.size", Bytes.format(tier.bytes()))));
	}

	@Override
	public void details(ItemStack stack, Consumer<Hint.Element> hint) {
		hint.accept(Hint.text(Component.translatable("item.modularcomputers.disk.draw", tier.draw())));
	}
}
