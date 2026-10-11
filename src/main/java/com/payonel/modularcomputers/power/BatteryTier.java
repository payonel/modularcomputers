package com.payonel.modularcomputers.power;

/**
 * The batteries that power a computer, by how much they hold. Charge is counted in ticks of
 * running a CPU: a full tier 1 battery runs one for ten Minecraft days (10% a day), and tier 2
 * holds twice that and recharges itself while it isn't running anything. Tier 3 never runs out.
 */
public enum BatteryTier {
	TIER_1(10 * BatteryCharge.DAY_TICKS, false),
	TIER_2(20 * BatteryCharge.DAY_TICKS, true),
	TIER_3(0, false);

	private final int capacity;
	private final boolean selfCharging;

	BatteryTier(int capacity, boolean selfCharging) {
		this.capacity = capacity;
		this.selfCharging = selfCharging;
	}

	/** Ticks of running a CPU when full; meaningless for an infinite battery. */
	public int capacity() {
		return capacity;
	}

	/** It never runs out (tier 3). */
	public boolean infinite() {
		return this == TIER_3;
	}

	/** It regains one tick of charge for every tick it isn't running a computer (tier 2). */
	public boolean selfCharging() {
		return selfCharging;
	}
}
