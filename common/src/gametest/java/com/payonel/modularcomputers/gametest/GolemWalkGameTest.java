package com.payonel.modularcomputers.gametest;

import com.payonel.modularcomputers.device.RoboticsDevice;
import com.payonel.modularcomputers.entity.BotGolem;
import com.payonel.modularcomputers.runtime.Arguments;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;

/**
 * A golem's walks and its position (user design, 2026-10-25): without pathfinding (the default) a walk
 * goes straight, whole blocks, onto a block's middle exactly, without turning (sideways, backwards),
 * stepping freely within its elevation, jumping a block at a time only up toward its height and
 * dropping at most three only down toward it, never into lava; with pathfinding it finds its way,
 * and still ends exactly, facing as it was. Its position is from its origin (its first tick, setPosition,
 * each dimension's own), kept through reboots and shoves, lost when it's picked up.
 */
public class GolemWalkGameTest {
	/** Where the golem stands (BotGolemGameTest's start), facing east (+X: its forward). */
	private static final BlockPos AT = new BlockPos(1, 1, 1);

	private static Arguments args(Object... values) {
		return new Arguments(java.util.Arrays.asList(values));
	}

	private static void same(GameTestHelper helper, Object got, Object expected, String what) {
		helper.assertTrue(Objects.equals(got, expected), what + ": expected " + expected + ", got " + got);
	}

	/** Where it is now, as a program reads it. */
	@SuppressWarnings("unchecked")
	private static Map<String, Object> position(BotGolem golem) {
		return (Map<String, Object>) golem.robotics().position(args()).first();
	}

	/** Where it really is (get's, live), not its plan (position's). */
	@SuppressWarnings("unchecked")
	private static Map<String, Object> real(BotGolem golem) {
		return (Map<String, Object>) ((Map<String, Object>) golem.robotics().get(args()).first()).get("position");
	}

	private static Map<String, Object> at(long forward, long right, Object up) {
		return Map.of("forward", forward, "right", right, "up", up, "heading", 0L);
	}

	/** Each robotics event, as it comes. */
	private static List<Object> events(GameTestHelper helper, BotGolem golem) {
		List<Object> events = new java.util.ArrayList<>();
		helper.onEachTick(() -> {
			Object event;
			while ((event = golem.robotics().pull(args()).first()) != null) {
				events.add(event);
			}
		});
		return events;
	}

	/** Its last event, once it has one more than {@code before}. */
	private static Object last(List<Object> events) {
		return events.isEmpty() ? null : events.getLast();
	}

	private static Map<String, Object> blocked(String why) {
		return Map.of("event", "walk", "ok", false, "why", why);
	}

	private static final Map<String, Object> ARRIVED = Map.of("event", "walk", "ok", true);
	private static final Map<String, Object> WALKED_TO = Map.of("event", "walkTo", "ok", true);
	private static final Map<String, Object> FLOWN_TO = Map.of("event", "flyTo", "ok", true);

	/** A walk from where it is, and the event it ends in, then a check. */
	private static void walks(GameTestHelper helper, Object[] walk, Map<String, Object> ending, Map<String, Object> position) {
		BotGolem golem = GolemFlightGameTest.golem(helper, false);
		List<Object> events = events(helper, golem);
		helper.startSequence()
			.thenExecuteAfter(3, () -> helper.assertTrue(!golem.robotics().walk(args(walk)).failed(), "it starts"))
			.thenWaitUntil(() -> helper.assertTrue(!events.isEmpty(), "its walk ended"))
			.thenExecute(() -> {
				same(helper, last(events), ending, "how it ended");
				same(helper, position(golem), position, "where");
				same(helper, golem.getYRot(), BotGolemGameTest.EAST, "facing as it was");
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = 200)
	public void aWalkEndsExactlyOnTheBlocksMiddleFacingAsItWas(GameTestHelper helper) {
		walks(helper, new Object[] {3L}, ARRIVED, at(3, 0, 0L));
	}

	@GameTest(maxTicks = 200)
	public void itWalksSidewaysAndBackwardsWithoutTurning(GameTestHelper helper) {
		BotGolem golem = GolemFlightGameTest.golem(helper, false);
		List<Object> events = events(helper, golem);
		helper.startSequence()
			.thenExecuteAfter(3, () -> golem.robotics().walk(args(0L, 2L)))
			.thenExecuteAfter(1, () -> same(helper, golem.getYRot(), BotGolemGameTest.EAST, "not turning as it goes"))
			.thenWaitUntil(() -> same(helper, last(events), ARRIVED, "to its right"))
			.thenExecute(() -> {
				events.clear();
				golem.robotics().walk(args(-1L));
			})
			.thenWaitUntil(() -> same(helper, last(events), ARRIVED, "backwards"))
			.thenExecute(() -> {
				same(helper, position(golem), at(-1, 2, 0L), "back one, two to its right");
				Vec3 rel = golem.position().subtract(helper.absoluteVec(Vec3.ZERO));
				helper.assertTrue(rel.x % 1 == 0.5 && Math.abs(rel.z % 1) == 0.5, "on a block's middle: " + rel);
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = 200)
	public void aLevelWalkStepsOntoASlabButNotOnUpABlock(GameTestHelper helper) {
		helper.setBlock(AT.east(), Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
		helper.setBlock(AT.east(2), Blocks.STONE);
		walks(helper, new Object[] {3L}, blocked("too high: 1"), at(1, 0, 0.5));
	}

	@GameTest(maxTicks = 200)
	public void upOneJumpsABlockAndAJumpItCantMakeIsBlockedAtTheHeight(GameTestHelper helper) {
		helper.setBlock(AT.east(2), Blocks.STONE);
		helper.setBlock(AT.east(3), Blocks.STONE);
		walks(helper, new Object[] {3L, 0L, 1L}, ARRIVED, at(3, 0, 1L));
	}

	@GameTest(maxTicks = 200)
	public void upOneOnTheFlatEndsBlockedNotAtTheHeight(GameTestHelper helper) {
		walks(helper, new Object[] {2L, 0L, 1L}, blocked("height: -1"), at(2, 0, 0L));
	}

	@GameTest(maxTicks = 200)
	public void aLevelWalkStopsAtAnEdgeAndDownThreeDropsOffIt(GameTestHelper helper) {
		// It stands on a platform three up; the floor's below its edge.
		for (int y = 1; y <= 3; y++) {
			helper.setBlock(AT.atY(y), Blocks.STONE);
		}
		BotGolem golem = GolemFlightGameTest.golem(helper, false);
		List<Object> events = events(helper, golem);
		golem.setPos(golem.getX(), golem.getY() + 3, golem.getZ());
		helper.startSequence()
			.thenExecuteAfter(3, () -> {
				golem.robotics().setPosition(args()); // (its origin up there)
				golem.robotics().walk(args(2L));
			})
			.thenWaitUntil(() -> same(helper, last(events), blocked("too low: 3"), "at the edge"))
			.thenExecute(() -> {
				same(helper, position(golem), at(0, 0, 0L), "still on its platform");
				events.clear();
				golem.robotics().walk(args(2L, 0L, -3L));
			})
			.thenWaitUntil(() -> same(helper, last(events), ARRIVED, "dropped"))
			.thenExecute(() -> same(helper, position(golem), at(2, 0, -3L), "three down"))
			.thenSucceed();
	}

	@GameTest(maxTicks = 200)
	public void itWontDropFourAtOnce(GameTestHelper helper) {
		for (int y = 1; y <= 4; y++) {
			helper.setBlock(AT.atY(y), Blocks.STONE);
		}
		BotGolem golem = GolemFlightGameTest.golem(helper, false);
		List<Object> events = events(helper, golem);
		golem.setPos(golem.getX(), golem.getY() + 4, golem.getZ());
		helper.startSequence()
			.thenExecuteAfter(3, () -> golem.robotics().walk(args(1L, 0L, -4L)))
			.thenWaitUntil(() -> same(helper, last(events), blocked("too low"), "four is more than a player falls unhurt"))
			.thenSucceed();
	}

	@GameTest(maxTicks = 200)
	public void itWontWalkIntoLava(GameTestHelper helper) {
		helper.setBlock(AT.east(2), Blocks.LAVA);
		walks(helper, new Object[] {3L}, blocked("blocked: minecraft:lava front"), at(1, 0, 0L));
	}

	@GameTest(maxTicks = 300)
	public void itsPositionOutlastsARebootAndAShoveAndWalkToGoesBackToItsOrigin(GameTestHelper helper) {
		BotGolem golem = GolemFlightGameTest.golem(helper, false);
		List<Object> events = events(helper, golem);
		helper.startSequence()
			.thenExecuteAfter(3, () -> golem.robotics().walk(args(2L, 1L)))
			.thenWaitUntil(() -> same(helper, last(events), ARRIVED, "walked"))
			.thenExecute(() -> {
				golem.machine().pressPower(); // (off)
			})
			.thenExecuteAfter(2, () -> golem.machine().pressPower()) // (and on: a reboot)
			.thenExecuteAfter(3, () -> {
				same(helper, position(golem), at(2, 1, 0L), "kept through a reboot");
				golem.setPos(golem.getX() + 0.3, golem.getY(), golem.getZ()); // (shoved)
				double shoved = ((Number) real(golem).get("forward")).doubleValue();
				helper.assertTrue(Math.abs(shoved - 2.3) < 1e-6, "a shove counted: " + shoved);
				same(helper, position(golem), at(2, 1, 0L), "its plan, where its walk left it");
				events.clear();
				helper.assertTrue(!golem.robotics().walkTo(args(0L, 0L)).failed(), "home");
			})
			.thenWaitUntil(() -> same(helper, last(events), WALKED_TO, "back"))
			.thenExecute(() -> same(helper, position(golem), at(0, 0, 0L), "at its origin exactly"))
			.thenSucceed();
	}

	@GameTest
	public void setPositionCentresItFacingARightAngleAndPickingItUpLosesItsOrigin(GameTestHelper helper) {
		BotGolem golem = GolemFlightGameTest.golem(helper, false);
		golem.setPos(golem.getX() + 0.2, golem.getY(), golem.getZ() - 0.3);
		golem.setYRot(BotGolemGameTest.EAST + 30);
		golem.robotics().setPosition(args());
		same(helper, golem.getYRot(), BotGolemGameTest.EAST, "a right angle");
		Vec3 rel = golem.position().subtract(helper.absoluteVec(Vec3.ZERO));
		helper.assertTrue(Math.abs(rel.x - 1.5) < 1e-9 && Math.abs(rel.z - 1.5) < 1e-9, "on its block's middle: " + rel);
		same(helper, position(golem), at(0, 0, 0L), "its origin");
		golem.setPos(golem.getX() + 2, golem.getY(), golem.getZ());
		// Picked up and set down again: an item keeps no origin; its new origin is where it's set.
		BotGolem again = com.payonel.modularcomputers.entity.ModEntities.TIER2_BOT_GOLEM.create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.SPAWN_ITEM_USE);
		again.fromItem(golem.asItem());
		again.snapTo(golem.getX(), golem.getY(), golem.getZ(), BotGolemGameTest.EAST, 0);
		same(helper, position(again), at(0, 0, 0L), "a new origin where it was set down");
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void eachDimensionHasItsOwnOrigin(GameTestHelper helper) {
		BotGolem golem = GolemFlightGameTest.golem(helper, false);
		List<Object> events = events(helper, golem);
		helper.startSequence()
			.thenExecuteAfter(3, () -> golem.robotics().walk(args(2L)))
			.thenWaitUntil(() -> same(helper, last(events), ARRIVED, "walked"))
			.thenExecute(() -> {
				var nether = helper.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
				var there = (BotGolem) golem.teleport(new net.minecraft.world.level.portal.TeleportTransition(nether, new Vec3(0.5, 100, 0.5), Vec3.ZERO,
					BotGolemGameTest.EAST, 0, net.minecraft.world.level.portal.TeleportTransition.DO_NOTHING));
				helper.assertTrue(there != null, "it went");
				same(helper, position(there), at(0, 0, 0L), "a new origin, there");
				var back = (BotGolem) there.teleport(new net.minecraft.world.level.portal.TeleportTransition(helper.getLevel(),
					helper.absoluteVec(Vec3.atBottomCenterOf(AT.east(2))), Vec3.ZERO, BotGolemGameTest.EAST, 0, net.minecraft.world.level.portal.TeleportTransition.DO_NOTHING));
				same(helper, position(back), at(2, 0, 0L), "its old one, back here");
				back.discard();
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = 400)
	public void withPathfindingItGoesRoundAWallAndStillEndsExactlyFacingAsItWas(GameTestHelper helper) {
		helper.setBlock(AT.east(2), Blocks.STONE);
		helper.setBlock(AT.east(2).above(), Blocks.STONE);
		BotGolem golem = GolemFlightGameTest.golem(helper, false);
		List<Object> events = events(helper, golem);
		helper.startSequence()
			.thenExecuteAfter(3, () -> {
				golem.robotics().setPathfinding(args(true));
				same(helper, ((Map<?, ?>) golem.robotics().get(args()).first()).get("pathfinding"), true, "on");
				golem.robotics().walk(args(4L));
			})
			.thenWaitUntil(() -> helper.assertTrue(!events.isEmpty(), "ended"))
			.thenExecute(() -> {
				same(helper, last(events), ARRIVED, "round the wall");
				same(helper, position(golem), at(4, 0, 0L), "exactly");
				same(helper, golem.getYRot(), BotGolemGameTest.EAST, "facing as it was");
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = 200)
	public void flyToGoesToAPointFromItsOrigin(GameTestHelper helper) {
		BotGolem golem = GolemFlightGameTest.golem(helper, false);
		List<Object> events = events(helper, golem);
		helper.startSequence()
			.thenExecuteAfter(3, () -> golem.robotics().flyTo(args(2L, -1L, 3L)))
			.thenWaitUntil(() -> same(helper, last(events), FLOWN_TO, "flown"))
			.thenExecute(() -> same(helper, position(golem), at(2, -1, 3L), "there, from its origin"))
			.thenSucceed();
	}

	/**
	 * setPosition with a place (the user's: a GPS's fix, say): position gives exactly those numbers, and its walks and walkTo go by that frame. Not whole, or not a
	 * right angle, it's refused.
	 */
	@GameTest(maxTicks = 200)
	public void setPositionGivesItsPositionAKnownPlacesFrame(GameTestHelper helper) {
		BotGolem golem = GolemFlightGameTest.golem(helper, false);
		List<Object> events = events(helper, golem);
		helper.startSequence()
			.thenExecuteAfter(3, () -> {
				helper.assertTrue(golem.robotics().setPosition(args(1.5)).failed(), "half a block refused");
				helper.assertTrue(golem.robotics().setPosition(args(0L, 0L, 0L, 45L)).failed(), "45 degrees refused");
				helper.assertTrue(!golem.robotics().setPosition(args(12L, -3L, 0L, 90L)).failed(), "set");
				same(helper, position(golem), Map.of("forward", 12L, "right", -3L, "up", 0L, "heading", 90L), "where it said it is");
				golem.robotics().walk(args(2L)); // (its own forward: the origin's right, as it faces 90)
			})
			.thenWaitUntil(() -> same(helper, last(events), ARRIVED, "walked"))
			.thenExecute(() -> {
				same(helper, position(golem), Map.of("forward", 12L, "right", -1L, "up", 0L, "heading", 90L), "two to the origin's right");
				events.clear();
				golem.robotics().walkTo(args(12L, -3L, 0L));
			})
			.thenWaitUntil(() -> same(helper, last(events), WALKED_TO, "back"))
			.thenExecute(() -> same(helper, position(golem), Map.of("forward", 12L, "right", -3L, "up", 0L, "heading", 90L), "where it set it"))
			.thenSucceed();
	}

	/**
	 * A stop squares nothing (the user's): the next walk goes on from exactly where it stopped, and must
	 * end squared, so a whole block on from between blocks is refused, and the rest of the way ends on
	 * the block's middle the stopped walk was going to.
	 */
	@GameTest(maxTicks = 200)
	public void afterAStopTheNextWalkGoesOnFromExactlyThereAndEndsSquared(GameTestHelper helper) {
		BotGolem golem = GolemFlightGameTest.golem(helper, false);
		List<Object> events = events(helper, golem);
		double[] stoppedAt = {0};
		helper.startSequence()
			.thenExecuteAfter(3, () -> golem.robotics().walk(args(5L)))
			.thenExecuteAfter(12, () -> {
				golem.robotics().stop(args());
				stoppedAt[0] = ((Number) real(golem).get("forward")).doubleValue();
				helper.assertTrue(stoppedAt[0] > 0 && stoppedAt[0] < 5, "stopped on the way: " + stoppedAt[0]);
				double from = ((Number) position(golem).get("forward")).doubleValue();
				helper.assertTrue(Math.abs(from - stoppedAt[0]) < RoboticsDevice.SQUARED, "its next walk from exactly there: " + from);
				double between = stoppedAt[0] - Math.floor(stoppedAt[0]);
				if (between >= RoboticsDevice.SQUARED && 1 - between >= RoboticsDevice.SQUARED) {
					String refused = golem.robotics().walk(args(1L)).error();
					helper.assertTrue(refused != null && refused.startsWith(RoboticsDevice.NOT_AN_INTEGER + "forward"), "a block on from between blocks: " + refused);
				}
				events.clear();
				golem.robotics().walk(args(5 - stoppedAt[0])); // (the rest of the way)
			})
			.thenWaitUntil(() -> same(helper, last(events), ARRIVED, "walked the rest"))
			.thenExecute(() -> {
				same(helper, position(golem), at(5, 0, 0L), "on the block's middle it was going to");
				same(helper, real(golem), at(5, 0, 0L), "really there");
			})
			.thenSucceed();
	}

	/**
	 * A piston shoves it, on its legs too (the user's): it's moved a block and says so, once the push
	 * is over; its way back is in the piston's arm, so it's stuck there and says why, and holds (no
	 * retries); the arm gone, a walk() takes it back.
	 */
	@GameTest(maxTicks = 100)
	public void aPistonShovesItOnItsLegsAndItSaysSo(GameTestHelper helper) {
		BotGolem golem = GolemFlightGameTest.golem(helper, false);
		List<Object> events = events(helper, golem);
		BlockPos piston = AT.west();
		double[] from = {0};
		helper.startSequence()
			.thenExecuteAfter(3, () -> {
				helper.setBlock(piston, com.payonel.modularcomputers.block.ModBlocks.ROBOTIC_PISTON.defaultBlockState()
					.setValue(com.payonel.modularcomputers.block.RoboticPistonBlock.FACING, net.minecraft.core.Direction.DOWN)); // (its cable below)
				from[0] = golem.getX();
				var arm = helper.getBlockEntity(piston, com.payonel.modularcomputers.block.RoboticPistonBlockEntity.class);
				helper.assertTrue(!arm.device().push(args("east")).failed(), "it pushes");
			})
			.thenWaitUntil(() -> helper.assertTrue(golem.getX() - from[0] > 0.9, "shoved a block: " + (golem.getX() - from[0])))
			.thenWaitUntil(() -> helper.assertTrue(events.stream().anyMatch(e -> e instanceof Map<?, ?> m && "notice".equals(m.get("event")) && "shoved".equals(m.get("what"))),
				"it says so: " + events))
			.thenWaitUntil(() -> helper.assertTrue(events.stream().anyMatch(e -> e instanceof Map<?, ?> m && "notice".equals(m.get("event")) && "stuck".equals(m.get("what"))),
				"stuck, its way back in the arm: " + events))
			.thenExecute(() -> {
				helper.assertTrue(events.stream().noneMatch(e -> e instanceof Map<?, ?> m && m.get("drift") instanceof Map<?, ?> d && ((Number) d.get("forward")).doubleValue() < 0.9),
					"said once the push was over: " + events);
				events.clear();
			})
			.thenWaitUntil(() -> helper.assertTrue(!helper.getBlockEntity(piston, com.payonel.modularcomputers.block.RoboticPistonBlockEntity.class).device().busy(), "the arm back"))
			.thenExecuteAfter(2, () -> helper.assertTrue(!golem.robotics().walk(args()).failed(), "back to where its last command left it"))
			.thenWaitUntil(() -> helper.assertTrue(events.contains(ARRIVED) && Math.abs(golem.getX() - from[0]) < 0.06, "walked back: " + (golem.getX() - from[0]) + " " + events))
			.thenSucceed();
	}

	/** A block gone from under it: it falls, its plan follows it down, it says so, and its next walk is from there. */
	@GameTest(maxTicks = 200)
	public void aFallItDidntAskForMovesItsPlanDownAndItSaysSo(GameTestHelper helper) {
		BlockPos ledge = AT.east();
		helper.setBlock(ledge, Blocks.STONE);
		BotGolem golem = GolemFlightGameTest.golem(helper, false);
		List<Object> events = events(helper, golem);
		helper.startSequence()
			.thenExecuteAfter(3, () -> golem.robotics().walk(args(1L, 0L, 1L))) // (up onto the ledge)
			.thenWaitUntil(() -> same(helper, last(events), ARRIVED, "up"))
			.thenExecute(() -> {
				same(helper, position(golem), at(1, 0, 1L), "on the ledge");
				events.clear();
				helper.setBlock(ledge, Blocks.AIR); // (gone from under it)
			})
			.thenWaitUntil(() -> same(helper, last(events), Map.of("event", "notice", "what", "fell", "by", 1L), "it says it fell"))
			.thenExecute(() -> {
				same(helper, position(golem), at(1, 0, 0L), "its plan came down with it");
				golem.robotics().walk(args(1L));
			})
			.thenWaitUntil(() -> same(helper, last(events), ARRIVED, "its next walk, from where it fell to"))
			.thenExecute(() -> same(helper, position(golem), at(2, 0, 0L), "one on"))
			.thenSucceed();
	}

	/** Saved mid-walk (its steps hold it up, gravity off), it loads with gravity its own: a walk under way doesn't go on after a load. */
	@GameTest(maxTicks = 100)
	public void savedMidWalkItLoadsWithGravity(GameTestHelper helper) {
		BotGolem golem = GolemFlightGameTest.golem(helper, false);
		helper.startSequence()
			.thenExecuteAfter(3, () -> golem.robotics().walk(args(5L)))
			.thenExecuteAfter(3, () -> {
				helper.assertTrue(golem.isNoGravity(), "mid-walk: its steps hold it up");
				var output = net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING,
					helper.getLevel().registryAccess());
				golem.saveWithoutId(output);
				BotGolem loaded = (BotGolem) golem.getType().create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.LOAD);
				loaded.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,
					helper.getLevel().registryAccess(), output.buildResult()));
				helper.assertTrue(!loaded.isNoGravity(), "loaded: gravity its own");
				loaded.discard();
			})
			.thenSucceed();
	}
}
