package com.payonel.modularcomputers.item;

import static com.payonel.modularcomputers.ModularComputers.id;

import com.mojang.serialization.MapCodec;
import com.payonel.modularcomputers.power.BatteryCharge;
import com.payonel.modularcomputers.power.BatteryTier;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Charging a battery in the crafting grid, the way tools are combined to repair them: the battery
 * and some glowing material, in any arrangement (a 2x2 is enough). Each material adds a share of
 * a full tier 1 battery; a tier 2 holds twice as much (by default), so the same material fills
 * half as much of it. A full battery takes nothing. Tier 3 never needs charging.
 */
public final class BatteryChargingRecipe extends CustomRecipe {
	/** What each glowing material adds, as a percentage of a full tier 1 battery. */
	public static final Map<Item, Integer> MATERIALS = Map.of(
		Items.SEA_LANTERN, 100,
		Items.GLOWSTONE, 25,
		Items.SHROOMLIGHT, 25,
		Items.GLOW_INK_SAC, 10,
		Items.GLOW_LICHEN, 5);

	/** A full tier 1's charge; its default size if tier 1 is configured never to run out. */
	private static long tier1Full() {
		BatteryTier tier1 = BatteryTier.TIER_1;
		return tier1.infinite() ? Math.round(tier1.defaultDays() * BatteryCharge.DAY_TICKS) : tier1.capacity();
	}

	public static final BatteryChargingRecipe INSTANCE = new BatteryChargingRecipe();
	public static final RecipeSerializer<BatteryChargingRecipe> SERIALIZER = Registry.register(BuiltInRegistries.RECIPE_SERIALIZER,
		id("battery_charging"), new RecipeSerializer<>(MapCodec.unit(INSTANCE), StreamCodec.unit(INSTANCE)));

	private BatteryChargingRecipe() {
	}

	/** Forces the static registration above. */
	public static void init() {
	}

	@Override
	public boolean matches(CraftingInput input, Level level) {
		return !assemble(input).isEmpty();
	}

	@Override
	public ItemStack assemble(CraftingInput input) {
		ItemStack battery = ItemStack.EMPTY;
		long percent = 0;
		for (ItemStack stack : input.items()) {
			if (stack.isEmpty()) {
				continue;
			}
			if (stack.getItem() instanceof BatteryItem item && !item.tier().infinite() && battery.isEmpty()) {
				battery = stack;
			} else if (MATERIALS.containsKey(stack.getItem())) {
				percent += MATERIALS.get(stack.getItem());
			} else {
				return ItemStack.EMPTY;
			}
		}
		if (battery.isEmpty() || percent == 0) {
			return ItemStack.EMPTY;
		}
		BatteryItem item = (BatteryItem) battery.getItem();
		BatteryCharge charge = BatteryItem.charge(battery);
		if (charge.isFull(item.tier())) {
			return ItemStack.EMPTY;
		}
		ItemStack charged = battery.copyWithCount(1);
		long added = tier1Full() * percent / 100;
		charged.set(ModComponents.CHARGE, charge.charge(item.tier(), (int) Math.min(Integer.MAX_VALUE, added)));
		return charged;
	}

	@Override
	public RecipeSerializer<BatteryChargingRecipe> getSerializer() {
		return SERIALIZER;
	}
}
