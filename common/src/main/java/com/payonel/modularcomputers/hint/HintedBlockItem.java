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

	/**
	 * Placing one that carries ids (a case with its parts, a hub with its cards) moves it into the
	 * world: refused when one of them is in it already (a creative copy), and used up in creative too.
	 */
	@Override
	public net.minecraft.world.InteractionResult place(net.minecraft.world.item.context.BlockPlaceContext context) {
		net.minecraft.world.item.ItemStack stack = context.getItemInHand();
		if (com.payonel.modularcomputers.item.ModComponents.copyOfOneInTheWorld(stack, context.getLevel())) {
			if (context.getPlayer() != null) {
				context.getPlayer().sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.modularcomputers.already_placed"));
			}
			return net.minecraft.world.InteractionResult.FAIL;
		}
		int before = stack.getCount();
		net.minecraft.world.InteractionResult result = super.place(context);
		if (result.consumesAction() && !context.getLevel().isClientSide() && stack.getCount() == before) {
			com.payonel.modularcomputers.item.ModComponents.placed(stack, context.getPlayer(), context.getHand()); // (creative left it: it moved all the same)
		}
		return result;
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
