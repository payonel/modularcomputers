package com.payonel.modularcomputers.config;

import com.payonel.modularcomputers.ModularComputers;
import com.payonel.modularcomputers.power.BatteryTier;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The mod's settings, in {@code config/modularcomputers.properties} in the game folder. It's
 * written with the defaults the first time the game starts. Lines are {@code key: value}.
 * {@code /modularcomputers reload} rereads it without restarting; a server sends its settings
 * to the players who join it.
 */
public final class ModConfig {
	public static final String FILE_NAME = ModularComputers.MOD_ID + ".properties";

	private ModConfig() {
	}

	/** The setting for how many days a full battery of this tier lasts. */
	public static String durationKey(BatteryTier tier) {
		return "battery_tier" + (tier.ordinal() + 1) + "_duration";
	}

	/** The setting for how fast tier 2 recharges itself. */
	public static final String TIER2_RECHARGE_KEY = "battery_tier2_recharge_rate";

	public static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	}

	/**
	 * Reads the settings file, writing the defaults first if there isn't one, and adding any
	 * settings it doesn't have yet.
	 */
	public static void load() {
		load(path());
	}

	public static void load(Path path) {
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
			apply(properties);
		} catch (IOException e) {
			ModularComputers.LOGGER.warn("Couldn't read {}; using the defaults", path, e);
			apply(new Properties());
		}
	}

	/** Sets every setting from these properties; missing or bad ones get their defaults. */
	public static void apply(Properties properties) {
		for (BatteryTier tier : BatteryTier.values()) {
			tier.setDays(number(properties, durationKey(tier), tier.defaultDays(), "0 or more days"));
		}
		BatteryTier tier2 = BatteryTier.TIER_2;
		tier2.setRechargeRate(number(properties, TIER2_RECHARGE_KEY, tier2.defaultRechargeRate(), "a rate of 0 or more"));
	}

	/** A setting that's a number 0 or more, or its default (with a warning) if it isn't. */
	private static double number(Properties properties, String key, double fallback, String what) {
		String value = properties.getProperty(key);
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
		ModularComputers.LOGGER.warn("{}: {} should be {}, not \"{}\"; using {}", FILE_NAME, key, what, value, fallback);
		return fallback;
	}

	/** A group of settings and the comment that explains them, as written to the file. */
	private record Section(String comment, Map<String, String> settings) {
		String text(Collection<String> keys) {
			StringBuilder text = new StringBuilder(comment);
			for (String key : keys) {
				text.append(key).append(": ").append(settings.get(key)).append('\n');
			}
			return text.toString();
		}
	}

	private static List<Section> sections() {
		Map<String, String> durations = new LinkedHashMap<>();
		for (BatteryTier tier : BatteryTier.values()) {
			durations.put(durationKey(tier), format(tier.defaultDays()));
		}
		return List.of(
			new Section("""
				# How many Minecraft days (20 minutes each) a full battery runs an all-in-one computer
				# (its CPU running, and its screen). 0 means it never runs out. Fractions work: 0.5 is ten minutes.
				""", durations),
			new Section("""
				# How fast a tier 2 battery recharges itself while not in use, as a share of what a running
				# all-in-one draws: 0.5 takes two seconds of rest to charge one second of running;
				# 1 charges as fast as it runs; 0 turns it off.
				""", Map.of(TIER2_RECHARGE_KEY, format(BatteryTier.TIER_2.defaultRechargeRate()))));
	}

	/** The file written the first time: every setting at its default, explained. */
	public static String defaults() {
		StringBuilder text = new StringBuilder("# Modular Computers settings. Edit, then run /modularcomputers reload (or restart the game).\n");
		for (Section section : sections()) {
			text.append('\n').append(section.text(section.settings().keySet()));
		}
		return text.toString();
	}

	/** The settings a file is missing (it's from an older version), at their defaults, explained. */
	static String missing(Properties properties) {
		StringBuilder text = new StringBuilder();
		for (Section section : sections()) {
			List<String> keys = section.settings().keySet().stream().filter(key -> !properties.containsKey(key)).toList();
			if (!keys.isEmpty()) {
				text.append('\n').append(section.text(keys));
			}
		}
		return text.toString();
	}

	private static String format(double days) {
		return days == Math.rint(days) ? Long.toString((long) days) : String.format(Locale.ROOT, "%s", days);
	}
}
