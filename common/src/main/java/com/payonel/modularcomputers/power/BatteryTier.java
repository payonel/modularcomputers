package com.payonel.modularcomputers.power;

import com.payonel.modularcomputers.config.Defaults;

/**
 * The batteries that power a computer, by how much they hold, in power units
 * ({@link PowerUnits}). How many Minecraft days of a running computer each tier lasts is
 * a setting ({@link #setDays}; 0 means it never runs out; its defaults are {@link Defaults}'). By default a full tier 1 runs one
 * for ten days (10% a day), tier 2 holds twice that and recharges itself while it isn't running
 * anything, and tier 3 never runs out. Tier 2 recharges itself at a configurable rate
 * ({@link #setRechargeRate}), as a share of a running computer's draw: by default half, so two
 * seconds of rest charge one second of running.
 */
public enum BatteryTier {
	TIER_1(Defaults.Power.Battery.Tier1.DURATION, Fixed.NO_RECHARGE),
	TIER_2(Defaults.Power.Battery.Tier2.DURATION, Defaults.Power.Battery.Tier2.RECHARGE_RATE),
	TIER_3(Defaults.Power.Battery.Tier3.DURATION, Fixed.NO_RECHARGE);

	/** What isn't a setting. */
	private static final class Fixed {
		/** Tiers 1 and 3 don't recharge themselves: it's what they are. */
		static final double NO_RECHARGE = 0;
	}

	private volatile double days;
	private volatile double rechargeRate;

	/** Its settings' defaults ({@link Defaults}), till the settings load. */
	BatteryTier(double days, double rechargeRate) {
		this.days = days;
		this.rechargeRate = rechargeRate;
	}

	/** What it regains each tick it rests, as a share of a running computer's draw; 0 means it doesn't recharge itself. */
	public double rechargeRate() {
		return rechargeRate;
	}

	/** Sets how fast it recharges itself while resting. Negative values are ignored. */
	public void setRechargeRate(double rate) {
		if (rate >= 0 && Double.isFinite(rate)) {
			this.rechargeRate = rate;
		}
	}

	/** How many days a full one runs a computer; 0 means forever. */
	public double days() {
		return days;
	}

	/** Sets how long a full one lasts, in days; 0 means forever. Negative values are ignored. */
	public void setDays(double days) {
		if (days >= 0 && Double.isFinite(days)) {
			this.days = days;
		}
	}

	/** Units when full; meaningless for an infinite battery. */
	public int capacity() {
		return (int) Math.min(Integer.MAX_VALUE, Math.max(1, Math.round(days * PowerUnits.DAY)));
	}

	/** It never runs out. */
	public boolean infinite() {
		return days == 0;
	}

	/** It regains charge while it isn't running a computer (tier 2). */
	public boolean selfCharging() {
		return rechargeRate > 0;
	}
}
