package com.payonel.modularcomputers.client.hint;

import com.payonel.modularcomputers.ModularComputers;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.recipe.v1.sync.ClientRecipeSynchronizedEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;

/**
 * The crafting grids for this mod's items, from the shaped recipes the server sends (the mod
 * asks Fabric to synchronize them). Each cell lists every item that fits it; a tag fits many.
 * A blast furnace recipe shows as its input over a blast furnace, as the furnace's own screen
 * puts the item above the fire.
 */
public final class ClientRecipes {
	/** A shaped recipe's cells, row by row, {@code width} by {@code height}; an empty list is an empty cell. */
	public record Grid(int width, int height, List<List<ItemStack>> cells) {
		public List<ItemStack> cell(int column, int row) {
			return column < width && row < height ? cells.get(row * width + column) : List.of();
		}
	}

	private static volatile Map<Item, Grid> grids = Map.of();

	private ClientRecipes() {
	}

	public static void init() {
		ClientRecipeSynchronizedEvent.EVENT.register((client, recipes) -> update(recipes.recipes()));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> grids = Map.of());
	}

	/** How to craft this item, if the server has a shaped recipe for it. */
	public static Optional<Grid> of(Item item) {
		return Optional.ofNullable(grids.get(item));
	}

	static void update(Collection<RecipeHolder<?>> recipes) {
		Map<Item, Grid> found = new HashMap<>();
		for (RecipeHolder<?> holder : recipes) {
			if (holder.value() instanceof net.minecraft.world.item.crafting.BlastingRecipe blasting) {
				List<ItemStack> input = stacks(blasting.input());
				ItemStack result = input.isEmpty() ? ItemStack.EMPTY : blasting.assemble(new net.minecraft.world.item.crafting.SingleRecipeInput(input.getFirst()));
				if (!result.isEmpty() && BuiltInRegistries.ITEM.getKey(result.getItem()).getNamespace().equals(ModularComputers.MOD_ID)) {
					List<List<ItemStack>> cells = new java.util.ArrayList<>(java.util.Collections.nCopies(9, List.<ItemStack>of()));
					cells.set(1, input);
					cells.set(7, List.of(new ItemStack(net.minecraft.world.item.Items.BLAST_FURNACE)));
					found.putIfAbsent(result.getItem(), new Grid(3, 3, List.copyOf(cells)));
				}
				continue;
			}
			if (!(holder.value() instanceof ShapedRecipe recipe)) {
				continue;
			}
			List<List<ItemStack>> cells = recipe.getIngredients().stream()
				.map(ingredient -> ingredient.map(ClientRecipes::stacks).orElse(List.of()))
				.toList();
			// The result isn't public: make it, from the first item that fits each cell.
			CraftingInput input = CraftingInput.of(recipe.getWidth(), recipe.getHeight(),
				cells.stream().map(cell -> cell.isEmpty() ? ItemStack.EMPTY : cell.getFirst()).toList());
			ItemStack result = recipe.assemble(input);
			if (!result.isEmpty() && BuiltInRegistries.ITEM.getKey(result.getItem()).getNamespace().equals(ModularComputers.MOD_ID)) {
				found.putIfAbsent(result.getItem(), new Grid(recipe.getWidth(), recipe.getHeight(), cells));
			}
		}
		grids = Map.copyOf(found);
	}

	private static List<ItemStack> stacks(Ingredient ingredient) {
		return ingredient.items().map(item -> new ItemStack(item.value())).toList();
	}
}
