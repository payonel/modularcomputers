package com.payonel.modularcomputers.gametest;

import com.payonel.modularcomputers.device.FieldCard;
import com.payonel.modularcomputers.entity.BotGolem;
import com.payonel.modularcomputers.entity.EnergyField;
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
 * A golem's legs (user design, 2026-10-24): detached with no field holding it, it falls over and
 * powers off, its legs left standing; in its own field it flies on, walks nowhere and swings
 * nothing, but presses buttons (a tool in hand as an empty hand); it attaches its legs from close
 * by, from the ground or from its field (the field letting go); without them it presses no plate;
 * its use pops legs up; it's picked up as its torso; torso over legs crafts a golem, its parts kept.
 */
public class GolemLegsGameTest {
	/** Where the golem stands (BotGolemGameTest's start), facing east. */
	private static final BlockPos AT = new BlockPos(1, 1, 1);
	private static final BlockPos FRONT = AT.east();
	/** How far the test flies it, in blocks, and how near counts as there. */
	private static final double LIFT = 2;
	private static final double NEAR = 0.2;

	private static Arguments args(Object... values) {
		return new Arguments(java.util.Arrays.asList(values));
	}

	private static void same(GameTestHelper helper, Object got, Object expected, String what) {
		helper.assertTrue(java.util.Objects.equals(got, expected), what + ": expected " + expected + ", got " + got);
	}

	/** A golem running a program that waits on (its devices take data), with an energy field card when asked. */
	private static BotGolem golem(GameTestHelper helper, boolean field) {
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

	@GameTest(maxTicks = 100)
	public void withNoFieldItFallsOverAndPowersOffItsLegsLeftStanding(GameTestHelper helper) {
		BotGolem golem = golem(helper, false);
		float height = golem.getBbHeight();
		helper.startSequence()
			.thenExecuteAfter(3, () -> {
				helper.assertTrue(golem.machine().isPowered(), "it runs");
				helper.assertTrue(!golem.robotics().detachLegs(args()).failed(), "it detaches them");
			})
			.thenExecuteAfter(2, () -> {
				helper.assertTrue(!golem.legs(), "its torso alone");
				helper.assertTrue(golem.getBbHeight() < height, "shorter without them");
				helper.assertTrue(!golem.machine().isPowered(), "it powered off");
				same(helper, legs(helper).size(), 1, "its legs, left standing");
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = 200)
	public void inItsOwnFieldItFliesOnButWalksAndSwingsNothing(GameTestHelper helper) {
		helper.setBlock(FRONT.above(2), Blocks.STONE);
		helper.setBlock(FRONT.above(3), Blocks.STONE_BUTTON.defaultBlockState().setValue(ButtonBlock.FACE, AttachFace.FLOOR));
		BotGolem golem = golem(helper, true);
		golem.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
		double[] start = new double[1];
		helper.startSequence()
			.thenExecuteAfter(3, () -> {
				FieldCard card = field(golem);
				helper.assertTrue(!card.capture(args(FieldCard.SELF)).failed(), "it holds itself");
				helper.assertTrue(!golem.robotics().detachLegs(args()).failed(), "it detaches them");
				start[0] = golem.getY();
				card.move(args(0.0, 0.0, LIFT + 1));
			})
			.thenWaitUntil(() -> helper.assertTrue(golem.getY() - start[0] > LIFT + 1 - NEAR, "it flew up: " + (golem.getY() - start[0])))
			.thenExecute(() -> {
				helper.assertTrue(golem.machine().isPowered(), "still running");
				same(helper, legs(helper).size(), 1, "its legs below");
				same(helper, golem.robotics().walk(args(1.0)).error(), "no legs", "it walks nowhere");
				same(helper, golem.robotics().swing(args("front")).error(), "no legs: it can't swing without its footing", "no tools");
				// Its axe works as an empty hand: the button beside it is pressed.
				same(helper, golem.robotics().use(args("front")).first(), "used", "a button pressed");
				helper.assertTrue(golem.getMainHandItem().is(Items.IRON_AXE), "its axe back in its hand");
			})
			.thenExecuteAfter(1, () -> helper.assertTrue(helper.getBlockState(FRONT.above(3)).getValue(ButtonBlock.POWERED), "the button's pressed"))
			.thenSucceed();
	}

	@GameTest(maxTicks = 200)
	public void fromItsFieldItGoesOntoItsLegsAndTheFieldLetsGo(GameTestHelper helper) {
		BotGolem golem = golem(helper, true);
		List<Object> events = events(helper, golem);
		List<Object> fieldEvents = new java.util.ArrayList<>();
		helper.startSequence()
			.thenExecuteAfter(3, () -> {
				field(golem).capture(args(FieldCard.SELF));
				golem.robotics().detachLegs(args());
				field(golem).move(args(1.0, 0.0, 1.0));
			})
			.thenWaitUntil(() -> {
				Object event = field(golem).pull(args()).first();
				if (event != null) {
					fieldEvents.add(event);
				}
				helper.assertTrue(fieldEvents.contains(java.util.Map.of("event", "arrived")), "its field moved it: " + fieldEvents);
			})
			.thenExecute(() -> helper.assertTrue(!golem.robotics().attachLegs(args()).failed(), "it goes to its legs"))
			.thenWaitUntil(() -> same(helper, events.isEmpty() ? null : events.getLast(), java.util.Map.of("event", "attached"), "its event"))
			.thenExecute(() -> {
				helper.assertTrue(golem.legs(), "on its legs");
				helper.assertTrue(!EnergyField.held(golem), "its field let go");
				same(helper, legs(helper).size(), 0, "its legs are its own again");
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = 100)
	public void itAttachesOnlyLegsCloseBy(GameTestHelper helper) {
		BotGolem golem = golem(helper, false);
		helper.startSequence()
			.thenExecuteAfter(3, () -> {
				golem.legs(false);
				same(helper, golem.robotics().attachLegs(args()).error(), "no legs within 3 blocks", "none near");
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
				helper.assertTrue(golem.getMainHandItem().is(ModItems.BOT_GOLEM_LEGS), "in its hand: " + golem.getMainHandItem());
			})
			.thenSucceed();
	}

	@GameTest
	public void itsTorsoIsPickedUpAndCraftedOverItsLegsIntoAGolemWithItsParts(GameTestHelper helper) {
		BotGolem golem = BotGolemGameTest.golem(helper, board -> {
		});
		golem.legs(false);
		ItemStack torso = golem.asItem();
		helper.assertTrue(torso.is(ModItems.BOT_GOLEM_TORSO), "picked up as its torso: " + torso);
		CraftingInput input = CraftingInput.of(1, 2, List.of(torso, new ItemStack(ModItems.BOT_GOLEM_LEGS)));
		var recipe = helper.getLevel().recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());
		helper.assertTrue(recipe.isPresent(), "torso over legs is a recipe");
		ItemStack made = recipe.get().value().assemble(input);
		helper.assertTrue(made.is(ModItems.BOT_GOLEM), "a golem: " + made);
		same(helper, made.get(ModComponents.SERIAL), golem.board().boardId(), "its board's id kept");
		NonNullList<ItemStack> parts = NonNullList.withSize(CaseMenu.PART_SLOTS, ItemStack.EMPTY);
		made.getOrDefault(net.minecraft.core.component.DataComponents.CONTAINER, net.minecraft.world.item.component.ItemContainerContents.EMPTY).copyInto(parts);
		helper.assertTrue(parts.get(CaseMenu.CPU_SLOT).is(ModItems.TIER3_CPU), "its parts kept: " + parts);
		helper.succeed();
	}
}
