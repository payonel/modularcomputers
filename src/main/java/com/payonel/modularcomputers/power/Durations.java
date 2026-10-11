package com.payonel.modularcomputers.power;

/** Ticks of running, shown as time. */
public final class Durations {
	private Durations() {
	}

	/**
	 * As DD:MM:SS: Minecraft days (20 real minutes each), then the real minutes (00..19) and
	 * seconds into that day, at the normal 20 ticks a second.
	 */
	public static String clock(long ticks) {
		long clamped = Math.max(0, ticks);
		long seconds = clamped % BatteryCharge.DAY_TICKS / 20;
		return String.format("%02d:%02d:%02d", clamped / BatteryCharge.DAY_TICKS, seconds / 60, seconds % 60);
	}
}
