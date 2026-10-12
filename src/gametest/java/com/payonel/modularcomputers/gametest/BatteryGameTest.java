package com.payonel.modularcomputers.gametest;

import com.payonel.modularcomputers.block.AllInOneBlock;
import com.payonel.modularcomputers.block.AllInOneBlockEntity;
import com.payonel.modularcomputers.block.ModBlocks;
import com.payonel.modularcomputers.command.ModCommands;
import com.payonel.modularcomputers.hint.Hint;
import com.payonel.modularcomputers.hint.HintTooltip;
import com.payonel.modularcomputers.item.BatteryItem;
import com.payonel.modularcomputers.item.ModComponents;
import com.payonel.modularcomputers.item.ModItems;
import com.payonel.modularcomputers.machine.Machine;
import com.payonel.modularcomputers.menu.CaseMenu;
import com.payonel.modularcomputers.power.BatteryCharge;
import com.payonel.modularcomputers.power.PowerBuffer;
import com.payonel.modularcomputers.power.PowerUnits;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ItemLike;

/** Server game tests for batteries: crafting, charging, and running a computer. */
public class BatteryGameTest {
	private static final BlockPos POS = new BlockPos(1, 1, 1);
	/** A day of a running all-in-one, in power units. */
	private static final int DAY = PowerUnits.DAY;

	private static AllInOneBlockEntity computer(GameTestHelper helper, ItemStack battery) {
		helper.setBlock(POS, ModBlocks.ALL_IN_ONE.defaultBlockState().setValue(AllInOneBlock.FACING, Direction.SOUTH));
		AllInOneBlockEntity computer = helper.getBlockEntity(POS, AllInOneBlockEntity.class);
		computer.parts().setItem(CaseMenu.CPU_SLOT, new ItemStack(ModItems.TIER3_CPU));
		computer.parts().setItem(CaseMenu.EEPROM_SLOT, new ItemStack(ModItems.EEPROM));
		computer.parts().setItem(CaseMenu.UPS_SLOT, battery);
		TestPrograms.flash(computer, TestPrograms.HELLO);
		TestPrograms.withScreen(computer);
		return computer;
	}

	private static ItemStack battery(BatteryItem item, int units, long settledAt) {
		ItemStack stack = new ItemStack(item);
		stack.set(ModComponents.CHARGE, new BatteryCharge(units, settledAt));
		return stack;
	}

	/** What the crafting grid makes from these, read row by row; null is an empty cell. */
	private static ItemStack craft(GameTestHelper helper, int width, Object... cells) {
		List<ItemStack> stacks = new ArrayList<>();
		for (Object cell : cells) {
			stacks.add(cell == null ? ItemStack.EMPTY : cell instanceof ItemStack stack ? stack : new ItemStack((ItemLike) cell));
		}
		CraftingInput input = CraftingInput.of(width, cells.length / width, stacks);
		return helper.getLevel().recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
			.map(recipe -> recipe.value().assemble(input))
			.orElse(ItemStack.EMPTY);
	}

	@GameTest
	public void craftingAnItemAloneClearsItsIdSoItStacksAgain(GameTestHelper helper) {
		ItemStack used = new ItemStack(ModItems.WLAN_CARD);
		java.util.UUID serial = ModComponents.serial(used);
		helper.assertTrue(com.payonel.modularcomputers.hint.Hint.lines(com.payonel.modularcomputers.hint.Hint.of(used, false))
			.contains("Id: id:" + serial), "its hint shows its id");
		helper.assertTrue(!ItemStack.isSameItemSameComponents(used, new ItemStack(ModItems.WLAN_CARD)), "with an id, it doesn't stack with new ones");
		ItemStack cleared = craft(helper, 2, used, null, null, null);
		helper.assertTrue(cleared.is(ModItems.WLAN_CARD) && !cleared.has(ModComponents.SERIAL), "crafted alone: the same card, no id");
		helper.assertTrue(ItemStack.isSameItemSameComponents(cleared, new ItemStack(ModItems.WLAN_CARD)), "so it stacks again");
		helper.assertTrue(craft(helper, 2, new ItemStack(ModItems.WLAN_CARD), null, null, null).isEmpty(), "nothing to clear: no recipe");
		helper.assertTrue(craft(helper, 2, used, Items.STICK, null, null).isEmpty(), "only alone");
		helper.succeed();
	}

	@GameTest
	public void eachTierHasItsRecipeAndCraftedBatteriesStartEmpty(GameTestHelper helper) {
		ItemStack tier1 = craft(helper, 3,
			Items.COPPER_INGOT, Items.COPPER_INGOT, Items.COPPER_INGOT,
			Items.IRON_INGOT, Items.REDSTONE, Items.IRON_INGOT,
			Items.IRON_INGOT, Items.REDSTONE, Items.IRON_INGOT);
		helper.assertTrue(tier1.is(ModItems.TIER1_BATTERY), "tier 1 recipe, got " + tier1);
		helper.assertValueEqual(BatteryItem.percent(tier1), 0, "a crafted battery is empty");
		ItemStack tier2 = craft(helper, 3,
			Items.GOLD_INGOT, Items.GOLD_INGOT, Items.GOLD_INGOT,
			Items.IRON_INGOT, Items.REDSTONE, Items.IRON_INGOT,
			Items.SEA_LANTERN, Items.BLAZE_ROD, Items.SEA_LANTERN);
		helper.assertTrue(tier2.is(ModItems.TIER2_BATTERY), "tier 2 recipe, got " + tier2);
		ItemStack tier3 = craft(helper, 3,
			Items.DIAMOND, Items.DIAMOND, Items.DIAMOND,
			Items.NETHERITE_INGOT, Items.NETHER_STAR, Items.NETHERITE_INGOT,
			Items.NETHERITE_INGOT, Items.ENDER_EYE, Items.NETHERITE_INGOT);
		helper.assertTrue(tier3.is(ModItems.TIER3_BATTERY), "tier 3 recipe, got " + tier3);
		helper.assertValueEqual(BatteryItem.percent(tier3), 100, "tier 3 is always full");
		helper.succeed();
	}

	@GameTest
	public void aBatteryHintShowsItsChargeAndWithShiftHowToChargeIt(GameTestHelper helper) {
		helper.assertValueEqual(Hint.lines(Hint.of(ModItems.TIER1_BATTERY.charged(), false)),
			List.of("Charge: 100%", "Stored: 26.4M", "Hold [Shift] for details"), "a full tier 1");
		ItemStack tier2 = battery(ModItems.TIER2_BATTERY, 5 * DAY + DAY / 2 + 20 * 7 * PowerUnits.ALL_IN_ONE, 0);
		helper.assertValueEqual(Hint.lines(Hint.of(tier2, false)), List.of("Charge: 28%", "Stored: 14.5M", "Hold [Shift] for details"), "a tier 2");
		List<Hint.Element> details = Hint.of(tier2, true);
		helper.assertValueEqual(Hint.lines(details), List.of("Charge: 28%", "Stored: 14.5M", "Recharges itself while not in use",
			"Charge it in a crafting grid with a glowing item:", "Crafting:"), "tier 2 details");
		Hint.Icons icons = (Hint.Icons) details.stream().filter(Hint.Icons.class::isInstance).findFirst().orElseThrow();
		helper.assertValueEqual(icons.icons().stream().map(icon -> icon.stack().getItem()).toList(),
			List.of(Items.SEA_LANTERN, Items.GLOWSTONE, Items.SHROOMLIGHT, Items.GLOW_INK_SAC, Items.GLOW_LICHEN), "the glowing items");
		helper.assertValueEqual(icons.icons().stream().map(icon -> icon.label().getString()).toList(),
			List.of("+50%", "+13%", "+13%", "+5%", "+3%"), "what each adds to a tier 2");
		helper.assertTrue(details.getLast() instanceof Hint.Crafting crafting && crafting.item() == ModItems.TIER2_BATTERY, "ends with its crafting grid");
		helper.assertValueEqual(Hint.lines(Hint.of(new ItemStack(ModItems.TIER3_BATTERY), true)), List.of("Charge: 100%", "Stored: inf", "Crafting:"), "tier 3");
		helper.succeed();
	}

	@GameTest
	public void everyModItemHasAHintWithItsCraftingGrid(GameTestHelper helper) {
		for (var item : ModCommands.modItems()) {
			ItemStack stack = new ItemStack(item);
			helper.assertTrue(item.getTooltipImage(stack).orElse(null) instanceof HintTooltip, item + " shows a hint");
			helper.assertTrue(Hint.of(stack, true).getLast() instanceof Hint.Crafting crafting && crafting.item() == item, item + " shows how it's crafted");
		}
		helper.succeed();
	}

	@GameTest
	public void glowingMaterialsChargeABatteryInA2x2Grid(GameTestHelper helper) {
		ItemStack empty = new ItemStack(ModItems.TIER1_BATTERY);
		helper.assertValueEqual(BatteryItem.percent(craft(helper, 2, empty, Items.GLOW_LICHEN, null, null)), 5, "glow lichen");
		helper.assertValueEqual(BatteryItem.percent(craft(helper, 2, null, Items.GLOW_INK_SAC, empty, null)), 10, "glow ink sac");
		helper.assertValueEqual(BatteryItem.percent(craft(helper, 2, empty, Items.GLOWSTONE, null, null)), 25, "glowstone block");
		helper.assertValueEqual(BatteryItem.percent(craft(helper, 2, empty, Items.SHROOMLIGHT, null, null)), 25, "shroomlight");
		helper.assertValueEqual(BatteryItem.percent(craft(helper, 2, empty, Items.SEA_LANTERN, null, null)), 100, "sea lantern");
		helper.assertValueEqual(BatteryItem.percent(craft(helper, 2, empty, Items.GLOWSTONE, Items.SHROOMLIGHT, Items.GLOW_INK_SAC)), 60,
			"materials add up");
		helper.assertValueEqual(BatteryItem.percent(craft(helper, 2, battery(ModItems.TIER1_BATTERY, 9 * DAY, 0), Items.GLOWSTONE, null, null)), 100,
			"up to full");
		helper.assertValueEqual(BatteryItem.percent(craft(helper, 2, new ItemStack(ModItems.TIER2_BATTERY), Items.SEA_LANTERN, null, null)), 50,
			"tier 2 holds twice as much");
		helper.succeed();
	}

	@GameTest
	public void chargingTakesOnlyOneBatteryThatNeedsIt(GameTestHelper helper) {
		helper.assertTrue(craft(helper, 2, ModItems.TIER1_BATTERY.charged(), Items.GLOWSTONE, null, null).isEmpty(), "a full battery");
		helper.assertTrue(craft(helper, 2, new ItemStack(ModItems.TIER3_BATTERY), Items.GLOWSTONE, null, null).isEmpty(), "tier 3");
		helper.assertTrue(craft(helper, 2, new ItemStack(ModItems.TIER1_BATTERY), null, null, null).isEmpty(), "no material");
		helper.assertTrue(craft(helper, 2, new ItemStack(ModItems.TIER1_BATTERY), Items.GLOWSTONE, Items.DIRT, null).isEmpty(), "something else");
		helper.assertTrue(craft(helper, 2, new ItemStack(ModItems.TIER1_BATTERY), new ItemStack(ModItems.TIER1_BATTERY), Items.GLOWSTONE, null)
			.isEmpty(), "two batteries");
		helper.succeed();
	}

	private static BatteryCharge charge(AllInOneBlockEntity computer) {
		return BatteryItem.charge(computer.parts().getItem(CaseMenu.UPS_SLOT));
	}

	@GameTest
	public void withoutPowerThePowerButtonDoesNothing(GameTestHelper helper) {
		for (ItemStack battery : Arrays.asList(ItemStack.EMPTY, new ItemStack(ModItems.TIER1_BATTERY))) {
			AllInOneBlockEntity computer = computer(helper, battery);
			helper.assertValueEqual(computer.pressPower(), Machine.PowerPress.NO_POWER, "press result with " + battery);
			helper.assertTrue(!computer.machine().isPowered(), "stays off");
			helper.assertBlockProperty(POS, AllInOneBlock.POWERED, false); // no flash: there's no power to light it
			helper.assertTrue(computer.terminal().buffer().isBlank(), "nothing runs");
		}
		helper.succeed();
	}

	@GameTest
	public void theCaseDrawsFromTheLastBayFirst(GameTestHelper helper) {
		AllInOneBlockEntity computer = computer(helper, ItemStack.EMPTY);
		computer.power().set(PowerBuffer.CAPACITY); // full: the batteries keep their charge going in
		int first = CaseMenu.UPS_SLOT;
		int last = CaseMenu.UPS_SLOT + CaseMenu.UPS_BAYS - 1;
		computer.parts().setItem(first, battery(ModItems.TIER1_BATTERY, DAY / 2, BatteryCharge.UNSETTLED));
		computer.parts().setItem(last, battery(ModItems.TIER1_BATTERY, DAY / 2, BatteryCharge.UNSETTLED));
		computer.power().set(DAY / 2);
		computer.parts().setItem(CaseMenu.RAM_SLOTS, new ItemStack(ModItems.TIER1_RAM)); // any part change tops the case up
		helper.assertValueEqual(computer.power().level(), DAY, "topped up");
		helper.assertValueEqual(BatteryItem.charge(computer.parts().getItem(last)).units(), 0, "out of the last bay");
		helper.assertValueEqual(BatteryItem.charge(computer.parts().getItem(first)).units(), DAY / 2, "the first bay untouched");
		computer.power().set(0);
		computer.parts().setItem(CaseMenu.RAM_SLOTS, ItemStack.EMPTY);
		helper.assertValueEqual(computer.power().level(), DAY / 2, "then the first bay");
		helper.assertValueEqual(BatteryItem.charge(computer.parts().getItem(first)).units(), 0, "now flat too");
		helper.succeed();
	}

	@GameTest
	public void aBatteryGoingInFillsTheCaseBufferWithADay(GameTestHelper helper) {
		AllInOneBlockEntity computer = computer(helper, ModItems.TIER1_BATTERY.charged());
		helper.assertValueEqual(computer.power().level(), DAY, "the case takes a day's worth");
		helper.assertValueEqual(charge(computer).units(), 9 * DAY, "out of the battery");
		computer.parts().setItem(CaseMenu.UPS_SLOT, battery(ModItems.TIER1_BATTERY, DAY / 4, BatteryCharge.UNSETTLED));
		helper.assertValueEqual(computer.power().level(), DAY, "already full: takes nothing");
		helper.assertValueEqual(charge(computer).units(), DAY / 4, "the battery keeps its charge");

		AllInOneBlockEntity other = computer(helper, ItemStack.EMPTY);
		other.power().set(0);
		other.parts().setItem(CaseMenu.UPS_SLOT, battery(ModItems.TIER1_BATTERY, DAY / 2, BatteryCharge.UNSETTLED));
		helper.assertValueEqual(other.power().percent(), 50, "takes what the battery has");
		helper.assertValueEqual(charge(other).units(), 0, "leaving it flat");
		helper.succeed();
	}

	@GameTest
	public void thePowerButtonIsFadedWithoutCasePowerOrACpu(GameTestHelper helper) {
		AllInOneBlockEntity computer = computer(helper, ItemStack.EMPTY);
		helper.assertBlockProperty(POS, AllInOneBlock.READY, false); // a CPU but no power
		computer.parts().setItem(CaseMenu.UPS_SLOT, new ItemStack(ModItems.TIER1_BATTERY));
		helper.assertBlockProperty(POS, AllInOneBlock.READY, false); // a flat battery
		computer.parts().setItem(CaseMenu.UPS_SLOT, ModItems.TIER1_BATTERY.charged());
		helper.assertBlockProperty(POS, AllInOneBlock.READY, true);
		computer.parts().setItem(CaseMenu.UPS_SLOT, ItemStack.EMPTY);
		helper.assertBlockProperty(POS, AllInOneBlock.READY, true); // the case holds a day
		computer.parts().setItem(CaseMenu.CPU_SLOT, ItemStack.EMPTY);
		helper.assertBlockProperty(POS, AllInOneBlock.READY, false); // powered, but no CPU
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void aTier2RechargingFromFlatLightsThePowerButton(GameTestHelper helper) {
		long now = helper.getLevel().getGameTime();
		computer(helper, battery(ModItems.TIER2_BATTERY, 0, now));
		helper.assertBlockProperty(POS, AllInOneBlock.READY, false);
		helper.succeedWhen(() -> helper.assertBlockProperty(POS, AllInOneBlock.READY, true));
	}

	@GameTest(maxTicks = 100)
	public void aRunningComputerDiesWhenTheCaseRunsOutOfPower(GameTestHelper helper) {
		AllInOneBlockEntity computer = computer(helper, battery(ModItems.TIER1_BATTERY, 30, BatteryCharge.UNSETTLED));
		helper.assertValueEqual(computer.power().level(), 30, "the case took all the battery had");
		helper.assertValueEqual(computer.pressPower(), Machine.PowerPress.ON, "press result");
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(!computer.machine().isPowered(), "ran out"))
			.thenExecute(() -> {
				helper.assertBlockProperty(POS, AllInOneBlock.POWERED, false);
				helper.assertBlockProperty(POS, AllInOneBlock.READY, false); // faded
				helper.assertTrue(computer.terminal().buffer().isBlank(), "the display goes dark");
				helper.assertTrue(computer.power().isEmpty(), "the case is empty");
				helper.assertValueEqual(computer.pressPower(), Machine.PowerPress.NO_POWER, "won't start again");
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = 100)
	public void pullingTheBatteryLeavesARunningComputerOnTheCaseBuffer(GameTestHelper helper) {
		AllInOneBlockEntity computer = computer(helper, ModItems.TIER1_BATTERY.charged());
		computer.pressPower();
		computer.parts().setItem(CaseMenu.UPS_SLOT, ItemStack.EMPTY);
		helper.assertTrue(computer.machine().isPowered(), "still running");
		helper.succeedWhen(() -> {
			helper.assertTrue(computer.power().level() < DAY, "the buffer runs down");
			helper.assertTrue(computer.machine().isPowered(), "still running");
		});
	}

	@GameTest(maxTicks = 100)
	public void theCaseViewFollowsTheCasePowerAsItRunsDown(GameTestHelper helper) {
		AllInOneBlockEntity computer = computer(helper, ModItems.TIER1_BATTERY.charged());
		computer.pressPower();
		computer.parts().setItem(CaseMenu.UPS_SLOT, ItemStack.EMPTY);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		CaseMenu menu = (CaseMenu) computer.createMenu(0, player.getInventory(), player);
		helper.assertValueEqual(menu.powerLevel(), DAY, "full to start");
		helper.succeedWhen(() -> {
			helper.assertTrue(menu.powerLevel() < DAY, "the menu's power runs down");
			helper.assertValueEqual(menu.powerLevel(), computer.power().level(), "with the case's");
			helper.assertTrue(menu.load() > 0, "and what the computer draws now, its load");
		});
	}

		@GameTest(maxTicks = 200)
	public void aRunningComputerDrawsOnTheBatteryAndATier1DoesNotRecharge(GameTestHelper helper) {
		int start = 5 * DAY;
		AllInOneBlockEntity computer = computer(helper, battery(ModItems.TIER1_BATTERY, start, BatteryCharge.UNSETTLED));
		int afterFill = start - DAY;
		TestPrograms.flash(computer, TestPrograms.BUSY); // a CPU only draws while it executes
		computer.pressPower();
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(charge(computer).units() < afterFill, "the case tops up from the battery"))
			.thenExecute(() -> helper.assertValueEqual(computer.power().level(), DAY, "keeping the buffer full"))
			.thenExecute(computer::pressPower)
			.thenIdle(45)
			.thenExecute(() -> helper.assertTrue(charge(computer).units() <= afterFill - 20, "a tier 1 stays down while off"))
			.thenSucceed();
	}

	@GameTest(maxTicks = 100)
	public void aTier2RechargesWhileTheComputerIsOff(GameTestHelper helper) {
		long now = helper.getLevel().getGameTime();
		AllInOneBlockEntity computer = computer(helper, battery(ModItems.TIER2_BATTERY, DAY + 100, now - 1000));
		helper.succeedWhen(() -> helper.assertTrue(charge(computer).units() >= 100 + 1000 * PowerUnits.ALL_IN_ONE / 2,
			"regains half the time it rested, got " + charge(computer)));
	}

	@GameTest(maxTicks = 100)
	public void aTier3NeverRunsOut(GameTestHelper helper) {
		AllInOneBlockEntity computer = computer(helper, new ItemStack(ModItems.TIER3_BATTERY));
		helper.assertValueEqual(computer.pressPower(), Machine.PowerPress.ON, "press result");
		helper.startSequence()
			.thenIdle(45)
			.thenExecute(() -> {
				helper.assertTrue(computer.machine().isPowered(), "still on");
				helper.assertValueEqual(computer.power().level(), DAY, "the case stays full");
				helper.assertValueEqual(BatteryItem.percent(computer.parts().getItem(CaseMenu.UPS_SLOT)), 100, "still full");
			})
			.thenSucceed();
	}
}
