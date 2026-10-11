package com.payonel.modularcomputers.power;

/**
 * The batteries that power a computer, by how much they hold. Charge is counted in ticks of
 * running a CPU. How many Minecraft days each tier lasts is configurable ({@link #setDays}; 0
 * means it never runs out). By default a full tier 1 runs a CPU for ten days (10% a day), tier 2
 * holds twice that and recharges itself while it isn't running anything, and tier 3 never runs
 * out.
 */
public enum BatteryTier {
	TIER_1(10, false),
	TIER_2(20, true),
	TIER_3(0, false);

	private final double defaultDays;
	private final boolean selfCharging;
	private volatile double days;

	BatteryTier(double defaultDays, boolean selfCharging) {
		this.defaultDays = defaultDays;
		this.selfCharging = selfCharging;
		this.days = defaultDays;
	}

	public double defaultDays() {
		return defaultDays;
	}

	/** How many days a full one runs a CPU; 0 means forever. */
	public double days() {
		return days;
	}

	/** Sets how long a full one lasts, in days; 0 means forever. Negative values are ignored. */
	public void setDays(double days) {
		if (days >= 0 && Double.isFinite(days)) {
			this.days = days;
		}
	}

	/** Ticks of running a CPU when full; meaningless for an infinite battery. */
	public int capacity() {
		return (int) Math.min(Integer.MAX_VALUE, Math.max(1, Math.round(days * BatteryCharge.DAY_TICKS)));
	}

	/** It never runs out. */
	public boolean infinite() {
		return days == 0;
	}

	/** It regains one tick of charge for every tick it isn't running a computer (tier 2). */
	public boolean selfCharging() {
		return selfCharging;
	}
}
