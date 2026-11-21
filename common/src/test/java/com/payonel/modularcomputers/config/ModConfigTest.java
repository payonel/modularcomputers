package com.payonel.modularcomputers.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.payonel.modularcomputers.bus.BusPower;
import com.payonel.modularcomputers.bus.DeviceType;
import com.payonel.modularcomputers.device.CardKind;
import com.payonel.modularcomputers.device.Disk;
import com.payonel.modularcomputers.device.DiskTier;
import com.payonel.modularcomputers.device.NetworkCard;
import com.payonel.modularcomputers.device.Photomask;
import com.payonel.modularcomputers.device.RoboticPiston;
import com.payonel.modularcomputers.device.RoboticsDevice;
import com.payonel.modularcomputers.device.SoundCard;
import com.payonel.modularcomputers.machine.CpuTier;
import com.payonel.modularcomputers.power.PowerBuffer;
import com.payonel.modularcomputers.entity.GolemWayfinder;
import com.payonel.modularcomputers.power.BatteryTier;
import com.payonel.modularcomputers.power.PowerUnits;
import com.payonel.modularcomputers.runtime.Arguments;
import com.payonel.modularcomputers.runtime.CpuCores;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModConfigTest {
	@TempDir
	Path dir;

	@AfterEach
	void restoreDefaults() {
		ModConfig.apply(new Properties());
	}

	private Path file() {
		return dir.resolve("config").resolve(ModConfig.FILE_NAME);
	}

	/** A file holding these lines, loaded: what it warned of. */
	private List<String> load(String text) throws IOException {
		Path file = file();
		Files.createDirectories(file.getParent());
		Files.writeString(file, text);
		return ModConfig.load(file);
	}

	/** Defaults' constants by the keys their paths name (the user's, 2026-11-18: Power.Cost.Robot.WALK is power.cost.robot.walk). */
	static Map<String, Object> defaultsByKey() throws IllegalAccessException {
		Map<String, Object> keys = new LinkedHashMap<>();
		walk(Defaults.class, "", keys);
		return keys;
	}

	private static void walk(Class<?> group, String prefix, Map<String, Object> keys) throws IllegalAccessException {
		for (Field field : group.getDeclaredFields()) {
			int modifiers = field.getModifiers();
			if (Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers) && Modifier.isPublic(modifiers)) {
				keys.put(prefix + field.getName().toLowerCase(Locale.ROOT), field.get(null));
			}
		}
		for (Class<?> nested : group.getDeclaredClasses()) {
			walk(nested, prefix + snake(nested.getSimpleName()) + ".", keys);
		}
	}

	/** A group's name as a key's: its words in snake_case (RingDepth is ring_depth, Tier1 tier1). */
	private static String snake(String name) {
		return name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
	}

	/**
	 * Every setting's default is one constant in Defaults, at the path its key names, and nowhere else
	 * (the user's, 2026-11-18): every key the config reads is a constant there, with the same default,
	 * and every constant there is a key in the file written by default, explained by a comment over it
	 * (or over the small group it's in).
	 */
	@Test
	void everySettingIsADefaultAtItsKeysPathAndEveryDefaultIsAnExplainedSetting() throws Exception {
		Map<String, Object> defaults = defaultsByKey();
		Map<String, Object> read = new LinkedHashMap<>();
		ModConfig.settings().forEach(setting -> read.put(setting.key(), setting.fallback()));
		assertThat(read.keySet()).as("every key the config reads is a Defaults constant").isSubsetOf(defaults.keySet());
		assertThat(defaults.keySet()).as("every Defaults constant is a key the config reads").isSubsetOf(read.keySet());
		assertThat(read).as("each with that constant as its default").isEqualTo(defaults);

		List<String> lines = ModConfig.defaults().lines().toList();
		for (String key : defaults.keySet()) {
			int at = -1;
			for (int i = 0; i < lines.size(); i++) {
				if (lines.get(i).startsWith(key + ":")) {
					at = i;
				}
			}
			assertThat(at).as(key + " is in the file written by default").isNotNegative();
			int above = at - 1;
			while (above >= 0 && !lines.get(above).isBlank() && !lines.get(above).startsWith("#")) {
				above--; // (a small group's settings under one comment)
			}
			assertThat(above >= 0 && lines.get(above).startsWith("#")).as(key + " has a comment over it").isTrue();
		}
		Properties written = new Properties();
		written.load(new java.io.StringReader(ModConfig.defaults()));
		assertThat(written.stringPropertyNames()).as("and nothing else").containsExactlyInAnyOrderElementsOf(defaults.keySet());
	}

	/** The manual's settings page, found from wherever the tests run. */
	private static Path settingsPage() {
		Path dir = Path.of("").toAbsolutePath();
		while (!Files.isRegularFile(dir.resolve(SETTINGS_PAGE))) {
			dir = dir.getParent();
		}
		return dir.resolve(SETTINGS_PAGE);
	}

	/** Where the manual lists every setting (the user's, 2026-11-18: the one page that gives their values). */
	private static final String SETTINGS_PAGE = "docs/settings.md";
	/** A table row of the page naming a setting: its key, its default. */
	private static final java.util.regex.Pattern PAGE_ROW = java.util.regex.Pattern.compile("^\\| `([a-z0-9_.<>]+)` \\| ([^|]+?) \\|");
	/** What the page says for an empty default, and for an opt-in setting's (none of its own). */
	private static final String PAGE_EMPTY = "nothing";
	private static final String PAGE_NOT_SET = "not set";

	/**
	 * The settings page is written by hand, and kept to the settings by this test: every setting is on
	 * it once, with its default, and nothing else is (an opt-in family's row aside, not set).
	 */
	@Test
	void theManualsSettingsPageListsEverySettingWithItsDefault() throws IOException {
		Map<String, String> page = new LinkedHashMap<>();
		for (String line : Files.readAllLines(settingsPage())) {
			var row = PAGE_ROW.matcher(line);
			if (row.find()) {
				String cell = row.group(2).strip();
				assertThat(page.put(row.group(1), cell.startsWith("`") ? cell.substring(1, cell.length() - 1) : cell))
					.as(row.group(1) + " is on the page once").isNull();
			}
		}
		assertThat(page.remove(ModConfig.RING_DEPTH_TYPE_PREFIX + "<type>")).as("a type's own ring depth, opt-in").isEqualTo(PAGE_NOT_SET);
		Map<String, String> settings = new LinkedHashMap<>();
		ModConfig.settings().forEach(setting -> settings.put(setting.key(), setting.text().isEmpty() ? PAGE_EMPTY : setting.text()));
		assertThat(page).as("every setting, with its default, and nothing else").containsExactlyInAnyOrderEntriesOf(settings);
	}

	/** A type's own ring depth is opt-in: no default of its own, in the group of the depth it falls back to. */
	@Test
	void aTypesOwnRingDepthHasNoDefaultAndFallsBackToEveryones() throws Exception {
		String group = ModConfig.RING_DEPTH.key().substring(0, ModConfig.RING_DEPTH.key().lastIndexOf('.') + 1);
		assertThat(ModConfig.RING_DEPTH_TYPE_PREFIX).isEqualTo(group);
		assertThat(ModConfig.ringDepthKey(DeviceType.ROBOTICS)).isEqualTo("device.ring_depth.robotics");
		assertThat(defaultsByKey()).doesNotContainKey("device.ring_depth.robotics");
		assertThat(ModConfig.defaults()).doesNotContain("device.ring_depth.robotics:");
	}

	@Test
	void writesTheDefaultsTheFirstTime() throws IOException {
		ModConfig.load(file());
		assertThat(Files.readString(file())).contains("computer.cpu_cores: 4\n", "power.battery.tier1.duration: 10\n",
			"power.battery.tier2.duration: 20\n", "power.battery.tier3.duration: 0\n", "power.battery.tier2.recharge_rate: 0.5\n",
			"power.rate.charging_pad: 10\n", "power.cost.network.wireless_send_per_block: 0.0625\n", "power.cost.terminal.write: 0.01\n",
			"device.ring_depth.all: 512\n", "internet.enabled: true\n", "internet.allow:\n", "internet.deny:\n",
			"internet.response_buffer_kib: 64\n", "internet.connect_timeout: 10\n", "internet.read_timeout: 30\n", "internet.request_timeout: 600\n",
			"internet.max_header_kib: 64\n", "internet.max_upload_kib: 1024\n", "internet.requests_per_card: 4\n", "internet.requests_per_server: 64\n",
			"device.motion_sensor.interval: 5\n", "device.motion_sensor.threshold: 0.1\n", "robot.pathfinding_range: 96\n", "power.cost.robot.walk: 1\n", "power.cost.robot.turn: 1\n",
			"power.cost.robot.hand: 1\n", "power.cost.robot.thrusters: 5\n");
		assertThat(BatteryTier.TIER_2.rechargeRate()).isEqualTo(0.5);
		assertThat(BatteryTier.TIER_1.days()).isEqualTo(10);
		assertThat(BatteryTier.TIER_3.infinite()).isTrue();
		assertThat(CpuCores.size()).isEqualTo(4);
	}

	/** A value set is read back, wherever it's used. */
	@Test
	void aValueSetIsReadBack() throws IOException {
		ModConfig.load(file());
		Files.writeString(file(), Files.readString(file()).replace("computer.cpu_cores: 4", "computer.cpu_cores: 2")
			.replace("internet.enabled: true", "internet.enabled: false").replace("internet.deny:", "internet.deny: a.example, 203.0.113.0/24"));
		assertThat(ModConfig.load(file())).isEmpty();
		assertThat(CpuCores.size()).isEqualTo(2);
		assertThat(ModConfig.internet().enabled()).isFalse();
		assertThat(ModConfig.internet().deny()).containsExactly("a.example", "203.0.113.0/24");
	}

	/**
	 * Ring depths are settings (the user's, 2026-11-11): everyone's, 512 by default, written; a
	 * type's own only where it's set, else everyone's.
	 */
	@Test
	void ringDepthsAreEveryonesUnlessATypeSetsItsOwn() {
		Properties set = new Properties();
		set.setProperty("device.ring_depth.all", "300");
		set.setProperty("device.ring_depth.robotics", "40");
		var depths = ModConfig.ringDepths(set); // (worked out, not applied: other tests' devices read the server's)
		assertThat(depths.of(DeviceType.ROBOTICS)).as("its own").isEqualTo(40);
		assertThat(depths.of(DeviceType.MOTION)).as("everyone's").isEqualTo(300);
		assertThat(ModConfig.ringDepths(new Properties()).of(DeviceType.ROBOTICS)).as("none set: the default").isEqualTo(512);
		set.setProperty("device.ring_depth.robotics", "deep");
		assertThat(ModConfig.ringDepths(set).of(DeviceType.ROBOTICS)).as("bad: everyone's").isEqualTo(300);
	}

	/**
	 * How far a bot golem's pathfinding searches is a setting (the user's, 2026-11-14): 96 blocks
	 * walked by default; set, a leg searches that far.
	 */
	@Test
	void thePathfindingRangeIsASetting() throws IOException {
		ModConfig.load(file());
		assertThat(GolemWayfinder.range()).isEqualTo(96);
		Files.writeString(file(), Files.readString(file()).replace("robot.pathfinding_range: 96", "robot.pathfinding_range: 40"));
		ModConfig.load(file());
		assertThat(GolemWayfinder.range()).isEqualTo(40);
	}

	/**
	 * How fast a bot golem turns is a setting (the user's, 2026-11-18): 18 degrees a tick by default
	 * (a right angle in five ticks); set, it turns that fast; 0 or less would never turn, so it falls
	 * back with a warning.
	 */
	@Test
	void theTurnSpeedIsASetting() throws IOException {
		ModConfig.load(file());
		assertThat(Files.readString(file())).contains("robot.turn_speed: 18\n");
		assertThat(ModConfig.turnSpeed()).isEqualTo(18);
		Files.writeString(file(), Files.readString(file()).replace("robot.turn_speed: 18", "robot.turn_speed: 45"));
		ModConfig.load(file());
		assertThat(ModConfig.turnSpeed()).isEqualTo(45);
		Files.writeString(file(), Files.readString(file()).replace("robot.turn_speed: 45", "robot.turn_speed: 0"));
		assertThat(ModConfig.load(file())).anyMatch(warning -> warning.startsWith("robot.turn_speed"));
		assertThat(ModConfig.turnSpeed()).isEqualTo(18);
	}

	/**
	 * How often a motion sensor looks and how far a creature must move to count are settings (the
	 * user's, 2026-11-21): 5 ticks and a tenth of a block by default; set, those; 0 is a threshold (every
	 * creature seen), not an interval, which is a whole count.
	 */
	@Test
	void theMotionSensorsLookIsASetting() {
		assertThat(ModConfig.motionSensorInterval()).isEqualTo(5);
		assertThat(ModConfig.motionSensorThreshold()).isEqualTo(0.1);
		assertThat(ModConfig.apply(props("device.motion_sensor.interval", "20", "device.motion_sensor.threshold", "0"))).isEmpty();
		assertThat(ModConfig.motionSensorInterval()).isEqualTo(20);
		assertThat(ModConfig.motionSensorThreshold()).isZero();
		List<String> warnings = ModConfig.apply(props("device.motion_sensor.interval", "0", "device.motion_sensor.threshold", "-1"));
		assertThat(warnings).hasSize(2).anySatisfy(warning -> assertThat(warning).startsWith("device.motion_sensor.interval should be a whole count"))
			.anySatisfy(warning -> assertThat(warning).startsWith("device.motion_sensor.threshold should be a number of blocks, 0 or more"));
		assertThat(ModConfig.motionSensorInterval()).isEqualTo(5);
		assertThat(ModConfig.motionSensorThreshold()).isEqualTo(0.1);
		assertThat(ModConfig.apply(props("device.motion_sensor.interval", "2.5"))).hasSize(1);
		assertThat(ModConfig.motionSensorInterval()).isEqualTo(5);
	}

	/**
	 * The internet card's limits are settings (the user's, 2026-11-21), in an admin's units: its
	 * timeouts in seconds (more than 0, fractions too), its sizes in KiB and its counts, whole, 1 or
	 * more; their defaults today's, a value set what a request is held to.
	 */
	@Test
	void theInternetCardsLimitsAreSettings() {
		assertThat(ModConfig.internetLimits()).as("today's").isEqualTo(new com.payonel.modularcomputers.internet.InternetLimits(10_000, 30_000,
			600_000_000_000L, 64 * 1024, 1024 * 1024, 4, 64));
		assertThat(ModConfig.apply(props("internet.connect_timeout", "2.5", "internet.read_timeout", "5", "internet.request_timeout", "60",
			"internet.max_header_kib", "8", "internet.max_upload_kib", "16", "internet.requests_per_card", "2", "internet.requests_per_server", "10")))
			.isEmpty();
		assertThat(ModConfig.internetLimits()).isEqualTo(new com.payonel.modularcomputers.internet.InternetLimits(2500, 5000, 60_000_000_000L,
			8 * 1024, 16 * 1024, 2, 10));
		List<String> warnings = ModConfig.apply(props("internet.connect_timeout", "0", "internet.read_timeout", "-1", "internet.request_timeout", "soon",
			"internet.max_header_kib", "0", "internet.max_upload_kib", "1.5", "internet.requests_per_card", "0", "internet.requests_per_server", "-3"));
		assertThat(warnings).hasSize(7);
		assertThat(warnings.subList(0, 3)).allSatisfy(warning -> assertThat(warning).contains("should be a number of seconds more than 0"));
		assertThat(ModConfig.internetLimits()).as("bad: the defaults").isEqualTo(new com.payonel.modularcomputers.internet.InternetLimits(10_000,
			30_000, 600_000_000_000L, 64 * 1024, 1024 * 1024, 4, 64));
		ModConfig.apply(props("internet.request_timeout", "1e300", "internet.read_timeout", "0.0001"));
		assertThat(ModConfig.internetLimits().requestNanos()).as("for ever, near enough: no deadline wraps round").isEqualTo(Long.MAX_VALUE / 2);
		assertThat(ModConfig.internetLimits().readMillis()).as("never 0, a socket's for ever").isEqualTo(1);
	}

	@Test
	void readsKeyColonValueLinesWithFractionsAndZeroForever() throws IOException {
		load("power.battery.tier1.duration: 0.5\npower.battery.tier2.duration: 1\npower.battery.tier3.duration: 0\n");
		assertThat(BatteryTier.TIER_1.capacity()).isEqualTo(PowerUnits.DAY / 2);
		assertThat(BatteryTier.TIER_2.capacity()).isEqualTo(PowerUnits.DAY);
		assertThat(BatteryTier.TIER_3.infinite()).isTrue();
		ModConfig.apply(props("power.battery.tier1.duration", "0", "power.battery.tier3.duration", "3"));
		assertThat(BatteryTier.TIER_1.infinite()).as("any tier can be set to last forever").isTrue();
		assertThat(BatteryTier.TIER_2.days()).as("missing: back to the default").isEqualTo(20);
		assertThat(BatteryTier.TIER_3.infinite()).isFalse();
		assertThat(BatteryTier.TIER_3.capacity()).isEqualTo(3 * PowerUnits.DAY);
	}

	@Test
	void anOlderFileGetsTheNewSettingsAddedUnderTheirGroupsAndKeepsItsOwn() throws IOException {
		load("# mine\npower.battery.tier1.duration: 0.5\npower.battery.tier2.duration: 1\npower.battery.tier3.duration: 0\n");
		String text = Files.readString(file());
		assertThat(text).startsWith("# mine\npower.battery.tier1.duration: 0.5\n").contains("\n# power.battery: ",
			"power.battery.tier2.recharge_rate: 0.5\n", "\n# robot: ", "power.cost.robot.thrusters: 5\n");
		assertThat(text).containsOnlyOnce("power.battery.tier1.duration");
		assertThat(BatteryTier.TIER_1.days()).isEqualTo(0.5);
		ModConfig.load(file());
		assertThat(Files.readString(file())).as("nothing more to add").isEqualTo(text);
		Properties all = new Properties();
		all.load(new java.io.StringReader(text));
		assertThat(all.stringPropertyNames()).containsExactlyInAnyOrderElementsOf(ModConfig.settings().stream().map(ModConfig.Setting::key).toList());
	}

	@Test
	void tier2RechargeRateCanBeChangedOrTurnedOff() throws IOException {
		load("power.battery.tier2.recharge_rate: 1\n");
		assertThat(BatteryTier.TIER_2.rechargeRate()).isEqualTo(1);
		ModConfig.apply(props("power.battery.tier2.recharge_rate", "0"));
		assertThat(BatteryTier.TIER_2.selfCharging()).isFalse();
		ModConfig.apply(props("power.battery.tier2.recharge_rate", "fast"));
		assertThat(BatteryTier.TIER_2.rechargeRate()).as("bad: the default").isEqualTo(0.5);
	}

	/** A bad value falls back to its default, and says so; a key that's no setting (an old name) is warned of, and ignored. */
	@Test
	void badValuesFallBackToTheDefaultsWithAWarning() {
		List<String> warnings = ModConfig.apply(props("power.battery.tier1.duration", "lots", "power.battery.tier2.duration", "-4",
			"computer.cpu_cores", "1.5", "internet.enabled", "maybe", "power.cost.robot.walk", "-1", "cpu_cores", "8"));
		assertThat(BatteryTier.TIER_1.days()).isEqualTo(10);
		assertThat(BatteryTier.TIER_2.days()).isEqualTo(20);
		assertThat(CpuCores.size()).isEqualTo(4);
		assertThat(ModConfig.internet().enabled()).isTrue();
		assertThat(ModConfig.costs().walk()).isEqualTo(PowerUnits.CPU);
		assertThat(warnings).hasSize(6).anySatisfy(warning -> assertThat(warning).startsWith("power.battery.tier1.duration should be")
			.endsWith("not \"lots\"; using 10")).anySatisfy(warning -> assertThat(warning).startsWith("computer.cpu_cores should be a whole count"))
			.anySatisfy(warning -> assertThat(warning).startsWith("internet.enabled should be true or false"))
			.anySatisfy(warning -> assertThat(warning).startsWith("power.cost.robot.walk should be"))
			.contains("cpu_cores is no setting; ignored");
		assertThat(ModConfig.apply(props("device.ring_depth.robotics", "40"))).as("a type's own depth is a setting").isEmpty();
	}

	/**
	 * The bot golem's costs and every device's are settings (the user's, 2026-11-18), in running
	 * CPUs' ticks, fractions too: their defaults today's, a value set what's charged.
	 */
	@Test
	void theCostsAreSettingsAndAreWhatsCharged() throws IOException {
		ModConfig.load(file());
		PowerCosts costs = ModConfig.costs();
		assertThat(List.of(costs.walk(), (double) costs.turn(), (double) costs.hand(), (double) costs.thrusters()))
			.as("today's: a running CPU's tick a block, a turn and a hand's action, five a tick of thrust").containsExactly(100.0, 100.0, 100.0, 500.0);
		assertThat(List.of(costs.redstoneChange(), costs.pistonPush(), costs.fieldHold(), costs.fieldMove(), costs.craft(), costs.craftingMove(),
			costs.diskStationAction(), costs.wiredSend(), costs.internetRequest(), costs.internetData(), costs.terminalWrite(), costs.chargingPadRate()))
			.containsExactly(100, 500, 200, 100, 100, 100, 2000, 10, 100, 10, 1, 1000);
		assertThat(costs.wirelessSendPerBlock()).isEqualTo(6.25);

		Files.writeString(file(), Files.readString(file()).replace("power.cost.robot.walk: 1\n", "power.cost.robot.walk: 0.5\n")
			.replace("power.cost.robot.thrusters: 5\n", "power.cost.robot.thrusters: 2\n").replace("power.cost.robot.turn: 1\n", "power.cost.robot.turn: 0\n")
			.replace("power.cost.robot.hand: 1\n", "power.cost.robot.hand: 3\n").replace("power.cost.robotic_piston.push: 5\n", "power.cost.robotic_piston.push: 0.25\n"));
		assertThat(ModConfig.load(file())).isEmpty();
		RoboticsDevice robotics = new RoboticsDevice();
		robotics.power().supply(this);
		robotics.walked(3);
		assertThat(robotics.power().collect(0)).as("half a CPU's tick a block").isEqualTo(150);
		robotics.thrusted();
		assertThat(robotics.power().collect(0)).isEqualTo(200);
		assertThat(ModConfig.costs().turn()).as("free").isZero();
		assertThat(ModConfig.costs().hand()).isEqualTo(300);
		RoboticPiston piston = new RoboticPiston();
		piston.power().supply(this);
		piston.moveWith((side, motion) -> RoboticPiston.Moved.DONE);
		piston.extend(new Arguments(List.of("up")));
		assertThat(piston.power().collect(0)).isEqualTo(25);
	}

	/**
	 * The bot golem's costs are with every other action's (the user's, 2026-11-18): power.cost.robot,
	 * and their old keys are no settings (renamed: never read).
	 */
	@Test
	void theRobotsCostsAreUnderPowerCostRobot() {
		assertThat(ModConfig.apply(props("power.cost.robot.walk", "2", "power.cost.robot.turn", "3", "power.cost.robot.hand", "0.5",
			"power.cost.robot.thrusters", "4"))).isEmpty();
		PowerCosts costs = ModConfig.costs();
		assertThat(List.of(costs.walk(), (double) costs.turn(), (double) costs.hand(), (double) costs.thrusters())).containsExactly(200.0, 300.0, 50.0, 400.0);
		assertThat(ModConfig.apply(props("robot.cost.walk", "2"))).containsExactly("robot.cost.walk is no setting; ignored");
		assertThat(ModConfig.costs().walk()).as("the default").isEqualTo(100.0);
	}

	/** Every idle draw is a setting (the user's, 2026-11-18), its default today's, read where it's used. */
	@Test
	void theDrawsAreSettingsAtTodaysValues() {
		PowerDraws draws = ModConfig.draws();
		assertThat(draws.cpu()).containsExactly(new PowerDraws.Cpu(100, 50), new PowerDraws.Cpu(150, 20), new PowerDraws.Cpu(200, 5));
		assertThat(List.of(draws.terminal(), draws.soundCard(), draws.motionSensor(), draws.audioSensor(), draws.wiredCard(), draws.wirelessCard(),
			draws.internetCard(), draws.diskStation(), draws.photomask())).containsExactly(10, 6, 9, 9, 2, 5, 4, 1, 5);
		assertThat(draws.disk()).containsExactly(8, 5, 2);

		ModConfig.apply(props("power.draw.cpu.tier2.running", "300", "power.draw.cpu.tier3.waiting", "0", "power.draw.disk.tier1", "12"));
		assertThat(CpuTier.TIER_2.running()).isEqualTo(300);
		assertThat(CpuTier.TIER_3.waiting()).as("free").isZero();
		assertThat(CpuTier.TIER_1.running()).as("the rest their defaults").isEqualTo(100);
		assertThat(DiskTier.TIER_1.draw()).isEqualTo(12);
		assertThat(PowerDraws.of(ModConfig.draws().values(), CpuTier.values().length, DiskTier.values().length)).as("sent as a list")
			.isEqualTo(ModConfig.draws());
		assertThat(PowerDraws.of(List.of(1, 2), CpuTier.values().length, DiskTier.values().length)).as("not a list of them").isNull();
	}

	/**
	 * A draw set is what a device is charged, and a reload reaches a device already built (its draw is
	 * read as its power's settled): the ticks since it was last settled are paid at the new draw.
	 */
	@Test
	void aDrawSetIsWhatADeviceIsChargedEvenOneBuiltBeforeTheReload() {
		SoundCard sound = new SoundCard();
		NetworkCard wlan = new NetworkCard(CardKind.TIER1_WLAN);
		Disk disk = new Disk(DiskTier.TIER_2, dir.resolve("disk"));
		for (BusPower power : List.of(sound.power(), wlan.power(), disk.power())) {
			power.supply(this);
			power.collect(0);
		}
		assertThat(sound.power().collect(10)).as("today's: 6 a tick").isEqualTo(60);
		assertThat(ModConfig.apply(props("power.draw.sound_card", "20", "power.draw.network.wireless", "0", "power.draw.disk.tier2", "7"))).isEmpty();
		assertThat(sound.power().collect(20)).as("set: 20 a tick").isEqualTo(200);
		assertThat(wlan.power().collect(20)).as("free").isZero();
		assertThat(disk.power().collect(20)).isEqualTo(20 * 7);
	}

	/** A draw is a whole number of units a tick, 0 or more: anything else is its default, with a warning. */
	@Test
	void aDrawIsAWholeNumber() {
		List<String> warnings = ModConfig.apply(props("power.draw.terminal", "2.5", "power.draw.sound_card", "-1", "power.draw.photomask", "0"));
		assertThat(warnings).hasSize(2).allSatisfy(warning -> assertThat(warning).contains("should be a whole number of power units a tick"));
		assertThat(ModConfig.draws().terminal()).isEqualTo(10);
		assertThat(ModConfig.draws().soundCard()).isEqualTo(6);
		assertThat(ModConfig.draws().photomask()).as("0 is free").isZero();
	}

	/**
	 * A battery's day is the yardstick's, a computer at the default draws, so a battery holds a fixed
	 * amount whatever the draws are set to: a hungrier CPU runs it down sooner (a real battery's
	 * watt-hours), and a battery's charge doesn't change under it at a reload.
	 */
	@Test
	void aBatteryHoldsAFixedAmountWhateverTheDraws() {
		int full = BatteryTier.TIER_1.capacity();
		ModConfig.apply(props("power.draw.cpu.tier1.running", "300", "power.draw.terminal", "50"));
		assertThat(BatteryTier.TIER_1.capacity()).isEqualTo(full).isEqualTo(10 * PowerUnits.DAY);
		assertThat(PowerUnits.COMPUTER).as("the yardstick: the default draws").isEqualTo(110);
	}

	/**
	 * A photomask's turn costs a setting (the user's, 2026-11-18): its crank charges a turn and its
	 * idle draw till the next, and its buffer holds four cranks, both from the settings as they are,
	 * so a reload reaches a photomask already placed.
	 */
	@Test
	void aPhotomasksTurnCostIsASettingAndItsCrankAndBufferFollow() {
		assertThat(List.of(Photomask.turnCost(), Photomask.crank(), Photomask.buffer())).as("today's").containsExactly(100, 125, 500);
		PowerBuffer buffer = new PowerBuffer(Photomask::buffer);
		buffer.fill(Long.MAX_VALUE);
		assertThat(ModConfig.apply(props("power.cost.photomask.turn", "2", "power.draw.photomask", "1"))).isEmpty();
		assertThat(List.of(Photomask.turnCost(), Photomask.crank(), Photomask.buffer())).containsExactly(200, 205, 820);
		assertThat(buffer.capacity()).as("one built before the reload, resized").isEqualTo(820);
		assertThat(buffer.level()).as("with room").isEqualTo(500);
		ModConfig.apply(props("power.cost.photomask.turn", "0.5"));
		assertThat(Photomask.crank()).isEqualTo(75);
		assertThat(buffer.level()).as("shrunk: what's past its size is gone").isEqualTo(300);
	}

	private static Properties props(String... pairs) {
		Properties properties = new Properties();
		for (int i = 0; i < pairs.length; i += 2) {
			properties.setProperty(pairs[i], pairs[i + 1]);
		}
		return properties;
	}
}
