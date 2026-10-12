package com.payonel.modularcomputers.bus;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * A bus-powered device's power, like a USB device's: it has power while at least one running
 * computer has it on its bus (its suppliers), and goes dark when the last one lets go. Nothing
 * here ticks: power comes and goes on events (power on and off, hot-plug, a computer unloading),
 * and what the device used is settled lazily.
 * <p>
 * What it uses is a debt, in the batteries' unit (ticks of a running CPU): its draw for every
 * tick it's had power, plus whatever its work cost ({@link #charge}). The first supplier to
 * {@link #collect} pays all of it; whoever comes next owes only what's new. Which computer pays
 * a shared device is a race, and doesn't matter.
 */
public final class BusPower {
	private final double draw;
	private final Set<Object> suppliers = Collections.newSetFromMap(new IdentityHashMap<>());
	/** Game time it's paid up to, or -1 when it isn't powered (or no one has collected yet). */
	private long paidUntil = -1;
	private double owed;

	/** {@code draw}: what it uses each tick it has power, in ticks of a running CPU (a CPU is 1). */
	public BusPower(double draw) {
		this.draw = draw;
	}

	public double draw() {
		return draw;
	}

	public boolean powered() {
		return !suppliers.isEmpty();
	}

	/** A running computer supplies it. Returns whether it just got power. */
	public boolean supply(Object supplier) {
		boolean was = powered();
		suppliers.add(supplier);
		return !was;
	}

	/** A computer stops supplying it. Returns whether that was the last: it has no power now. */
	public boolean release(Object supplier) {
		if (!suppliers.remove(supplier) || powered()) {
			return false;
		}
		paidUntil = -1;
		owed = 0; // what it owed goes unpaid: nobody's left to pay it
		return true;
	}

	/** Work it did (a write), in ticks of a running CPU. */
	public void charge(double cost) {
		if (powered()) {
			owed += cost;
		}
	}

	/** Settles up to game time {@code now}: the whole ticks it owes, which the caller pays. */
	public long collect(long now) {
		if (!powered()) {
			return 0;
		}
		if (paidUntil >= 0 && now > paidUntil) {
			owed += draw * (now - paidUntil);
		}
		paidUntil = Math.max(paidUntil, now);
		long whole = (long) owed;
		owed -= whole;
		return whole;
	}
}
