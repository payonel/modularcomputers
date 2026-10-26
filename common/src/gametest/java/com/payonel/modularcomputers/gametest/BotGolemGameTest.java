package com.payonel.modularcomputers.gametest;

import com.payonel.modularcomputers.entity.BotGolem;
import com.payonel.modularcomputers.entity.ModEntities;
import com.payonel.modularcomputers.item.ModComponents;
import com.payonel.modularcomputers.item.ModItems;
import com.payonel.modularcomputers.machine.GolemLayout;
import com.payonel.modularcomputers.machine.Motherboard;
import com.payonel.modularcomputers.machine.Program;
import com.payonel.modularcomputers.menu.CaseMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The bot golem in a world (user design, 2026-10-24): its robotics device walks it to a point
 * relative to itself, at a player's walking pace, and says it arrived; a walk with no way there
 * ends blocked; a turn turns it; it does nothing on its own; a player's hit picks it up, its parts
 * in its item.
 */
public class BotGolemGameTest {
	/** Where it stands: the test space's corner row, on the floor the test lays. */
	private static final BlockPos START = new BlockPos(1, 1, 1);
	/** Facing east (+X), along the row: Minecraft's heading for east. */
	static final float EAST = -90;
	/** How far the test's walks go, in blocks. */
	private static final long WALK = 5;
	/** A player's walking speed, in blocks a second (the game's), and the margin its pace is held to. */
	private static final double PLAYER_PACE = 4.317;
	private static final double PACE_MARGIN = 0.25;
	private static final double TICKS_A_SECOND = 20;
	static final String ROBOTICS = Motherboard.exp(GolemLayout.ROBOTICS_LANE);
	/** How long its program waits for its robotics device at a time, in seconds. */
	private static final double WAIT = 1000;

	/** A stone floor under the test space. */
	private static void floor(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
	}

	/** A tier 2 golem at the start, facing east, with a board: running {@code program} once pressed on. */
	static BotGolem golem(GameTestHelper helper, Program program) {
		return golem(helper, program, ModEntities.TIER2_BOT_GOLEM);
	}

	/** A golem of a tier at the start, facing east, with a board: running {@code program} once pressed on. */
	static BotGolem golem(GameTestHelper helper, Program program, net.minecraft.world.entity.EntityType<BotGolem> tier) {
		floor(helper);
		BotGolem golem = tier.create(helper.getLevel(), EntitySpawnReason.COMMAND);
		Vec3 at = helper.absoluteVec(Vec3.atBottomCenterOf(START));
		golem.snapTo(at.x, at.y, at.z, EAST, 0);
		golem.setYBodyRot(EAST);
		golem.setYHeadRot(EAST);
		helper.getLevel().addFreshEntity(golem);
		var parts = golem.board().parts();
		parts.setItem(CaseMenu.CPU_SLOT, new ItemStack(ModItems.TIER3_CPU));
		parts.setItem(CaseMenu.RAM_SLOTS, new ItemStack(ModItems.TIER3_RAM));
		parts.setItem(CaseMenu.EEPROM_SLOT, new ItemStack(ModItems.EEPROM));
		parts.setItem(CaseMenu.UPS_SLOT, ModItems.TIER1_BATTERY.charged());
		golem.board().machine().board().eeprom().orElseThrow().flash(program);
		return golem;
	}

	/** A program that does {@code method}(args) on its robotics device, then keeps every event it hears. */
	private static Program doing(List<Map<?, ?>> heard, String method, Object... args) {
		return board -> {
			var answer = board.invoke(ROBOTICS, method, args);
			if (answer.failed()) {
				synchronized (heard) {
					heard.add(Map.of("event", "refused", "why", answer.error()));
				}
			}
			while (true) {
				board.waitForInterrupt(WAIT, ROBOTICS);
				Object event;
				while ((event = board.invoke(ROBOTICS, "pull").first()) != null) {
					synchronized (heard) {
						heard.add((Map<?, ?>) event);
					}
				}
			}
		};
	}

	/** Where it stands, in the test's space. */
	private static BlockPos at(GameTestHelper helper, BotGolem golem) {
		return golem.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO));
	}

	private static Object last(List<Map<?, ?>> heard) {
		synchronized (heard) {
			return heard.isEmpty() ? null : heard.get(heard.size() - 1).get("event");
		}
	}

	@GameTest(maxTicks = 200)
	public void itWalksWhereItsToldAtAPlayersPaceAndSaysItArrived(GameTestHelper helper) {
		List<Map<?, ?>> heard = new ArrayList<>();
		BotGolem golem = golem(helper, doing(heard, "walk", WALK));
		long[] started = {-1};
		long[] arrived = {-1};
		helper.onEachTick(() -> {
			if (started[0] < 0 && golem.robotics().state().equals("walking")) {
				started[0] = helper.getTick();
			}
			if (arrived[0] < 0 && "arrived".equals(last(heard))) {
				arrived[0] = helper.getTick();
			}
		});
		helper.runAfterDelay(1, () -> golem.board().pressPower());
		helper.succeedWhen(() -> {
			helper.assertTrue(arrived[0] >= 0, "arrived: " + heard + " (powered " + golem.machine().isPowered() + ", " + golem.robotics().state()
				+ ", at " + at(helper, golem) + ", bus " + golem.board().busEntries().size() + ", "
				+ golem.machine().board().virtualBus().console().screen().row(0) + ")");
			BlockPos there = at(helper, golem);
			helper.assertValueEqual(there, START.east((int) WALK), "five blocks east, the way it faced");
			double pace = WALK / ((arrived[0] - started[0]) / TICKS_A_SECOND);
			helper.assertTrue(Math.abs(pace - PLAYER_PACE) <= PLAYER_PACE * PACE_MARGIN, "a player's walking pace: " + pace + " blocks a second");
		});
	}

	@GameTest(maxTicks = 200)
	public void aWalkWithNoWayThereEndsBlocked(GameTestHelper helper) {
		List<Map<?, ?>> heard = new ArrayList<>();
		BotGolem golem = golem(helper, doing(heard, "walk", WALK));
		for (int y = 1; y <= 3; y++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(new BlockPos(3, y, z), Blocks.STONE); // a wall across its way
			}
		}
		helper.runAfterDelay(1, () -> golem.board().pressPower());
		helper.succeedWhen(() -> {
			synchronized (heard) {
				helper.assertTrue(!heard.isEmpty() && "blocked".equals(heard.get(heard.size() - 1).get("event")), "blocked: " + heard);
			}
			helper.assertTrue(at(helper, golem).getX() < 3, "on its side of the wall");
		});
	}

	@GameTest(maxTicks = 100)
	public void aTurnTurnsItAndSaysSo(GameTestHelper helper) {
		List<Map<?, ?>> heard = new ArrayList<>();
		BotGolem golem = golem(helper, doing(heard, "turn", "right"));
		helper.runAfterDelay(1, () -> golem.board().pressPower());
		helper.succeedWhen(() -> {
			helper.assertTrue("turned".equals(last(heard)), "turned: " + heard);
			helper.assertValueEqual(Math.round(net.minecraft.util.Mth.wrapDegrees(golem.getYRot())), 0, "from east, right: south (0)");
		});
	}

	@GameTest(maxTicks = 60)
	public void itDoesNothingOnItsOwn(GameTestHelper helper) {
		BotGolem golem = golem(helper, board -> {
			while (true) {
				board.waitForInterrupt(WAIT, "nothing");
			}
		});
		helper.runAfterDelay(1, () -> golem.board().pressPower());
		helper.runAfterDelay(50, () -> {
			helper.assertValueEqual(at(helper, golem), START, "where it was put (at " + golem.position() + ", put at "
				+ helper.absoluteVec(Vec3.atBottomCenterOf(START)) + ", powered " + golem.machine().isPowered() + ")");
			helper.assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(golem.getYRot() - EAST)) < 1, "facing as it was");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void onItsLegsNothingShovesIt(GameTestHelper helper) {
		BotGolem golem = golem(helper, board -> board.halt());
		// A pig walking into it, a push, a knockback: once it stands, none moves it.
		var pig = helper.spawn(net.minecraft.world.entity.EntityTypes.PIG, START);
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(golem.onGround(), "standing"))
			.thenExecute(() -> {
				golem.push(1, 0, 1);
				golem.knockback(2, 1, 0, helper.getLevel().damageSources().generic(), 1, true);
				golem.push(pig);
				helper.assertTrue(!golem.isPushable(), "not pushable on its legs");
			})
			.thenIdle(20)
			.thenExecute(() -> helper.assertValueEqual(at(helper, golem), START, "where it stood"))
			.thenSucceed();
	}

	@GameTest(maxTicks = 40)
	public void itsSolidNothingWalksThroughIt(GameTestHelper helper) {
		BotGolem golem = golem(helper, board -> board.halt());
		var pig = helper.spawn(net.minecraft.world.entity.EntityTypes.PIG, START.west().west().east()); // (a block west of it: START is at x 1)
		pig.snapTo(golem.getX() - 1.5, golem.getY(), golem.getZ(), 0, 0);
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(golem.onGround() && pig.onGround(), "both standing"))
			.thenExecute(() -> {
				pig.move(net.minecraft.world.entity.MoverType.SELF, new Vec3(3, 0, 0)); // (straight into it, and past)
				helper.assertTrue(pig.getBoundingBox().maxX <= golem.getBoundingBox().minX + 1e-3,
					"stopped at it: the pig's edge " + pig.getBoundingBox().maxX + ", the golem's " + golem.getBoundingBox().minX);
			})
			.thenSucceed();
	}

	@GameTest
	public void aPlayersHitPicksItUpWithItsParts(GameTestHelper helper) {
		BotGolem golem = golem(helper, board -> board.halt());
		golem.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE));
		golem.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, new ItemStack(net.minecraft.world.item.Items.COBBLESTONE));
		var player = GameTests.serverPlayer(helper);
		java.util.UUID board = golem.board().boardId();
		golem.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), 1);
		helper.assertTrue(golem.isRemoved(), "gone from the world");
		var items = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, golem.getBoundingBox().inflate(2));
		helper.assertTrue(items.size() == 1 && items.get(0).getItem().is(ModItems.TIER2_BOT_GOLEM), "dropped as its item: " + items);
		ItemStack item = items.get(0).getItem();
		List<ItemStack> parts = item.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyItemCopyStream().toList();
		helper.assertTrue(parts.stream().anyMatch(part -> part.is(ModItems.TIER3_CPU)), "its CPU in it: " + parts);
		helper.assertValueEqual(item.get(ModComponents.SERIAL), board, "its board's id (what Bluetooth cards link to)");
		helper.assertTrue(hands(item).get(ModComponents.MAIN_HAND).is(net.minecraft.world.item.Items.IRON_PICKAXE)
			&& hands(item).get(ModComponents.OFF_HAND).is(net.minecraft.world.item.Items.COBBLESTONE), "what it held, kept: " + hands(item));
		// Set down again, it holds them still.
		BotGolem again = ModEntities.TIER2_BOT_GOLEM.create(helper.getLevel(), EntitySpawnReason.SPAWN_ITEM_USE);
		again.fromItem(item);
		helper.assertTrue(again.getMainHandItem().is(net.minecraft.world.item.Items.IRON_PICKAXE) && again.getOffhandItem().is(net.minecraft.world.item.Items.COBBLESTONE),
			"set down, it holds them: " + again.getMainHandItem() + ", " + again.getOffhandItem());
		helper.succeed();
	}

	/** What a golem's item keeps of its hands, its main hand's then its off hand's. */
	private static net.minecraft.core.NonNullList<ItemStack> hands(ItemStack golem) {
		net.minecraft.core.NonNullList<ItemStack> hands = net.minecraft.core.NonNullList.withSize(ModComponents.HAND_SLOTS, ItemStack.EMPTY);
		golem.getOrDefault(ModComponents.HANDS, ItemContainerContents.EMPTY).copyInto(hands);
		return hands;
	}

	@GameTest
	public void itsGoldSlotTakesOnlyATier1Screen(GameTestHelper helper) {
		helper.assertTrue(CaseMenu.accepts(GolemLayout.TIER_2, CaseMenu.EXP_SLOTS, new ItemStack(com.payonel.modularcomputers.block.ModBlocks.TIER1_SCREEN)), "a tier 1 screen");
		helper.assertTrue(!CaseMenu.accepts(GolemLayout.TIER_2, CaseMenu.EXP_SLOTS, new ItemStack(com.payonel.modularcomputers.block.ModBlocks.TIER2_SCREEN)), "not a tier 2");
		helper.assertTrue(!CaseMenu.available(GolemLayout.TIER_2, CaseMenu.EXP_SLOTS + GolemLayout.ROBOTICS_LANE), "its robotics lane: no slot");
		helper.succeed();
	}

	/**
	 * Sneaking with a golem in hand (its torso here) opens its parts view on the item (the user's), on
	 * a block too (not placed): its parts, its hands and a crafting card's slots are the item's, taken
	 * out and put back there, and the view closes once that stack's out of the hand.
	 */
	@GameTest
	public void sneakingWithAGolemInHandOpensItsPartsViewOnTheItem(GameTestHelper helper) {
		int lane = 1; // (its crafting card's: the second card slot)
		net.minecraft.core.NonNullList<ItemStack> grid = net.minecraft.core.NonNullList.withSize(com.payonel.modularcomputers.device.CraftingCard.SLOTS, ItemStack.EMPTY);
		grid.set(0, new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS));
		grid.set(3, new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS)); // (one above the other: sticks)
		ItemStack card = new ItemStack(ModItems.CRAFTING_CARD);
		card.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(grid));
		net.minecraft.core.NonNullList<ItemStack> parts = net.minecraft.core.NonNullList.withSize(CaseMenu.PART_SLOTS, ItemStack.EMPTY);
		parts.set(CaseMenu.CPU_SLOT, new ItemStack(ModItems.TIER3_CPU));
		parts.set(CaseMenu.EXP_SLOTS + lane, card);
		ItemStack golem = new ItemStack(ModItems.TIER2_BOT_GOLEM_TORSO);
		golem.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(parts));
		golem.set(ModComponents.HANDS, ItemContainerContents.fromItems(List.of(new ItemStack(net.minecraft.world.item.Items.IRON_AXE))));
		var player = GameTests.serverPlayer(helper);
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, golem);
		player.setShiftKeyDown(true);
		BlockPos floor = new BlockPos(2, 1, 2);
		helper.setBlock(floor, Blocks.STONE);
		BlockPos at = helper.absolutePos(floor);
		player.gameMode.useItemOn(player, helper.getLevel(), golem, net.minecraft.world.InteractionHand.MAIN_HAND,
			new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(at).add(0, 0.5, 0), net.minecraft.core.Direction.UP, at, false));
		helper.assertTrue(player.containerMenu instanceof CaseMenu menu && menu.hasHand(), "its parts view, with its hands: " + player.containerMenu);
		helper.assertTrue(helper.getLevel().getEntitiesOfClass(BotGolem.class, new net.minecraft.world.phys.AABB(at).inflate(3)).isEmpty(), "not placed");
		helper.assertTrue(player.getMainHandItem() == golem, "still in hand");
		CaseMenu menu = (CaseMenu) player.containerMenu;
		var input = net.minecraft.world.inventory.ContainerInput.PICKUP;
		// Its hand: the axe taken off the item, and put back.
		menu.clicked(CaseMenu.HAND_SLOT, 0, input, player);
		helper.assertTrue(menu.getCarried().is(net.minecraft.world.item.Items.IRON_AXE) && !golem.has(ModComponents.HANDS), "its axe taken from the item: " + golem.get(ModComponents.HANDS));
		menu.clicked(CaseMenu.HAND_SLOT, 0, input, player);
		helper.assertTrue(hands(golem).get(ModComponents.MAIN_HAND).is(net.minecraft.world.item.Items.IRON_AXE), "and back on it: " + hands(golem));
		// Its crafting card's grid, crafted from by hand: the card on the item keeps what's left.
		menu.openTab(lane);
		int craft = CaseMenu.CRAFT_SLOTS + lane * CaseMenu.CRAFT_LANE_SLOTS;
		menu.slotsChanged(menu.slots.get(craft).container);
		menu.clicked(craft + CaseMenu.CRAFT_RESULT, 0, input, player);
		helper.assertTrue(menu.getCarried().is(net.minecraft.world.item.Items.STICK), "sticks made: " + menu.getCarried());
		menu.setCarried(ItemStack.EMPTY);
		net.minecraft.core.NonNullList<ItemStack> now = net.minecraft.core.NonNullList.withSize(CaseMenu.PART_SLOTS, ItemStack.EMPTY);
		golem.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(now);
		net.minecraft.core.NonNullList<ItemStack> left = net.minecraft.core.NonNullList.withSize(com.payonel.modularcomputers.device.CraftingCard.SLOTS, ItemStack.EMPTY);
		now.get(CaseMenu.EXP_SLOTS + lane).getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(left);
		helper.assertTrue(left.get(0).isEmpty() && left.get(3).isEmpty(), "its card's grid used, on the item: " + left);
		// A part: its CPU taken out of the item.
		menu.clicked(CaseMenu.CPU_SLOT, 0, input, player);
		helper.assertTrue(menu.getCarried().is(ModItems.TIER3_CPU), "its CPU taken: " + menu.getCarried());
		golem.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(now);
		helper.assertTrue(now.get(CaseMenu.CPU_SLOT).isEmpty(), "gone from the item: " + now);
		// Out of the hand, the view's done.
		helper.assertTrue(menu.stillValid(player), "open while it's in hand");
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		helper.assertTrue(!menu.stillValid(player), "closed once it's out of the hand");
		helper.succeed();
	}

	/** Each tier's board (the user's): a tier 2 has a second battery bay and four RAM slots to a tier 1's one and two; the same cards and storage. */
	@GameTest
	public void eachTierHasItsBatteriesAndRam(GameTestHelper helper) {
		helper.assertTrue(!CaseMenu.available(GolemLayout.TIER_1, CaseMenu.UPS_SLOT + 1) && CaseMenu.available(GolemLayout.TIER_2, CaseMenu.UPS_SLOT + 1),
			"a second battery bay on tier 2 only");
		helper.assertTrue(CaseMenu.available(GolemLayout.TIER_1, CaseMenu.RAM_SLOTS + 1) && !CaseMenu.available(GolemLayout.TIER_1, CaseMenu.RAM_SLOTS + 2),
			"two RAM slots on tier 1");
		helper.assertTrue(CaseMenu.available(GolemLayout.TIER_2, CaseMenu.RAM_SLOTS + 3), "four on tier 2");
		helper.assertTrue(CaseMenu.available(GolemLayout.TIER_1, CaseMenu.SATA_SLOTS) && !CaseMenu.available(GolemLayout.TIER_2, CaseMenu.SATA_SLOTS + 1)
			&& CaseMenu.available(GolemLayout.TIER_2, CaseMenu.EXP_SLOTS + 2) && !CaseMenu.available(GolemLayout.TIER_2, CaseMenu.EXP_SLOTS + GolemLayout.ROBOTICS_LANE),
			"the same disk and cards on both");
		helper.succeed();
	}

	/** A tier 1 golem only walks: its thrusters' calls say it has none (its field can still lift it); picked up, it's a tier 1's item. */
	@GameTest(maxTicks = 100)
	public void aTier1GolemHasNoThrusters(GameTestHelper helper) {
		BotGolem golem = golem(helper, board -> {
			while (true) {
				board.waitForInterrupt(1000, "nothing");
			}
		}, ModEntities.TIER1_BOT_GOLEM);
		golem.board().parts().setItem(CaseMenu.UPS_SLOT, ModItems.TIER3_BATTERY.charged());
		golem.board().parts().setItem(CaseMenu.EXP_SLOTS + 1, new ItemStack(ModItems.ENERGY_FIELD_CARD));
		helper.runAfterDelay(1, () -> golem.board().pressPower());
		helper.runAfterDelay(5, () -> {
			var none = new com.payonel.modularcomputers.runtime.Arguments(List.of());
			var up = new com.payonel.modularcomputers.runtime.Arguments(List.of(0.0, 0.0, 1.0));
			for (var said : List.of(golem.robotics().fly(up), golem.robotics().flyTo(up), golem.robotics().hover(none), golem.robotics().land(none))) {
				helper.assertTrue(BotGolem.NO_THRUSTERS.equals(said.error()), "no thrusters: " + said);
			}
			helper.assertTrue(golem.legs() && !golem.thrusting(), "still on its legs");
			var field = golem.board().cards().stream().filter(com.payonel.modularcomputers.device.FieldCard.class::isInstance)
				.map(com.payonel.modularcomputers.device.FieldCard.class::cast).findFirst().orElseThrow();
			var held = field.capture(new com.payonel.modularcomputers.runtime.Arguments(List.of("self")));
			helper.assertTrue(held.error() == null, "its field holds it all the same: " + held);
			helper.assertTrue(golem.asItem().is(ModItems.TIER1_BOT_GOLEM), "picked up, a tier 1's item: " + golem.asItem());
			helper.succeed();
		});
	}
}
