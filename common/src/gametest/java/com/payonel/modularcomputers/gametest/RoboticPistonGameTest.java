package com.payonel.modularcomputers.gametest;

import com.payonel.modularcomputers.block.ModBlocks;
import com.payonel.modularcomputers.block.RoboticPistonBlock;
import com.payonel.modularcomputers.block.RoboticPistonBlockEntity;
import com.payonel.modularcomputers.device.RoboticPiston;
import com.payonel.modularcomputers.runtime.Arguments;
import com.payonel.modularcomputers.runtime.Result;
import java.util.List;
import java.util.Map;
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

	/** Its events from now on (as a running program's would be: it takes data). */
	private static List<Object> ends(RoboticPistonBlockEntity piston) {
		piston.device().accepting(true);
		return new java.util.ArrayList<>();
	}

	/** Its next end, taken (null: none yet). */
	private static Object next(RoboticPistonBlockEntity piston) {
		return GameTests.withoutCall(piston.device().pull(new Arguments(List.of())).first());
	}

	/** A blocked end (the user's: one shape): the block that won't move (null: none to name), its face the world's way. */
	private static Map<String, Object> blocked(String method, String block, String face) {
		return Map.of("event", method, "ok", false, "why", "blocked", "block", block == null ? "" : block, "face", face);
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
		ends(piston);
		helper.setBlock(PISTON.west(), Blocks.OBSIDIAN);
		helper.assertTrue(Boolean.TRUE.equals(push(piston, "down").first()), "the cable's side: a command too");
		Object cable = next(piston);
		helper.assertTrue(java.util.Objects.equals(cable, blocked("push", "minecraft:air", "down")), "blocked by what's on it (no cable here): " + cable);
		helper.assertTrue(Boolean.TRUE.equals(push(piston, "west").first()), "a command: true, its end says how");
		helper.assertTrue(java.util.Objects.equals(next(piston), blocked("push", "minecraft:obsidian", "west")), "blocked by the obsidian, west");
		helper.assertBlockPresent(Blocks.OBSIDIAN, PISTON.west());
		helper.assertTrue(push(piston, "sideways").failed(), "no such side");
		helper.succeed();
	}

	/** What won't move needn't be next to it (the user's): the first down its line, as the game's own rules have it. */
	@GameTest
	public void obsidianDownTheLineIsTheBlockThatBlocksIt(GameTestHelper helper) {
		RoboticPistonBlockEntity piston = piston(helper, ModBlocks.ROBOTIC_PISTON);
		ends(piston);
		helper.setBlock(PISTON.east(), Blocks.STONE);
		helper.setBlock(PISTON.east(2), Blocks.DIRT);
		helper.setBlock(PISTON.east(3), Blocks.OBSIDIAN);
		piston.device().extend(new Arguments(List.of("east")));
		Object end = next(piston);
		helper.assertTrue(java.util.Objects.equals(end, blocked("extend", "minecraft:obsidian", "east")), "the obsidian three along: " + end);
		helper.assertBlockPresent(Blocks.STONE, PISTON.east());
		helper.succeed();
	}

	/** More than a piston moves: no one block is to blame, so none is named (the user's). */
	@GameTest
	public void moreThanAPistonMovesNamesNoBlock(GameTestHelper helper) {
		RoboticPistonBlockEntity piston = piston(helper, ModBlocks.ROBOTIC_PISTON);
		ends(piston);
		for (int i = 1; i <= net.minecraft.world.level.block.piston.PistonStructureResolver.MAX_PUSH_DEPTH + 1; i++) {
			helper.setBlock(PISTON.above(i), Blocks.STONE);
		}
		push(piston, "up");
		Object end = next(piston);
		helper.assertTrue(java.util.Objects.equals(end, blocked("push", null, "up")), "blocked, no block named: " + end);
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
		ends(piston);
		helper.assertTrue(Boolean.TRUE.equals(move(piston, "retract", "north").first()), "nothing out to retract: nothing to do, true (the user's: a lit lamp lit again)");
		helper.assertTrue(java.util.Objects.equals(next(piston), Map.of("event", "retract", "ok", true)), "and its end, ok");
		helper.assertTrue(!move(piston, "extend", "north").failed(), "extend");
		helper.assertTrue(!move(piston, "extend", "north").failed(), "extend again: it's out, true");
		helper.assertTrue(Boolean.TRUE.equals(push(piston, "north").first()), "a push with its arm out: it's out, true");
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
