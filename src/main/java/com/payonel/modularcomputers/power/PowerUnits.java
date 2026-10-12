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

	/** How long {@code units} run an all-in-one, in ticks. */
	public static long ticks(long units) {
		return units / ALL_IN_ONE;
	}
}
