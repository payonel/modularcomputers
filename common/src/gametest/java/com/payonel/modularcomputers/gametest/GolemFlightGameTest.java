package com.payonel.modularcomputers.gametest;

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
				same(helper, golem.robotics().swing(args("front")).first(), "breaking", "on its legs");
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
				same(helper, golem.robotics().walk(args(1.0)).error(), "no legs", "it walks nowhere");
				same(helper, golem.robotics().swing(args("front")).first(), "breaking", "its axe works on its thrusters");
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

	@GameTest(maxTicks = 160)
	public void shovedInTheAirItHoldsItsPlanAndSaysSo(GameTestHelper helper) {
		BotGolem golem = golem(helper, false);
		double[] at = new double[1];
		double[] most = {0};
		List<Object> events = new java.util.ArrayList<>();
		helper.onEachTick(() -> {
			Object event;
			while ((event = golem.robotics().pull(args()).first()) != null) {
				events.add(event);
			}
			most[0] = Math.max(most[0], golem.getX() - at[0]);
		});
		helper.startSequence()
			.thenExecuteAfter(3, () -> helper.assertTrue(!golem.robotics().fly(args()).failed(), "it takes off where it stands: fly() with nothing to go to"))
			.thenWaitUntil(() -> helper.assertTrue(golem.robotics().state().equals(RoboticsDevice.HOVERING), "holding there: " + golem.robotics().state()))
			.thenExecute(() -> {
				at[0] = golem.getX();
				most[0] = 0;
				golem.push(1.0, 0, 0); // (a shove: as a player's walk into it, a blast)
			})
			.thenWaitUntil(() -> helper.assertTrue(notices(events, "shoved") == 1, "it says it was shoved: " + events))
			.thenWaitUntil(() -> {
				helper.assertTrue(most[0] > SHOVED, "shoved off: " + most[0]);
				helper.assertTrue(Math.abs(golem.getX() - at[0]) < 0.06 && golem.robotics().state().equals(RoboticsDevice.HOVERING),
					"back on its plan, holding it as a drone does: " + (golem.getX() - at[0]) + " " + golem.robotics().state());
			})
			.thenExecute(() -> {
				helper.assertTrue(golem.thrusting() && !golem.onGround(), "still in the air");
				helper.assertTrue(notices(events, "returned") == 1, "it says it's back: " + events);
			})
			.thenSucceed();
	}

	/** How many notices of {@code what} (shoved, returned...) are among {@code events}. */
	private static long notices(List<Object> events, String what) {
		return events.stream().filter(e -> e instanceof java.util.Map<?, ?> m && "notice".equals(m.get("event")) && what.equals(m.get("what"))).count();
	}

	/** Shoved again on its way back, it says so again (the user's: each shove its notice), and still gets back. */
	@GameTest(maxTicks = 200)
	public void shovedAgainOnItsWayBackItSaysSoAgainAndGetsBack(GameTestHelper helper) {
		BotGolem golem = golem(helper, false);
		List<Object> events = events(helper, golem);
		double[] at = new double[1];
		helper.startSequence()
			.thenExecuteAfter(3, () -> golem.robotics().fly(args()))
			.thenWaitUntil(() -> helper.assertTrue(golem.robotics().state().equals(RoboticsDevice.HOVERING), "holding there: " + golem.robotics().state()))
			.thenExecute(() -> {
				at[0] = golem.getX();
				golem.push(1.0, 0, 0);
			})
			.thenWaitUntil(() -> helper.assertTrue(golem.robotics().state().equals(RoboticsDevice.RETURNING), "on its way back: " + golem.robotics().state()))
			.thenExecuteAfter(2, () -> golem.push(1.0, 0, 0)) // (again, on its way: farther off than its own step left it)
			.thenWaitUntil(() -> helper.assertTrue(notices(events, "shoved") == 2, "each shove said: " + events))
			.thenWaitUntil(() -> helper.assertTrue(notices(events, "returned") >= 1 && golem.robotics().state().equals(RoboticsDevice.HOVERING)
				&& Math.abs(golem.getX() - at[0]) < 0.06, "back, holding it: " + (golem.getX() - at[0]) + " " + events))
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
			.thenWaitUntil(() -> helper.assertTrue(notices(events, "falling") == 1, "it says it's falling: " + events))
			.thenExecute(() -> {
				same(helper, golem.robotics().state(), RoboticsDevice.FALLING, "still falling");
				helper.assertTrue(!golem.robotics().fly(args()).failed(), "fly() taken mid-fall");
				helper.assertTrue(!golem.legs(), "its legs dropped");
			})
			.thenWaitUntil(() -> helper.assertTrue(events.contains(event("fly")), "back up: " + events))
			.thenExecute(() -> {
				same(helper, golem.robotics().position(args()).first(), java.util.Map.of("forward", 2L, "right", 0L, "up", 2L, "heading", 0L), "where its last command left it");
				helper.assertTrue(notices(events, "fell") == 0, "caught: no fall to say: " + events);
			})
			.thenWaitUntil(() -> helper.assertTrue(legs(helper).size() == 1 && legs(helper).getFirst().onGround(), "its legs down below"))
			.thenExecute(() -> {
				events.clear();
				golem.robotics().flyTo(args(6L, 0L, 2L)); // (out of its legs' reach)
			})
			.thenWaitUntil(() -> helper.assertTrue(events.contains(event("flyTo")), "away from its legs: " + events))
			.thenExecute(() -> {
				same(helper, golem.robotics().land(args()).error(), "no legs below", "land() wants its legs");
				helper.assertTrue(!golem.robotics().land(args(true)).failed(), "land(true) comes down without them");
			})
			.thenWaitUntil(() -> helper.assertTrue(events.contains(event("land")), "down: " + events))
			.thenWaitUntil(() -> helper.assertTrue(golem.onGround() && !golem.legs() && !golem.thrusting(),
				"on the ground, legless, its thrusters off: " + golem.onGround() + " " + golem.legs() + " " + golem.thrusting() + " " + golem.robotics().state()))
			.thenExecute(() -> helper.assertTrue(golem.machine().isPowered(), "still running: not a crash"))
			.thenExecuteAfter(5, () -> helper.assertTrue(golem.machine().isPowered(), "and still running after"))
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

	@GameTest(maxTicks = 200)
	public void withNoLegsBelowItWontLandAndHoversOn(GameTestHelper helper) {
		BotGolem golem = golem(helper, false);
		List<Object> events = events(helper, golem);
		helper.startSequence()
			.thenExecuteAfter(3, () -> golem.robotics().fly(args(5.0, 0.0, 1.0)))
			.thenWaitUntil(() -> helper.assertTrue(events.contains(event("fly")), "away: " + events))
			.thenExecute(() -> {
				same(helper, golem.robotics().land(args()).error(), "no legs below", "refused");
				helper.assertTrue(golem.thrusting() && golem.machine().isPowered(), "hovering on");
			})
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
				same(helper, golem.robotics().refusal("walk"), RoboticsDevice.HELD, "no moves");
				helper.assertTrue(events.contains(java.util.Map.of("event", "notice", "what", "frozen")), "it said so: " + events);
				field(golem).release(args()); // (it comes down onto its legs, two ahead)
			})
			.thenWaitUntil(() -> helper.assertTrue(events.contains(java.util.Map.of("event", "notice", "what", "thawed")), "down, and thawed: " + events))
			.thenExecute(() -> {
				same(helper, golem.robotics().position(args()).first(), before[0], "its position reads as before: the carry moved its origin");
				helper.assertTrue(!golem.blockPosition().equals(from[0]), "though it's somewhere else: " + golem.blockPosition());
				helper.assertTrue(golem.robotics().refusal("walk") == null, "its robotics back");
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

	/** Held by a field mid-walk (its steps holding it up), let go: it comes down, thaws, and its walk goes on to its end. */
	@GameTest(maxTicks = 300)
	public void heldMidWalkAndLetGoItThawsAndItsWalkEnds(GameTestHelper helper) {
		BotGolem golem = golem(helper, true);
		List<Object> events = events(helper, golem);
		helper.startSequence()
			.thenExecuteAfter(3, () -> golem.robotics().walk(args(3L)))
			.thenExecuteAfter(4, () -> helper.assertTrue(!field(golem).capture(args(FieldCard.SELF)).failed(), "held mid-walk"))
			.thenExecuteAfter(5, () -> field(golem).release(args()))
			.thenWaitUntil(() -> helper.assertTrue(events.contains(java.util.Map.of("event", "notice", "what", "thawed")), "down and thawed: " + events))
			.thenWaitUntil(() -> helper.assertTrue(events.contains(java.util.Map.of("event", "walk", "ok", true)), "its walk ended: " + events))
			.thenSucceed();
	}

	/** A flight with no way there (pathfinding) is refused before it does anything: its legs stay on. */
	@GameTest(maxTicks = 100)
	public void aFlightWithNoWayThereIsRefusedBeforeItTakesOff(GameTestHelper helper) {
		BotGolem golem = golem(helper, false);
		for (int x = 0; x < 3; x++) {
			for (int z = 0; z < 3; z++) {
				helper.setBlock(AT.offset(x - 1, 3, z - 1), Blocks.STONE);
				helper.setBlock(AT.offset(x - 1, 4, z - 1), Blocks.STONE); // (solid where it would fly to)
			}
		}
		helper.startSequence()
			.thenExecuteAfter(3, () -> {
				golem.robotics().setPathfinding(args(true));
				same(helper, golem.robotics().fly(args(0.0, 0.0, 3.0)).error(), "no path", "refused");
				helper.assertTrue(golem.legs() && !golem.thrusting(), "on its legs still, its thrusters off");
			})
			.thenSucceed();
	}
}
