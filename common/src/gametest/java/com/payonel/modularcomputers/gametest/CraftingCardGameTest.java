package com.payonel.modularcomputers.gametest;

import com.payonel.modularcomputers.block.AllInOneBlock;
import com.payonel.modularcomputers.block.AllInOneBlockEntity;
import com.payonel.modularcomputers.block.ModBlocks;
import com.payonel.modularcomputers.device.CraftingCard;
import com.payonel.modularcomputers.entity.BotGolem;
import com.payonel.modularcomputers.item.ModItems;
import com.payonel.modularcomputers.menu.CaseMenu;
import com.payonel.modularcomputers.runtime.Arguments;
import com.payonel.modularcomputers.runtime.Result;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;

/**
 * The crafting card (user design, 2026-10-24): in a robot, a program crafts from its grid into its
 * storage, a craft taking time, two cards crafting at once; a craft with no room fails, its grid
 * left as it was; what the robot breaks goes to its storage; drop fills a container there and
 * drops the rest; store answers true only when nothing's left. In a case its methods need
 * robotics, but a player crafts with it by hand in the case's view.
 */
public class CraftingCardGameTest {
	/** Where the golem stands, facing east (BotGolemGameTest's), and what's in front of and below it. */
	private static final BlockPos AT = new BlockPos(1, 1, 1);
	private static final BlockPos FRONT = AT.east();
	/** The lanes the cards go in (a golem's second and third: its first is its screen's). */
	private static final int LANE = 1;
	private static final int SECOND_LANE = 2;

	/** A crafting card holding these, by slot (0 to 24). */
	private static ItemStack card(Map<Integer, ItemStack> slots) {
		ItemStack card = new ItemStack(ModItems.CRAFTING_CARD);
		NonNullList<ItemStack> items = NonNullList.withSize(CraftingCard.SLOTS, ItemStack.EMPTY);
		slots.forEach(items::set);
		card.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
		return card;
	}

	/** Two planks, one above the other: four sticks. */
	private static Map<Integer, ItemStack> sticks(int planks) {
		return Map.of(0, new ItemStack(Items.OAK_PLANKS, planks), 3, new ItemStack(Items.OAK_PLANKS, planks));
	}

	/** What a card's slot holds (0 to 24), on the card in a golem's lane. */
	private static ItemStack slot(BotGolem golem, int lane, int slot) {
		NonNullList<ItemStack> items = NonNullList.withSize(CraftingCard.SLOTS, ItemStack.EMPTY);
		golem.board().parts().getItem(CaseMenu.EXP_SLOTS + lane).getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(items);
		return items.get(slot);
	}

	/** A golem, running a program that waits on (its cards take data), with crafting cards in its lanes. */
	private static BotGolem golem(GameTestHelper helper, Map<Integer, ItemStack> cards) {
		BotGolem golem = BotGolemGameTest.golem(helper, board -> {
			while (true) {
				board.waitForInterrupt(1000, "nothing");
			}
		});
		cards.forEach((lane, card) -> golem.board().parts().setItem(CaseMenu.EXP_SLOTS + lane, card));
		helper.runAfterDelay(1, () -> golem.board().pressPower());
		return golem;
	}

	/** The crafting card in a lane (cards are listed in their lanes' order). */
	private static CraftingCard crafting(com.payonel.modularcomputers.board.BoardAssembly board, int lane) {
		List<CraftingCard> cards = board.cards().stream().filter(CraftingCard.class::isInstance).map(CraftingCard.class::cast).toList();
		int nth = 0;
		for (int before = 0; before < lane; before++) {
			nth += com.payonel.modularcomputers.item.ModItems.cardKind(board.parts().getItem(CaseMenu.EXP_SLOTS + before))
				.filter(kind -> kind == com.payonel.modularcomputers.device.CardKind.CRAFTING).isPresent() ? 1 : 0;
		}
		return nth < cards.size() ? cards.get(nth) : null;
	}

	private static CraftingCard crafting(BotGolem golem, int lane) {
		return crafting(golem.board(), lane);
	}

	private static Arguments args(Object... values) {
		return new Arguments(List.of(values));
	}

	private static void same(GameTestHelper helper, Object got, Object expected, String what) {
		helper.assertTrue(java.util.Objects.equals(got, expected), what + ": expected " + expected + ", got " + got);
	}

	@GameTest(maxTicks = 100)
	public void aRobotCraftsFromItsGridIntoItsStorageInTime(GameTestHelper helper) {
		BotGolem golem = golem(helper, Map.of(LANE, card(sticks(1))));
		long[] asked = {-1};
		List<Object> events = new ArrayList<>();
		helper.runAfterDelay(3, () -> {
			Result started = crafting(golem, LANE).craft(args());
			helper.assertTrue(!started.failed(), "it started: " + started.error());
			asked[0] = helper.getTick();
		});
		helper.onEachTick(() -> {
			if (asked[0] >= 0 && crafting(golem, LANE) instanceof CraftingCard card) {
				Object event = card.pull(args()).first();
				if (event != null) {
					events.add(event);
				}
			}
		});
		helper.succeedWhen(() -> {
			same(helper, events.isEmpty() ? null : events.getFirst(), Map.of("event", "crafted", "name", "minecraft:stick", "count", 4L), "its event");
			helper.assertTrue(slot(golem, LANE, CraftingCard.GRID).is(Items.STICK) && slot(golem, LANE, CraftingCard.GRID).getCount() == 4,
				"4 sticks in its storage: " + slot(golem, LANE, CraftingCard.GRID));
			helper.assertTrue(slot(golem, LANE, 0).isEmpty() && slot(golem, LANE, 3).isEmpty(), "its grid used");
			helper.assertTrue(helper.getTick() - asked[0] >= CraftingCard.CRAFT_TICKS, "it took a craft's time");
		});
	}

	@GameTest(maxTicks = 100)
	public void twoCardsCraftAtOnce(GameTestHelper helper) {
		int crafts = 3;
		BotGolem golem = golem(helper, Map.of(LANE, card(sticks(crafts)), SECOND_LANE, card(sticks(crafts))));
		long[] asked = {-1};
		helper.runAfterDelay(3, () -> {
			crafting(golem, LANE).craft(args((long) crafts));
			crafting(golem, SECOND_LANE).craft(args((long) crafts));
			asked[0] = helper.getTick();
		});
		helper.succeedWhen(() -> {
			for (int lane : new int[] {LANE, SECOND_LANE}) {
				helper.assertTrue(slot(golem, lane, CraftingCard.GRID).getCount() == 4 * crafts, "lane " + lane + "'s sticks: " + slot(golem, lane, CraftingCard.GRID));
			}
			// Each took its three crafts' time, side by side: not six crafts' time.
			long took = helper.getTick() - asked[0];
			helper.assertTrue(took < 2 * crafts * CraftingCard.CRAFT_TICKS, "they crafted at once: " + took + " ticks");
		});
	}

	@GameTest(maxTicks = 100)
	public void aCraftWithNoRoomFailsAndLeavesItsGrid(GameTestHelper helper) {
		java.util.Map<Integer, ItemStack> full = new java.util.HashMap<>(sticks(1));
		for (int slot = CraftingCard.GRID; slot < CraftingCard.SLOTS; slot++) {
			full.put(slot, new ItemStack(Items.DIRT, 64));
		}
		BotGolem golem = golem(helper, Map.of(LANE, card(full)));
		List<Object> events = new ArrayList<>();
		helper.runAfterDelay(3, () -> crafting(golem, LANE).craft(args()));
		helper.onEachTick(() -> {
			if (crafting(golem, LANE) instanceof CraftingCard card) {
				Object event = card.pull(args()).first();
				if (event != null) {
					events.add(event);
				}
			}
		});
		helper.succeedWhen(() -> {
			same(helper, events.isEmpty() ? null : events.getFirst(), Map.of("event", "failed", "count", 0L, "why", "no room"), "its event");
			helper.assertTrue(slot(golem, LANE, 0).is(Items.OAK_PLANKS) && slot(golem, LANE, 3).is(Items.OAK_PLANKS), "its grid as it was");
		});
	}

	@GameTest(maxTicks = 200)
	public void whatARobotBreaksGoesToItsStorage(GameTestHelper helper) {
		helper.setBlock(FRONT, Blocks.OAK_LOG);
		BotGolem golem = golem(helper, Map.of(LANE, card(Map.of())));
		golem.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
		helper.runAfterDelay(3, () -> golem.robotics().swing(args("front")));
		helper.succeedWhen(() -> {
			helper.assertBlockPresent(Blocks.AIR, FRONT);
			helper.assertTrue(slot(golem, LANE, CraftingCard.GRID).is(Items.OAK_LOG), "the log in its storage: " + slot(golem, LANE, CraftingCard.GRID));
			helper.assertTrue(golem.getOffhandItem().isEmpty(), "its off hand caught nothing: storage first");
		});
	}

	@GameTest(maxTicks = 100)
	public void storeAnswersTrueOnlyWhenNothingsLeftAndDropFillsAContainerThenTheWorld(GameTestHelper helper) {
		helper.setBlock(FRONT, Blocks.CHEST);
		ChestBlockEntity chest = helper.getBlockEntity(FRONT, ChestBlockEntity.class);
		for (int slot = 1; slot < chest.getContainerSize(); slot++) {
			chest.setItem(slot, new ItemStack(Items.STONE, 64)); // room in one slot only
		}
		BotGolem golem = golem(helper, Map.of(LANE, card(Map.of(CraftingCard.GRID, new ItemStack(Items.DIRT, 64),
			CraftingCard.GRID + 1, new ItemStack(Items.DIRT, 10)))));
		List<Object> answers = new ArrayList<>();
		helper.runAfterDelay(3, () -> {
			CraftingCard card = crafting(golem, LANE);
			Result stored = card.store(args());
			answers.add(stored.values()); // in front: 64 go, 10 are left
			chest.setItem(0, ItemStack.EMPTY);
			chest.setItem(0, new ItemStack(Items.DIRT, 60)); // room for 4 more
			Result dropped = card.drop(args("front"));
			answers.add(dropped.values()); // 4 into the chest, 6 into the world
			answers.add(card.store(args()).values()); // nothing left: true, 0
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(answers.size() == 3, "its answers: " + answers);
			same(helper, answers.get(0), List.of(false, 10L), "store, the chest full: false and how many are left");
			same(helper, answers.get(1), List.of(6L), "drop: how many went into the world");
			same(helper, answers.get(2), List.of(true, 0L), "store with nothing left");
			helper.assertTrue(chest.getItem(0).getCount() == 64, "the chest filled first");
			int inWorld = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(AT)).inflate(2),
				item -> item.getItem().is(Items.DIRT)).stream().mapToInt(item -> item.getItem().getCount()).sum();
			same(helper, inWorld, 6, "the rest in the world");
		});
	}

	@GameTest(maxTicks = 100)
	public void inACaseItsMethodsNeedRoboticsButAPlayerCraftsByHand(GameTestHelper helper) {
		BlockPos pos = new BlockPos(4, 2, 4);
		helper.setBlock(pos, ModBlocks.TIER3_CASE.defaultBlockState().setValue(AllInOneBlock.FACING, Direction.SOUTH));
		AllInOneBlockEntity computer = helper.getBlockEntity(pos, AllInOneBlockEntity.class);
		computer.parts().setItem(CaseMenu.EXP_SLOTS + LANE, card(sticks(1)));
		CraftingCard card = crafting(computer.board(), LANE);
		same(helper, card.list(args()).error(), CraftingCard.NO_ROBOTICS, "a program's call");
		same(helper, card.craft(args()).error(), CraftingCard.NO_ROBOTICS, "a program's craft");
		var player = GameTests.serverPlayer(helper);
		CaseMenu menu = (CaseMenu) computer.createMenu(1, player.getInventory(), player);
		int lane = CaseMenu.CRAFT_SLOTS + LANE * CaseMenu.CRAFT_LANE_SLOTS;
		menu.slotsChanged(menu.slots.get(lane).container); // (its grid as it opened: what it makes)
		ItemStack result = menu.slots.get(lane + CaseMenu.CRAFT_RESULT).getItem();
		helper.assertTrue(result.is(Items.STICK) && result.getCount() == 4, "its result: four sticks, got " + result);
		menu.clicked(lane + CaseMenu.CRAFT_RESULT, 0, ContainerInput.PICKUP, player);
		helper.assertTrue(menu.getCarried().is(Items.STICK) && menu.getCarried().getCount() == 4, "taken: " + menu.getCarried());
		NonNullList<ItemStack> items = NonNullList.withSize(CraftingCard.SLOTS, ItemStack.EMPTY);
		computer.parts().getItem(CaseMenu.EXP_SLOTS + LANE).getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(items);
		helper.assertTrue(items.get(0).isEmpty() && items.get(3).isEmpty(), "its grid used, on the card: " + items);
		helper.succeed();
	}
}
