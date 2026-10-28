package com.payonel.modularcomputers.gametest;

import com.payonel.modularcomputers.block.CaseBlock;
import com.payonel.modularcomputers.block.CaseBlockEntity;
import com.payonel.modularcomputers.block.ModBlocks;
import com.payonel.modularcomputers.device.FieldCard;
import com.payonel.modularcomputers.entity.BotGolem;
import com.payonel.modularcomputers.entity.EnergyField;
import com.payonel.modularcomputers.item.ModItems;
import com.payonel.modularcomputers.menu.CaseMenu;
import com.payonel.modularcomputers.runtime.Arguments;
import com.payonel.modularcomputers.runtime.Result;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * The energy field card (user design, 2026-10-24): it holds a pig in the air, where it doesn't fall,
 * moves it up and stops at a block in the way, and lets it go (it falls); a lit creeper held
 * doesn't blow; it reaches only so far; its computer stopping lets go; a golem holds itself and
 * is carried up.
 */
public class FieldCardGameTest {
	/** The case, facing south: its front +Z, its right as someone facing it sees it, west (-X). */
	private static final BlockPos CASE = new BlockPos(1, 1, 1);
	/** Where the pig starts: two in front of the case. */
	private static final BlockPos PIG = new BlockPos(1, 1, 3);
	/** How far a test lifts, in blocks, and how near counts as there. */
	private static final double LIFT = 3;
	private static final double NEAR = 0.2;

	private static Arguments args(Object... values) {
		return new Arguments(java.util.Arrays.asList(values));
	}

	/** A running case with a field card in, on a floor; its card. */
	private static FieldCard fieldCase(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
		return fieldCase(helper, CASE);
	}

	/** A running case with a field card in, at {@code at}, facing south; its card. */
	private static FieldCard fieldCase(GameTestHelper helper, BlockPos at) {
		helper.setBlock(at, ModBlocks.TIER3_CASE.defaultBlockState().setValue(CaseBlock.FACING, Direction.SOUTH));
		CaseBlockEntity computer = helper.getBlockEntity(at, CaseBlockEntity.class);
		computer.parts().setItem(CaseMenu.CPU_SLOT, new ItemStack(ModItems.TIER3_CPU));
		computer.parts().setItem(CaseMenu.RAM_SLOTS, new ItemStack(ModItems.TIER3_RAM));
		computer.parts().setItem(CaseMenu.EEPROM_SLOT, new ItemStack(ModItems.EEPROM));
		computer.parts().setItem(CaseMenu.UPS_SLOT, ModItems.TIER3_BATTERY.charged());
		computer.parts().setItem(CaseMenu.EXP_SLOTS + 1, new ItemStack(ModItems.ENERGY_FIELD_CARD));
		TestPrograms.flash(computer, board -> {
			while (true) {
				board.waitForInterrupt(1000, "nothing");
			}
		});
		computer.pressPower();
		return field(computer.board());
	}

	private static FieldCard field(com.payonel.modularcomputers.board.BoardAssembly board) {
		return board.cards().stream().filter(FieldCard.class::isInstance).map(FieldCard.class::cast).findFirst().orElseThrow();
	}

	/** Each event the card has, as it comes. */
	private static List<Object> events(GameTestHelper helper, FieldCard card) {
		List<Object> events = new ArrayList<>();
		helper.onEachTick(() -> {
			Object event;
			while ((event = card.pull(args()).first()) != null) {
				events.add(event);
			}
		});
		return events;
	}

	private static void same(GameTestHelper helper, Object got, Object expected, String what) {
		helper.assertTrue(java.util.Objects.equals(got, expected), what + ": expected " + expected + ", got " + got);
	}

	@GameTest(maxTicks = 200)
	public void itHoldsAPigUpWhereItDoesntFallThenLetsItGo(GameTestHelper helper) {
		FieldCard card = fieldCase(helper);
		Pig pig = helper.spawn(EntityTypes.PIG, PIG);
		List<Object> events = events(helper, card);
		double[] start = new double[1];
		double[] held = new double[1];
		helper.startSequence()
			.thenExecuteAfter(2, () -> {
				start[0] = pig.getY();
				Result caught = card.capture(args(pig.getStringUUID()));
				same(helper, caught.first(), "minecraft:pig", "what it holds: " + caught.error());
				helper.assertTrue(EnergyField.held(pig), "the pig is held");
				helper.assertTrue(!card.move(args(0.0, 0.0, LIFT)).failed(), "it moves");
			})
			.thenWaitUntil(() -> same(helper, events.isEmpty() ? null : events.getLast(), Map.of("event", "move", "ok", true), "its event"))
			.thenExecute(() -> held[0] = pig.getY())
			.thenExecuteAfter(20, () -> {
				helper.assertTrue(Math.abs(held[0] - start[0] - LIFT) < NEAR, "lifted " + LIFT + ": " + (held[0] - start[0]));
				helper.assertTrue(Math.abs(pig.getY() - held[0]) < NEAR, "and held there, not falling: " + pig.getY() + " vs " + held[0]);
				card.release(args());
				helper.assertTrue(!EnergyField.held(pig), "let go");
			})
			.thenWaitUntil(() -> helper.assertTrue(pig.onGround() && pig.getY() < held[0] - LIFT + NEAR, "it fell: " + pig.getY()))
			.thenSucceed();
	}

	@GameTest(maxTicks = 200)
	public void aBlockInTheWayStopsAMove(GameTestHelper helper) {
		FieldCard card = fieldCase(helper);
		Pig pig = helper.spawn(EntityTypes.PIG, PIG);
		for (int y = 1; y < 6; y++) {
			helper.setBlock(new BlockPos(1, y, 5), Blocks.STONE); // a wall two further out
		}
		List<Object> events = events(helper, card);
		helper.startSequence()
			.thenExecuteAfter(2, () -> {
				card.capture(args(pig.getStringUUID()));
				card.move(args(5.0)); // forward: out of the case's front (+Z), through the wall
			})
			.thenWaitUntil(() -> same(helper, events.isEmpty() ? null : events.getLast(), Map.of("event", "move", "ok", false, "why", "blocked: minecraft:stone"),
				"its event: what's in the way"))
			.thenExecute(() -> helper.assertTrue(pig.getZ() < helper.absolutePos(new BlockPos(1, 1, 5)).getZ(), "the pig's this side of the wall"))
			.thenSucceed();
	}

	/**
	 * A lift stopped by a ceiling says what stopped it: the block over what it holds, not the air round
	 * its middle (something tall: a zombie, whose middle is further from its head than a probe across).
	 */
	@GameTest(maxTicks = 200)
	public void aCeilingStopsALiftAndSaysWhatItIs(GameTestHelper helper) {
		FieldCard card = fieldCase(helper);
		var zombie = helper.spawn(EntityTypes.ZOMBIE, PIG);
		zombie.setNoAi(true);
		helper.setBlock(PIG.above(4), Blocks.STONE); // (a ceiling four up)
		List<Object> events = events(helper, card);
		helper.startSequence()
			.thenExecuteAfter(2, () -> {
				card.capture(args(zombie.getStringUUID()));
				card.move(args(0.0, 0.0, 5.0)); // (up, through the ceiling)
			})
			.thenWaitUntil(() -> same(helper, events.isEmpty() ? null : events.getLast(), Map.of("event", "move", "ok", false, "why", "blocked: minecraft:stone"),
				"its event: the ceiling"))
			.thenSucceed();
	}

	/** Something another field holds can't be captured: captured (not held, which means a robot's wait). */
	@GameTest(maxTicks = 200)
	public void whatAnotherFieldHoldsCantBeCapturedAndSaysSo(GameTestHelper helper) {
		FieldCard one = fieldCase(helper);
		FieldCard two = fieldCase(helper, CASE.east(3));
		Pig pig = helper.spawn(EntityTypes.PIG, PIG);
		helper.startSequence()
			.thenExecuteAfter(2, () -> {
				same(helper, one.capture(args(pig.getStringUUID())).first(), "minecraft:pig", "the first holds it");
				same(helper, two.capture(args(pig.getStringUUID())).error(), "captured", "the second can't");
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = 200)
	public void aLitCreeperHeldDoesntBlow(GameTestHelper helper) {
		FieldCard card = fieldCase(helper);
		Creeper creeper = helper.spawn(EntityTypes.CREEPER, PIG);
		helper.startSequence()
			.thenExecuteAfter(2, () -> {
				creeper.ignite();
				card.capture(args(creeper.getStringUUID()));
			})
			.thenExecuteAfter(60, () -> { // (a creeper's fuse is 30 ticks)
				helper.assertTrue(creeper.isAlive(), "it hasn't blown");
				helper.assertBlockPresent(Blocks.STONE, new BlockPos(1, 0, 3));
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = 100)
	public void itReachesOnlySoFar(GameTestHelper helper) {
		FieldCard card = fieldCase(helper);
		Pig pig = helper.spawn(EntityTypes.PIG, PIG);
		helper.startSequence()
			.thenExecuteAfter(2, () -> {
				same(helper, card.capture(args(0.0, 0.0, 4.0)).error(), "no entity", "a point with nothing near it");
				helper.assertTrue(card.capture(args(30.0)).error().startsWith("too far"), "a point past its reach: it senses no farther"); // (whatever's there: a neighbour's)
				card.capture(args(pig.getStringUUID()));
				helper.assertTrue(card.move(args(0.0, 0.0, FieldCard.RANGE + 1)).error().startsWith("too far"), "a move out of its reach");
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = 100)
	public void itsComputerStoppingLetsGo(GameTestHelper helper) {
		FieldCard card = fieldCase(helper);
		Pig pig = helper.spawn(EntityTypes.PIG, PIG);
		CaseBlockEntity computer = helper.getBlockEntity(CASE, CaseBlockEntity.class);
		helper.startSequence()
			.thenExecuteAfter(2, () -> card.capture(args(pig.getStringUUID())))
			.thenExecuteAfter(2, () -> computer.pressPower()) // off
			.thenWaitUntil(() -> helper.assertTrue(!EnergyField.held(pig), "let go"))
			.thenSucceed();
	}

	@GameTest(maxTicks = 200)
	public void aGolemHoldsItselfAndIsCarriedUp(GameTestHelper helper) {
		BotGolem golem = BotGolemGameTest.golem(helper, board -> {
			while (true) {
				board.waitForInterrupt(1000, "nothing");
			}
		});
		golem.board().parts().setItem(CaseMenu.EXP_SLOTS + 1, new ItemStack(ModItems.ENERGY_FIELD_CARD));
		golem.board().parts().setItem(CaseMenu.UPS_SLOT, ModItems.TIER3_BATTERY.charged());
		double[] start = new double[1];
		helper.startSequence()
			.thenExecuteAfter(1, () -> golem.board().pressPower())
			.thenExecuteAfter(3, () -> {
				FieldCard card = field(golem.board());
				start[0] = golem.getY();
				same(helper, card.capture(args(FieldCard.SELF)).first(), "modularcomputers:tier2_bot_golem", "it holds itself");
				// Farther than a case's field reaches: it carries its card with it.
				helper.assertTrue(!card.move(args(0.0, 0.0, LIFT)).failed(), "it moves");
			})
			.thenWaitUntil(() -> helper.assertTrue(golem.getY() - start[0] > LIFT - NEAR, "carried up: " + (golem.getY() - start[0])))
			.thenSucceed();
	}
}
