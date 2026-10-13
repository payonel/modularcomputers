package com.payonel.modularcomputers.power;

/**
 * What a battery holds: {@code units} of power ({@link PowerUnits}), as of game time {@code settledAt}. A
 * self-charging battery regains charge for the time since then that it spent resting, worked
 * out when it's next looked at, so it charges in a chest or an unloaded chunk too.
 * {@link #UNSETTLED} means the time hasn't been set yet (a new battery): it starts counting at
 * the first look.
 */
public record BatteryCharge(int units, long settledAt) {
	/** One Minecraft day and night. */
	public static final int DAY_TICKS = 24000;
	public static final long UNSETTLED = -1;
	public static final BatteryCharge EMPTY = new BatteryCharge(0, UNSETTLED);

	public static BatteryCharge full(BatteryTier tier) {
		return new BatteryCharge(tier.capacity(), UNSETTLED);
	}

	public boolean isEmpty(BatteryTier tier) {
		return !tier.infinite() && units <= 0;
	}

	public boolean isFull(BatteryTier tier) {
		return tier.infinite() || units >= tier.capacity();
	}

	/** The charge as a percentage, rounded up so that a battery with any charge left shows some. */
	public int percent(BatteryTier tier) {
		if (tier.infinite()) {
			return 100;
		}
		return (int) Math.min(100, Math.ceil(units * 100.0 / tier.capacity()));
	}

	/** Adds charge (from glowing materials), up to full. */
	public BatteryCharge charge(BatteryTier tier, int amount) {
		if (tier.infinite()) {
			return this;
		}
		return new BatteryCharge((int) Math.min(tier.capacity(), (long) units + amount), settledAt);
	}

	/**
	 * The battery spent the time until {@code now} resting: a self-charging one gains its
	 * recharge rate (a share of a running all-in-one's draw) for each tick. Rest too short to earn
	 * a whole unit carries over to next time.
	 */
	public BatteryCharge rest(BatteryTier tier, long now) {
		if (isFull(tier)) {
			return this; // nothing to gain: unchanged, so a full battery's data doesn't change in a player's hand
		}
		if (!tier.selfCharging() || settledAt == UNSETTLED || now <= settledAt) {
			return new BatteryCharge(units, now);
		}
		double rate = tier.rechargeRate() * PowerUnits.ALL_IN_ONE;
		long gained = (long) Math.floor((now - settledAt) * rate);
		long total = units + gained;
		if (total >= tier.capacity()) {
			return new BatteryCharge(tier.capacity(), now);
		}
		long spent = Math.min(now - settledAt, (long) Math.ceil(gained / rate));
		return new BatteryCharge((int) total, settledAt + spent);
	}

	/** The battery gave {@code used} units to a computer, until {@code now}. */
	public BatteryCharge run(BatteryTier tier, long used, long now) {
		if (tier.infinite()) {
			return new BatteryCharge(units, now);
		}
		return new BatteryCharge((int) Math.max(0, units - used), now);
	}
}
