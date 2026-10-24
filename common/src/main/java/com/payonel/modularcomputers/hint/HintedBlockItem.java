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

	/** Taken from the crafting grid: a disk its recipe used up loses its files ({@code DiskUsingRecipe}). */
	@Override
	public void onCraftedBy(ItemStack stack, net.minecraft.world.entity.player.Player player) {
		super.onCraftedBy(stack, player);
		com.payonel.modularcomputers.item.DiskItem.eraseWiped(stack, player);
	}

	@Override
	public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
		return Optional.of(new HintTooltip(stack));
	}
}
