package com.payonel.modularcomputers.gametest;

import com.payonel.modularcomputers.block.ModBlocks;
import com.payonel.modularcomputers.block.RoboticPistonBlock;
import com.payonel.modularcomputers.block.RoboticPistonBlockEntity;
import com.payonel.modularcomputers.device.RoboticPiston;
import com.payonel.modularcomputers.runtime.Arguments;
import com.payonel.modularcomputers.runtime.Result;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** The robotic piston pushes on any face but its cable's, as a real piston does (user design, 2026-10-13). */
public class RoboticPistonGameTest {
	private static final BlockPos PISTON = new BlockPos(2, 2, 2);

	private static RoboticPistonBlockEntity piston(GameTestHelper helper, Block block) {
		helper.setBlock(PISTON, block.defaultBlockState().setValue(RoboticPistonBlock.FACING, Direction.DOWN)); // its cable below
		return helper.getBlockEntity(PISTON, RoboticPistonBlockEntity.class);
	}

	private static Result push(RoboticPistonBlockEntity piston, String side) {
		return piston.device().push(new Arguments(List.of(side)));
	}

	@GameTest(maxTicks = 60)
	public void itPushesABlockAndItsArmComesOutAndGoesBack(GameTestHelper helper) {
		RoboticPistonBlockEntity piston = piston(helper, ModBlocks.ROBOTIC_PISTON);
		helper.setBlock(PISTON.east(), Blocks.STONE);
		Result pushed = push(piston, "east");
		helper.assertTrue(!pushed.failed() && Boolean.TRUE.equals(pushed.first()), "a push: true");
		helper.startSequence()
			.thenWaitUntil(() -> {
				helper.assertBlockPresent(Blocks.STONE, PISTON.east(2));
				helper.assertBlockPresent(ModBlocks.ROBOTIC_PISTON_ARM, PISTON.east());
			})
			.thenWaitUntil(() -> helper.assertBlockPresent(Blocks.AIR, PISTON.east())) // the arm goes back
			.thenExecute(() -> helper.assertBlockPresent(Blocks.STONE, PISTON.east(2))) // what it pushed stays
			.thenSucceed();
	}

	@GameTest
	public void theCablesSideAndWhatWontMoveCantBePushed(GameTestHelper helper) {
		RoboticPistonBlockEntity piston = piston(helper, ModBlocks.ROBOTIC_PISTON);
		helper.setBlock(PISTON.west(), Blocks.OBSIDIAN);
		helper.assertValueEqual(push(piston, "down").error(), RoboticPiston.CANNOT_PUSH, "the cable's side");
		helper.assertValueEqual(push(piston, "west").error(), RoboticPiston.CANNOT_PUSH, "obsidian");
		helper.assertBlockPresent(Blocks.OBSIDIAN, PISTON.west());
		helper.assertTrue(push(piston, "sideways").failed(), "no such side");
		helper.succeed();
	}

	private static Result move(RoboticPistonBlockEntity piston, String method, String side) {
		Arguments args = new Arguments(List.of(side));
		return method.equals("extend") ? piston.device().extend(args) : piston.device().retract(args);
	}

	@GameTest(maxTicks = 80)
	public void anExtendedArmStaysOutUntilRetracted(GameTestHelper helper) {
		RoboticPistonBlockEntity piston = piston(helper, ModBlocks.ROBOTIC_STICKY_PISTON);
		helper.setBlock(PISTON.north(), Blocks.STONE);
		helper.assertValueEqual(move(piston, "retract", "north").error(), RoboticPiston.NOT_EXTENDED, "nothing out to retract");
		helper.assertTrue(!move(piston, "extend", "north").failed(), "extend");
		helper.assertTrue(!move(piston, "extend", "north").failed(), "extend again: it's out, true");
		helper.assertValueEqual(push(piston, "north").error(), RoboticPiston.CANNOT_PUSH, "a push needs its arm in");
		var registries = helper.getLevel().registryAccess();
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertBlockPresent(ModBlocks.ROBOTIC_PISTON_ARM, PISTON.north()))
			.thenIdle(RoboticPistonBlock.ARM_TICKS * 3)
			.thenExecute(() -> {
				helper.assertBlockPresent(ModBlocks.ROBOTIC_PISTON_ARM, PISTON.north()); // still out
				helper.assertBlockPresent(Blocks.STONE, PISTON.north(2));
				// It stays out through a save and a load.
				RoboticPistonBlockEntity loaded = (RoboticPistonBlockEntity) net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
					piston.getBlockPos(), piston.getBlockState(), piston.saveWithFullMetadata(registries), registries);
				helper.getLevel().setBlockEntity(loaded);
			})
			.thenIdle(RoboticPistonBlock.ARM_TICKS * 2)
			.thenExecute(() -> {
				helper.assertBlockPresent(ModBlocks.ROBOTIC_PISTON_ARM, PISTON.north());
				RoboticPistonBlockEntity loaded = helper.getBlockEntity(PISTON, RoboticPistonBlockEntity.class);
				helper.assertTrue(!move(loaded, "retract", "north").failed(), "retract");
			})
			.thenWaitUntil(() -> helper.assertBlockPresent(Blocks.STONE, PISTON.north())) // a sticky one pulls it back
			.thenExecute(() -> helper.assertBlockPresent(Blocks.AIR, PISTON.north(2)))
			.thenSucceed();
	}

	@GameTest(maxTicks = 60)
	public void aStickyOnePullsBackWhatItPushed(GameTestHelper helper) {
		RoboticPistonBlockEntity piston = piston(helper, ModBlocks.ROBOTIC_STICKY_PISTON);
		helper.setBlock(PISTON.above(), Blocks.STONE);
		helper.assertTrue(!push(piston, "up").failed(), "a push");
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertBlockPresent(Blocks.STONE, PISTON.above(2)))
			.thenWaitUntil(() -> helper.assertBlockPresent(Blocks.STONE, PISTON.above())) // pulled back
			.thenSucceed();
	}
}
