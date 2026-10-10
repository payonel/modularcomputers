package com.payonel.modularcomputers.gametest;

import com.payonel.modularcomputers.block.AllInOneBlock;
import com.payonel.modularcomputers.block.AllInOneBlockEntity;
import com.payonel.modularcomputers.block.ModBlocks;
import com.payonel.modularcomputers.machine.Machine;
import com.payonel.modularcomputers.machine.PartKind;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Server game tests for the all-in-one computer block. */
public class AllInOneGameTest {
	private static final BlockPos POS = new BlockPos(1, 1, 1);

	private static AllInOneBlockEntity place(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.ALL_IN_ONE.defaultBlockState().setValue(AllInOneBlock.FACING, Direction.SOUTH));
		return helper.getBlockEntity(POS, AllInOneBlockEntity.class);
	}

	@GameTest
	public void placedComputerIsOffWithSolderedParts(GameTestHelper helper) {
		Machine machine = place(helper).machine();
		helper.assertTrue(!machine.isRunning(), "a new computer should be off");
		helper.assertTrue(machine.display().isBlank(), "an off computer's display should be blank");
		for (PartKind kind : new PartKind[] {PartKind.CPU, PartKind.MEMORY, PartKind.STORAGE}) {
			helper.assertTrue(machine.hardware().find(kind).map(part -> part.soldered()).orElse(false), kind + " should be soldered in");
		}
		helper.succeed();
	}

	@GameTest
	public void sneakRightClickTogglesPower(GameTestHelper helper) {
		Machine machine = place(helper).machine();
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setShiftKeyDown(true);

		helper.useBlock(POS, player);
		helper.assertTrue(machine.isRunning(), "sneak + right-click should turn the computer on");
		helper.assertBlockProperty(POS, AllInOneBlock.RUNNING, true);
		helper.assertValueEqual(machine.display().row(0).strip(), Machine.GREETING, "first display row");

		helper.useBlock(POS, player);
		helper.assertTrue(!machine.isRunning(), "a second sneak + right-click should turn it off");
		helper.assertTrue(machine.display().isBlank(), "turning off should blank the display");
		helper.succeed();
	}

	/** A right-click on the computer's back face at texel (u, v), as seen from behind. Placed facing south. */
	private static BlockHitResult backClick(GameTestHelper helper, double u, double v) {
		// Facing south, the back faces north; from behind (looking south) right is west (-X).
		BlockPos abs = helper.absolutePos(POS);
		Vec3 location = new Vec3(abs.getX() + 1 - u / 16, abs.getY() + 1 - v / 16, abs.getZ());
		return new BlockHitResult(location, Direction.NORTH, abs, false);
	}

	@GameTest
	public void backPowerButtonTogglesWithoutSneaking(GameTestHelper helper) {
		AllInOneBlockEntity computer = place(helper);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);

		helper.useBlock(POS, player, backClick(helper, 11.5, 11.5));
		helper.assertTrue(computer.machine().isRunning(), "the back power button should turn the computer on");
		helper.assertBlockProperty(POS, AllInOneBlock.RUNNING, true);
		helper.assertValueEqual(computer.machine().display().row(0).strip(), Machine.GREETING, "first display row");

		helper.useBlock(POS, player, backClick(helper, 11.5, 11.5));
		helper.assertTrue(!computer.machine().isRunning(), "pressing it again should turn the computer off");
		helper.assertBlockProperty(POS, AllInOneBlock.RUNNING, false);
		helper.succeed();
	}

	@GameTest
	public void restOfTheBackDoesNotTogglePower(GameTestHelper helper) {
		AllInOneBlockEntity computer = place(helper);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		helper.useBlock(POS, player, backClick(helper, 4.5, 11.5));
		helper.useBlock(POS, player, backClick(helper, 11.5, 4.5));
		helper.assertTrue(!computer.machine().isRunning(), "only the button should toggle power");
		helper.succeed();
	}

	@GameTest
	public void plainRightClickDoesNotTogglePower(GameTestHelper helper) {
		Machine machine = place(helper).machine();
		helper.useBlock(POS, helper.makeMockPlayer(GameType.SURVIVAL));
		helper.assertTrue(!machine.isRunning(), "right-click without sneaking should only open the display");
		helper.succeed();
	}

	@GameTest
	public void powerStateSurvivesSaveAndLoad(GameTestHelper helper) {
		AllInOneBlockEntity computer = place(helper);
		computer.togglePower();
		var registries = helper.getLevel().registryAccess();
		BlockState state = computer.getBlockState();
		BlockEntity loaded = BlockEntity.loadStatic(computer.getBlockPos(), state, computer.saveWithFullMetadata(registries), registries);
		helper.assertTrue(loaded instanceof AllInOneBlockEntity reloaded && reloaded.machine().isRunning(),
			"a running computer should still be running after reload");
		helper.succeed();
	}

	@GameTest
	public void dropsItself(GameTestHelper helper) {
		AllInOneBlockEntity computer = place(helper);
		var drops = Block.getDrops(computer.getBlockState(), helper.getLevel(), helper.absolutePos(POS), computer);
		helper.assertTrue(drops.size() == 1 && drops.getFirst().is(ModBlocks.ALL_IN_ONE.asItem()),
			"should drop one all-in-one computer, got " + drops);
		helper.succeed();
	}
}
