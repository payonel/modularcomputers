package com.payonel.modularcomputers.gametest;

import com.payonel.modularcomputers.entity.BotGolem;
import com.payonel.modularcomputers.machine.Program;
import com.payonel.modularcomputers.runtime.Result;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

/**
 * A golem's senses and hand, without cards: it scans the six spaces round it at once, in its own
 * frame; uses what it holds on a space (places a block, flips a lever); breaks a block with a tool
 * in a player's time, catching what it drops (its off hand, with no storage; what it can't, its
 * event says); hits a mob with a weapon; takes from a chest and puts back; and on its legs
 * it presses a pressure plate.
 */
public class GolemHandsGameTest {
	/** Where it stands (BotGolemGameTest's start), facing east: its front east, its left north, its right south, its back west. */
	private static final BlockPos AT = new BlockPos(1, 1, 1);
	private static final BlockPos FRONT = AT.east();
	private static final BlockPos LEFT = AT.north();
	private static final BlockPos RIGHT = AT.south();
	private static final BlockPos BACK = AT.west();

	/** A program that makes these calls on its robotics device, one after another, keeping each answer; then each event it hears. */
	private static Program calling(List<Object> answers, Object[]... calls) {
		return board -> {
			for (Object[] call : calls) {
				Object[] args = java.util.Arrays.copyOfRange(call, 1, call.length);
				Result answer = board.invoke(BotGolemGameTest.ROBOTICS, (String) call[0], args);
				synchronized (answers) {
					answers.add(answer.failed() ? "refused: " + answer.error() : answer.first() == null ? "nil" : answer.first());
				}
			}
			while (true) {
				board.waitForInterrupt(1000, BotGolemGameTest.ROBOTICS);
				Object event;
				while ((event = board.invoke(BotGolemGameTest.ROBOTICS, "pull").first()) != null) {
					synchronized (answers) {
						answers.add(event);
					}
				}
			}
		};
	}

	private static Object[] call(String method, Object... args) {
		Object[] call = new Object[args.length + 1];
		call[0] = method;
		System.arraycopy(args, 0, call, 1, args.length);
		return call;
	}

	private static BotGolem start(GameTestHelper helper, List<Object> answers, Object[]... calls) {
		BotGolem golem = BotGolemGameTest.golem(helper, calling(answers, calls));
		helper.runAfterDelay(1, () -> golem.board().pressPower());
		return golem;
	}

	/** Asserts a value (which may be nil) is what's expected (the game's assertValueEqual fails on a null). */
	private static void same(GameTestHelper helper, Object got, Object expected, String what) {
		helper.assertTrue(java.util.Objects.equals(got, expected), what + ": expected " + expected + ", got " + got);
	}

	private static List<Object> copy(List<Object> answers) {
		synchronized (answers) {
			return new ArrayList<>(answers);
		}
	}

	@GameTest(maxTicks = 200)
	public void aScanSeesTheSixSpacesRoundItAtOnceInItsOwnFrame(GameTestHelper helper) {
		helper.setBlock(FRONT, Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST)); // facing it
		helper.setBlock(LEFT, Blocks.WATER);
		helper.setBlock(RIGHT, Blocks.OAK_LOG);
		helper.spawn(EntityTypes.PIG, BACK);
		List<Object> answers = new ArrayList<>();
		start(helper, answers, call("scan"));
		helper.succeedWhen(() -> {
			List<Object> got = copy(answers);
			helper.assertTrue(!got.isEmpty() && got.getFirst() instanceof Map<?, ?>, "a scan: " + got);
			Map<?, ?> scan = (Map<?, ?>) got.getFirst();
			Map<?, ?> front = (Map<?, ?>) scan.get("front");
			same(helper, front.get("kind"), "block", "front");
			same(helper, front.get("name"), "minecraft:chest", "front");
			same(helper, ((Map<?, ?>) front.get("state")).get("facing"), "back", "the chest faces it: its facing in its frame");
			same(helper, ((Number) front.get("container")).intValue(), 27, "a chest's slots");
			Map<?, ?> left = (Map<?, ?>) scan.get("left");
			same(helper, left.get("kind"), "fluid", "left: " + left);
			same(helper, left.get("name"), "minecraft:water", "left");
			same(helper, left.get("source"), true, "left");
			same(helper, ((Map<?, ?>) scan.get("right")).get("name"), "minecraft:oak_log", "right");
			Map<?, ?> back = (Map<?, ?>) scan.get("back");
			same(helper, back.get("kind"), "entity", "back: " + back);
			same(helper, back.get("name"), "minecraft:pig", "back");
			same(helper, ((Map<?, ?>) scan.get("up")).get("kind"), "air", "up");
			same(helper, ((Map<?, ?>) scan.get("down")).get("name"), "minecraft:stone", "down: the floor");
		});
	}

	@GameTest(maxTicks = 200)
	public void itPlacesWhatItHoldsAndFlipsALever(GameTestHelper helper) {
		helper.setBlock(LEFT, Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR));
		List<Object> answers = new ArrayList<>();
		BotGolem golem = start(helper, answers, call("use", "front"), call("use", "left"));
		golem.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.COBBLESTONE, 2));
		helper.succeedWhen(() -> {
			helper.assertBlockPresent(Blocks.COBBLESTONE, FRONT);
			same(helper, golem.getMainHandItem().getCount(), 1, "one used");
			helper.assertTrue(helper.getBlockState(LEFT).getValue(LeverBlock.POWERED), "the lever's flipped: " + copy(answers));
			same(helper, copy(answers).subList(0, 2), List.of("placed", "used"), "its answers");
		});
	}

	@GameTest(maxTicks = 200)
	public void itBreaksALogWithAnAxeAndCatchesItInItsOffHandWithNoStorage(GameTestHelper helper) {
		helper.setBlock(FRONT, Blocks.OAK_LOG);
		List<Object> answers = new ArrayList<>();
		BotGolem golem = start(helper, answers, call("swing", "front"));
		golem.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
		helper.succeedWhen(() -> {
			List<Object> got = copy(answers);
			same(helper, got.size() > 1 ? got.get(1) : null, Map.of("event", "broke", "block", "minecraft:oak_log", "kept", true), "its event: " + got);
			helper.assertBlockPresent(Blocks.AIR, FRONT);
			helper.assertTrue(golem.getMainHandItem().getDamageValue() == 1, "the axe wore");
			// No storage: its off hand caught the log, in the background.
			helper.assertTrue(golem.getOffhandItem().is(Items.OAK_LOG), "the log in its off hand: " + golem.getOffhandItem());
			helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(FRONT)).inflate(1)).isEmpty(),
				"nothing left in the world");
		});
	}

	@GameTest(maxTicks = 200)
	public void whatItCantKeepStaysInTheWorldAndItsEventSaysSo(GameTestHelper helper) {
		helper.setBlock(FRONT, Blocks.OAK_LOG);
		List<Object> answers = new ArrayList<>();
		BotGolem golem = start(helper, answers, call("swing", "front"));
		golem.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
		golem.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.APPLE));
		helper.succeedWhen(() -> {
			List<Object> got = copy(answers);
			same(helper, got.size() > 1 ? got.get(1) : null, Map.of("event", "broke", "block", "minecraft:oak_log", "kept", false, "dropped", 1L),
				"its event: " + got);
			helper.assertTrue(golem.getOffhandItem().is(Items.APPLE), "its off hand kept its apple");
			helper.assertTrue(!helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(FRONT)).inflate(1),
				item -> item.getItem().is(Items.OAK_LOG)).isEmpty(), "the log stayed in the world");
		});
	}

	@GameTest(maxTicks = 200)
	public void breakingStoneWithAPickaxeTakesAPlayersTime(GameTestHelper helper) {
		helper.setBlock(FRONT, Blocks.STONE);
		List<Object> answers = new ArrayList<>();
		BotGolem golem = start(helper, answers, call("swing", "front"));
		golem.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_PICKAXE));
		long[] began = {-1};
		helper.onEachTick(() -> {
			if (began[0] < 0 && golem.robotics().state().equals("breaking")) {
				began[0] = helper.getTick();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertBlockPresent(Blocks.AIR, FRONT);
			// A wooden pickaxe on stone: 1.5 hardness, speed 2, the right tool: 2 / 1.5 / 30 a tick, so 23 ticks.
			long took = helper.getTick() - began[0];
			helper.assertTrue(began[0] >= 0 && took >= 22, "it took a player's time: " + took + " ticks");
		});
	}

	@GameTest(maxTicks = 200)
	public void aBlockWhoseStateChangesWhileItsBrokenStillBreaks(GameTestHelper helper) {
		// Leaves by a log: the log goes as it starts, so the leaves' distance changes under it (a tree cut).
		helper.setBlock(FRONT, Blocks.OAK_LEAVES);
		helper.setBlock(FRONT.east(), Blocks.OAK_LOG);
		List<Object> answers = new ArrayList<>();
		BotGolem golem = start(helper, answers, call("swing", "front"));
		golem.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_SWORD));
		boolean[] cut = {false};
		helper.onEachTick(() -> {
			if (!cut[0] && golem.robotics().state().equals("breaking")) {
				cut[0] = true;
				helper.setBlock(FRONT.east(), Blocks.AIR);
			}
		});
		helper.succeedWhen(() -> {
			List<Object> got = copy(answers);
			helper.assertTrue(got.size() > 1 && got.get(1) instanceof Map<?, ?> event && "broke".equals(event.get("event")), "it broke them: " + got);
			helper.assertBlockPresent(Blocks.AIR, FRONT);
		});
	}

	@GameTest(maxTicks = 200)
	public void itHitsAMobWithAWeapon(GameTestHelper helper) {
		Pig pig = helper.spawn(EntityTypes.PIG, FRONT);
		pig.setNoAi(true);
		List<Object> answers = new ArrayList<>();
		BotGolem golem = start(helper, answers, call("swing", "front"));
		golem.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		helper.succeedWhen(() -> {
			same(helper, copy(answers).isEmpty() ? null : copy(answers).getFirst(), "attacked", "its answer");
			// An iron sword's 6, full strength: a pig's 10 down to 4 (a bare hand's 1 would leave 9).
			helper.assertTrue(pig.getHealth() <= 4.0f, "hit with the sword's damage: " + pig.getHealth());
		});
	}

	@GameTest(maxTicks = 200)
	public void itTakesFromAChestAndPutsBack(GameTestHelper helper) {
		helper.setBlock(FRONT, Blocks.CHEST);
		ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(FRONT, ChestBlockEntity.class);
		chest.setItem(4, new ItemStack(Items.APPLE, 5));
		List<Object> answers = new ArrayList<>();
		BotGolem golem = start(helper, answers, call("contents", "front"), call("take", "front"), call("store", "front", 2L));
		helper.succeedWhen(() -> {
			List<Object> got = copy(answers);
			helper.assertTrue(got.size() >= 3, "its answers: " + got);
			List<?> contents = (List<?>) got.get(0);
			same(helper, ((Number) ((Map<?, ?>) contents.getFirst()).get("slot")).intValue(), 5, "the apples' slot, from 1");
			same(helper, ((Number) got.get(1)).intValue(), 5, "took all 5");
			same(helper, got.get(2), true, "put 2 back, none of them left");
			helper.assertTrue(golem.getMainHandItem().is(Items.APPLE) && golem.getMainHandItem().getCount() == 3, "3 in hand");
			int inChest = 0;
			for (int slot = 0; slot < chest.getContainerSize(); slot++) {
				inChest += chest.getItem(slot).is(Items.APPLE) ? chest.getItem(slot).getCount() : 0;
			}
			same(helper, inChest, 2, "2 in the chest");
		});
	}

	@GameTest(maxTicks = 100)
	public void onItsLegsItPressesAPressurePlate(GameTestHelper helper) {
		helper.setBlock(AT, Blocks.STONE_PRESSURE_PLATE);
		BotGolemGameTest.golem(helper, board -> {
		});
		helper.succeedWhen(() -> helper.assertTrue(helper.getBlockState(AT).getValue(PressurePlateBlock.POWERED), "the plate's pressed"));
	}
}
