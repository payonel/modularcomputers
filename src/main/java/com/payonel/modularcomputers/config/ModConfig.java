package com.payonel.modularcomputers.config;

import com.payonel.modularcomputers.ModularComputers;
import com.payonel.modularcomputers.power.BatteryTier;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
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

	public static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	}

	/** Reads the settings file, writing the defaults first if there isn't one. */
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
			apply(properties);
		} catch (IOException e) {
			ModularComputers.LOGGER.warn("Couldn't read {}; using the defaults", path, e);
			apply(new Properties());
		}
	}

	/** Sets every setting from these properties; missing or bad ones get their defaults. */
	public static void apply(Properties properties) {
		for (BatteryTier tier : BatteryTier.values()) {
			String key = durationKey(tier);
			String value = properties.getProperty(key);
			double days = tier.defaultDays();
			if (value != null) {
				try {
					days = Double.parseDouble(value.strip());
				} catch (NumberFormatException e) {
					days = -1;
				}
				if (days < 0 || !Double.isFinite(days)) {
					ModularComputers.LOGGER.warn("{}: {} should be 0 or more days, not \"{}\"; using {}", FILE_NAME, key, value, tier.defaultDays());
					days = tier.defaultDays();
				}
			}
			tier.setDays(days);
		}
	}

	/** The file written the first time: every setting at its default, explained. */
	public static String defaults() {
		StringBuilder text = new StringBuilder("""
			# Modular Computers settings. Edit, then run /modularcomputers reload (or restart the game).

			# How many Minecraft days (20 minutes each) a full battery runs a computer.
			# 0 means it never runs out. Fractions work: 0.5 is ten minutes.
			""");
		for (BatteryTier tier : BatteryTier.values()) {
			text.append(durationKey(tier)).append(": ").append(format(tier.defaultDays())).append('\n');
		}
		return text.toString();
	}

	private static String format(double days) {
		return days == Math.rint(days) ? Long.toString((long) days) : String.format(Locale.ROOT, "%s", days);
	}
}
