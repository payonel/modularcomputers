package com.payonel.modularcomputers.power;

/** Ticks of running, as the real time they take at the normal 20 ticks a second. */
public final class Durations {
	private Durations() {
	}

	/** Like "3h 20m", "18m" or "40s". */
	public static String realTime(long ticks) {
		long seconds = Math.max(0, ticks) / 20;
		if (seconds >= 3600) {
			return seconds / 3600 + "h " + seconds % 3600 / 60 + "m";
		}
		return seconds >= 60 ? seconds / 60 + "m" : seconds + "s";
	}
}
