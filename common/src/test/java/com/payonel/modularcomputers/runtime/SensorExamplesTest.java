package com.payonel.modularcomputers.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.payonel.modularcomputers.device.AudioSensorCard;
import com.payonel.modularcomputers.device.BluetoothCard;
import com.payonel.modularcomputers.device.MotionSensorCard;
import com.payonel.modularcomputers.device.RedstoneCard;
import com.payonel.modularcomputers.device.WeatherCard;
import com.payonel.modularcomputers.machine.Motherboard;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * The manual's programs for the redstone card, the sensors and the Bluetooth card
 * (docs/examples/redstone-*, motion-*, audio-*, weather-*, bluetooth-*), each run on a computer
 * with the devices it uses, checked by what it does to them and to its files.
 */
class SensorExamplesTest extends McosHarness {
	/** The redstone card's faces, as its {@code SIDES}. */
	private static final int NORTH = 0;
	private static final int WEST = 3;
	private static final int UP = 4;

	private final RedstoneCard redstone = new RedstoneCard();

	/** Until a file is there and holds {@code part}. */
	private void untilFileHas(Path file, String part) {
		settle(() -> Files.isRegularFile(file) && read(file).contains(part));
	}

	@Test
	void theCounterLogsEachPulseOnItsFace() {
		devices.put(Motherboard.exp(0), redstone);
		boot();
		file("counter.lua", example("redstone-counter").replace("local PERIOD = 60 ", "local PERIOD = 1 "));
		type("lua /counter.lua &\r");
		untilIdle();
		server.add(() -> {
			redstone.sense(WEST, 15);
			redstone.sense(WEST, 0);
			redstone.sense(WEST, 9);
			redstone.sense(WEST, 0);
			redstone.sense(NORTH, 15); // (another face: not counted)
		});
		Path log = root().resolve("home/pulses.log");
		settle(() -> Files.isRegularFile(log) && pulses(log) == 2);
	}

	/** The pulses a counter's log adds up to (its lines end in a count). */
	private static int pulses(Path log) {
		return Arrays.stream(read(log).split("\n")).filter(line -> !line.isBlank())
			.mapToInt(line -> Integer.parseInt(line.substring(line.lastIndexOf(' ') + 1))).sum();
	}

	@Test
	void theDoorOpensOnAPressAndClosesWhenNothingChanges() {
		devices.put(Motherboard.exp(0), redstone);
		boot();
		file("door.lua", example("redstone-door").replace("local HOLD = 3 ", "local HOLD = 1 "));
		type("lua /door.lua &\r");
		untilIdle();
		assertThat(redstone.output(NORTH)).as("closed until pressed").isZero();
		server.add(() -> redstone.sense(WEST, 15));
		settle(() -> redstone.output(NORTH) == 15);
		server.add(() -> redstone.sense(WEST, 0));
		settle(() -> redstone.output(NORTH) == 0);
		server.add(() -> redstone.sense(WEST, 15)); // (and again)
		settle(() -> redstone.output(NORTH) == 15);
	}

	@Test
	void theCensusCountsEachKindSeen() {
		MotionSensorCard motion = new MotionSensorCard();
		devices.put(Motherboard.exp(0), motion);
		boot();
		file("census.lua", example("motion-census"));
		type("lua /census.lua &\r");
		untilIdle();
		server.add(() -> {
			motion.sense(Map.of("mob", "minecraft:pig", "x", 1.5, "y", 0.0, "z", -3.0));
			motion.sense(Map.of("mob", "minecraft:cow", "x", 2.0, "y", 0.0, "z", 1.0));
			motion.sense(Map.of("mob", "minecraft:pig", "x", 1.0, "y", 0.0, "z", -3.0));
		});
		untilFile(root().resolve("home/census.txt"), "minecraft:cow 1\nminecraft:pig 2\n");
	}

	/** The threads page's tally: two threads, two sensors, one count kept by a mutex, none lost. */
	@Test
	void theTallyCountsWhatBothSensorsSeeInOneTallyLosingNone() {
		MotionSensorCard first = new MotionSensorCard();
		MotionSensorCard second = new MotionSensorCard();
		devices.put(Motherboard.exp(0), first);
		devices.put(Motherboard.exp(1), second);
		boot();
		file("tally.lua", example("threads-tally"));
		type("lua /tally.lua > /dev/null 2> /err &\r");
		untilIdle();
		server.add(() -> {
			for (int i = 0; i < 3; i++) {
				first.sense(Map.of("mob", "minecraft:pig", "x", 1.0, "y", 0.0, "z", 0.0));
				second.sense(Map.of("mob", "minecraft:pig", "x", 2.0, "y", 0.0, "z", 0.0));
			}
		});
		untilFile(root().resolve("home/tally.txt"), "minecraft:pig 6\n");
	}

	@Test
	void theVisitorsServiceStartsWithRcLogsWhatItSeesAndStops() {
		MotionSensorCard motion = new MotionSensorCard();
		devices.put(Motherboard.exp(0), motion);
		boot();
		file("home/visitors.lua", example("service-visitors"));
		file("etc/rc.d/visitors", example("service-visitors-rc"));
		type("rc visitors start; rc visitors status > /running\r");
		settle(() -> Files.exists(root().resolve("running")));
		untilIdle();
		server.add(() -> motion.sense(Map.of("mob", "minecraft:pig", "x", 1.5, "y", 0.0, "z", -3.0)));
		settle(() -> Files.exists(root().resolve("home/visitors.log")) && read(root().resolve("home/visitors.log")).contains("minecraft:pig"));
		type("rc visitors stop; rc visitors status > /stopped; echo $? > /status\r");
		untilFile(root().resolve("status"), "3\n"); // (LSB's "not running")
		settle(() -> read(root().resolve("home/visitors.log")).contains(" stopped\n"));
		assertThat(read(root().resolve("home/visitors.log"))).as("a line a creature, then its stop").containsPattern("minecraft:pig\n.* stopped\n$");
	}

	@Test
	void theVisitorsServiceWrittenOutInFullStartsCountsAndStops() {
		MotionSensorCard motion = new MotionSensorCard();
		devices.put(Motherboard.exp(0), motion);
		boot();
		file("home/visitors.lua", example("service-visitors"));
		file("etc/rc.d/visitors", example("service-visitors-full-rc"));
		type("rc visitors start; rc visitors status > /running; echo $? > /up\r");
		untilFile(root().resolve("up"), "0\n");
		untilIdle();
		server.add(() -> motion.sense(Map.of("mob", "minecraft:pig", "x", 1.5, "y", 0.0, "z", -3.0)));
		settle(() -> Files.exists(root().resolve("home/visitors.log")) && read(root().resolve("home/visitors.log")).contains("minecraft:pig"));
		type("rc visitors count > /count; rc visitors restart; rc visitors stop; rc visitors status > /stopped; echo $? > /status\r");
		untilFile(root().resolve("status"), "3\n");
		assertThat(read(root().resolve("count"))).isEqualTo("1 visitors\n");
		assertThat(read(root().resolve("running"))).startsWith("visitors is running (pid ");
		assertThat(read(root().resolve("stopped"))).isEqualTo("visitors is stopped\n");
	}

	@Test
	void theNearLightGrowsBrighterAsSomethingComesCloseAndGoesDark() {
		MotionSensorCard motion = new MotionSensorCard();
		devices.put(Motherboard.exp(0), motion);
		devices.put(Motherboard.exp(1), redstone);
		boot();
		file("near.lua", example("motion-nearlight"));
		type("lua /near.lua &\r");
		untilIdle();
		server.add(() -> {
			motion.sense(Map.of("mob", "minecraft:pig", "x", 6.0, "y", 0.0, "z", 0.0));
			motion.sense(Map.of("mob", "minecraft:pig", "x", 1.0, "y", 0.0, "z", 0.0)); // the nearest: 15 * 7/8
		});
		settle(() -> redstone.output(UP) == 13);
		settle(() -> redstone.output(UP) == 0); // (nothing moves: dark)
	}

	@Test
	void theSoundLogKeepsOnlyTheSoundsItWatches() {
		AudioSensorCard audio = new AudioSensorCard();
		devices.put(Motherboard.exp(0), audio);
		boot();
		file("sounds.lua", example("audio-log"));
		type("lua /sounds.lua &\r");
		untilIdle();
		server.add(() -> {
			audio.hear(Map.of("sound", "minecraft:entity.pig.ambient", "x", 1.0, "y", 0.0, "z", 0.0));
			audio.hear(Map.of("sound", "minecraft:block.chest.open", "x", 2.5, "y", 0.0, "z", -1.5));
			audio.hear(Map.of("sound", "minecraft:block.wooden_door.close", "x", 0.5, "y", 1.0, "z", 0.5));
		});
		Path log = root().resolve("home/sounds.log");
		untilFileHas(log, "minecraft:block.wooden_door.close 0.5 1.0 0.5\n");
		assertThat(read(log)).contains(" minecraft:block.chest.open 2.5 0.0 -1.5\n").doesNotContain("pig");
		assertThat(read(log).split("\n")).hasSize(2);
	}

	@Test
	void thePointerLightsTheFaceTowardTheLastSound() {
		AudioSensorCard audio = new AudioSensorCard();
		devices.put(Motherboard.exp(0), audio);
		devices.put(Motherboard.exp(1), redstone);
		boot();
		file("pointer.lua", example("audio-pointer"));
		type("lua /pointer.lua &\r");
		untilIdle();
		server.add(() -> audio.hear(Map.of("sound", "minecraft:entity.cow.ambient", "x", -3.0, "y", 0.0, "z", 1.0)));
		settle(() -> redstone.output(WEST) == 15);
		server.add(() -> audio.hear(Map.of("sound", "minecraft:entity.bat.ambient", "x", 1.0, "y", 5.0, "z", 0.0)));
		settle(() -> redstone.output(UP) == 15 && redstone.output(WEST) == 0);
	}

	@Test
	void theWeatherDiaryNotesTheStartAndEachChange() {
		WeatherCard weather = new WeatherCard();
		weather.sense("clear"); // (its first look)
		devices.put(Motherboard.exp(0), weather);
		boot();
		file("diary.lua", example("weather-log"));
		type("lua /diary.lua &\r");
		Path log = root().resolve("home/weather.log");
		untilFileHas(log, " clear (started)\n");
		server.add(() -> weather.sense("rain"));
		untilFileHas(log, " rain\n");
		server.add(() -> weather.sense("thunder"));
		untilFileHas(log, " thunder\n");
	}

	@Test
	void theRoofClosesWhileItRainsFromTheWeatherAtTheStart() {
		WeatherCard weather = new WeatherCard();
		weather.sense("clear");
		weather.sense("rain"); // (raining already when the program starts: no change it sees)
		devices.put(Motherboard.exp(0), weather);
		devices.put(Motherboard.exp(1), redstone);
		boot();
		file("roof.lua", example("weather-roof"));
		type("lua /roof.lua &\r");
		settle(() -> redstone.output(UP) == 15);
		server.add(() -> weather.sense("clear"));
		settle(() -> redstone.output(UP) == 0);
		server.add(() -> weather.sense("snow"));
		settle(() -> redstone.output(UP) == 15);
	}

	/** A Bluetooth card whose link is kept in {@code link}, in a computer whose board is {@code board}. */
	private static BluetoothCard bluetooth(AtomicReference<UUID> link, UUID board) {
		BluetoothCard card = new BluetoothCard();
		card.linkIn(link::get, link::set);
		card.boardFrom(() -> board);
		return card;
	}

	@Test
	void theSwitchLinksToEachBoardInTurnAndRefusesANonBoard() {
		UUID a = UUID.fromString("5f0c0000-0000-0000-0000-00000000000a");
		UUID b = UUID.fromString("9a210000-0000-0000-0000-00000000000b");
		AtomicReference<UUID> link = new AtomicReference<>();
		devices.put(Motherboard.exp(0), bluetooth(link, UUID.randomUUID()));
		boot();
		file("switch.lua", example("bluetooth-switch"));
		type("lua /switch.lua " + a + " " + b + "\r");
		settle(() -> a.equals(link.get())); // (no link: the first)
		untilIdle();
		type("lua /switch.lua " + a + " " + b + "\r");
		settle(() -> b.equals(link.get()));
		untilIdle();
		type("lua /switch.lua " + a + " " + b + "\r");
		settle(() -> a.equals(link.get())); // (round again)
		untilIdle();
		type("lua /switch.lua nope; echo $? > /status\r");
		untilFile(root().resolve("status"), "1\n");
		assertThat(link.get()).isEqualTo(a);
	}

	@Test
	void theRememberedLinkComesBackAndClears() {
		UUID a = UUID.fromString("5f0c0000-0000-0000-0000-00000000000a");
		AtomicReference<UUID> link = new AtomicReference<>(a);
		devices.put(Motherboard.exp(0), bluetooth(link, UUID.randomUUID()));
		boot();
		file("remember.lua", example("bluetooth-remember"));
		type("lua /remember.lua save\r");
		untilFile(root().resolve("home/bluetooth.link"), a + "\n");
		untilIdle();
		type("lua /remember.lua clear\r");
		settle(() -> link.get() == null);
		untilIdle();
		type("lua /remember.lua restore\r");
		settle(() -> a.equals(link.get()));
	}
}
