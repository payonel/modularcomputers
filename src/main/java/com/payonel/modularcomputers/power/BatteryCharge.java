package com.payonel.modularcomputers.power;

/**
 * What a battery holds: {@code ticks} of running a CPU, as of game time {@code settledAt}. A
 * self-charging battery regains charge for the time since then that it spent resting, worked
 * out when it's next looked at, so it charges in a chest or an unloaded chunk too.
 * {@link #UNSETTLED} means the time hasn't been set yet (a new battery): it starts counting at
 * the first look.
 */
public record BatteryCharge(int ticks, long settledAt) {
	/** One Minecraft day and night. */
	public static final int DAY_TICKS = 24000;
	public static final long UNSETTLED = -1;
	public static final BatteryCharge EMPTY = new BatteryCharge(0, UNSETTLED);

	public static BatteryCharge full(BatteryTier tier) {
		return new BatteryCharge(tier.capacity(), UNSETTLED);
	}

	public boolean isEmpty(BatteryTier tier) {
		return !tier.infinite() && ticks <= 0;
	}

	public boolean isFull(BatteryTier tier) {
		return tier.infinite() || ticks >= tier.capacity();
	}

	/** The charge as a percentage, rounded up so that a battery with any charge left shows some. */
	public int percent(BatteryTier tier) {
		if (tier.infinite()) {
			return 100;
		}
		return (int) Math.min(100, Math.ceil(ticks * 100.0 / tier.capacity()));
	}

	/** Adds charge (from glowing materials), up to full. */
	public BatteryCharge charge(BatteryTier tier, int amount) {
		if (tier.infinite()) {
			return this;
		}
		return new BatteryCharge((int) Math.min(tier.capacity(), (long) ticks + amount), settledAt);
	}

	/** The battery spent the time until {@code now} resting: a self-charging one gains that much. */
	public BatteryCharge rest(BatteryTier tier, long now) {
		if (tier.selfCharging() && settledAt != UNSETTLED && now > settledAt) {
			return new BatteryCharge((int) Math.min(tier.capacity(), ticks + (now - settledAt)), now);
		}
		return new BatteryCharge(ticks, now);
	}

	/** The battery ran a computer for {@code used} ticks, until {@code now}. */
	public BatteryCharge run(BatteryTier tier, long used, long now) {
		if (tier.infinite()) {
			return new BatteryCharge(ticks, now);
		}
		return new BatteryCharge((int) Math.max(0, ticks - used), now);
	}
}
