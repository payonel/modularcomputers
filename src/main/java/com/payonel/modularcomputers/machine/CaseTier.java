package com.payonel.modularcomputers.machine;

import com.payonel.modularcomputers.power.PowerUnits;

/**
 * What a case tier brings (user design, 2026-10-11; for our purposes case == board): more slots,
 * a screen slot, and a bigger power buffer at higher tiers. Every tier is the same board, like a
 * fab printing one board and leaving features out of the cheaper ones: a lower tier has the first
 * slots of each kind, and the rest are greyed out, in the same places. On tiers 2 and 3 the first
 * expansion slot is gold-trimmed: it also takes a screen (a terminal), which then shows on the
 * case's front, as an AGP slot took graphics.
 */
public enum CaseTier {
	TIER_1(1, 1, 1, 2, false, 0.25),
	TIER_2(2, 2, 2, 3, true, 0.5),
	TIER_3(3, 4, 4, 4, true, 1);

	private final int bays;
	private final int ram;
	private final int sata;
	private final int expansion;
	private final boolean screenSlot;
	private final double bufferDays;

	CaseTier(int bays, int ram, int sata, int expansion, boolean screenSlot, double bufferDays) {
		this.bays = bays;
		this.ram = ram;
		this.sata = sata;
		this.expansion = expansion;
		this.screenSlot = screenSlot;
		this.bufferDays = bufferDays;
	}

	/** Battery bays. */
	public int bays() {
		return bays;
	}

	public int ram() {
		return ram;
	}

	public int sata() {
		return sata;
	}

	public int expansion() {
		return expansion;
	}

	/** Its first expansion slot also takes a screen. */
	public boolean screenSlot() {
		return screenSlot;
	}

	/** How much its power buffer holds: a share of a day of a running all-in-one. */
	public int bufferCapacity() {
		return (int) Math.round(bufferDays * PowerUnits.DAY);
	}

	public int number() {
		return ordinal() + 1;
	}
}
