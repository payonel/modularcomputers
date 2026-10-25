package com.payonel.modularcomputers.item;

import static com.payonel.modularcomputers.ModularComputers.id;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;

/**
 * A bot golem put back together (user design, 2026-10-24): its torso over its legs, in a crafting
 * grid (2x2 is enough). The golem made is the torso's: its parts, its board's id, what it held, its
 * name, all it kept. Written like a shaped recipe in JSON, and it is one, so recipe hints show it.
 */
public final class GolemAssemblyRecipe extends ShapedRecipe {
	public static final MapCodec<GolemAssemblyRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(recipe -> recipe.group(
		Recipe.CommonInfo.MAP_CODEC.forGetter(GolemAssemblyRecipe::commonInfo),
		CraftingRecipe.CraftingBookInfo.MAP_CODEC.forGetter(GolemAssemblyRecipe::bookInfo),
		ShapedRecipePattern.MAP_CODEC.forGetter(GolemAssemblyRecipe::pattern),
		ItemStackTemplate.CODEC.fieldOf("result").forGetter(GolemAssemblyRecipe::result)
	).apply(recipe, GolemAssemblyRecipe::new));
	public static final StreamCodec<RegistryFriendlyByteBuf, GolemAssemblyRecipe> STREAM_CODEC = StreamCodec.composite(
		Recipe.CommonInfo.STREAM_CODEC, GolemAssemblyRecipe::commonInfo,
		CraftingRecipe.CraftingBookInfo.STREAM_CODEC, GolemAssemblyRecipe::bookInfo,
		ShapedRecipePattern.STREAM_CODEC, GolemAssemblyRecipe::pattern,
		ItemStackTemplate.STREAM_CODEC, GolemAssemblyRecipe::result,
		GolemAssemblyRecipe::new);
	public static final RecipeSerializer<GolemAssemblyRecipe> SERIALIZER = Registry.register(BuiltInRegistries.RECIPE_SERIALIZER,
		id("golem_assembly"), new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC));

	private final ShapedRecipePattern pattern;
	private final ItemStackTemplate result;

	public GolemAssemblyRecipe(Recipe.CommonInfo common, CraftingRecipe.CraftingBookInfo book, ShapedRecipePattern pattern, ItemStackTemplate result) {
		super(common, book, pattern, result);
		this.pattern = pattern;
		this.result = result;
	}

	/** Forces the static registration above. */
	public static void init() {
	}

	private Recipe.CommonInfo commonInfo() {
		return commonInfo;
	}

	private CraftingRecipe.CraftingBookInfo bookInfo() {
		return bookInfo;
	}

	private ShapedRecipePattern pattern() {
		return pattern;
	}

	private ItemStackTemplate result() {
		return result;
	}

	@Override
	public ItemStack assemble(CraftingInput input) {
		ItemStack golem = super.assemble(input);
		for (ItemStack stack : input.items()) {
			if (stack.is(ModItems.BOT_GOLEM_TORSO)) {
				return stack.transmuteCopy(golem.getItem(), 1); // (all the torso kept)
			}
		}
		return golem;
	}

	@Override
	public RecipeSerializer<ShapedRecipe> getSerializer() {
		@SuppressWarnings({"unchecked", "rawtypes"})
		RecipeSerializer<ShapedRecipe> serializer = (RecipeSerializer) SERIALIZER;
		return serializer;
	}
}
