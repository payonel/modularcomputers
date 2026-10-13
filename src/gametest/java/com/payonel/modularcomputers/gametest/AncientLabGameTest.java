package com.payonel.modularcomputers.gametest;

import com.payonel.modularcomputers.ModularComputers;
import com.payonel.modularcomputers.block.ModBlocks;
import com.payonel.modularcomputers.block.PhotomaskBlockEntity;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * The ancient lab (user design, 2026-10-12): a ruin where someone built computers before you,
 * found in the world, with broken computers, cables, a chest of parts and a working photomask.
 * A toolsmith also sells photomasks, the other way to the first one.
 */
public class AncientLabGameTest {
	private static final net.minecraft.resources.Identifier LAB = ModularComputers.id("ancient_lab");

	private static StructureTemplate template(GameTestHelper helper) {
		return helper.getLevel().getStructureTemplateManager().get(LAB).orElseThrow(() -> new AssertionError("no ancient_lab template"));
	}

	private static int count(GameTestHelper helper, Block block) {
		return template(helper).filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), block).size();
	}

	@GameTest
	public void itGeneratesInTheWorld(GameTestHelper helper) {
		var registries = helper.getLevel().registryAccess();
		helper.assertTrue(registries.lookupOrThrow(Registries.STRUCTURE).get(ResourceKey.create(Registries.STRUCTURE, LAB)).isPresent(), "the structure");
		helper.assertTrue(registries.lookupOrThrow(Registries.STRUCTURE_SET).get(ResourceKey.create(Registries.STRUCTURE_SET, LAB)).isPresent(),
			"placed in the world by its structure set");
		helper.assertTrue(registries.lookupOrThrow(Registries.PROCESSOR_LIST).get(ResourceKey.create(Registries.PROCESSOR_LIST, LAB)).isPresent(),
			"weathered by its processors");
		helper.succeed();
	}

	@GameTest
	public void itHoldsBrokenComputersCablesAChestAndAWorkingPhotomask(GameTestHelper helper) {
		helper.assertValueEqual(count(helper, ModBlocks.PHOTOMASK), 1, "a working photomask among the ruins");
		helper.assertValueEqual(count(helper, ModBlocks.BROKEN_CASE), 3, "broken computers");
		helper.assertTrue(count(helper, ModBlocks.CABLE) >= 7, "cables along the floor");
		List<StructureTemplate.StructureBlockInfo> chests = template(helper).filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.CHEST);
		helper.assertValueEqual(chests.size(), 1, "a chest");
		helper.assertValueEqual(chests.getFirst().nbt().getStringOr("LootTable", ""), "modularcomputers:chests/ancient_lab", "of parts");
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void placedThePhotomaskWorksAndTheChestHasParts(GameTestHelper helper) {
		BlockPos at = helper.absolutePos(new BlockPos(0, 1, 0)).above(20);
		template(helper).placeInWorld(helper.getLevel(), at, at, new StructurePlaceSettings(), RandomSource.create(1), Block.UPDATE_CLIENTS);
		var placed = template(helper).filterBlocks(at, new StructurePlaceSettings(), ModBlocks.PHOTOMASK).getFirst().pos();
		helper.assertTrue(helper.getLevel().getBlockEntity(placed) instanceof PhotomaskBlockEntity, "a photomask block entity");
		var chest = template(helper).filterBlocks(at, new StructurePlaceSettings(), Blocks.CHEST).getFirst().pos();
		ChestBlockEntity box = (ChestBlockEntity) helper.getLevel().getBlockEntity(chest);
		box.unpackLootTable(null);
		helper.assertTrue(!box.isEmpty(), "the chest has parts in it");
		helper.succeed();
	}

	@GameTest
	public void aToolsmithSellsPhotomasks(GameTestHelper helper) {
		var trades = helper.getLevel().registryAccess().lookupOrThrow(Registries.VILLAGER_TRADE);
		var trade = trades.get(ResourceKey.create(Registries.VILLAGER_TRADE, ModularComputers.id("toolsmith/4/emerald_photomask")));
		helper.assertTrue(trade.isPresent(), "the trade");
		var expert = TagKey.create(Registries.VILLAGER_TRADE, net.minecraft.resources.Identifier.withDefaultNamespace("toolsmith/level_4"));
		helper.assertTrue(trades.get(expert).map(set -> set.stream().anyMatch(holder -> holder.is(trade.get().key()))).orElse(false),
			"among an expert toolsmith's trades");
		helper.succeed();
	}
}
