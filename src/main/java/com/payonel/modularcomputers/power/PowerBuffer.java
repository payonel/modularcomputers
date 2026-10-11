package com.payonel.modularcomputers.power;

/**
 * The case's own store of power, like a PSU's capacitors scaled up: it holds one day of running a
 * CPU. The computer runs from it, and it refills from the battery; with the battery out it runs
 * down on its own. The case's charge meter shows this, not the battery.
 */
public final class PowerBuffer {
	public static final int CAPACITY = BatteryCharge.DAY_TICKS;

	private int level;

	public int level() {
		return level;
	}

	/** Sets the level from a save or a sync, clamped to 0..{@link #CAPACITY}. */
	public void set(int level) {
		this.level = Math.clamp(level, 0, CAPACITY);
	}

	public boolean isEmpty() {
		return level <= 0;
	}

	public int percent() {
		return (int) Math.ceil(level * 100.0 / CAPACITY);
	}

	/** Takes up to what it's missing from {@code available}; returns how much it took. */
	public int fill(long available) {
		int taken = (int) Math.min(CAPACITY - level, Math.max(0, available));
		level += taken;
		return taken;
	}

	/** The computer ran for {@code ticks}. */
	public void use(int ticks) {
		level = Math.max(0, level - ticks);
	}
}
