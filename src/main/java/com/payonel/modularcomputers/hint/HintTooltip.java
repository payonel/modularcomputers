package com.payonel.modularcomputers.hint;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

/** The tooltip image every mod item has: its {@link Hint}, drawn by the client. */
public record HintTooltip(ItemStack stack) implements TooltipComponent {
}
