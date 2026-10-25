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
	private static final float EAST = -90;
	/** How far the test's walks go, in blocks. */
	private static final long WALK = 5;
	/** A player's walking speed, in blocks a second (the game's), and the margin its pace is held to. */
	private static final double PLAYER_PACE = 4.317;
	private static final double PACE_MARGIN = 0.25;
	private static final double TICKS_A_SECOND = 20;
	private static final String ROBOTICS = Motherboard.exp(GolemLayout.ROBOTICS_LANE);
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

	/** A golem at the start, facing east, with a board: running {@code program} once pressed on. */
	private static BotGolem golem(GameTestHelper helper, Program program) {
		floor(helper);
		BotGolem golem = ModEntities.BOT_GOLEM.create(helper.getLevel(), EntitySpawnReason.COMMAND);
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
		var player = GameTests.serverPlayer(helper);
		java.util.UUID board = golem.board().boardId();
		golem.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), 1);
		helper.assertTrue(golem.isRemoved(), "gone from the world");
		var items = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, golem.getBoundingBox().inflate(2));
		helper.assertTrue(items.size() == 1 && items.get(0).getItem().is(ModItems.BOT_GOLEM), "dropped as its item: " + items);
		ItemStack item = items.get(0).getItem();
		List<ItemStack> parts = item.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyItemCopyStream().toList();
		helper.assertTrue(parts.stream().anyMatch(part -> part.is(ModItems.TIER3_CPU)), "its CPU in it: " + parts);
		helper.assertValueEqual(item.get(ModComponents.SERIAL), board, "its board's id (what Bluetooth cards link to)");
		helper.succeed();
	}

	@GameTest
	public void itsGoldSlotTakesOnlyATier1Screen(GameTestHelper helper) {
		helper.assertTrue(CaseMenu.accepts(GolemLayout.BOT_GOLEM, CaseMenu.EXP_SLOTS, new ItemStack(com.payonel.modularcomputers.block.ModBlocks.TIER1_SCREEN)), "a tier 1 screen");
		helper.assertTrue(!CaseMenu.accepts(GolemLayout.BOT_GOLEM, CaseMenu.EXP_SLOTS, new ItemStack(com.payonel.modularcomputers.block.ModBlocks.TIER2_SCREEN)), "not a tier 2");
		helper.assertTrue(!CaseMenu.available(GolemLayout.BOT_GOLEM, CaseMenu.EXP_SLOTS + GolemLayout.ROBOTICS_LANE), "its robotics lane: no slot");
		helper.succeed();
	}
}
