package com.payonel.modularcomputers.item;

import com.payonel.modularcomputers.hint.Hint;
import com.payonel.modularcomputers.hint.HintedItem;
import com.payonel.modularcomputers.machine.Architecture;
import com.payonel.modularcomputers.machine.CpuTier;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** A CPU of a tier (its bus capacity and its power draw) and an architecture: Lua, or the creative Java CPU. */
public final class CpuItem extends HintedItem {
	private final CpuTier tier;
	private final Architecture architecture;

	public CpuItem(Properties properties, CpuTier tier) {
		this(properties, tier, Architecture.LUA);
	}

	public CpuItem(Properties properties, CpuTier tier, Architecture architecture) {
		super(properties);
		this.tier = tier;
		this.architecture = architecture;
	}

	public CpuTier tier() {
		return tier;
	}

	public Architecture architecture() {
		return architecture;
	}

	@Override
	public void summary(ItemStack stack, Consumer<Hint.Element> hint) {
		hint.accept(Hint.text(Component.translatable("item.modularcomputers.cpu.bus", tier.busCapacity())));
	}

	@Override
	public void details(ItemStack stack, Consumer<Hint.Element> hint) {
		hint.accept(Hint.text(Component.translatable("item.modularcomputers.cpu.draw", com.payonel.modularcomputers.power.PowerUnits.rate(tier.running()),
			com.payonel.modularcomputers.power.PowerUnits.rate(tier.waiting()))));
	}
}
