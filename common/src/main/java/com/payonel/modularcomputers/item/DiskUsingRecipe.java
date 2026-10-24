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
 * A shaped recipe that uses a disk up (the Disk Station's): a disk that held files loses them, as
 * one crafted alone does. The result carries the disk's id ({@link ModComponents#WIPED}) and its
 * files go when the player takes it ({@code HintedBlockItem.onCraftedBy}), never at a preview.
 * Written like a shaped recipe in JSON, and it is one, so recipe hints show its grid.
 */
public final class DiskUsingRecipe extends ShapedRecipe {
	public static final MapCodec<DiskUsingRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(recipe -> recipe.group(
		Recipe.CommonInfo.MAP_CODEC.forGetter(DiskUsingRecipe::commonInfo),
		CraftingRecipe.CraftingBookInfo.MAP_CODEC.forGetter(DiskUsingRecipe::bookInfo),
		ShapedRecipePattern.MAP_CODEC.forGetter(DiskUsingRecipe::pattern),
		ItemStackTemplate.CODEC.fieldOf("result").forGetter(DiskUsingRecipe::result)
	).apply(recipe, DiskUsingRecipe::new));
	public static final StreamCodec<RegistryFriendlyByteBuf, DiskUsingRecipe> STREAM_CODEC = StreamCodec.composite(
		Recipe.CommonInfo.STREAM_CODEC, DiskUsingRecipe::commonInfo,
		CraftingRecipe.CraftingBookInfo.STREAM_CODEC, DiskUsingRecipe::bookInfo,
		ShapedRecipePattern.STREAM_CODEC, DiskUsingRecipe::pattern,
		ItemStackTemplate.STREAM_CODEC, DiskUsingRecipe::result,
		DiskUsingRecipe::new);
	public static final RecipeSerializer<DiskUsingRecipe> SERIALIZER = Registry.register(BuiltInRegistries.RECIPE_SERIALIZER,
		id("disk_using"), new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC));

	private final ShapedRecipePattern pattern;
	private final ItemStackTemplate result;

	public DiskUsingRecipe(Recipe.CommonInfo common, CraftingRecipe.CraftingBookInfo book, ShapedRecipePattern pattern, ItemStackTemplate result) {
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
		ItemStack made = super.assemble(input);
		for (ItemStack stack : input.items()) {
			if (stack.getItem() instanceof DiskItem && stack.has(ModComponents.SERIAL)) {
				made.set(ModComponents.WIPED, stack.get(ModComponents.SERIAL)); // (a recipe takes one disk)
			}
		}
		return made;
	}

	@Override
	public RecipeSerializer<ShapedRecipe> getSerializer() {
		@SuppressWarnings({"unchecked", "rawtypes"})
		RecipeSerializer<ShapedRecipe> serializer = (RecipeSerializer) SERIALIZER;
		return serializer;
	}
}
