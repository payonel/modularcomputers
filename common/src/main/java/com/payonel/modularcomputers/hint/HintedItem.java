package com.payonel.modularcomputers.hint;

import java.util.Optional;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** A mod item: its tooltip shows its {@link Hint}. */
public class HintedItem extends Item implements Hinted {
	public HintedItem(Properties properties) {
		super(properties);
	}

	@Override
	public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
		return Optional.of(new HintTooltip(stack));
	}
}
