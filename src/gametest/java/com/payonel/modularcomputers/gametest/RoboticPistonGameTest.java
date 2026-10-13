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
