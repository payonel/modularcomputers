package com.payonel.modularcomputers.hint;

import java.util.Optional;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/** A mod block's item: its tooltip shows its {@link Hint}. */
public class HintedBlockItem extends BlockItem implements Hinted {
	public HintedBlockItem(Block block, Properties properties) {
		super(block, properties);
	}

	@Override
	public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
		return Optional.of(new HintTooltip(stack));
	}
}
