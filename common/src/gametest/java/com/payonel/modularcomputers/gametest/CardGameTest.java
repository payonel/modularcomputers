package com.payonel.modularcomputers.gametest;

import com.payonel.modularcomputers.block.CaseBlock;
import com.payonel.modularcomputers.block.CaseBlockEntity;
import com.payonel.modularcomputers.block.ModBlocks;
import com.payonel.modularcomputers.device.MotionSensorCard;
import com.payonel.modularcomputers.device.RedstoneCard;
import com.payonel.modularcomputers.device.WeatherCard;
import com.payonel.modularcomputers.item.ModItems;
import com.payonel.modularcomputers.menu.CaseMenu;
import com.payonel.modularcomputers.runtime.Arguments;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneLampBlock;

/** Cards that work where they're installed (user design, 2026-10-13): redstone, weather, motion. */
public class CardGameTest {
	private static final BlockPos POS = new BlockPos(4, 2, 4);
	/** How long the bell test waits between its bells, in ticks: past a bell's suppression. */
	private static final int BELL_GAP_TICKS = 20;
	/** How long the test computer's program waits at a time (it waits on, so its cards take data). */
	private static final double WAIT_SECONDS = 1000;

	/** A running computer (its program waits on: its devices take data only while one runs) with {@code card} in its first expansion slot. */
	@SuppressWarnings("unchecked")
	private static <T> T computerWith(GameTestHelper helper, Item card, Class<T> type) {
		helper.setBlock(POS, ModBlocks.TIER3_CASE.defaultBlockState().setValue(CaseBlock.FACING, Direction.SOUTH));
		CaseBlockEntity computer = helper.getBlockEntity(POS, CaseBlockEntity.class);
		computer.parts().setItem(CaseMenu.CPU_SLOT, new ItemStack(ModItems.TIER3_CPU));
		computer.parts().setItem(CaseMenu.RAM_SLOTS, new ItemStack(ModItems.TIER3_RAM)); // no RAM, no boot
		computer.parts().setItem(CaseMenu.EEPROM_SLOT, new ItemStack(ModItems.EEPROM));
		computer.parts().setItem(CaseMenu.UPS_SLOT, ModItems.TIER1_BATTERY.charged());
		computer.parts().setItem(CaseMenu.EXP_SLOTS + 1, new ItemStack(card));
		TestPrograms.flash(computer, board -> {
			while (true) {
				board.waitForInterrupt(WAIT_SECONDS, "nothing");
			}
		});
		computer.pressPower();
		return (T) computer.machine().board().bus().devices().stream().filter(type::isInstance).findFirst().orElseThrow();
	}

	private static Arguments args(Object... values) {
		return new Arguments(List.of(values));
	}

	/** Null-safe, as assertValueEqual isn't (a null value crashes the test server). */
	private static void same(GameTestHelper helper, Object got, Object expected, String what) {
		helper.assertTrue(java.util.Objects.equals(got, expected), what + ": expected " + expected + ", got " + got);
	}

	/** A face's field, as the card answers it. */
	private static Object field(RedstoneCard card, String side, String field) {
		return ((Map<?, ?>) ((Map<?, ?>) card.get(args()).first()).get(side)).get(field);
	}

	private static Object level(RedstoneCard card, String side) {
		return field(card, side, "level");
	}

	@GameTest(maxTicks = 40)
	public void twoRedstoneCardsInABlockPutOutTheStrongestAndEachReadsTheOthers(GameTestHelper helper) {
		RedstoneCard first = computerWith(helper, ModItems.REDSTONE_CARD, RedstoneCard.class);
		CaseBlockEntity computer = helper.getBlockEntity(POS, CaseBlockEntity.class);
		computer.parts().setItem(CaseMenu.EXP_SLOTS + 2, new ItemStack(ModItems.REDSTONE_CARD));
		helper.setBlock(POS.east(), Blocks.REDSTONE_LAMP);
		RedstoneCard[] second = {null};
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertValueEqual(computer.machine().board().bus().devices().stream().filter(RedstoneCard.class::isInstance).count(),
				2L, "both cards on the bus"))
			.thenExecute(() -> {
				second[0] = computer.machine().board().bus().devices().stream().filter(RedstoneCard.class::isInstance)
					.map(RedstoneCard.class::cast).filter(card -> card != first).findFirst().orElseThrow();
				first.set(args(Map.of("east", Map.of("powered", 15L)))); // (applied at its next tick)
			})
			.thenWaitUntil(() -> helper.assertValueEqual(level(second[0], "east"), 15L, "the other card reads the first's signal"))
			.thenExecute(() -> helper.assertValueEqual(field(second[0], "east", "powered"), 0L, "but puts out none"))
			.thenWaitUntil(() -> helper.assertBlockProperty(POS.east(), RedstoneLampBlock.LIT, true))
			.thenSucceed();
	}

	@GameTest(maxTicks = 40)
	public void aRedstoneCardSetsAndReadsRedstoneOnItsBlocksFaces(GameTestHelper helper) {
		RedstoneCard card = computerWith(helper, ModItems.REDSTONE_CARD, RedstoneCard.class);
		helper.setBlock(POS.east(), Blocks.REDSTONE_LAMP);
		helper.assertTrue(!card.set(args(Map.of("east", Map.of("powered", 15L)))).failed(), "set east to 15");
		helper.setBlock(POS.west(), Blocks.REDSTONE_BLOCK);
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertBlockProperty(POS.east(), RedstoneLampBlock.LIT, true))
			.thenWaitUntil(() -> helper.assertValueEqual(level(card, "west"), 15L, "west reads 15"))
			.thenExecute(() -> {
				helper.assertTrue(card.interruptPending(), "a change is an interrupt");
				List<Map<?, ?>> events = new ArrayList<>();
				Object event;
				while ((event = card.pull(args()).first()) != null) {
					events.add((Map<?, ?>) event);
				}
				helper.assertTrue(events.stream().anyMatch(e -> "west".equals(e.get("side")) && Long.valueOf(15).equals(e.get("level"))), "the change, as an event: " + events);
				helper.assertTrue(events.stream().anyMatch(e -> "set".equals(e.get("event")) && e.get("result") instanceof Map<?, ?> faces
					&& faces.get("east") instanceof Map<?, ?> east && Long.valueOf(15).equals(east.get("powered"))), "its own change: its set's end: " + events);
				helper.assertTrue(events.stream().noneMatch(e -> "east".equals(e.get("side"))), "and no face's event for it: " + events);
				card.set(args(Map.of("east", Map.of("powered", 0L))));
			})
			.thenWaitUntil(() -> helper.assertBlockProperty(POS.east(), RedstoneLampBlock.LIT, false))
			.thenSucceed();
	}

	@GameTest(maxTicks = 60)
	public void aCardsEventsAndOutputsOutlastASave(GameTestHelper helper) {
		RedstoneCard card = computerWith(helper, ModItems.REDSTONE_CARD, RedstoneCard.class);
		card.set(args(Map.of("east", Map.of("powered", 9L))));
		helper.setBlock(POS.west(), Blocks.REDSTONE_BLOCK);
		var registries = helper.getLevel().registryAccess();
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertValueEqual(level(card, "west"), 15L, "the signal came in"))
			.thenExecute(() -> {
				helper.assertTrue(card.interruptPending(), "the changes wait for the program");
				// Saved as the world is, then gone, and loaded again: a save isn't a power cut.
				net.minecraft.nbt.CompoundTag saved = helper.getBlockEntity(POS, CaseBlockEntity.class).saveWithoutMetadata(registries);
				helper.setBlock(POS, Blocks.AIR);
				helper.setBlock(POS, ModBlocks.TIER3_CASE.defaultBlockState().setValue(CaseBlock.FACING, Direction.SOUTH));
				CaseBlockEntity again = helper.getBlockEntity(POS, CaseBlockEntity.class);
				again.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, registries, saved));
				RedstoneCard loaded = again.machine().board().bus().devices().stream()
					.filter(RedstoneCard.class::isInstance).map(RedstoneCard.class::cast).findFirst().orElseThrow();
				helper.assertTrue(loaded != card, "a new card device, from the save");
				helper.assertValueEqual(field(loaded, "east", "powered"), 9L, "what it put out");
				List<Map<?, ?>> events = new ArrayList<>();
				Object event;
				while ((event = loaded.pull(args()).first()) != null) {
					events.add((Map<?, ?>) event);
				}
				helper.assertTrue(events.stream().anyMatch(e -> "west".equals(e.get("side")) && Long.valueOf(15).equals(e.get("level"))),
					"the events that waited: " + events);
			})
			.thenSucceed();
	}

	@GameTest
	public void aHubsCardsNameFacesFromTheHubsFront(GameTestHelper helper) {
		BlockPos hubPos = POS.west(2);
		helper.setBlock(hubPos, ModBlocks.EXPANSION_HUB.defaultBlockState().setValue(com.payonel.modularcomputers.block.ExpansionHubBlock.FACING, Direction.EAST));
		var hub = helper.getBlockEntity(hubPos, com.payonel.modularcomputers.block.ExpansionHubBlockEntity.class);
		hub.cards().setItem(0, new ItemStack(ModItems.REDSTONE_CARD));
		RedstoneCard card = (RedstoneCard) hub.card(0).orElseThrow();
		card.power().supply(this); // (as a running computer's bus powers it)
		card.set(args(Map.of("front", Map.of("powered", 6L), "left", Map.of("powered", 2L))));
		helper.succeedWhen(() -> {
			helper.assertValueEqual(field(card, "east", "powered"), 6L, "its front: east, the way the hub faces");
			helper.assertValueEqual(field(card, "south", "powered"), 2L, "its left, as we see it facing its front: south");
		});
	}

	/**
	 * Minecraft writes only chunks marked changed, even at an unload: a device's data changing (keys
	 * typed or read, events, outputs) marks its block's chunk at once, or the old copy stays on disk.
	 */
	@GameTest
	public void aDevicesDataChangingMarksItsChunkForTheNextSave(GameTestHelper helper) {
		BlockPos screenPos = POS.east(2);
		BlockPos hubPos = POS.west(2);
		helper.setBlock(screenPos, ModBlocks.TIER3_SCREEN);
		helper.setBlock(hubPos, ModBlocks.EXPANSION_HUB);
		var screen = helper.getBlockEntity(screenPos, com.payonel.modularcomputers.block.ScreenBlockEntity.class);
		var hub = helper.getBlockEntity(hubPos, com.payonel.modularcomputers.block.ExpansionHubBlockEntity.class);
		hub.cards().setItem(0, new ItemStack(ModItems.REDSTONE_CARD));
		RedstoneCard card = (RedstoneCard) hub.card(0).orElseThrow();
		screen.terminal().accepting(true); // as on a running computer's bus
		card.accepting(true);
		var screenChunk = helper.getLevel().getChunkAt(helper.absolutePos(screenPos));
		var hubChunk = helper.getLevel().getChunkAt(helper.absolutePos(hubPos));
		java.util.Map<Runnable, net.minecraft.world.level.chunk.LevelChunk> changes = new java.util.LinkedHashMap<>();
		changes.put(() -> screen.terminal().press("a"), screenChunk);
		changes.put(() -> screen.terminal().read(args()), screenChunk);
		changes.put(() -> card.set(args(Map.of("up", Map.of("powered", 3L)))), hubChunk);
		changes.put(() -> card.sense(0, 5), hubChunk);
		changes.put(() -> card.pull(args()), hubChunk);
		changes.forEach((change, chunk) -> {
			screenChunk.tryMarkSaved();
			hubChunk.tryMarkSaved();
			helper.assertTrue(!chunk.isUnsaved(), "saved");
			change.run();
			helper.assertTrue(chunk.isUnsaved(), "marked at once, for the next save");
		});
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void aWeatherCardReportsTheWeatherAndItsChanges(GameTestHelper helper) {
		WeatherCard card = computerWith(helper, ModItems.WEATHER_SENSOR_CARD, WeatherCard.class);
		// The test world's biome never rains (as a desert): it reports clear there, rain or not.
		helper.startSequence()
			.thenIdle(21)
			.thenExecute(() -> {
				helper.assertValueEqual(card.getWeather(args()).first(), "clear", "clear here");
				var none = net.minecraft.world.level.biome.Biome.Precipitation.NONE;
				var rain = net.minecraft.world.level.biome.Biome.Precipitation.RAIN;
				var snow = net.minecraft.world.level.biome.Biome.Precipitation.SNOW;
				helper.assertValueEqual(List.of(com.payonel.modularcomputers.block.CardWorld.weather(false, false, rain),
					com.payonel.modularcomputers.block.CardWorld.weather(true, false, rain), com.payonel.modularcomputers.block.CardWorld.weather(true, true, rain),
					com.payonel.modularcomputers.block.CardWorld.weather(true, false, snow), com.payonel.modularcomputers.block.CardWorld.weather(true, true, none)),
					List.of("clear", "rain", "thunder", "snow", "clear"), "the weather by biome");
				card.sense("rain"); // as the world would, where it rains
				Map<?, ?> event = (Map<?, ?>) card.pull(args()).first();
				helper.assertTrue(event != null && "clear".equals(event.get("old")) && "rain".equals(event.get("new")), "a change is an event: " + event);
				helper.assertValueEqual(card.getWeather(args()).first(), "rain", "and what it reports");
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = 60)
	public void aMotionSensorSeesMobsMoveWhereInItsComputersFrameAndNotItems(GameTestHelper helper) {
		MotionSensorCard card = computerWith(helper, ModItems.MOTION_SENSOR_CARD, MotionSensorCard.class);
		ItemEntity drop = new ItemEntity(helper.getLevel(), helper.absolutePos(POS.east(2)).getX() + 0.5, helper.absolutePos(POS).getY() + 0.5,
			helper.absolutePos(POS).getZ() + 0.5, new ItemStack(Items.DIAMOND));
		drop.setDeltaMovement(0, 0, 0);
		drop.setNoGravity(true);
		helper.getLevel().addFreshEntity(drop);
		var pig = helper.spawn(EntityTypes.PIG, POS.north(3));
		pig.setNoAi(true);
		List<Map<?, ?>> seen = new ArrayList<>();
		helper.startSequence()
			.thenWaitUntil(() -> {
				Object event;
				while ((event = card.pull(args()).first()) != null) {
					seen.add((Map<?, ?>) event);
				}
				helper.assertTrue(!seen.isEmpty(), "saw something");
			})
			.thenExecute(() -> {
				helper.assertTrue(seen.stream().allMatch(e -> "minecraft:pig".equals(e.get("mob"))), "mobs only: " + seen);
				Map<?, ?> first = seen.getFirst();
				helper.assertValueEqual(first.keySet(), java.util.Set.of("mob", "forward", "right", "up"), "which mob, and where");
				same(helper, first.get("forward"), -3.0, "three blocks north of it: behind (its front is south)");
				same(helper, first.get("right"), 0.0, "straight behind");
			})
			.thenSucceed();
	}

	/** A robot's sensor says where in its own frame (its heading, its own right), so as it turns, what it sees turns with it. */
	@GameTest(maxTicks = 120)
	public void aGolemsMotionSensorSaysWhereInItsOwnFrameAsItTurns(GameTestHelper helper) {
		var golem = GolemFlightGameTest.golem(helper, false); // (facing east)
		golem.board().parts().setItem(CaseMenu.EXP_SLOTS + 1, new ItemStack(ModItems.MOTION_SENSOR_CARD));
		BlockPos at = golem.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO));
		var pig = helper.spawn(EntityTypes.PIG, at.east(3));
		pig.setNoAi(true);
		List<Map<?, ?>> seen = new ArrayList<>();
		helper.onEachTick(() -> {
			if (!golem.machine().isPowered()) {
				return;
			}
			golem.board().cards().stream().filter(MotionSensorCard.class::isInstance).map(MotionSensorCard.class::cast).findFirst().ifPresent(card -> {
				Object event;
				while ((event = card.pull(args()).first()) != null) {
					seen.add((Map<?, ?>) event);
				}
			});
		});
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(!seen.isEmpty(), "saw it"))
			.thenExecute(() -> {
				same(helper, seen.getLast().get("forward"), 3.0, "three blocks ahead (east, its heading)");
				same(helper, seen.getLast().get("right"), 0.0, "straight ahead");
				helper.assertTrue(!golem.robotics().turn(args("left")).failed(), "it turns left: north");
			})
			.thenWaitUntil(() -> helper.assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(golem.getYRot() - 180)) < 1, "facing north: " + golem.getYRot()))
			.thenExecute(() -> {
				seen.clear();
				pig.teleportTo(pig.getX() + 0.5, pig.getY(), pig.getZ()); // (it moves: half a block further east)
			})
			.thenWaitUntil(() -> helper.assertTrue(!seen.isEmpty(), "saw it move"))
			.thenExecute(() -> {
				same(helper, seen.getLast().get("forward"), 0.0, "beside it now");
				same(helper, seen.getLast().get("right"), 3.5, "on its own right (east, facing north)");
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = 60)
	public void anAudioSensorHearsSoundsAndLevelEventsByIdWhereInItsComputersFrameWithinTheirRange(GameTestHelper helper) {
		var card = computerWith(helper, ModItems.AUDIO_SENSOR_CARD, com.payonel.modularcomputers.device.AudioSensorCard.class);
		BlockPos near = helper.absolutePos(POS.north(3));
		BlockPos far = helper.absolutePos(POS.north(40)); // past a sound's range at its usual volume (16 blocks)
		List<Map<?, ?>> heard = new ArrayList<>();
		helper.startSequence()
			.thenIdle(2) // (powered: its computer runs)
			.thenExecute(() -> {
				helper.getLevel().playSound(null, far.getX() + 0.5, far.getY() + 0.5, far.getZ() + 0.5,
					net.minecraft.sounds.SoundEvents.PIGLIN_ANGRY, net.minecraft.sounds.SoundSource.HOSTILE, 1, 1);
				helper.getLevel().playSound(null, near.getX() + 0.5, near.getY() + 0.5, near.getZ() + 0.5,
					net.minecraft.sounds.SoundEvents.PIGLIN_AMBIENT, net.minecraft.sounds.SoundSource.HOSTILE, 1, 1);
				helper.getLevel().levelEvent(net.minecraft.world.level.block.LevelEvent.SOUND_DISPENSER_DISPENSE, near, 0);
			})
			.thenWaitUntil(() -> {
				Object event;
				while ((event = card.pull(args()).first()) != null) {
					// (only what this test played, north of it: tests run side by side, and it hears its neighbours' too)
					if (((Map<?, ?>) event).get("right") instanceof Number right && right.doubleValue() == 0) {
						heard.add((Map<?, ?>) event);
					}
				}
				helper.assertValueEqual(heard.size(), 2, "the two near sounds: " + heard);
			})
			.thenExecute(() -> {
				helper.assertValueEqual(heard.getFirst().get("sound"), "minecraft:entity.piglin.ambient", "the sound the game played, by its id");
				helper.assertValueEqual(heard.getFirst().keySet(), java.util.Set.of("sound", "forward", "right", "up"), "and where: nothing else");
				same(helper, heard.getFirst().get("forward"), -3.0, "three blocks north of it: behind (its front is south)");
				helper.assertValueEqual(heard.get(1).get("sound"), "minecraft:block.dispenser.dispense", "a level event's sound");
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = 100)
	public void aSoundCardPlaysWhatsWrittenByGameTimeAndAnAudioSensorHearsItStart(GameTestHelper helper) {
		var card = computerWith(helper, ModItems.SOUND_CARD, com.payonel.modularcomputers.device.SoundCard.class);
		CaseBlockEntity computer = helper.getBlockEntity(POS, CaseBlockEntity.class);
		computer.parts().setItem(CaseMenu.EXP_SLOTS + 2, new ItemStack(ModItems.AUDIO_SENSOR_CARD));
		List<Map<?, ?>> heard = new ArrayList<>();
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(computer.machine().board().bus().devices().stream()
				.anyMatch(com.payonel.modularcomputers.device.AudioSensorCard.class::isInstance), "the sensor on the bus"))
			.thenIdle(2) // (powered: its computer runs)
			.thenExecute(() -> helper.assertValueEqual(card.write(args("x".repeat(4000))).first(), 4000L, "half a second at 8000 a second"))
			.thenWaitUntil(() -> {
				var sensor = computer.machine().board().bus().devices().stream()
					.filter(com.payonel.modularcomputers.device.AudioSensorCard.class::isInstance).findFirst().orElseThrow();
				Object event;
				while ((event = ((com.payonel.modularcomputers.device.AudioSensorCard) sensor).pull(args()).first()) != null) {
					heard.add((Map<?, ?>) event);
				}
				helper.assertValueEqual(card.getDelay(args()).first(), 0L, "played out, by game time");
			})
			.thenExecute(() -> helper.assertTrue(heard.stream().anyMatch(event -> "modularcomputers:sound_card".equals(event.get("sound"))),
				"the sensor heard it start: " + heard))
			.thenSucceed();
	}

	@GameTest(maxTicks = 100)
	public void aBelRingsTheTerminalsBellOnceForBellsCloseTogether(GameTestHelper helper) {
		var sensor = computerWith(helper, ModItems.AUDIO_SENSOR_CARD, com.payonel.modularcomputers.device.AudioSensorCard.class);
		CaseBlockEntity computer = helper.getBlockEntity(POS, CaseBlockEntity.class);
		TestPrograms.withScreen(computer); // (its terminal: a screen in its gold slot)
		List<Map<?, ?>> heard = new ArrayList<>();
		helper.startSequence()
			.thenIdle(BELL_GAP_TICKS) // (the sensor powered and listening)
			.thenExecute(() -> computer.terminal().write("\u0007\u0007\u0007")) // (as a program's write comes)
			.thenIdle(BELL_GAP_TICKS)
			.thenExecute(() -> computer.terminal().write("\u0007"))
			.thenWaitUntil(() -> {
				Object event;
				while ((event = sensor.pull(args()).first()) != null) {
					heard.add((Map<?, ?>) event);
				}
				helper.assertValueEqual(heard.stream().filter(sound -> "modularcomputers:bell".equals(sound.get("sound"))).count(), 2L,
					"\\a\\a\\a rings once, a \\a later again: " + heard);
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = 60)
	public void anOperatorCardRunsServerCommandsAsACommandBlockDoes(GameTestHelper helper) {
		var card = computerWith(helper, ModItems.OPERATOR_CARD, com.payonel.modularcomputers.device.OperatorCard.class);
		Map<?, ?> answer = (Map<?, ?>) card.command(args("time set 1000")).first();
		helper.assertValueEqual(answer.get("success"), true, "it ran: " + answer);
		helper.assertValueEqual(helper.getLevel().getOverworldClockTime() % 24000, 1000L, "the world's time is set");
		helper.assertTrue(String.valueOf(answer.get("output")).contains("1000"), "and it said so: " + answer);
		Map<?, ?> refused = (Map<?, ?>) card.command(args("op somebody")).first();
		helper.assertValueEqual(refused.get("success"), false, "an operator's own command is past a command block's permission");
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void aBluetoothCardsLinkIsReadAndSetOnTheCardItself(GameTestHelper helper) {
		var card = computerWith(helper, ModItems.BLUETOOTH_CARD, com.payonel.modularcomputers.device.BluetoothCard.class);
		CaseBlockEntity computer = helper.getBlockEntity(POS, CaseBlockEntity.class);
		java.util.UUID other = java.util.UUID.randomUUID();
		helper.assertTrue(card.getLink(args()).first() == null, "linked to nothing");
		helper.assertValueEqual(card.getBoard(args()).first(), computer.boardId().toString(), "its computer's board");
		helper.assertTrue(!card.setLink(args(other.toString())).failed(), "relinked");
		helper.assertValueEqual(computer.parts().getItem(CaseMenu.EXP_SLOTS + 1).get(com.payonel.modularcomputers.item.ModComponents.BLUETOOTH_LINK), other,
			"on the card itself");
		helper.assertValueEqual(card.getLink(args()).first(), other.toString(), "read back");
		helper.assertTrue(card.setLink(args("not an id")).failed(), "not an id");
		helper.assertTrue(!card.setLink(args()).failed(), "unlinked");
		helper.assertTrue(!computer.parts().getItem(CaseMenu.EXP_SLOTS + 1).has(com.payonel.modularcomputers.item.ModComponents.BLUETOOTH_LINK), "gone from it");
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void aMotionSensorSeesAPlayerMove(GameTestHelper helper) {
		MotionSensorCard card = computerWith(helper, ModItems.MOTION_SENSOR_CARD, MotionSensorCard.class);
		var player = GameTests.serverPlayer(helper);
		BlockPos at = helper.absolutePos(POS.north(3));
		player.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		List<Map<?, ?>> seen = new ArrayList<>();
		helper.startSequence()
			.thenIdle(10)
			.thenExecute(() -> player.teleportTo(player.getX() + 1, player.getY(), player.getZ()))
			.thenWaitUntil(() -> {
				Object event;
				while ((event = card.pull(args()).first()) != null) {
					seen.add((Map<?, ?>) event);
				}
				helper.assertTrue(seen.stream().anyMatch(e -> "minecraft:player".equals(e.get("mob"))), "saw the player: " + seen);
			})
			.thenSucceed();
	}

	/**
	 * The whole way, in a world: mcos on a case with the least RAM (no cache: every read a call), a
	 * motion sensor card put in while it runs, a pig, and a program reading the card's file.
	 */
	@GameTest(maxTicks = 1200) // (mcos boots off the disk: a board call a read)
	public void mcosReadsAMotionSensorPutInWhileItRunsAsAMobMoves(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.TIER3_CASE.defaultBlockState().setValue(CaseBlock.FACING, Direction.SOUTH));
		CaseBlockEntity computer = helper.getBlockEntity(POS, CaseBlockEntity.class);
		TestPrograms.withScreen(computer);
		computer.parts().setItem(CaseMenu.CPU_SLOT, new ItemStack(ModItems.TIER1_CPU));
		computer.parts().setItem(CaseMenu.RAM_SLOTS, new ItemStack(ModItems.TIER1_RAM));
		computer.parts().setItem(CaseMenu.EEPROM_SLOT, new ItemStack(ModItems.EEPROM));
		computer.parts().setItem(CaseMenu.UPS_SLOT, ModItems.TIER1_BATTERY.charged());
		computer.parts().setItem(CaseMenu.SATA_SLOTS, new ItemStack(ModItems.TIER1_HARD_DISK));
		java.util.UUID disk = computer.parts().getItem(CaseMenu.SATA_SLOTS).get(com.payonel.modularcomputers.item.ModComponents.SERIAL);
		java.nio.file.Path seen = CaseBlockEntity.diskFolder(helper.getLevel().getServer(), disk).resolve("seen");
		computer.pressPower();
		var pig = helper.spawn(EntityTypes.PIG, POS.north(3));
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(computer.machine().runner().map(r -> r.state() == com.payonel.modularcomputers.runtime.Runner.State.WAITING)
				.orElse(false) && computer.terminal().buffer().rows().stream().anyMatch(row -> row.contains("$")), "at the shell"))
			.thenExecute(() -> computer.parts().setItem(CaseMenu.EXP_SLOTS + 1, new ItemStack(ModItems.MOTION_SENSOR_CARD))) // (put in while it runs)
			.thenIdle(10)
			.thenExecute(() -> "head -n 2 /dev/motion0 > /seen\r".chars().forEach(c -> computer.terminal().press(String.valueOf((char) c))))
			.thenIdle(5)
			.thenExecute(() -> pig.teleportTo(pig.getX() + 1, pig.getY(), pig.getZ())) // (it moved)
			.thenWaitUntil(() -> {
				try {
					helper.assertTrue(java.nio.file.Files.isRegularFile(seen) && java.nio.file.Files.readString(seen).lines().count() == 2,
						"two motions read; the screen: " + computer.terminal().buffer().rows());
				} catch (java.io.IOException e) {
					throw new java.io.UncheckedIOException(e);
				}
			})
			.thenSucceed();
	}
}
