package com.payonel.modularcomputers.power;

import com.payonel.modularcomputers.config.Defaults;

/**
 * Power is counted in whole units. What draws it, per tick, is a setting a device
 * ({@code power.draw}, {@code ModConfig.draws}). The yardstick is a running computer at the
 * default draws (a tier 1 CPU running, {@link #CPU}, and one terminal, {@link #TERMINAL}): a
 * fixed unit, whatever the draws are set to. Batteries are rated in days of it ({@link #DAY}), so
 * a battery holds a fixed amount and a hungrier computer runs it down sooner (a real battery's
 * watt-hours); costs are in ticks of its CPU. Times shown to players ("DD:MM:SS") are at its rate.
 */
public final class PowerUnits {
	/** The yardstick's CPU, each tick: a tier 1 running, at its default draw. The unit costs are counted in. */
	public static final int CPU = Defaults.Power.Draw.Cpu.Tier1.RUNNING;
	/** The yardstick's terminal, each tick: at its default draw. */
	public static final int TERMINAL = Defaults.Power.Draw.TERMINAL;
	/** A running computer (a CPU and a terminal) at the default draws, each tick: the yardstick. */
	public static final int COMPUTER = CPU + TERMINAL;
	/** A day of a running computer. */
	public static final int DAY = BatteryCharge.DAY_TICKS * COMPUTER;

	private PowerUnits() {
	}

	/** What an infinite store shows instead of a number. */
	public static final String INFINITE = "inf";

	// How power is named to players: here only, so it's one change (the user's: watts for now, maybe
	// not for good). Everything shown goes through rate() and amount().
	/** A rate, units a tick: one a tick is a watt. */
	public static final String RATE_SYMBOL = "W";
	/** An amount, units: shown in watt-hours. */
	public static final String AMOUNT_SYMBOL = "Wh";
	/** Units in one shown amount: a watt for an hour of ticks (20 a second). */
	public static final double UNITS_PER_AMOUNT = 20 * 60 * 60;

	/** A draw or load, units a tick, for players: "8 W", "1.10 kW". */
	public static String rate(long perTick) {
		return si(perTick, RATE_SYMBOL);
	}

	/** Units stored, for players: "367 Wh", or {@link #INFINITE}. */
	public static String amount(long units, boolean infinite) {
		return infinite ? INFINITE : si(units / UNITS_PER_AMOUNT, AMOUNT_SYMBOL);
	}

	/**
	 * A value in a unit, short, as a meter shows it: three significant digits with an SI prefix
	 * ("950 W", "12.3 kW", "26.4 MW", "13.2 mWh"), a whole number under a thousand as it is ("8 W").
	 */
	static String si(double value, String symbol) {
		String[] prefixes = {"m", "", "k", "M", "G", "T"};
		value = Math.max(0, value);
		int prefix = 1;
		if (value > 0 && value < 1) {
			value *= 1000;
			prefix = 0;
		}
		while (value >= 999.5 && prefix < prefixes.length - 1) {
			value /= 1000;
			prefix++;
		}
		String digits = prefix == 1 && value == Math.rint(value) ? Long.toString((long) value)
			: value >= 99.95 ? String.format(java.util.Locale.ROOT, "%.0f", value)
			: value >= 9.995 ? String.format(java.util.Locale.ROOT, "%.1f", value)
			: String.format(java.util.Locale.ROOT, "%.2f", value);
		return digits + " " + prefixes[prefix] + symbol;
	}

	/** How many ticks {@code stored} units last at {@code load} units a tick; -1 with no load. */
	public static long runtime(long stored, long load) {
		return load <= 0 ? -1 : stored / load;
	}

	/** How long {@code units} run a computer, in ticks. */
	public static long ticks(long units) {
		return units / COMPUTER;
	}
}
