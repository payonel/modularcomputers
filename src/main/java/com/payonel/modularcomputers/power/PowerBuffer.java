package com.payonel.modularcomputers.power;

/**
 * The case's own store of power, like a PSU's capacitors scaled up, in power units
 * ({@link PowerUnits}): a tier 3 case's holds a hundredth of a day of a running all-in-one (12
 * seconds), smaller cases less.
 * The computer runs from it, and it refills from the batteries; with them out it runs down on its
 * own. The case's charge meter shows this, not the batteries.
 */
public final class PowerBuffer {
	/** The biggest case's: a hundredth of a day of a running all-in-one (user, 2026-10-11: a day was far too much). */
	public static final int CAPACITY = PowerUnits.DAY / 100;

	private final int capacity;
	private int level;

	/** A buffer of {@link #CAPACITY}. */
	public PowerBuffer() {
		this(CAPACITY);
	}

	public PowerBuffer(int capacity) {
		this.capacity = capacity;
	}

	public int capacity() {
		return capacity;
	}

	public int level() {
		return level;
	}

	/** Sets the level from a save or a sync, clamped to 0..{@link #capacity()}. */
	public void set(int level) {
		this.level = Math.clamp(level, 0, capacity);
	}

	public boolean isEmpty() {
		return level <= 0;
	}

	public int percent() {
		return (int) Math.ceil(level * 100.0 / capacity);
	}

	/** Takes up to what it's missing from {@code available}; returns how much it took. */
	public int fill(long available) {
		int taken = (int) Math.min(capacity - level, Math.max(0, available));
		level += taken;
		return taken;
	}

	/** The computer used {@code units}. */
	public void use(long units) {
		level = (int) Math.max(0, level - units);
	}
}
