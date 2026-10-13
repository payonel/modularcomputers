package com.payonel.modularcomputers.gametest;

import com.payonel.modularcomputers.block.ModBlocks;
import com.payonel.modularcomputers.block.PhotomaskBlock;
import com.payonel.modularcomputers.block.PhotomaskBlockEntity;
import com.payonel.modularcomputers.item.BatteryItem;
import com.payonel.modularcomputers.item.ModItems;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;

/**
 * The photomask prints chips from silicon (user design, 2026-10-12): a wafer takes WORK turns of
 * its crank (right-click) and gives CHIPS chips; a charged battery turns it by itself.
 */
public class PhotomaskGameTest {
	private static final BlockPos POS = new BlockPos(1, 1, 1);

	private static PhotomaskBlockEntity place(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.PHOTOMASK.defaultBlockState());
		return helper.getBlockEntity(POS, PhotomaskBlockEntity.class);
	}

	@GameTest
	public void crankingPrintsAWaferIntoChips(GameTestHelper helper) {
		PhotomaskBlockEntity mask = place(helper);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SILICON, 2));
		helper.useBlock(POS, player);
		helper.assertValueEqual(mask.items().getItem(PhotomaskBlockEntity.INPUT).getCount(), 2, "right-click with silicon puts it in");
		helper.assertTrue(player.getMainHandItem().isEmpty(), "out of the hand");

		for (int turn = 0; turn < PhotomaskBlockEntity.WORK; turn++) {
			helper.useBlock(POS, player); // an empty hand turns the crank
		}
		helper.assertValueEqual(mask.items().getItem(PhotomaskBlockEntity.OUTPUT).getCount(), PhotomaskBlockEntity.CHIPS, "a wafer's chips");
		helper.assertTrue(mask.items().getItem(PhotomaskBlockEntity.OUTPUT).is(ModItems.CHIP), "chips");
		helper.assertValueEqual(mask.items().getItem(PhotomaskBlockEntity.INPUT).getCount(), 1, "one wafer used");
		helper.assertValueEqual(mask.progress(), 0, "ready for the next");
		helper.succeed();
	}

	@GameTest
	public void crankingWithNothingInItDoesNothing(GameTestHelper helper) {
		PhotomaskBlockEntity mask = place(helper);
		mask.crank();
		helper.assertValueEqual(mask.progress(), 0, "no silicon: no work");
		mask.items().setItem(PhotomaskBlockEntity.INPUT, new ItemStack(ModItems.SILICON));
		mask.items().setItem(PhotomaskBlockEntity.OUTPUT, new ItemStack(ModItems.CHIP, 62));
		mask.crank();
		helper.assertValueEqual(mask.progress(), 0, "no room for a wafer's chips: no work");
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void aChargedBatteryTurnsItByItself(GameTestHelper helper) {
		PhotomaskBlockEntity mask = place(helper);
		ItemStack battery = ModItems.TIER1_BATTERY.charged();
		int full = BatteryItem.charge(battery).units();
		mask.items().setItem(PhotomaskBlockEntity.BATTERY, battery);
		mask.items().setItem(PhotomaskBlockEntity.INPUT, new ItemStack(ModItems.SILICON, 2));
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertBlockProperty(POS, PhotomaskBlock.WORKING, true))
			.thenWaitUntil(() -> helper.assertValueEqual(mask.items().getItem(PhotomaskBlockEntity.OUTPUT).getCount(),
				2 * PhotomaskBlockEntity.CHIPS, "both wafers printed"))
			.thenWaitUntil(() -> helper.assertBlockProperty(POS, PhotomaskBlock.WORKING, false))
			.thenExecute(() -> helper.assertValueEqual(BatteryItem.charge(mask.items().getItem(PhotomaskBlockEntity.BATTERY)).units(),
				full - 2 * PhotomaskBlockEntity.WORK * PhotomaskBlockEntity.TURN_COST, "a turn's power for each turn"))
			.thenSucceed();
	}

	@GameTest
	public void brokenItKeepsWhatsInIt(GameTestHelper helper) {
		PhotomaskBlockEntity mask = place(helper);
		mask.items().setItem(PhotomaskBlockEntity.INPUT, new ItemStack(ModItems.SILICON, 5));
		var drops = Block.getDrops(mask.getBlockState(), helper.getLevel(), mask.getBlockPos(), mask);
		helper.assertValueEqual(drops.size(), 1, "one item");
		helper.assertTrue(drops.getFirst().has(DataComponents.CONTAINER), "with its silicon in it");
		helper.succeed();
	}
}
