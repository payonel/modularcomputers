package com.payonel.modularcomputers.power;

/**
 * Power is counted in whole units. What draws it, per tick: a CPU while it executes
 * ({@link #CPU}), ten times a terminal ({@link #TERMINAL}). A running all-in-one (its CPU and its
 * built-in terminal) is the yardstick: batteries are rated in days of it ({@link #DAY}), so a full
 * tier 1 runs one for ten days by default. Times shown to players ("DD:MM:SS") are at that rate.
 */
public final class PowerUnits {
	/** A CPU, each tick it executes (nothing while halted or waiting for an interrupt). */
	public static final int CPU = 100;
	/** A terminal, each tick it has power. */
	public static final int TERMINAL = 10;
	/** One write to a terminal, flat. */
	public static final int TERMINAL_WRITE = 1;
	/** A running all-in-one, each tick: the yardstick. */
	public static final int ALL_IN_ONE = CPU + TERMINAL;
	/** A day of a running all-in-one. */
	public static final int DAY = BatteryCharge.DAY_TICKS * ALL_IN_ONE;

	private PowerUnits() {
	}

	/** What an infinite store shows instead of a number. */
	public static final String INFINITE = "inf";

	/**
	 * A number of units for players, short, as batteries are rated in kWh: three significant
	 * digits with an SI prefix ("950", "12.3k", "264k", "26.4M", "1.06G").
	 */
	public static String format(long units) {
		String[] prefixes = {"", "k", "M", "G", "T"};
		double value = Math.max(0, units);
		int prefix = 0;
		while (value >= 999.5 && prefix < prefixes.length - 1) {
			value /= 1000;
			prefix++;
		}
		if (prefix == 0) {
			return Long.toString(Math.max(0, units));
		}
		String digits = value >= 99.95 ? String.format(java.util.Locale.ROOT, "%.0f", value)
			: value >= 9.995 ? String.format(java.util.Locale.ROOT, "%.1f", value)
			: String.format(java.util.Locale.ROOT, "%.2f", value);
		return digits + prefixes[prefix];
	}

	/** How many ticks {@code stored} units last at {@code load} units a tick; -1 with no load. */
	public static long runtime(long stored, long load) {
		return load <= 0 ? -1 : stored / load;
	}

	/** How long {@code units} run an all-in-one, in ticks. */
	public static long ticks(long units) {
		return units / ALL_IN_ONE;
	}
}
