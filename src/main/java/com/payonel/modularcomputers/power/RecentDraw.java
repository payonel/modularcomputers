package com.payonel.modularcomputers.power;

/**
 * What a computer has drawn lately, for estimating how long its power will last: a running
 * average of the power used in each settling period (every 20 ticks), each new period weighing
 * a quarter, so it follows a change within a few seconds.
 */
public final class RecentDraw {
	/** Ticks in each period it's told about. */
	public static final int PERIOD = 20;

	private long average;

	/** The power used over the last period. */
	public void record(long used) {
		used = Math.max(0, used);
		average = average == 0 ? used : (3 * average + used) / 4; // from nothing, it starts at the draw
	}

	/** The average power used per period. */
	public long perPeriod() {
		return average;
	}

	public void set(long perPeriod) {
		average = Math.max(0, perPeriod);
	}

	/** How many ticks {@code available} units last at {@code perPeriod}; -1 when nothing is drawn. */
	public static long runtime(long available, long perPeriod) {
		return perPeriod <= 0 ? -1 : available * PERIOD / perPeriod;
	}
}
