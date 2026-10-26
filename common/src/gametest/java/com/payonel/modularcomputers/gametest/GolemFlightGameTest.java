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
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

/**
 * A golem's flight (user design, 2026-10-25): on its thrusters ({@code fly}, {@code hover}) its
 * legs stay standing where it took off, it flies straight and fast, uses no tools (its axe as an
 * empty hand: buttons), and can be shoved; {@code land} brings it down onto its legs, and refuses
 * with none below; its program ending mid-air, it falls and powers off. Held in its own field it
 * keeps its legs and its tools (off the ground: at a fifth of their speed), and isn't flying.
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
		return java.util.Map.of("event", name);
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
			.thenWaitUntil(() -> helper.assertTrue(events.contains(event("arrived")), "arrived: " + events))
			.thenExecute(() -> {
				helper.assertTrue(Math.abs(golem.getX() - start[1] - 4) < NEAR && Math.abs(golem.getY() - start[0] - LIFT - BotGolem.LEG_HEIGHT) < NEAR,
					"there: " + (golem.getX() - start[1]) + ", " + (golem.getY() - start[0]));
				helper.assertTrue(helper.getTick() - took[0] <= Math.ceil(Math.hypot(4, LIFT) / BotGolem.THRUST_SPEED) + 2, "fast: " + (helper.getTick() - took[0]) + " ticks");
				same(helper, golem.robotics().state(), RoboticsDevice.HOVERING, "hovering there");
				helper.assertTrue(golem.machine().isPowered() && golem.flying(), "still running, and flying");
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = 200)
	public void onItsThrustersItUsesNoToolsButPressesButtons(GameTestHelper helper) {
		helper.setBlock(FRONT.above(2), Blocks.STONE);
		helper.setBlock(FRONT.above(3), Blocks.STONE_BUTTON.defaultBlockState().setValue(ButtonBlock.FACE, AttachFace.FLOOR));
		BotGolem golem = golem(helper, false);
		List<Object> events = events(helper, golem);
		golem.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
		helper.startSequence()
			.thenExecuteAfter(3, () -> golem.robotics().fly(args(0.0, 0.0, LIFT + 1)))
			.thenWaitUntil(() -> helper.assertTrue(events.contains(event("arrived")), "up: " + events))
			.thenExecute(() -> {
				same(helper, golem.robotics().walk(args(1.0)).error(), "no legs", "it walks nowhere");
				same(helper, golem.robotics().swing(args("front")).error(), "no legs: it can't swing without its footing", "no tools");
				// Its axe works as an empty hand: the button beside it is pressed.
				same(helper, golem.robotics().use(args("front")).first(), "used", "a button pressed");
				helper.assertTrue(golem.getMainHandItem().is(Items.IRON_AXE), "its axe back in its hand");
			})
			.thenExecuteAfter(1, () -> helper.assertTrue(helper.getBlockState(FRONT.above(3)).getValue(ButtonBlock.POWERED), "the button's pressed"))
			.thenSucceed();
	}

	@GameTest(maxTicks = 100)
	public void hoveringItCanBeShovedAndDriftsToAStop(GameTestHelper helper) {
		BotGolem golem = golem(helper, false);
		double[] at = new double[1];
		helper.startSequence()
			.thenExecuteAfter(3, () -> helper.assertTrue(!golem.robotics().hover(args()).failed(), "it hovers"))
			.thenExecuteAfter(2, () -> {
				at[0] = golem.getX();
				golem.push(1.0, 0, 0); // (a shove: as a player's walk into it, a blast)
			})
			.thenExecuteAfter(10, () -> {
				helper.assertTrue(golem.getX() - at[0] > SHOVED, "shoved: " + (golem.getX() - at[0]));
				helper.assertTrue(golem.getDeltaMovement().length() < 0.01, "and stopped: " + golem.getDeltaMovement());
				helper.assertTrue(golem.thrusting() && !golem.onGround(), "still hovering");
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = 200)
	public void itLandsOntoItsLegsBelowIt(GameTestHelper helper) {
		BotGolem golem = golem(helper, false);
		List<Object> events = events(helper, golem);
		helper.startSequence()
			.thenExecuteAfter(3, () -> golem.robotics().fly(args(0.0, 1.0, LIFT)))
			.thenWaitUntil(() -> helper.assertTrue(events.contains(event("arrived")), "up: " + events))
			.thenExecute(() -> helper.assertTrue(!golem.robotics().land(args()).failed(), "it lands"))
			.thenWaitUntil(() -> helper.assertTrue(events.contains(event("landed")), "landed: " + events))
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
			.thenWaitUntil(() -> helper.assertTrue(events.contains(event("arrived")), "away: " + events))
			.thenExecute(() -> {
				same(helper, golem.robotics().land(args()).error(), "no legs there to land on", "refused");
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
			.thenWaitUntil(() -> helper.assertTrue(events.contains(event("arrived")), "up: " + events))
			.thenExecute(() -> golem.robotics().accepting(false)) // (as its board says when its program ends)
			.thenWaitUntil(() -> helper.assertTrue(golem.onGround() && !golem.thrusting(), "fallen"))
			.thenExecute(() -> {
				helper.assertTrue(!golem.machine().isPowered(), "powered off");
				helper.assertTrue(!golem.legs(), "its torso, on the ground");
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = 200)
	public void inItsOwnFieldItKeepsItsLegsAndItsToolsAndIsntFlying(GameTestHelper helper) {
		helper.setBlock(FRONT.above(2), Blocks.DIRT);
		BotGolem golem = golem(helper, true);
		List<Object> events = events(helper, golem);
		golem.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SHOVEL));
		helper.startSequence()
			.thenExecuteAfter(3, () -> {
				helper.assertTrue(!field(golem).capture(args(FieldCard.SELF)).failed(), "it holds itself");
				field(golem).move(args(0.0, 0.0, LIFT));
			})
			.thenWaitUntil(() -> helper.assertTrue(field(golem).get(args()).first() instanceof java.util.Map<?, ?> now && "holding".equals(now.get("state")), "up"))
			.thenExecute(() -> {
				double x = golem.getX();
				golem.push(1.0, 0, 0); // (a shove: its field holds it where it is)
				helper.assertTrue(Math.abs(golem.getX() - x) < 1e-6, "not shoved");
			})
			.thenExecuteAfter(5, () -> {
				helper.assertTrue(Math.abs(golem.getDeltaMovement().x) < 1e-6, "still not shoved: " + golem.getDeltaMovement());
				helper.assertTrue(golem.legs() && !golem.flying() && !golem.onGround(), "on its legs, off the ground, and not flying");
				same(helper, golem.robotics().fly(args(1.0)).error(), "held in a field: its field moves it", "its thrusters wait");
				same(helper, golem.robotics().swing(args("front")).first(), RoboticsDevice.BREAKING, "it digs");
			})
			.thenWaitUntil(() -> helper.assertTrue(events.stream().anyMatch(e -> e instanceof java.util.Map<?, ?> m && "broke".equals(m.get("event"))), "dug: " + events))
			.thenExecute(() -> helper.assertBlockPresent(Blocks.AIR, FRONT.above(2)))
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
}
