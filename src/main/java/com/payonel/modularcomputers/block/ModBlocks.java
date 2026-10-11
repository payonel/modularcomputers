package com.payonel.modularcomputers.block;

import static com.payonel.modularcomputers.ModularComputers.id;

import com.payonel.modularcomputers.hint.HintedBlockItem;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/** Block, block item and block entity registrations. */
public final class ModBlocks {
	public static final Block ALL_IN_ONE = registerWithItem("all_in_one", AllInOneBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(2.0f).sound(SoundType.METAL));

	public static final BlockEntityType<AllInOneBlockEntity> ALL_IN_ONE_ENTITY = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE, id("all_in_one"),
		new BlockEntityType<>(AllInOneBlockEntity::new, Set.of(ALL_IN_ONE)));

	public static final Block SCREEN = registerWithItem("screen", ScreenBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(2.0f).sound(SoundType.METAL));

	public static final BlockEntityType<ScreenBlockEntity> SCREEN_ENTITY = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE, id("screen"),
		new BlockEntityType<>(ScreenBlockEntity::new, Set.of(SCREEN)));

	public static final Block CABLE = registerWithItem("cable", CableBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(0.5f).sound(SoundType.WOOL).noOcclusion());

	public static final Block EXPANSION_HUB = registerWithItem("expansion_hub", ExpansionHubBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(2.0f).sound(SoundType.METAL));

	public static final BlockEntityType<ExpansionHubBlockEntity> EXPANSION_HUB_ENTITY = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE, id("expansion_hub"),
		new BlockEntityType<>(ExpansionHubBlockEntity::new, Set.of(EXPANSION_HUB)));

	public static final Block ROBOTIC_PISTON = registerWithItem("robotic_piston", RoboticPistonBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0f).sound(SoundType.METAL));

	public static final BlockEntityType<RoboticPistonBlockEntity> ROBOTIC_PISTON_ENTITY = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE, id("robotic_piston"),
		new BlockEntityType<>(RoboticPistonBlockEntity::new, Set.of(ROBOTIC_PISTON)));

	public static final Block PAINT_GUN = registerWithItem("paint_gun", PaintGunBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0f).sound(SoundType.METAL));

	public static final BlockEntityType<PaintGunBlockEntity> PAINT_GUN_ENTITY = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE, id("paint_gun"),
		new BlockEntityType<>(PaintGunBlockEntity::new, Set.of(PAINT_GUN)));

	private ModBlocks() {
	}

	/** Forces the static registrations above. */
	public static void init() {
	}

	private static Block registerWithItem(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id(name));
		Block block = Registry.register(BuiltInRegistries.BLOCK, blockKey, factory.apply(properties.setId(blockKey)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id(name));
		Registry.register(BuiltInRegistries.ITEM, itemKey,
			new HintedBlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		return block;
	}
}
