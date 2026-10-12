package com.payonel.modularcomputers.item;

import static com.payonel.modularcomputers.ModularComputers.id;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Clearing an item's id (its serial number, stamped the first time it goes into a machine): the
 * item alone in a crafting grid comes out without one, as good as new, so it stacks with new
 * ones again. Put back in a machine, it gets a new id: it's a new device to the bus.
 */
public final class ClearIdRecipe extends CustomRecipe {
	public static final ClearIdRecipe INSTANCE = new ClearIdRecipe();
	public static final RecipeSerializer<ClearIdRecipe> SERIALIZER = Registry.register(BuiltInRegistries.RECIPE_SERIALIZER,
		id("clear_id"), new RecipeSerializer<>(MapCodec.unit(INSTANCE), StreamCodec.unit(INSTANCE)));

	private ClearIdRecipe() {
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
		ItemStack found = ItemStack.EMPTY;
		for (ItemStack stack : input.items()) {
			if (stack.isEmpty()) {
				continue;
			}
			if (!found.isEmpty()) {
				return ItemStack.EMPTY; // alone in the grid
			}
			found = stack;
		}
		if (!found.has(ModComponents.SERIAL)) {
			return ItemStack.EMPTY;
		}
		ItemStack cleared = found.copyWithCount(1);
		cleared.remove(ModComponents.SERIAL);
		return cleared;
	}

	@Override
	public RecipeSerializer<ClearIdRecipe> getSerializer() {
		return SERIALIZER;
	}
}
