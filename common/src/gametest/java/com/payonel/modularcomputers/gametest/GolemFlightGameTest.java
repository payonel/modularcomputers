package com.payonel.modularcomputers.gametest;

import com.payonel.modularcomputers.device.Buffered;
import com.payonel.modularcomputers.device.FieldCard;
import com.payonel.modularcomputers.device.RoboticsDevice;
import com.payonel.modularcomputers.entity.BotGolem;
import com.payonel.modularcomputers.entity.GolemLegs;
import com.payonel.modularcomputers.entity.ModEntities;
import com.payonel.modularcomputers.item.ModComponents;
import com.payonel.modularcomputers.item.ModItems;
import com.payonel.modularcomputers.menu.CaseMenu;
import com.payonel.modularcomputers.runtime.Arguments;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

/**
 * A golem's flight (user design, 2026-10-25): on its thrusters ({@code fly}, {@code hover}) its
 * legs stay standing where it took off, it flies straight, walks nowhere but uses its tools (the
 * user's: flight can do everything, breaking as a player off the ground does, the game's rule), and
 * can be shoved; {@code land} brings it down onto its legs, and refuses with none below; its program
 * ending mid-air, it falls and powers off. Held in its own field it keeps its legs, and isn't flying.
 * Without legs it presses no plate; its use pops legs up; it's picked up as its torso; torso over
 * legs crafts a golem, its parts kept.
 */
public class GolemFlightGameTest {
	/** Where the golem stands (BotGolemGameTest's start), facing east. */
	private static final BlockPos AT = new BlockPos(1, 1, 1);
	private static final BlockPos FRONT = AT.east();
	/** How far the tests fly it, in blocks, and how near counts as there. */
	private static final double LIFT = 2;
	private static final double NEAR = 0.2;
	/** How far a shove must move a hovering golem to count, in blocks. */
	private static final double SHOVED = 0.3;

	private static Arguments args(Object... values) {
		return new Arguments(java.util.Arrays.asList(values));
	}

	private static void same(GameTestHelper helper, Object got, Object expected, String what) {
		helper.assertTrue(java.util.Objects.equals(got, expected), what + ": expected " + expected + ", got " + got);
	}

	/** A golem running a program that waits on (its devices take data), with an energy field card when asked. */
	static BotGolem golem(GameTestHelper helper, boolean field) {
		BotGolem golem = BotGolemGameTest.golem(helper, board -> {
			while (true) {
				board.waitForInterrupt(1000, "nothing");
			}
		});
		golem.board().parts().setItem(CaseMenu.UPS_SLOT, ModItems.TIER3_BATTERY.charged());
		if (field) {
			golem.board().parts().setItem(CaseMenu.EXP_SLOTS + 1, new ItemStack(ModItems.ENERGY_FIELD_CARD));
		}
		helper.runAfterDelay(1, () -> golem.board().pressPower());
		return golem;
	}

	private static FieldCard field(BotGolem golem) {
		return golem.board().cards().stream().filter(FieldCard.class::isInstance).map(FieldCard.class::cast).findFirst().orElseThrow();
	}

	private static List<GolemLegs> legs(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(GolemLegs.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(8), GolemLegs::isAlive);
	}

	/** Each robotics event the golem has, as it comes. */
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

	private static java.util.Map<String, Object> event(String name) {
		return java.util.Map.of("event", name, "ok", true);
	}

	@GameTest(maxTicks = 100)
	public void flyingLeavesItsLegsStandingWhereItTookOffAndArrivesFast(GameTestHelper helper) {
		BotGolem golem = golem(helper, false);
		List<Object> events = events(helper, golem);
		double[] start = new double[2];
		int[] took = new int[1];
		helper.startSequence()
			.thenExecuteAfter(3, () -> {
				start[0] = golem.getY();
				start[1] = golem.getX();
				helper.assertTrue(!golem.robotics().fly(args(4.0, 0.0, LIFT)).failed(), "it takes off");
				helper.assertTrue(!golem.legs() && golem.thrusting(), "its legs off, its thrusters firing");
				same(helper, legs(helper).size(), 1, "its legs, left standing");
				same(helper, golem.robotics().state(), RoboticsDevice.FLYING, "flying");
				took[0] = (int) helper.getTick();
			})
			.thenWaitUntil(() -> helper.assertTrue(events.contains(event("fly")), "arrived: " + events))
			.thenExecute(() -> {
				helper.assertTrue(Math.abs(golem.getX() - start[1] - 4) < NEAR && Math.abs(golem.getY() - start[0] - LIFT - BotGolem.LEG_HEIGHT) < NEAR,
					"there: " + (golem.getX() - start[1]) + ", " + (golem.getY() - start[0]));
				helper.assertTrue(helper.getTick() - took[0] <= Math.ceil(Math.hypot(4, LIFT) / BotGolem.THRUST_SPEED) + 2, "fast: " + (helper.getTick() - took[0]) + " ticks");
				same(helper, golem.robotics().state(), RoboticsDevice.HOVERING, "hovering there");
				helper.assertTrue(golem.machine().isPowered() && golem.flying(), "still running, and flying");
			})
			.thenSucceed();
	}

	/** How high the stopped flight's test sends it, in blocks. */
	private static final double HIGH = 4;

	/** A flight stopped in the air goes on from exactly there, as a walk does (the user's), and the rest of the way ends squared. */
	@GameTest(maxTicks = 200)
	public void aFlightStoppedInTheAirGoesOnFromThereAndEndsSquared(GameTestHelper helper) {
		BotGolem golem = golem(helper, false);
		List<Object> events = events(helper, golem);
		double[] stoppedAt = {0};
		helper.startSequence()
			.thenExecuteAfter(3, () -> golem.robotics().fly(args(0.0, 0.0, HIGH)))
			.thenExecuteAfter(1, () -> {
				golem.robotics().stop(args());
				java.util.Map<?, ?> live = (java.util.Map<?, ?>) ((java.util.Map<?, ?>) golem.robotics().get(args()).first()).get("position");
				stoppedAt[0] = ((Number) live.get("up")).doubleValue();
				helper.assertTrue(stoppedAt[0] > 0 && stoppedAt[0] < HIGH, "stopped on the way up: " + stoppedAt[0]);
				events.clear();
				golem.robotics().fly(args(0.0, 0.0, HIGH - stoppedAt[0])); // (the rest of the way)
			})
			.thenWaitUntil(() -> helper.assertTrue(events.contains(event("fly")), "up the rest: " + events))
			.thenExecute(() -> same(helper, golem.robotics().position(args()).first(),
				java.util.Map.of("forward", 0L, "right", 0L, "up", (long) HIGH, "heading", 0L), "squared, where it was going"))
			.thenSucceed();
	}

	/** How much longer a break takes off the ground at least (the game's rule: a fifth as fast). */
	private static final int SLOWER_OFF_THE_GROUND = 3;

	@GameTest(maxTicks = 300)
	public void onItsThrustersItWalksNowhereButCutsAsAPlayerOffTheGroundDoes(GameTestHelper helper) {
		helper.setBlock(FRONT, Blocks.OAK_LOG);
		helper.setBlock(FRONT.above((int) LIFT + 1), Blocks.OAK_LOG);
		BotGolem golem = golem(helper, false);
		List<Object> events = events(helper, golem);
		golem.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
		long[] ticks = new long[4]; // (each swing's start and end)
		java.util.function.Predicate<Object> cut = event -> event instanceof java.util.Map<?, ?> map && "swing".equals(map.get("event")) && Boolean.TRUE.equals(map.get("ok"));
		helper.startSequence()
			.thenExecuteAfter(3, () -> {
				same(helper, golem.robotics().swing(args("front")).first(), true, "on its legs");
				ticks[0] = helper.getTick();
			})
			.thenWaitUntil(() -> helper.assertTrue(events.stream().anyMatch(cut), "cut on its legs: " + events))
			.thenExecute(() -> {
				ticks[1] = helper.getTick();
				events.clear();
				golem.robotics().fly(args(0.0, 0.0, LIFT + 1));
			})
			.thenWaitUntil(() -> helper.assertTrue(events.contains(event("fly")), "up: " + events))
			.thenExecute(() -> {
				var walked = golem.robotics().walk(args(1.0));
				same(helper, java.util.List.of(walked.error(), walked.failure().get("part")), java.util.List.of("fault", "legs"), "it walks nowhere: its legs missing");
				same(helper, golem.robotics().swing(args("front")).first(), true, "its axe works on its thrusters");
				ticks[2] = helper.getTick();
			})
			.thenWaitUntil(() -> helper.assertTrue(events.stream().anyMatch(cut), "cut in the air: " + events))
			.thenExecute(() -> {
				ticks[3] = helper.getTick();
				helper.assertTrue(helper.getBlockState(FRONT.above((int) LIFT + 1)).isAir(), "the log up there is gone");
				long standing = ticks[1] - ticks[0], flying = ticks[3] - ticks[2];
				helper.assertTrue(flying >= SLOWER_OFF_THE_GROUND * standing, "slower off the ground: " + flying + " ticks, against " + standing + " on its legs");
			})
			.thenSucceed();
	}

	/**
	 * Shoved in the air, it says so once the shove's over and stays where it was put (the user's: no
	 * way back of its own); a positional command there fails, drifted; fly() takes it back.
	 */
	@GameTest(maxTicks = 200)
	public void shovedInTheAirItSaysSoAndStaysWhereItWasPut(GameTestHelper helper) {
		BotGolem golem = golem(helper, false);
		List<Object> events = events(helper, golem);
		double[] at = new double[1];
		helper.startSequence()
			.thenExecuteAfter(3, () -> helper.assertTrue(!golem.robotics().fly(args()).failed(), "it takes off where it stands: fly() with nothing to go to"))
			.thenWaitUntil(() -> helper.assertTrue(golem.robotics().state().equals(RoboticsDevice.HOVERING), "holding there: " + golem.robotics().state()))
			.thenExecute(() -> {
				at[0] = golem.getX();
				events.clear();
				golem.push(1.0, 0, 0); // (a shove: as a player's walk into it, a blast)
			})
			.thenWaitUntil(() -> helper.assertTrue(notices(events, "shove") == 1, "it says it was shoved: " + events))
			.thenExecuteAfter(20, () -> {
				helper.assertTrue(golem.getX() - at[0] > SHOVED, "still where it was put: " + (golem.getX() - at[0]));
				helper.assertTrue(notices(events, "shove") == 1, "said once: " + events);
				same(helper, golem.robotics().refusal("scan"), com.payonel.modularcomputers.device.Buffered.DRIFTED, "off its spot: a positional command fails");
				events.clear();
				helper.assertTrue(!golem.robotics().fly(args()).failed(), "fly(): back to where its last command left it");
			})
			.thenWaitUntil(() -> helper.assertTrue(events.contains(event("fly")) && Math.abs(golem.getX() - at[0]) < 0.06, "back: " + (golem.getX() - at[0]) + " " + events))
			.thenExecute(() -> same(helper, golem.robotics().refusal("scan"), null, "on its spot: it scans"))
			.thenSucceed();
	}

	/** Whether a landing ended blocked, down, by a block below instead of its legs (not "": what it found there). */
	private static boolean blockedBelow(List<Object> events) {
		return events.stream().anyMatch(e -> e instanceof java.util.Map<?, ?> m && "land".equals(m.get("event")) && "blocked".equals(m.get("why"))
			&& "down".equals(m.get("face")) && m.get("name") instanceof String block && !block.isEmpty());
	}

	/** How many notices of {@code why} (shove, fall) are among {@code events}. */
	private static long notices(List<Object> events, String why) {
		return events.stream().filter(e -> e instanceof java.util.Map<?, ?> m && "notice".equals(m.get("event")) && why.equals(m.get("why"))).count();
	}

	/** Shoved twice, it says each shove (the user's: each its notice), from where each put it. */
	@GameTest(maxTicks = 200)
	public void shovedTwiceItSaysEachShove(GameTestHelper helper) {
		BotGolem golem = golem(helper, false);
		List<Object> events = events(helper, golem);
		helper.startSequence()
			.thenExecuteAfter(3, () -> golem.robotics().fly(args()))
			.thenWaitUntil(() -> helper.assertTrue(golem.robotics().state().equals(RoboticsDevice.HOVERING), "holding there: " + golem.robotics().state()))
			.thenExecute(() -> golem.push(1.0, 0, 0))
			.thenWaitUntil(() -> helper.assertTrue(notices(events, "shove") == 1, "the first: " + events))
			.thenExecute(() -> golem.push(1.0, 0, 0))
			.thenWaitUntil(() -> helper.assertTrue(notices(events, "shove") == 2, "and the second: " + events))
			.thenSucceed();
	}

	/**
	 * A landing comes straight down however far (the user's): over a pit down to the world's bottom,
	 * more than a flight's reach deep, land() finds its legs at the bottom, and land(true) the ground.
	 * (Down, not up: a test's structure is boxed in by barriers.)
	 */
	@GameTest(maxTicks = 2400)
	public void aLandingComesDownHoweverFarBelowItsLegsOrTheGroundAre(GameTestHelper helper) {
		BotGolem golem = golem(helper, false);
		List<Object> events = events(helper, golem);
		int[] bottom = new int[1];
		helper.startSequence()
			.thenExecuteAfter(3, () -> golem.robotics().fly(args()))
			.thenWaitUntil(() -> helper.assertTrue(events.contains(event("fly")), "off its legs: " + events))
			.thenExecute(() -> {
				events.clear();
				bottom[0] = pit(helper, golem);
				helper.assertTrue(golem.getY() - bottom[0] > FAR, "deeper than a flight's reach");
				GolemLegs legs = legs(helper).getFirst();
				legs.setPos(legs.getX(), bottom[0], legs.getZ()); // (its legs at the bottom)
				helper.assertTrue(Boolean.TRUE.equals(golem.robotics().land(args()).first()), "land(): its legs found, far below");
			})
			.thenWaitUntil(() -> helper.assertTrue(events.contains(event("land")), "down onto its legs: " + events))
			.thenExecute(() -> {
				helper.assertTrue(golem.legs(), "on its legs");
				events.clear();
				golem.robotics().fly(args(0.0, 0.0, FAR));
			})
			.thenWaitUntil(() -> helper.assertTrue(events.contains(event("fly")), "up a flight's reach: " + events))
			.thenExecute(() -> {
				events.clear();
				golem.robotics().fly(args(0.0, 0.0, 2.0));
			})
			.thenWaitUntil(() -> helper.assertTrue(events.contains(event("fly")), "and two more: " + events))
			.thenExecute(() -> {
				events.clear();
				for (GolemLegs legs : helper.getLevel().getEntitiesOfClass(GolemLegs.class, golem.getBoundingBox().inflate(BotGolem.LEGS_REACH, 2 * FAR, BotGolem.LEGS_REACH))) {
					legs.discard(); // (no legs, at the bottom where it left them: it lands on the ground)
				}
				helper.assertTrue(golem.getY() - bottom[0] > FAR, "past a flight's reach above the ground");
				helper.assertTrue(Boolean.TRUE.equals(golem.robotics().land(args(true)).first()), "land(true): the ground found, far below");
			})
			.thenWaitUntil(() -> helper.assertTrue(events.contains(event("land")), "down onto the ground: " + events))
			.thenWaitUntil(() -> helper.assertTrue(golem.onGround() && !golem.legs() && golem.getY() - bottom[0] < 1,
				"on the ground at the bottom, legless: " + golem.onGround() + " " + golem.legs() + " " + (golem.getY() - bottom[0])))
			.thenSucceed();
	}

	/** A pit under the golem, open down to the world's bottom, stone there: its floor's height. */
	private static int pit(GameTestHelper helper, BotGolem golem) {
		var level = helper.getLevel();
		BlockPos under = golem.blockPosition();
		int floor = level.getMinY() + 1;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int y = under.getY(); y >= level.getMinY(); y--) {
					level.setBlock(new BlockPos(under.getX() + dx, y, under.getZ() + dz), (y == level.getMinY() ? Blocks.STONE : Blocks.AIR).defaultBlockState(), 2);
				}
			}
		}
		return floor;
	}

	/**
	 * land(true) over a hole through the world, nothing under it down to the world's bottom, is
	 * blocked by the air below (the user's): the call true, its land event blocked at once,
	 * minecraft:air on its down face, and it goes on hovering.
	 */
	@GameTest(maxTicks = 200)
	public void landingWithForceOverAHoleThroughTheWorldIsBlockedByAir(GameTestHelper helper) {
		BotGolem golem = golem(helper, false);
		List<Object> events = events(helper, golem);
		helper.startSequence()
			.thenExecuteAfter(3, () -> golem.robotics().fly(args()))
			.thenWaitUntil(() -> helper.assertTrue(events.contains(event("fly")), "off: " + events))
			.thenExecute(() -> {
				events.clear();
				var level = helper.getLevel();
				BlockPos under = golem.blockPosition();
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						for (int y = under.getY(); y >= level.getMinY(); y--) { // (bedrock too: a hole through the world)
							level.setBlock(new BlockPos(under.getX() + dx, y, under.getZ() + dz), Blocks.AIR.defaultBlockState(), 2);
						}
					}
				}
				for (net.minecraft.world.entity.Entity legs : legs(helper)) {
					legs.discard(); // (its legs gone too: nothing below it at all)
				}
				helper.assertTrue(Boolean.TRUE.equals(golem.robotics().land(args(true)).first()), "a command: true");
			})
			.thenWaitUntil(() -> helper.assertTrue(events.stream().map(GameTests::withoutCall).anyMatch(java.util.Map.of("event", "land", "ok", false, "why", "blocked", "name", "minecraft:air", "face", "down")::equals),
				"blocked by the air below: " + events))
			.thenExecute(() -> helper.assertTrue(golem.thrusting(), "still hovering"))
			.thenSucceed();
	}

	/**
	 * Falling, it says so as it begins, and a tier 2 catches itself (the user's): fly() is taken
	 * mid-fall, its legs drop, and it flies back to where its last command left it. Out of its legs'
	 * reach, land() refuses, and land(true) sets it down on the ground without them, still running.
	 */
	@GameTest(maxTicks = 300)
	public void fallingItCatchesItselfOnItsThrustersAndLandsWithoutItsLegsWhenItMust(GameTestHelper helper) {
		helper.setBlock(FRONT, Blocks.STONE); // (two steps up)
		helper.setBlock(FRONT.east(), Blocks.STONE);
		helper.setBlock(FRONT.east().above(), Blocks.STONE);
		BotGolem golem = golem(helper, false);
		List<Object> events = events(helper, golem);
		helper.startSequence()
			.thenExecuteAfter(3, () -> golem.robotics().walk(args(1.0, 0.0, 1.0)))
			.thenWaitUntil(() -> helper.assertTrue(events.contains(java.util.Map.of("event", "walk", "ok", true)), "up a step: " + events))
			.thenExecute(() -> {
				events.clear();
				golem.robotics().walk(args(1.0, 0.0, 1.0));
			})
			.thenWaitUntil(() -> helper.assertTrue(events.contains(java.util.Map.of("event", "walk", "ok", true)), "up two: " + events))
			.thenExecute(() -> {
				events.clear();
				helper.setBlock(FRONT.east().above(), Blocks.AIR); // (gone from under it, and the one under that)
				helper.setBlock(FRONT.east(), Blocks.AIR);
			})
			.thenWaitUntil(() -> helper.assertTrue(notices(events, "fall") == 1, "it says it's falling: " + events))
			.thenExecute(() -> {
				same(helper, golem.robotics().state(), RoboticsDevice.FALLING, "still falling");
				helper.assertTrue(!golem.robotics().fly(args()).failed(), "fly() taken mid-fall");
				helper.assertTrue(!golem.legs(), "its legs dropped");
			})
			.thenWaitUntil(() -> helper.assertTrue(events.contains(event("fly")), "back up: " + events))
			.thenExecute(() -> {
				same(helper, golem.robotics().position(args()).first(), java.util.Map.of("forward", 2L, "right", 0L, "up", 2L, "heading", 0L), "where its last command left it");
				helper.assertTrue(notices(events, "fall") == 1, "caught: no landing to say, its flight says it: " + events);
			})
			.thenWaitUntil(() -> helper.assertTrue(legs(helper).size() == 1 && legs(helper).getFirst().onGround(), "its legs down below"))
			.thenExecute(() -> {
				events.clear();
				golem.robotics().flyTo(args(6L, 0L, 2L)); // (out of its legs' reach)
			})
			.thenWaitUntil(() -> helper.assertTrue(events.contains(event("flyTo")), "away from its legs: " + events))
			.thenExecute(() -> {
				events.clear();
				same(helper, golem.robotics().land(args()).first(), true, "land(): a command");
			})
			.thenWaitUntil(() -> helper.assertTrue(blockedBelow(events), "no legs below: blocked, down, by what's there instead: " + events))
			.thenExecute(() -> {
				events.clear();
				helper.assertTrue(!golem.robotics().land(args(true)).failed(), "land(true) comes down without them");
			})
			.thenWaitUntil(() -> helper.assertTrue(events.contains(event("land")), "down: " + events))
			.thenWaitUntil(() -> helper.assertTrue(golem.onGround() && !golem.legs() && !golem.thrusting(),
				"on the ground, legless, its thrusters off: " + golem.onGround() + " " + golem.legs() + " " + golem.thrusting() + " " + golem.robotics().state()))
			.thenExecute(() -> helper.assertTrue(golem.machine().isPowered(), "still running: not a crash"))
			.thenExecuteAfter(5, () -> {
				helper.assertTrue(golem.machine().isPowered(), "and still running after");
				events.clear();
				helper.assertTrue(!golem.robotics().fly(args()).failed(), "off the ground again, legless");
			})
			.thenWaitUntil(() -> helper.assertTrue(events.contains(event("fly")), "up: " + events))
			.thenExecute(() -> {
				events.clear();
				golem.robotics().flyTo(args(2L, 0L, 1L)); // (over its legs, down where they fell)
			})
			.thenWaitUntil(() -> helper.assertTrue(events.contains(event("flyTo")), "over its legs: " + events))
			.thenExecute(() -> {
				events.clear();
				helper.assertTrue(!golem.robotics().land(args()).failed(), "land() onto them");
			})
			.thenWaitUntil(() -> helper.assertTrue(events.contains(event("land")) && golem.legs() && legs(helper).isEmpty(), "its legs back on: " + events))
			.thenSucceed();
	}

	/**
	 * stop() then fly() catches a fall where it is (the user's: no stop(true), no hover): a stop lets
	 * the fall go on but takes where it is as where its last command left it, and fly() goes there,
	 * catching itself, its legs dropped.
	 */
	@GameTest(maxTicks = 300)
	public void aStopThenAFlightCatchesAFallWhereItIs(GameTestHelper helper) {
		helper.setBlock(FRONT, Blocks.STONE); // (two steps up)
		helper.setBlock(FRONT.east(), Blocks.STONE);
		helper.setBlock(FRONT.east().above(), Blocks.STONE);
		BotGolem golem = golem(helper, false);
		List<Object> events = events(helper, golem);
		double[] caught = new double[1];
		helper.startSequence()
			.thenExecuteAfter(3, () -> golem.robotics().walk(args(1.0, 0.0, 1.0)))
			.thenWaitUntil(() -> helper.assertTrue(events.contains(java.util.Map.of("event", "walk", "ok", true)), "up a step: " + events))
			.thenExecute(() -> {
				events.clear();
				golem.robotics().walk(args(1.0, 0.0, 1.0));
			})
			.thenWaitUntil(() -> helper.assertTrue(events.contains(java.util.Map.of("event", "walk", "ok", true)), "up two: " + events))
			.thenExecute(() -> {
				events.clear();
				helper.setBlock(FRONT.east().above(), Blocks.AIR);
				helper.setBlock(FRONT.east(), Blocks.AIR);
			})
			.thenWaitUntil(() -> helper.assertTrue(notices(events, "fall") == 1, "it says it's falling: " + events))
			.thenExecute(() -> {
				golem.robotics().stop(args());
				caught[0] = golem.getY();
				same(helper, golem.robotics().state(), RoboticsDevice.FALLING, "a stop: the fall goes on");
				helper.assertTrue(!golem.robotics().fly(args()).failed(), "fly(): taken mid-fall");
			})
			.thenWaitUntil(() -> helper.assertTrue(events.contains(event("fly")), "caught, there: " + events))
			.thenExecute(() -> {
				same(helper, golem.robotics().state(), RoboticsDevice.HOVERING, "hovering there");
				helper.assertTrue(Math.abs(golem.getY() - BotGolem.LEG_HEIGHT - caught[0]) < 0.06,
					"where it stopped (its torso its legs' height over its feet's point): " + (golem.getY() - BotGolem.LEG_HEIGHT - caught[0]));
				helper.assertTrue(!golem.legs(), "its legs dropped");
				helper.assertTrue(notices(events, "fall") == 1, "caught: no landing to say: " + events);
			})
			.thenWaitUntil(() -> helper.assertTrue(legs(helper).size() == 1 && legs(helper).getFirst().onGround(), "its legs down below"))
			.thenSucceed();
	}

	@GameTest(maxTicks = 200)
	public void itLandsOntoItsLegsBelowIt(GameTestHelper helper) {
		BotGolem golem = golem(helper, false);
		List<Object> events = events(helper, golem);
		helper.startSequence()
			.thenExecuteAfter(3, () -> golem.robotics().fly(args(0.0, 1.0, LIFT)))
			.thenWaitUntil(() -> helper.assertTrue(events.contains(event("fly")), "up: " + events))
			.thenExecute(() -> helper.assertTrue(!golem.robotics().land(args()).failed(), "it lands"))
			.thenWaitUntil(() -> helper.assertTrue(events.contains(event("land")), "landed: " + events))
			.thenExecute(() -> {
				helper.assertTrue(golem.legs() && !golem.thrusting(), "on its legs, its thrusters off");
				same(helper, legs(helper).size(), 0, "its legs are its own again");
				same(helper, golem.robotics().state(), RoboticsDevice.IDLE, "idle");
				helper.assertTrue(golem.machine().isPowered(), "still running");
			})
			.thenSucceed();
	}

	/**
	 * A landing never goes through blocks, its last blocks onto its legs too: a stone put between it and
	 * its legs, in the last of its way down, ends it blocked there, the robot above the stone, legless.
	 * (Its last blocks once moved with no collision, to pass its own legs, and so passed anything.)
	 */
	@GameTest(maxTicks = 300)
	public void aLandingIsBlockedByABlockInItsLastBlocksAsAnywhere(GameTestHelper helper) {
		BotGolem golem = golem(helper, false);
		List<Object> events = events(helper, golem);
		net.minecraft.core.BlockPos[] stone = {null};
		helper.startSequence()
			.thenExecuteAfter(3, () -> golem.robotics().fly(args(0.0, 0.0, 3.0)))
			.thenWaitUntil(() -> helper.assertTrue(events.contains(event("fly")), "up: " + events))
			.thenExecute(() -> {
				stone[0] = legs(helper).getFirst().blockPosition().above(); // (just over its legs: in the last of its way down)
				helper.getLevel().setBlockAndUpdate(stone[0], net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
				helper.assertTrue(!golem.robotics().land(args()).failed(), "it lands");
			})
			.thenWaitUntil(() -> helper.assertTrue(blockedBelow(events), "blocked by the stone: " + events))
			.thenExecute(() -> {
				helper.assertTrue(!golem.legs(), "not on its legs: the stone between");
				helper.assertTrue(golem.getY() >= stone[0].getY() + 1 - 1e-6, "above the stone, never through it: " + golem.getY() + " over " + stone[0]);
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = 200)
	public void withNoLegsBelowItWontLandAndHoversOn(GameTestHelper helper) {
		BotGolem golem = golem(helper, false);
		List<Object> events = events(helper, golem);
		helper.startSequence()
			.thenExecuteAfter(3, () -> golem.robotics().fly(args(5.0, 0.0, 1.0)))
			.thenWaitUntil(() -> helper.assertTrue(events.contains(event("fly")), "away: " + events))
			.thenExecute(() -> {
				events.clear();
				same(helper, golem.robotics().land(args()).first(), true, "a command");
			})
			.thenWaitUntil(() -> helper.assertTrue(blockedBelow(events), "blocked, down, by what's below instead of its legs: " + events))
			.thenExecute(() -> helper.assertTrue(golem.thrusting() && golem.machine().isPowered(), "hovering on"))
			.thenSucceed();
	}

	@GameTest(maxTicks = 200)
	public void itsProgramEndingMidAirItFallsAndPowersOff(GameTestHelper helper) {
		BotGolem golem = golem(helper, false);
		List<Object> events = events(helper, golem);
		helper.startSequence()
			.thenExecuteAfter(3, () -> golem.robotics().fly(args(0.0, 0.0, LIFT)))
			.thenWaitUntil(() -> helper.assertTrue(events.contains(event("fly")), "up: " + events))
			.thenExecute(() -> golem.robotics().accepting(false)) // (as its board says when its program ends)
			.thenWaitUntil(() -> helper.assertTrue(golem.onGround() && !golem.thrusting(), "fallen"))
			.thenExecute(() -> {
				helper.assertTrue(!golem.machine().isPowered(), "powered off");
				helper.assertTrue(!golem.legs(), "its torso, on the ground");
			})
			.thenSucceed();
	}

	/**
	 * A field holding it (its own too) freezes its robotics (the user's): no moves, no tools, its
	 * commands refused held; the field's carry moves its origin with it, so where it's set down its
	 * position reads as before (its robotics never saw the carry).
	 */
	@GameTest(maxTicks = 200)
	public void inAFieldItsRoboticsFreezeAndTheCarryMovesItsOrigin(GameTestHelper helper) {
		BotGolem golem = golem(helper, true);
		List<Object> events = events(helper, golem);
		Object[] before = {null};
		BlockPos[] from = {null};
		helper.startSequence()
			.thenExecuteAfter(3, () -> {
				before[0] = golem.robotics().position(args()).first();
				from[0] = golem.blockPosition();
				helper.assertTrue(!field(golem).capture(args(FieldCard.SELF)).failed(), "it holds itself");
				field(golem).move(args(2.0, 0.0, LIFT)); // (forward two, and up: its field carries it)
			})
			.thenWaitUntil(() -> helper.assertTrue(field(golem).get(args()).first() instanceof java.util.Map<?, ?> now && "holding".equals(now.get("state")), "carried"))
			.thenExecute(() -> {
				double x = golem.getX();
				golem.push(1.0, 0, 0); // (a shove: its field holds it where it is)
				helper.assertTrue(Math.abs(golem.getX() - x) < 1e-6, "not shoved");
				same(helper, golem.robotics().state(), RoboticsDevice.HELD, "its robotics frozen");
				same(helper, golem.robotics().refusal("swing"), RoboticsDevice.HELD, "no tools in a field");
				same(helper, golem.robotics().refusal("scan"), RoboticsDevice.HELD, "a command at once can't act: held");
				same(helper, golem.robotics().refusal("walk"), null, "a move is taken: it waits in stasis (the user's)");
				helper.assertTrue(events.stream().anyMatch(e -> e instanceof java.util.Map<?, ?> m && "notice".equals(m.get("event")) && "held".equals(m.get("why")) && m.get("id") instanceof String id && !id.isEmpty()), "it said so: " + events);
				same(helper, golem.robotics().turn(args("right")).first(), true, "a turn, taken while held");
			})
			.thenExecuteAfter(10, () -> {
				helper.assertTrue(events.stream().noneMatch(e -> e instanceof java.util.Map<?, ?> m && "turn".equals(m.get("event"))), "nothing said while it waits: " + events);
				same(helper, golem.robotics().refusal("walk"), Buffered.BUSY, "one at a time: the turn waits");
				field(golem).release(args()); // (it comes down onto its legs, two ahead)
			})
			.thenWaitUntil(() -> helper.assertTrue(events.contains(java.util.Map.of("event", "notice", "why", "held", "id", "")), "down, and thawed: " + events))
			.thenWaitUntil(() -> helper.assertTrue(events.contains(event("turn")), "let go, its turn went: " + events))
			.thenExecute(() -> {
				same(helper, golem.robotics().position(args()).first(), java.util.Map.of("forward", ((java.util.Map<?, ?>) before[0]).get("forward"),
					"right", ((java.util.Map<?, ?>) before[0]).get("right"), "up", ((java.util.Map<?, ?>) before[0]).get("up"), "heading", 90L),
					"its position reads as before (the carry moved its origin), turned right");
				helper.assertTrue(!golem.blockPosition().equals(from[0]), "though it's somewhere else: " + golem.blockPosition());
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = 100)
	public void withoutLegsItPressesNoPressurePlate(GameTestHelper helper) {
		helper.setBlock(AT, Blocks.STONE_PRESSURE_PLATE);
		BotGolem golem = BotGolemGameTest.golem(helper, board -> {
		});
		golem.legs(false);
		helper.startSequence()
			.thenExecuteAfter(20, () -> helper.assertTrue(!helper.getBlockState(AT).getValue(PressurePlateBlock.POWERED), "the plate isn't pressed"))
			.thenSucceed();
	}

	@GameTest(maxTicks = 100)
	public void itsUsePopsLegsUpIntoItsHand(GameTestHelper helper) {
		BotGolem golem = golem(helper, false);
		GolemLegs standing = ModEntities.GOLEM_LEGS.create(helper.getLevel(), EntitySpawnReason.TRIGGERED);
		var at = helper.absoluteVec(net.minecraft.world.phys.Vec3.atBottomCenterOf(FRONT));
		standing.snapTo(at.x, at.y, at.z, 0, 0);
		helper.getLevel().addFreshEntity(standing);
		helper.startSequence()
			.thenExecuteAfter(3, () -> same(helper, golem.robotics().use(args("front")).first(), "used", "it popped them up"))
			.thenExecute(() -> {
				helper.assertTrue(!standing.isAlive(), "gone from the world");
				helper.assertTrue(golem.getMainHandItem().is(ModItems.TIER2_BOT_GOLEM_LEGS), "in its hand: " + golem.getMainHandItem());
			})
			.thenSucceed();
	}

	@GameTest
	public void itsTorsoIsPickedUpAndCraftedOverItsLegsIntoAGolemWithItsParts(GameTestHelper helper) {
		BotGolem golem = BotGolemGameTest.golem(helper, board -> {
		});
		golem.legs(false);
		ItemStack torso = golem.asItem();
		helper.assertTrue(torso.is(ModItems.TIER2_BOT_GOLEM_TORSO), "picked up as its torso: " + torso);
		CraftingInput input = CraftingInput.of(1, 2, List.of(torso, new ItemStack(ModItems.TIER2_BOT_GOLEM_LEGS)));
		var recipe = helper.getLevel().recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());
		helper.assertTrue(recipe.isPresent(), "torso over legs is a recipe");
		ItemStack made = recipe.get().value().assemble(input);
		helper.assertTrue(made.is(ModItems.TIER2_BOT_GOLEM), "a golem: " + made);
		same(helper, made.get(ModComponents.SERIAL), golem.board().boardId(), "its board's id kept");
		NonNullList<ItemStack> parts = NonNullList.withSize(CaseMenu.PART_SLOTS, ItemStack.EMPTY);
		made.getOrDefault(net.minecraft.core.component.DataComponents.CONTAINER, net.minecraft.world.item.component.ItemContainerContents.EMPTY).copyInto(parts);
		helper.assertTrue(parts.get(CaseMenu.CPU_SLOT).is(ModItems.TIER3_CPU), "its parts kept: " + parts);
		helper.succeed();
	}

	/** A swing's events, as they come. */
	private static boolean swingEnded(List<Object> events, java.util.function.Predicate<java.util.Map<?, ?>> how) {
		return events.stream().anyMatch(e -> e instanceof java.util.Map<?, ?> m && "swing".equals(m.get("event")) && how.test(m));
	}

	/**
	 * A swing breaks a place, as a player's does (the game's own): another block pushed into its spot,
	 * the break goes on into that block, and breaks it.
	 */
	@GameTest(maxTicks = 400)
	public void aBlockPushedIntoItsSpotIsBrokenAsThePlayersBreakGoesOn(GameTestHelper helper) {
		BotGolem golem = golem(helper, false);
		helper.setBlock(FRONT, Blocks.OAK_LOG);
		golem.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WOODEN_AXE));
		List<Object> events = events(helper, golem);
		helper.startSequence()
			.thenExecuteAfter(3, () -> same(helper, golem.robotics().swing(args("front")).first(), true, "it starts breaking"))
			.thenExecuteAfter(5, () -> helper.setBlock(FRONT, Blocks.OAK_PLANKS)) // (another block in its spot)
			.thenWaitUntil(() -> helper.assertTrue(swingEnded(events, m -> Boolean.TRUE.equals(m.get("ok"))
				&& m.get("result") instanceof java.util.Map<?, ?> r && "minecraft:oak_planks".equals(r.get("name"))), "the planks broken: " + events))
			.thenExecute(() -> helper.assertBlockPresent(Blocks.AIR, FRONT))
			.thenSucceed();
	}

	/** Its spot turned to air mid-break, the swing has lost what it was breaking (the user's): lost, its name, no id (a block's), where it was, and its call as far as it got. */
	@GameTest(maxTicks = 400)
	public void aBlockGoneMidBreakIsLostWithItsNameAndWhereItWas(GameTestHelper helper) {
		BotGolem golem = golem(helper, false);
		helper.setBlock(FRONT, Blocks.OAK_LOG);
		golem.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WOODEN_AXE));
		List<Object> events = events(helper, golem);
		helper.startSequence()
			.thenExecuteAfter(3, () -> same(helper, golem.robotics().swing(args("front")).first(), true, "it starts breaking"))
			.thenExecuteAfter(5, () -> helper.setBlock(FRONT, Blocks.AIR)) // (gone from its spot)
			.thenWaitUntil(() -> helper.assertTrue(swingEnded(events, m -> java.util.Objects.equals(GameTests.withoutCall(m),
				java.util.Map.of("event", "swing", "ok", false, "why", "lost", "name", "minecraft:oak_log", "id", "",
					"at", java.util.Map.of("forward", 1L, "right", 0L, "up", 0L)))
				&& m.get("call") instanceof java.util.Map<?, ?> call && call.get("progress") instanceof Number done && done.doubleValue() > 0), "lost, its call as far as it got: " + events))
			.thenSucceed();
	}

	/** Held by a field mid-swing, its break pauses (the user's: as a move does), and let go, it goes on: the log broken, its swing ok. */
	@GameTest(maxTicks = 400)
	public void heldMidSwingItsBreakPausesAndGoesOnOnceLetGo(GameTestHelper helper) {
		BotGolem golem = golem(helper, true);
		helper.setBlock(FRONT, Blocks.OAK_LOG);
		golem.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WOODEN_AXE));
		List<Object> events = events(helper, golem);
		helper.startSequence()
			.thenExecuteAfter(3, () -> same(helper, golem.robotics().swing(args("front")).first(), true, "it starts breaking"))
			.thenExecuteAfter(3, () -> helper.assertTrue(!field(golem).capture(args(FieldCard.SELF)).failed(), "held mid-swing"))
			.thenExecuteAfter(40, () -> {
				helper.assertBlockPresent(Blocks.OAK_LOG, FRONT); // (paused: not broken while held)
				helper.assertTrue(events.stream().noneMatch(e -> e instanceof java.util.Map<?, ?> m && "swing".equals(m.get("event"))), "no end while held: " + events);
				field(golem).release(args());
			})
			.thenWaitUntil(() -> helper.assertTrue(events.stream().anyMatch(e -> e instanceof java.util.Map<?, ?> m && "swing".equals(m.get("event")) && Boolean.TRUE.equals(m.get("ok"))),
				"let go, its break went on: " + events))
			.thenExecute(() -> helper.assertBlockPresent(Blocks.AIR, FRONT))
			.thenSucceed();
	}

	/** Held by a field mid-walk (its steps holding it up), let go: it comes down, thaws, and its walk goes on to its end. */
	@GameTest(maxTicks = 300)
	public void heldMidWalkAndLetGoItThawsAndItsWalkEnds(GameTestHelper helper) {
		BotGolem golem = golem(helper, true);
		List<Object> events = events(helper, golem);
		helper.startSequence()
			.thenExecuteAfter(3, () -> golem.robotics().walk(args(3L)))
			.thenExecuteAfter(4, () -> helper.assertTrue(!field(golem).capture(args(FieldCard.SELF)).failed(), "held mid-walk"))
			.thenExecuteAfter(5, () -> field(golem).release(args()))
			.thenWaitUntil(() -> helper.assertTrue(events.contains(java.util.Map.of("event", "notice", "why", "held", "id", "")), "down and thawed: " + events))
			.thenWaitUntil(() -> helper.assertTrue(events.contains(java.util.Map.of("event", "walk", "ok", true)), "its walk ended: " + events))
			.thenSucceed();
	}

	/**
	 * A flight with no way there (pathfinding) is blocked (the user's: one word for every way a move
	 * can't go), no one block to name, before it does anything: its legs stay on.
	 */
	@GameTest(maxTicks = 100)
	public void aFlightWithNoWayThereIsBlockedBeforeItTakesOff(GameTestHelper helper) {
		BotGolem golem = golem(helper, false);
		List<Object> events = events(helper, golem);
		for (int x = 0; x < 3; x++) {
			for (int z = 0; z < 3; z++) {
				helper.setBlock(AT.offset(x - 1, 3, z - 1), Blocks.STONE);
				helper.setBlock(AT.offset(x - 1, 4, z - 1), Blocks.STONE); // (solid where it would fly to)
			}
		}
		helper.startSequence()
			.thenExecuteAfter(3, () -> {
				golem.robotics().setPathfinding(args(true));
				same(helper, golem.robotics().fly(args(0.0, 0.0, 3.0)).first(), true, "a command");
			})
			.thenWaitUntil(() -> helper.assertTrue(events.stream().map(GameTests::withoutCall)
				.anyMatch(java.util.Map.of("event", "fly", "ok", false, "why", "blocked", "name", "", "face", "up")::equals), "blocked, no way there: " + events))
			.thenExecute(() -> helper.assertTrue(golem.legs() && !golem.thrusting(), "on its legs still, its thrusters off"))
			.thenSucceed();
	}

	/** How far the pit tests go: as far as the old limit, 64 blocks (a long way, no limit of its own now). */
	private static final double FAR = 64;

	/**
	 * Its moves have no limit of their own (the user's, 2026-11-13: it goes as far as it's told): a
	 * walk farther than 64 blocks starts walking, and a flight farther goes all the way. (Both were
	 * once blocked at once.)
	 */
	@GameTest(maxTicks = 100)
	public void aLongWalkStartsWalking(GameTestHelper helper) {
		BotGolem golem = golem(helper, false);
		List<Object> events = events(helper, golem);
		net.minecraft.world.phys.Vec3[] was = new net.minecraft.world.phys.Vec3[1];
		helper.startSequence()
			.thenExecuteAfter(3, () -> {
				was[0] = golem.position();
				same(helper, golem.robotics().walk(args(FAR + 36)).first(), true, "a command");
			})
			.thenWaitUntil(() -> helper.assertTrue(golem.position().distanceTo(was[0]) > 0.5, "it walks: " + events))
			.thenExecute(() -> {
				helper.assertTrue(events.stream().noneMatch(event -> event instanceof java.util.Map<?, ?> map && "walk".equals(map.get("event"))), "not ended at once: " + events);
				golem.robotics().stop(args());
			})
			.thenSucceed();
	}

	/** (Down a pit, not up: a test's structure is boxed in by barriers.) */
	@GameTest(maxTicks = 1200)
	public void aLongFlightGoesAllTheWay(GameTestHelper helper) {
		BotGolem golem = golem(helper, false);
		List<Object> events = events(helper, golem);
		double[] from = new double[1];
		double down = FAR + 1; // (past the old limit: the pit is about 69 deep)
		helper.startSequence()
			.thenExecuteAfter(3, () -> golem.robotics().fly(args()))
			.thenWaitUntil(() -> helper.assertTrue(events.contains(event("fly")), "off its legs: " + events))
			.thenExecute(() -> {
				events.clear();
				int bottom = pit(helper, golem);
				legs(helper).forEach(GolemLegs::discard); // (out of its way: its own legs under it are solid)
				from[0] = golem.getY();
				helper.assertTrue(from[0] - bottom > down + 1, "the pit's deep enough: " + (from[0] - bottom));
				same(helper, golem.robotics().fly(args(0.0, 0.0, -down)).first(), true, "a command");
			})
			.thenWaitUntil(() -> helper.assertTrue(events.contains(event("fly")), "it flew all the way: " + events))
			.thenExecute(() -> helper.assertTrue(Math.abs(from[0] - golem.getY() - down) < 1e-3, "down " + down + ": " + (from[0] - golem.getY())))
			.thenSucceed();
	}
}
