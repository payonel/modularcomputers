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

	/**
	 * Taken from the crafting grid wiped (crafted alone): the files of the id it had go, now, on the
	 * server. This runs once, when the player takes the result, unlike the grid's previews; it's the
	 * one moment a disk's data is known to be let go (MC copies stacks too freely to tell when one is
	 * really gone otherwise).
	 */
	@Override
	public void onCraftedBy(ItemStack stack, net.minecraft.world.entity.player.Player player) {
		super.onCraftedBy(stack, player);
		java.util.UUID wiped = stack.get(ModComponents.WIPED);
		if (wiped == null) {
			return;
		}
		stack.remove(ModComponents.WIPED);
		if (player.level() instanceof net.minecraft.server.level.ServerLevel level) {
			com.payonel.modularcomputers.device.Disk.erase(com.payonel.modularcomputers.block.AllInOneBlockEntity.diskFolder(level.getServer(), wiped));
		}
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
