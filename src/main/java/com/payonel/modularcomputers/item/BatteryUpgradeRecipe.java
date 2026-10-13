package com.payonel.modularcomputers.item;

import static com.payonel.modularcomputers.ModularComputers.id;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.payonel.modularcomputers.power.BatteryCharge;
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
 * A battery tier's upgrade: a shaped recipe built around the battery of the tier below, which
 * keeps its charge (user, 2026-10-12), up to the new battery's capacity. The new battery starts
 * counting its own time at the first look, as a new one does. Written like a shaped recipe in
 * JSON, and it is one, so recipe hints show its grid.
 */
public final class BatteryUpgradeRecipe extends ShapedRecipe {
	public static final MapCodec<BatteryUpgradeRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(recipe -> recipe.group(
		Recipe.CommonInfo.MAP_CODEC.forGetter(BatteryUpgradeRecipe::commonInfo),
		CraftingRecipe.CraftingBookInfo.MAP_CODEC.forGetter(BatteryUpgradeRecipe::bookInfo),
		ShapedRecipePattern.MAP_CODEC.forGetter(BatteryUpgradeRecipe::pattern),
		ItemStackTemplate.CODEC.fieldOf("result").forGetter(BatteryUpgradeRecipe::result)
	).apply(recipe, BatteryUpgradeRecipe::new));
	public static final StreamCodec<RegistryFriendlyByteBuf, BatteryUpgradeRecipe> STREAM_CODEC = StreamCodec.composite(
		Recipe.CommonInfo.STREAM_CODEC, BatteryUpgradeRecipe::commonInfo,
		CraftingRecipe.CraftingBookInfo.STREAM_CODEC, BatteryUpgradeRecipe::bookInfo,
		ShapedRecipePattern.STREAM_CODEC, BatteryUpgradeRecipe::pattern,
		ItemStackTemplate.STREAM_CODEC, BatteryUpgradeRecipe::result,
		BatteryUpgradeRecipe::new);
	public static final RecipeSerializer<BatteryUpgradeRecipe> SERIALIZER = Registry.register(BuiltInRegistries.RECIPE_SERIALIZER,
		id("battery_upgrade"), new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC));

	private final ShapedRecipePattern pattern;
	private final ItemStackTemplate result;

	public BatteryUpgradeRecipe(Recipe.CommonInfo common, CraftingRecipe.CraftingBookInfo book, ShapedRecipePattern pattern, ItemStackTemplate result) {
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
		ItemStack upgraded = super.assemble(input);
		if (!(upgraded.getItem() instanceof BatteryItem battery)) {
			return upgraded;
		}
		for (ItemStack stack : input.items()) {
			if (stack.getItem() instanceof BatteryItem) {
				int units = BatteryItem.charge(stack).units();
				upgraded.set(ModComponents.CHARGE, new BatteryCharge(Math.min(units, battery.tier().capacity()), BatteryCharge.UNSETTLED));
			}
		}
		return upgraded;
	}

	@Override
	public RecipeSerializer<ShapedRecipe> getSerializer() {
		@SuppressWarnings({"unchecked", "rawtypes"})
		RecipeSerializer<ShapedRecipe> serializer = (RecipeSerializer) SERIALIZER;
		return serializer;
	}
}
