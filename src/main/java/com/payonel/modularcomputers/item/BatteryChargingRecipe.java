package com.payonel.modularcomputers.item;

import static com.payonel.modularcomputers.ModularComputers.id;

import com.mojang.serialization.MapCodec;
import com.payonel.modularcomputers.power.BatteryCharge;
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
 * a full tier 1 battery; a tier 2 holds twice as much, so the same material fills half as much of
 * it. A full battery takes nothing. Tier 3 never needs charging.
 */
public final class BatteryChargingRecipe extends CustomRecipe {
	private static final int TIER1_FULL = 10 * BatteryCharge.DAY_TICKS;
	/** What each glowing material adds, in ticks of running a CPU. */
	public static final Map<Item, Integer> MATERIALS = Map.of(
		Items.SEA_LANTERN, TIER1_FULL,
		Items.GLOWSTONE, TIER1_FULL / 4,
		Items.SHROOMLIGHT, TIER1_FULL / 4,
		Items.GLOW_INK_SAC, TIER1_FULL / 10,
		Items.GLOW_LICHEN, TIER1_FULL / 20);

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
		long added = 0;
		for (ItemStack stack : input.items()) {
			if (stack.isEmpty()) {
				continue;
			}
			if (stack.getItem() instanceof BatteryItem item && !item.tier().infinite() && battery.isEmpty()) {
				battery = stack;
			} else if (MATERIALS.containsKey(stack.getItem())) {
				added += MATERIALS.get(stack.getItem());
			} else {
				return ItemStack.EMPTY;
			}
		}
		if (battery.isEmpty() || added == 0) {
			return ItemStack.EMPTY;
		}
		BatteryItem item = (BatteryItem) battery.getItem();
		BatteryCharge charge = BatteryItem.charge(battery);
		if (charge.isFull(item.tier())) {
			return ItemStack.EMPTY;
		}
		ItemStack charged = battery.copyWithCount(1);
		charged.set(ModComponents.CHARGE, charge.charge(item.tier(), (int) Math.min(Integer.MAX_VALUE, added)));
		return charged;
	}

	@Override
	public RecipeSerializer<BatteryChargingRecipe> getSerializer() {
		return SERIALIZER;
	}
}
