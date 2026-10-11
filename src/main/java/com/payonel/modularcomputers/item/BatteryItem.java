package com.payonel.modularcomputers.item;

import com.payonel.modularcomputers.hint.Hint;
import com.payonel.modularcomputers.hint.HintedItem;
import com.payonel.modularcomputers.power.BatteryCharge;
import com.payonel.modularcomputers.power.BatteryTier;
import com.payonel.modularcomputers.power.ChargeMeter;
import com.payonel.modularcomputers.power.Durations;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * A battery for a computer's UPS slot. It carries its charge ({@link ModComponents#CHARGE}); a
 * crafted one starts empty. How long each tier lasts is configurable, and one set to 0 days
 * (tier 3, by default) never runs out.
 */
public final class BatteryItem extends HintedItem {
	/** How often a battery in a player's inventory works out its self-charging. */
	private static final int REST_INTERVAL = 20;

	private final BatteryTier tier;

	public BatteryItem(Properties properties, BatteryTier tier) {
		// Every tier carries a charge: which ones never run out is configurable.
		super(properties.component(ModComponents.CHARGE, BatteryCharge.EMPTY));
		this.tier = tier;
	}

	public BatteryTier tier() {
		return tier;
	}

	public static BatteryCharge charge(ItemStack stack) {
		return stack.getOrDefault(ModComponents.CHARGE, BatteryCharge.EMPTY);
	}

	/** A fully charged one, for the creative tab and the giveall cheat. */
	public ItemStack charged() {
		ItemStack stack = new ItemStack(this);
		if (!tier.infinite()) {
			stack.set(ModComponents.CHARGE, BatteryCharge.full(tier));
		}
		return stack;
	}

	/** Whether this stack is a battery with charge to run on. */
	public static boolean hasPower(ItemStack stack) {
		return stack.getItem() instanceof BatteryItem battery && !charge(stack).isEmpty(battery.tier);
	}

	/** The charge as a percentage, 100 for a battery that never runs out. */
	public static int percent(ItemStack stack) {
		return stack.getItem() instanceof BatteryItem battery ? charge(stack).percent(battery.tier) : 0;
	}

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return !tier.infinite();
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		return Math.round(MAX_BAR_WIDTH * percent(stack) / 100f);
	}

	@Override
	public int getBarColor(ItemStack stack) {
		return switch (ChargeMeter.level(ChargeMeter.litBars(percent(stack)))) {
			case OFF, LOW -> 0xDC3C32;
			case MEDIUM -> 0xE6C83C;
			default -> 0x5AE66E;
		};
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, EquipmentSlot slot) {
		if (tier.selfCharging() && level.getGameTime() % REST_INTERVAL == 0) {
			stack.set(ModComponents.CHARGE, charge(stack).rest(tier, level.getGameTime()));
		}
	}

	@Override
	public void summary(ItemStack stack, Consumer<Hint.Element> hint) {
		if (tier.infinite()) {
			hint.accept(Hint.text(Component.translatable("item.modularcomputers.battery.infinite")));
			return;
		}
		hint.accept(Hint.text(Component.translatable("item.modularcomputers.battery.charge", percent(stack), Durations.clock(charge(stack).ticks()))));
	}

	@Override
	public void details(ItemStack stack, Consumer<Hint.Element> hint) {
		if (tier.selfCharging()) {
			hint.accept(Hint.text(Component.translatable("item.modularcomputers.battery.self_charging")));
			hint.accept(Hint.text(Component.translatable("item.modularcomputers.battery.self_charging_rate", seconds(1 / tier.rechargeRate()))));
		}
		if (tier.infinite()) {
			return;
		}
		hint.accept(Hint.text(Component.translatable("item.modularcomputers.battery.charging")));
		List<Hint.Icon> icons = new ArrayList<>();
		BatteryChargingRecipe.MATERIALS.forEach((item, percent) -> icons.add(new Hint.Icon(new ItemStack(item),
			Component.literal("+" + Math.round(BatteryChargingRecipe.added(percent) * 100.0 / tier.capacity()) + "%"))));
		hint.accept(new Hint.Icons(icons));
	}

	/** Seconds, without a needless ".0": "2", "1.5", "3.33". */
	private static String seconds(double seconds) {
		return new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.ROOT)).format(seconds);
	}
}
