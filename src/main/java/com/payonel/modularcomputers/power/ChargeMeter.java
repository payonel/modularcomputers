package com.payonel.modularcomputers.power;

/**
 * The backup battery's charge meter: a row of bars that fill left to right. With no battery
 * every bar is off. An empty battery still shows one red bar; as charge rises more bars light,
 * yellow and then green.
 */
public final class ChargeMeter {
	public static final int SEGMENTS = 6;

	public enum Level {
		/** No battery: all bars off. */
		OFF,
		LOW,
		MEDIUM,
		HIGH,
	}

	private ChargeMeter() {
	}

	/** How many bars light for a charge of 0..100 percent; at least one when there's a battery. */
	public static int litBars(int charge) {
		int clamped = Math.max(0, Math.min(100, charge));
		return Math.max(1, (int) Math.ceil(clamped * SEGMENTS / 100.0));
	}

	/** The color the lit bars share: red with one bar, yellow with two or three, green with more. */
	public static Level level(int litBars) {
		if (litBars <= 0) {
			return Level.OFF;
		}
		return litBars == 1 ? Level.LOW : litBars <= 3 ? Level.MEDIUM : Level.HIGH;
	}
}
