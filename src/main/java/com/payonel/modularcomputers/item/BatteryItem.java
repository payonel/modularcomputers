package com.payonel.modularcomputers.item;

import com.payonel.modularcomputers.power.BatteryCharge;
import com.payonel.modularcomputers.power.BatteryTier;
import com.payonel.modularcomputers.power.ChargeMeter;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * A battery for a computer's UPS slot. Tiers 1 and 2 carry their charge
 * ({@link ModComponents#CHARGE}); a crafted one starts empty. Tier 3 never runs out.
 */
public final class BatteryItem extends Item {
	/** How often a battery in a player's inventory works out its self-charging. */
	private static final int REST_INTERVAL = 20;

	private final BatteryTier tier;

	public BatteryItem(Properties properties, BatteryTier tier) {
		super(tier.infinite() ? properties : properties.component(ModComponents.CHARGE, BatteryCharge.EMPTY));
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
	@SuppressWarnings("deprecation") // still how vanilla's own items add tooltip lines
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		if (tier.infinite()) {
			builder.accept(Component.translatable("item.modularcomputers.battery.infinite").withStyle(ChatFormatting.GRAY));
			return;
		}
		BatteryCharge charge = charge(stack);
		String days = String.format("%.1f", charge.ticks() / (double) BatteryCharge.DAY_TICKS);
		builder.accept(Component.translatable("item.modularcomputers.battery.charge", percent(stack), days).withStyle(ChatFormatting.GRAY));
		if (tier.selfCharging()) {
			builder.accept(Component.translatable("item.modularcomputers.battery.self_charging").withStyle(ChatFormatting.GRAY));
		}
	}
}
