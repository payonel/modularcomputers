package com.payonel.modularcomputers.power;

/**
 * A charge meter: a row of bars that fill left to right. Empty, every bar is off; any charge
 * lights at least one red bar, and as it rises more bars light, yellow and then green.
 */
public final class ChargeMeter {
	public static final int SEGMENTS = 6;

	public enum Level {
		/** Empty: all bars off. */
		OFF,
		LOW,
		MEDIUM,
		HIGH,
	}

	private ChargeMeter() {
	}

	/** How many bars light for a charge of 0..100 percent: none when empty, else at least one. */
	public static int litBars(int charge) {
		int clamped = Math.max(0, Math.min(100, charge));
		return (int) Math.ceil(clamped * SEGMENTS / 100.0);
	}

	/** The color the lit bars share: red with one bar, yellow with two or three, green with more. */
	public static Level level(int litBars) {
		if (litBars <= 0) {
			return Level.OFF;
		}
		return litBars == 1 ? Level.LOW : litBars <= 3 ? Level.MEDIUM : Level.HIGH;
	}
}
