package com.payonel.modularcomputers.power;

import java.util.function.IntSupplier;

/**
 * The case's own store of power, like a PSU's capacitors scaled up, in power units
 * ({@link PowerUnits}): a tier 3 case's holds a hundredth of a day of a running computer (12
 * seconds), smaller cases less.
 * The computer runs from it, and it refills from the batteries; with them out it runs down on its
 * own. The case's charge meter shows this, not the batteries.
 * <p>
 * A buffer sized from settings (a photomask's) reads its capacity each time, so a reload resizes
 * one already built: grown, it has room; shrunk, what it held past its new size is gone.
 */
public final class PowerBuffer {
	/** The biggest case's: a hundredth of a day of a running computer (user, 2026-10-11: a day was far too much). */
	public static final int CAPACITY = PowerUnits.DAY / 100;

	private final IntSupplier capacity;
	private int level;

	/** A buffer of {@link #CAPACITY}. */
	public PowerBuffer() {
		this(CAPACITY);
	}

	public PowerBuffer(int capacity) {
		this(() -> capacity);
	}

	/** A buffer whose size is read each time it's needed (a setting's, as loaded). */
	public PowerBuffer(IntSupplier capacity) {
		this.capacity = capacity;
	}

	public int capacity() {
		return Math.max(0, capacity.getAsInt());
	}

	/** What it holds: never more than its capacity now. */
	public int level() {
		level = Math.min(level, capacity());
		return level;
	}

	/** Sets the level from a save or a sync, clamped to 0..{@link #capacity()}. */
	public void set(int level) {
		this.level = Math.clamp(level, 0, capacity());
	}

	public boolean isEmpty() {
		return level() <= 0;
	}

	/** Under half full: a case with a load sounds its low-power alarm. */
	public boolean isLow() {
		return level() * 2L < capacity();
	}

	/** How full, 0 to 100; an empty one of no size is 0. */
	public int percent() {
		int capacity = capacity();
		return capacity == 0 ? 0 : (int) Math.ceil(level() * 100.0 / capacity);
	}

	/** Takes up to what it's missing from {@code available}; returns how much it took. */
	public int fill(long available) {
		int taken = (int) Math.min(capacity() - level(), Math.max(0, available));
		level += taken;
		return taken;
	}

	/** The computer used {@code units}. */
	public void use(long units) {
		level = (int) Math.max(0, level() - units);
	}
}
