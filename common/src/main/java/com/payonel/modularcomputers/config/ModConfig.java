package com.payonel.modularcomputers.config;

import com.payonel.modularcomputers.ModularComputers;
import com.payonel.modularcomputers.bus.DeviceType;
import com.payonel.modularcomputers.device.InternetCard;
import com.payonel.modularcomputers.device.RingDepths;
import com.payonel.modularcomputers.entity.GolemWayfinder;
import com.payonel.modularcomputers.internet.InternetPolicy;
import com.payonel.modularcomputers.platform.Platform;
import com.payonel.modularcomputers.power.BatteryTier;
import com.payonel.modularcomputers.runtime.CpuCores;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The mod's settings, in {@code config/modularcomputers.properties} in the game folder: Java's
 * properties, a line a setting ({@code key: value}), grouped under comments. It's written with the
 * defaults the first time the game starts, and a setting a file is missing (it's from an older
 * version) is added to it at its default. {@code /modularcomputers reload} rereads it without
 * restarting; a server sends its settings that clients show to the players who join it
 * ({@code SettingsPayload}).
 * <p>
 * Every setting's default is {@link Defaults}', and its key is its path there (the user's,
 * 2026-11-18): {@code Defaults.Robot.Cost.WALK} is {@code robot.cost.walk}. A value missing or not
 * understood takes its default, with a warning; a key that's no setting is warned of too (a
 * setting renamed: no old names are read).
 */
public final class ModConfig {
	public static final String FILE_NAME = ModularComputers.MOD_ID + ".properties";

	/** A setting: its key (its path in {@link Defaults}) and its default (the constant there). */
	public record Setting(String key, Object fallback) {
		/** Its default as the file writes it. */
		String text() {
			return fallback instanceof Double number ? format(number) : String.valueOf(fallback);
		}
	}

	/** Settings explained together: their comment, then them. */
	private record Block(String comment, List<Setting> settings) {
	}

	/** A group of settings, its header comment and its blocks, as written to the file. */
	private record Group(String comment, List<Block> blocks) {
	}

	public static final Setting CPU_CORES = new Setting("computer.cpu_cores", Defaults.Computer.CPU_CORES);
	/** How many days a full battery of each tier lasts, by tier. */
	public static final List<Setting> BATTERY_DURATIONS = List.of(
		new Setting("power.battery.tier1.duration", Defaults.Power.Battery.Tier1.DURATION),
		new Setting("power.battery.tier2.duration", Defaults.Power.Battery.Tier2.DURATION),
		new Setting("power.battery.tier3.duration", Defaults.Power.Battery.Tier3.DURATION));
	public static final Setting TIER2_RECHARGE_RATE = new Setting("power.battery.tier2.recharge_rate", Defaults.Power.Battery.Tier2.RECHARGE_RATE);
	public static final Setting CHARGING_PAD_RATE = new Setting("power.rate.charging_pad", Defaults.Power.Rate.CHARGING_PAD);
	public static final Setting REDSTONE_CHANGE = new Setting("power.cost.redstone.change", Defaults.Power.Cost.Redstone.CHANGE);
	public static final Setting PISTON_PUSH = new Setting("power.cost.robotic_piston.push", Defaults.Power.Cost.RoboticPiston.PUSH);
	public static final Setting FIELD_HOLD = new Setting("power.cost.field.hold", Defaults.Power.Cost.Field.HOLD);
	public static final Setting FIELD_MOVE = new Setting("power.cost.field.move", Defaults.Power.Cost.Field.MOVE);
	public static final Setting CRAFT = new Setting("power.cost.crafting.craft", Defaults.Power.Cost.Crafting.CRAFT);
	public static final Setting CRAFTING_MOVE = new Setting("power.cost.crafting.move", Defaults.Power.Cost.Crafting.MOVE);
	public static final Setting DISK_STATION_ACTION = new Setting("power.cost.disk_station.action", Defaults.Power.Cost.DiskStation.ACTION);
	public static final Setting WIRED_SEND = new Setting("power.cost.network.wired_send", Defaults.Power.Cost.Network.WIRED_SEND);
	public static final Setting WIRELESS_SEND = new Setting("power.cost.network.wireless_send_per_block", Defaults.Power.Cost.Network.WIRELESS_SEND_PER_BLOCK);
	public static final Setting INTERNET_REQUEST = new Setting("power.cost.internet.request", Defaults.Power.Cost.Internet.REQUEST);
	public static final Setting INTERNET_DATA = new Setting("power.cost.internet.data", Defaults.Power.Cost.Internet.DATA);
	public static final Setting TERMINAL_WRITE = new Setting("power.cost.terminal.write", Defaults.Power.Cost.Terminal.WRITE);
	/** Every device's ring depth; a type's own is opt-in ({@link #ringDepthKey}), with no default of its own: this. */
	public static final Setting RING_DEPTH = new Setting("device.ring_depth.all", Defaults.Device.RingDepth.ALL);
	public static final Setting INTERNET_ENABLED = new Setting("internet.enabled", Defaults.Internet.ENABLED);
	public static final Setting INTERNET_ALLOW = new Setting("internet.allow", Defaults.Internet.ALLOW);
	public static final Setting INTERNET_DENY = new Setting("internet.deny", Defaults.Internet.DENY);
	public static final Setting INTERNET_RESPONSE_BUFFER = new Setting("internet.response_buffer_kib", Defaults.Internet.RESPONSE_BUFFER_KIB);
	public static final Setting PATHFINDING_RANGE = new Setting("robot.pathfinding_range", Defaults.Robot.PATHFINDING_RANGE);
	public static final Setting WALK = new Setting("robot.cost.walk", Defaults.Robot.Cost.WALK);
	public static final Setting TURN = new Setting("robot.cost.turn", Defaults.Robot.Cost.TURN);
	public static final Setting HAND = new Setting("robot.cost.hand", Defaults.Robot.Cost.HAND);
	public static final Setting THRUSTERS = new Setting("robot.cost.thrusters", Defaults.Robot.Cost.THRUSTERS);
	/** Where a type's own ring depth is: this, then its /sys class's name ({@code device.ring_depth.robotics}). */
	public static final String RING_DEPTH_TYPE_PREFIX = "device.ring_depth.";

	/** The file's groups, in order: every setting, explained. */
	private static final List<Group> GROUPS = List.of(
		new Group("""
			# computer: the computers themselves.
			""", List.of(
			new Block("""
				# How many computers run their Lua at once: the server's cores they may use together. A busy
				# computer runs as fast as it can on one, taking turns with the others when there are more
				# computers than cores; a computer waiting (for a key, a device, the server) holds none.
				# A whole count, 1 or more.
				""", List.of(CPU_CORES)))),
		new Group("""
			# power.battery: the batteries that run computers.
			""", List.of(
			new Block("""
				# How many Minecraft days (20 minutes each) a full battery of each tier runs a computer
				# (its CPU running, and its screen). 0 means it never runs out. Fractions work: 0.5 is ten minutes.
				""", BATTERY_DURATIONS),
			new Block("""
				# How fast a tier 2 battery recharges itself while not in use, as a share of what a running
				# computer draws: 0.5 takes two seconds of rest to charge one second of running;
				# 1 charges as fast as it runs; 0 turns it off.
				""", List.of(TIER2_RECHARGE_RATE)))),
		new Group("""
			# power.rate: how fast blocks give power, in ticks of a running CPU's power each tick. 0 or more.
			""", List.of(
			new Block("""
				# What a charging pad gives the robot on it.
				""", List.of(CHARGING_PAD_RATE)))),
		new Group("""
			# power.cost: what devices' actions cost, in ticks of a running CPU's power (the bot golem's own
			# are robot.cost). Fractions work; 0 makes one free.
			""", List.of(
			new Block("""
				# A redstone card: each change of an output.
				""", List.of(REDSTONE_CHANGE)),
			new Block("""
				# A robotic piston: each push, extend or retract, whether it moved anything or not.
				""", List.of(PISTON_PUSH)),
			new Block("""
				# An energy field card: each tick it holds something, and each block it moves it.
				""", List.of(FIELD_HOLD, FIELD_MOVE)),
			new Block("""
				# A crafting card: each craft, and each move of items (between its slots, into its robot's hand,
				# to or from a container, a pickup, a drop).
				""", List.of(CRAFT, CRAFTING_MOVE)),
			new Block("""
				# A Disk Station: each action (a format, wipe, clone, install or update).
				""", List.of(DISK_STATION_ACTION)),
			new Block("""
				# Network cards: each frame a wired card sends; each frame a WLAN card sends, for each block of
				# its strength (a farther signal costs more).
				""", List.of(WIRED_SEND, WIRELESS_SEND)),
			new Block("""
				# An internet card: each request, and each 4 KiB of a body read.
				""", List.of(INTERNET_REQUEST, INTERNET_DATA)),
			new Block("""
				# A screen: each write to it, whatever its length.
				""", List.of(TERMINAL_WRITE)))),
		new Group("""
			# device: every device on a bus.
			""", List.of(
			new Block("""
				# How deep a device's rings are: the events it keeps for its computer's program. Its news (a
				# sensor's sightings, a card's changes, its logs' lines) keeps the newest that many: when more come,
				# the oldest go, and the next says how many were lost. Its commands' ends (how each went) are never
				# dropped: with that many unread, it takes no more commands till the program reads some. A network
				# card's frames wait as many. device.ring_depth.all is every device's; a type sets its own with
				# device.ring_depth.<type> (device.ring_depth.robotics, .redstone, .motion, .network...), which
				# isn't set unless you add it. Each a whole count, 1 or more.
				""", List.of(RING_DEPTH)))),
		new Group("""
			# internet: internet cards, HTTP and HTTPS requests from computers, made by this server. By default
			# they reach only the public internet: never this machine (localhost), its network (10.x,
			# 192.168.x...), link-local addresses (169.254.x: a cloud's metadata), or other private and reserved
			# ranges.
			""", List.of(
			new Block("""
				# false turns them off.
				""", List.of(INTERNET_ENABLED)),
			new Block("""
				# Hosts, domains (.example.com) or ranges (203.0.113.0/24), comma-separated: allow, ones reached
				# though they'd be refused (a LAN service you mean computers to use); deny, ones also refused.
				# A deny wins.
				""", List.of(INTERNET_ALLOW, INTERNET_DENY)),
			new Block("""
				# How much of a response's body each request holds unread, in KiB, before its download waits for
				# the program to read it. A whole count, 1 or more.
				""", List.of(INTERNET_RESPONSE_BUFFER)))),
		new Group("""
			# robot: the bot golem.
			""", List.of(
			new Block("""
				# How far its pathfinding searches, in blocks walked. A walk keeps to its line, and where
				# something stops it, searches once, within this, for its way back onto the line past it (and no
				# more places than the game's own budget for a search this far: 16 a block); none found, the walk
				# ends blocked. A walk itself goes however far it's told. A flight's way is found whole within
				# this, or not at all. A whole count, 1 or more.
				""", List.of(PATHFINDING_RANGE)),
			new Block("""
				# What its robotics costs, in ticks of a running CPU's power: each block walked, each turn, each
				# action with its hand (a use, a swing, a store into a container, a take from one), and each tick
				# its thrusters fire. Fractions work; 0 makes one free.
				""", List.of(WALK, TURN, HAND, THRUSTERS)))));

	/** The internet's settings, as last loaded (the server's: clients never use them). */
	private static volatile InternetPolicy internet = InternetPolicy.DEFAULT;
	/** What actions cost and blocks give, as last loaded. */
	private static volatile PowerCosts costs = costs(new Values(new Properties()));

	private ModConfig() {
	}

	public static InternetPolicy internet() {
		return internet;
	}

	/** What devices' actions cost and the rates blocks give power at, as last loaded (the server's). */
	public static PowerCosts costs() {
		return costs;
	}

	/** Every setting the file has, in its order. */
	public static List<Setting> settings() {
		return GROUPS.stream().flatMap(group -> group.blocks().stream()).flatMap(block -> block.settings().stream()).toList();
	}

	/** The setting for one type's own ring depth: its /sys class's name, under {@link #RING_DEPTH_TYPE_PREFIX}. */
	public static String ringDepthKey(DeviceType type) {
		return RING_DEPTH_TYPE_PREFIX + type.name().toLowerCase(Locale.ROOT);
	}

	public static Path path() {
		return Platform.INSTANCE.configDir().resolve(FILE_NAME);
	}

	/**
	 * Reads the settings file, writing the defaults first if there isn't one, and adding any
	 * settings it doesn't have yet.
	 */
	public static void load() {
		load(path());
	}

	/** Loads this file: what it warned of (each logged too). */
	public static List<String> load(Path path) {
		try {
			if (!Files.exists(path)) {
				Files.createDirectories(path.getParent());
				Files.writeString(path, defaults());
			}
			Properties properties = new Properties();
			try (Reader reader = Files.newBufferedReader(path)) {
				properties.load(reader);
			}
			String missing = missing(properties);
			if (!missing.isEmpty()) {
				Files.writeString(path, missing, StandardOpenOption.APPEND); // new settings show up to edit
			}
			return apply(properties);
		} catch (IOException e) {
			ModularComputers.LOGGER.warn("Couldn't read {}; using the defaults", path, e);
			return apply(new Properties());
		}
	}

	/** Sets every setting from these properties; missing or bad ones get their defaults. What it warned of (each logged too). */
	public static List<String> apply(Properties properties) {
		Values values = new Values(properties);
		for (BatteryTier tier : BatteryTier.values()) {
			tier.setDays(values.number(BATTERY_DURATIONS.get(tier.ordinal()), "0 or more days"));
		}
		BatteryTier.TIER_2.setRechargeRate(values.number(TIER2_RECHARGE_RATE, "a rate of 0 or more"));
		RingDepths.configure(ringDepths(values));
		CpuCores.configure(values.count(CPU_CORES));
		GolemWayfinder.configure(values.count(PATHFINDING_RANGE));
		InternetCard.responseBuffer(values.count(INTERNET_RESPONSE_BUFFER));
		internet = new InternetPolicy(values.flag(INTERNET_ENABLED), values.list(INTERNET_ALLOW), values.list(INTERNET_DENY));
		costs = costs(values);
		values.unknown();
		return values.warnings;
	}

	/** What actions cost, from these values. */
	private static PowerCosts costs(Values values) {
		return new PowerCosts(PowerCosts.exact(values.cost(WALK)), PowerCosts.units(values.cost(TURN)), PowerCosts.units(values.cost(HAND)),
			PowerCosts.units(values.cost(THRUSTERS)), PowerCosts.units(values.cost(REDSTONE_CHANGE)), PowerCosts.units(values.cost(PISTON_PUSH)),
			PowerCosts.units(values.cost(FIELD_HOLD)), PowerCosts.units(values.cost(FIELD_MOVE)), PowerCosts.units(values.cost(CRAFT)),
			PowerCosts.units(values.cost(CRAFTING_MOVE)), PowerCosts.units(values.cost(DISK_STATION_ACTION)), PowerCosts.units(values.cost(WIRED_SEND)),
			PowerCosts.exact(values.cost(WIRELESS_SEND)), PowerCosts.units(values.cost(INTERNET_REQUEST)), PowerCosts.units(values.cost(INTERNET_DATA)),
			PowerCosts.units(values.cost(TERMINAL_WRITE)), PowerCosts.units(values.cost(CHARGING_PAD_RATE)));
	}

	/** Ring depths: everyone's, then a type's own where it's set (the user's, 2026-11-11). */
	static RingDepths.Depths ringDepths(Properties properties) {
		return ringDepths(new Values(properties));
	}

	private static RingDepths.Depths ringDepths(Values values) {
		int depth = values.count(RING_DEPTH);
		Map<DeviceType, Integer> own = new LinkedHashMap<>();
		for (DeviceType type : DeviceType.values()) {
			String key = ringDepthKey(type);
			if (values.properties.containsKey(key)) {
				own.put(type, values.count(new Setting(key, depth))); // (bad: everyone's)
			}
		}
		return new RingDepths.Depths(depth, own);
	}

	/** A file's values, read setting by setting, and what was wrong with them. */
	private static final class Values {
		final Properties properties;
		final List<String> warnings = new ArrayList<>();

		Values(Properties properties) {
			this.properties = properties;
		}

		private void warn(String warning) {
			warnings.add(warning);
			ModularComputers.LOGGER.warn("{}: {}", FILE_NAME, warning);
		}

		private void bad(Setting setting, String what) {
			warn(setting.key() + " should be " + what + ", not \"" + properties.getProperty(setting.key()) + "\"; using " + setting.text());
		}

		/** A number 0 or more, or its default (with a warning) if it isn't. */
		double number(Setting setting, String what) {
			String value = properties.getProperty(setting.key());
			double fallback = ((Number) setting.fallback()).doubleValue();
			if (value == null) {
				return fallback;
			}
			try {
				double number = Double.parseDouble(value.strip());
				if (number >= 0 && Double.isFinite(number)) {
					return number;
				}
			} catch (NumberFormatException e) {
				// warned below
			}
			bad(setting, what);
			return fallback;
		}

		/** A power cost or rate: running CPUs' ticks, 0 or more. */
		double cost(Setting setting) {
			return number(setting, "a number of running CPU ticks, 0 or more");
		}

		/** A whole count, 1 or more, or its default (with a warning) if it isn't. */
		int count(Setting setting) {
			String what = "a whole count of 1 or more";
			int fallback = (Integer) setting.fallback();
			double number = number(setting, what);
			if (number < 1 || number != Math.rint(number)) {
				bad(setting, what);
				return fallback;
			}
			return (int) Math.min(Integer.MAX_VALUE, number);
		}

		/** true or false, or its default (with a warning). */
		boolean flag(Setting setting) {
			boolean fallback = (Boolean) setting.fallback();
			String value = properties.getProperty(setting.key());
			if (value == null) {
				return fallback;
			}
			return switch (value.strip()) {
				case "true" -> true;
				case "false" -> false;
				default -> {
					bad(setting, "true or false");
					yield fallback;
				}
			};
		}

		/** A list, separated by commas. */
		List<String> list(Setting setting) {
			return Arrays.stream(properties.getProperty(setting.key(), (String) setting.fallback()).split(",")).map(String::strip)
				.filter(entry -> !entry.isEmpty()).toList();
		}

		/** Warns of every key that's no setting (one renamed, or misspelled): it's ignored. */
		void unknown() {
			Set<String> known = settings().stream().map(Setting::key).collect(Collectors.toSet());
			Arrays.stream(DeviceType.values()).map(ModConfig::ringDepthKey).forEach(known::add);
			properties.stringPropertyNames().stream().filter(key -> !known.contains(key)).sorted()
				.forEach(key -> warn(key + " is no setting; ignored"));
		}
	}

	/** One block's settings as the file writes them: its comment, then a line each. */
	private static String text(Block block, List<Setting> settings) {
		StringBuilder text = new StringBuilder(block.comment());
		for (Setting setting : settings) {
			String value = setting.text();
			text.append(setting.key()).append(':').append(value.isEmpty() ? "" : " " + value).append('\n');
		}
		return text.toString();
	}

	/** The file written the first time: every setting at its default, explained. */
	public static String defaults() {
		return "# Modular Computers settings. Edit, then run /modularcomputers reload (or restart the game).\n"
			+ "# A line a setting, key: value; one missing or not understood takes its default.\n" + missing(new Properties());
	}

	/** The settings a file is missing (it's from an older version), at their defaults, explained under their groups. */
	static String missing(Properties properties) {
		StringBuilder text = new StringBuilder();
		for (Group group : GROUPS) {
			StringBuilder blocks = new StringBuilder();
			for (Block block : group.blocks()) {
				List<Setting> settings = block.settings().stream().filter(setting -> !properties.containsKey(setting.key())).toList();
				if (!settings.isEmpty()) {
					blocks.append('\n').append(text(block, settings));
				}
			}
			if (!blocks.isEmpty()) {
				text.append('\n').append(group.comment()).append(blocks);
			}
		}
		return text.toString();
	}

	private static String format(double number) {
		return number == Math.rint(number) ? Long.toString((long) number) : String.format(Locale.ROOT, "%s", number);
	}
}
