package com.payonel.modularcomputers.machine;

import com.payonel.modularcomputers.device.TerminalTier;

/**
 * The bot golem's board (user design, 2026-10-24): the same board as a case's, small: a battery
 * bay, two RAM slots, one SATA drive, a gold slot taking only a tier 1 screen (its face), two card
 * slots, and its robotics device soldered onto the last expansion lane. Its buffer is a tier 1
 * case's.
 */
public enum GolemLayout implements BoardLayout {
	BOT_GOLEM;

	/** The expansion lane its robotics device is soldered onto. */
	public static final int ROBOTICS_LANE = Motherboard.LANES - 1;

	@Override
	public int bays() {
		return 1;
	}

	@Override
	public int ram() {
		return 2;
	}

	@Override
	public int sata() {
		return 1;
	}

	@Override
	public int expansion() {
		return Motherboard.LANES;
	}

	@Override
	public boolean screenSlot() {
		return true;
	}

	@Override
	public int bufferCapacity() {
		return CaseTier.TIER_1.bufferCapacity();
	}

	@Override
	public int number() {
		return 1;
	}

	@Override
	public boolean soldered(int lane) {
		return lane == ROBOTICS_LANE;
	}

	@Override
	public String solderedKind(int lane) {
		return lane == ROBOTICS_LANE ? "robotics" : null;
	}

	@Override
	public boolean takesScreen(TerminalTier tier) {
		return tier == TerminalTier.TIER_1;
	}
}
