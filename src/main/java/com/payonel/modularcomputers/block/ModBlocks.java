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
import com.payonel.modularcomputers.device.TerminalTier;
import com.payonel.modularcomputers.machine.CaseTier;
import com.payonel.modularcomputers.item.ScreenItem;
import java.util.List;

/** Block, block item and block entity registrations. */
public final class ModBlocks {
	/** The dev kit: a tier 3 case (it may become a creative, everything-in block later). */
	public static final Block ALL_IN_ONE = registerWithItem("all_in_one", properties -> new AllInOneBlock(properties, CaseTier.TIER_3),
		BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(2.0f).sound(SoundType.METAL));
	/** Cases, by tier: the same board, with more of it at higher tiers. */
	public static final Block TIER1_CASE = caseBlock("tier1_case", CaseTier.TIER_1);
	public static final Block TIER2_CASE = caseBlock("tier2_case", CaseTier.TIER_2);
	public static final Block TIER3_CASE = caseBlock("tier3_case", CaseTier.TIER_3);
	public static final List<Block> CASES = List.of(TIER1_CASE, TIER2_CASE, TIER3_CASE);

	public static final BlockEntityType<AllInOneBlockEntity> ALL_IN_ONE_ENTITY = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE, id("all_in_one"),
		new BlockEntityType<>(AllInOneBlockEntity::new, Set.of(ALL_IN_ONE, TIER1_CASE, TIER2_CASE, TIER3_CASE)));

	/** Screens (terminals: a display and keyboard), by tier: what colors they show. */
	public static final ScreenBlock TIER1_SCREEN = screen("tier1_screen", TerminalTier.TIER_1);
	public static final ScreenBlock TIER2_SCREEN = screen("tier2_screen", TerminalTier.TIER_2);
	public static final ScreenBlock TIER3_SCREEN = screen("tier3_screen", TerminalTier.TIER_3);
	public static final List<ScreenBlock> SCREENS = List.of(TIER1_SCREEN, TIER2_SCREEN, TIER3_SCREEN);

	public static final BlockEntityType<ScreenBlockEntity> SCREEN_ENTITY = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE, id("screen"),
		new BlockEntityType<>(ScreenBlockEntity::new, Set.copyOf(SCREENS)));

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

	/** The photomask: prints chips from silicon, cranked by hand or run on a battery. */
	public static final Block PHOTOMASK = registerWithItem("photomask", PhotomaskBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0f).sound(SoundType.METAL).noOcclusion()); // light reaches into its gear's recess

	public static final BlockEntityType<PhotomaskBlockEntity> PHOTOMASK_ENTITY = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE, id("photomask"),
		new BlockEntityType<>(PhotomaskBlockEntity::new, Set.of(PHOTOMASK)));

	/** A broken computer from the ancient sites: decoration, nothing works; broken, it's gone (no drops). */
	public static final Block BROKEN_CASE = registerWithItem("broken_case", BrokenCaseBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(1.5f).sound(SoundType.METAL).noOcclusion().noLootTable());

	private ModBlocks() {
	}

	/** Forces the static registrations above. */
	public static void init() {
	}

	private static Block caseBlock(String name, CaseTier tier) {
		return registerWithItem(name, properties -> new AllInOneBlock(properties, tier),
			BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(2.0f).sound(SoundType.METAL));
	}

	private static ScreenBlock screen(String name, TerminalTier tier) {
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id(name));
		ScreenBlock block = Registry.register(BuiltInRegistries.BLOCK, blockKey, new ScreenBlock(
			BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(2.0f).sound(SoundType.METAL).setId(blockKey), tier));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id(name));
		Registry.register(BuiltInRegistries.ITEM, itemKey,
			new ScreenItem(block, holderProperties(itemKey)));
		return block;
	}

	/** A block item's properties; vanilla's list of a container's items is hidden, as the hint lists them in bus order. */
	private static Item.Properties holderProperties(ResourceKey<Item> itemKey) {
		return new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()
			.component(net.minecraft.core.component.DataComponents.TOOLTIP_DISPLAY,
				net.minecraft.world.item.component.TooltipDisplay.DEFAULT.withHidden(net.minecraft.core.component.DataComponents.CONTAINER, true));
	}

	private static Block registerWithItem(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id(name));
		Block block = Registry.register(BuiltInRegistries.BLOCK, blockKey, factory.apply(properties.setId(blockKey)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id(name));
		Registry.register(BuiltInRegistries.ITEM, itemKey,
			new HintedBlockItem(block, holderProperties(itemKey)));
		return block;
	}
}
