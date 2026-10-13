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

	/** A click on face {@code side}, at block-relative {@code x, y, z}. */
	private static net.minecraft.world.phys.BlockHitResult hit(GameTestHelper helper, net.minecraft.core.Direction side, double x, double y, double z) {
		BlockPos at = helper.absolutePos(POS);
		return new net.minecraft.world.phys.BlockHitResult(new net.minecraft.world.phys.Vec3(at.getX() + x, at.getY() + y, at.getZ() + z), side, at, false);
	}

	private static PhotomaskBlockEntity place(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.PHOTOMASK.defaultBlockState());
		return helper.getBlockEntity(POS, PhotomaskBlockEntity.class);
	}

	@GameTest
	public void crankingPrintsAWaferIntoChips(GameTestHelper helper) {
		PhotomaskBlockEntity mask = place(helper);
		net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel(); // a server player: menus open for it
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SILICON, 2));
		helper.useBlock(POS, player);
		helper.assertValueEqual(mask.items().getItem(PhotomaskBlockEntity.INPUT).getCount(), 2, "right-click with silicon puts it in");
		helper.assertTrue(player.getMainHandItem().isEmpty(), "out of the hand");

		helper.useBlock(POS, player, hit(helper, net.minecraft.core.Direction.UP, 0.5, 1.0, 0.5)); // its top opens its slots
		helper.assertTrue(player.containerMenu instanceof com.payonel.modularcomputers.menu.PhotomaskMenu, "its slots open, got " + player.containerMenu);
		player.closeContainer();
		helper.useBlock(POS, player, hit(helper, net.minecraft.core.Direction.NORTH, 0.05, 0.05, 0)); // the front, off the gear: opens too
		helper.assertTrue(player.containerMenu instanceof com.payonel.modularcomputers.menu.PhotomaskMenu, "off the gear, its slots open");
		player.closeContainer();
		player.setShiftKeyDown(true);
		helper.useBlock(POS, player, hit(helper, net.minecraft.core.Direction.NORTH, 0.5, 0.5, 0)); // crouching, even on the gear: opens
		helper.assertTrue(player.containerMenu instanceof com.payonel.modularcomputers.menu.PhotomaskMenu, "crouching, its slots open");
		player.closeContainer();
		player.setShiftKeyDown(false);
		helper.assertValueEqual(mask.progress(), 0, "and the gear hasn't turned");
		for (int turn = 0; turn < PhotomaskBlockEntity.WORK; turn++) {
			helper.useBlock(POS, player, hit(helper, net.minecraft.core.Direction.NORTH, 0.5, 0.5, 0)); // right-clicking the gear turns it
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
			.thenIdle(10)
			.thenExecute(() -> {
				helper.assertBlockProperty(POS, PhotomaskBlock.WORKING, true); // nothing left to print: it idles, still running
				int used = full - BatteryItem.charge(mask.items().getItem(PhotomaskBlockEntity.BATTERY)).units();
				int work = 2 * PhotomaskBlockEntity.WORK * PhotomaskBlockEntity.TURN_COST;
				helper.assertTrue(used > work, "a turn's power for each turn, and a little more idling: " + used);
				helper.assertTrue(used < work * 2, "idling draws very little: " + used);
				mask.items().setItem(PhotomaskBlockEntity.BATTERY, ItemStack.EMPTY);
			})
			.thenWaitUntil(() -> helper.assertBlockProperty(POS, PhotomaskBlock.WORKING, false)) // no power: it stops
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

	@GameTest(maxTicks = 200)
	public void aRunningComputerPowersItOverTheBus(GameTestHelper helper) {
		// A computer, a cable, and the photomask, its gear facing away from the cable.
		helper.setBlock(new BlockPos(1, 1, 1), ModBlocks.TIER2_CASE.defaultBlockState()
			.setValue(com.payonel.modularcomputers.block.AllInOneBlock.FACING, net.minecraft.core.Direction.SOUTH));
		var computer = helper.getBlockEntity(new BlockPos(1, 1, 1), com.payonel.modularcomputers.block.AllInOneBlockEntity.class);
		computer.parts().setItem(com.payonel.modularcomputers.menu.CaseMenu.CPU_SLOT, new ItemStack(ModItems.TIER2_CPU));
		computer.parts().setItem(com.payonel.modularcomputers.menu.CaseMenu.EEPROM_SLOT, new ItemStack(ModItems.EEPROM));
		computer.parts().setItem(com.payonel.modularcomputers.menu.CaseMenu.UPS_SLOT, ModItems.TIER1_BATTERY.charged());
		TestPrograms.flash(computer, TestPrograms.HELLO);
		helper.setBlock(new BlockPos(2, 1, 1), ModBlocks.CABLE);
		helper.setBlock(new BlockPos(3, 1, 1), ModBlocks.PHOTOMASK.defaultBlockState().setValue(PhotomaskBlock.FACING, net.minecraft.core.Direction.EAST));
		PhotomaskBlockEntity mask = helper.getBlockEntity(new BlockPos(3, 1, 1), PhotomaskBlockEntity.class);
		mask.items().setItem(PhotomaskBlockEntity.INPUT, new ItemStack(ModItems.SILICON));
		computer.pressPower();
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(computer.machine().board().bus().device("id:" + mask.id()).isPresent(), "on the computer's bus"))
			.thenWaitUntil(() -> helper.assertTrue(mask.device().power().powered(), "with power from it"))
			.thenWaitUntil(() -> helper.assertValueEqual(mask.items().getItem(PhotomaskBlockEntity.OUTPUT).getCount(), PhotomaskBlockEntity.CHIPS,
				"it prints without a battery"))
			.thenExecute(() -> computer.pressPower())
			.thenExecute(() -> helper.assertTrue(!mask.device().power().powered(), "off: no power"))
			.thenSucceed();
	}
}
