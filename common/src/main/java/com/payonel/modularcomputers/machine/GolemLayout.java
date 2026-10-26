package com.payonel.modularcomputers.machine;

import com.payonel.modularcomputers.device.TerminalTier;

/**
 * The bot golem's board, by its tier (user design, 2026-10-24; tiers 2026-10-26): the same board as
 * a case's, small: battery bays, RAM slots, one SATA drive, a gold slot taking only a tier 1 screen
 * (its face), two card slots, and its robotics device soldered onto the last expansion lane. Its
 * buffer is a tier 1 case's. A tier 1 golem only walks; a tier 2 has thrusters too (it flies), a
 * second battery bay (as a tier 2 case has) and more RAM (a tier 3 case's); the same cards, the same
 * storage.
 */
public enum GolemLayout implements BoardLayout {
	TIER_1(1, 2, false),
	TIER_2(2, 4, true);

	/** The expansion lane its robotics device is soldered onto. */
	public static final int ROBOTICS_LANE = Motherboard.LANES - 1;

	private final int bays;
	private final int ram;
	private final boolean thrusters;

	GolemLayout(int bays, int ram, boolean thrusters) {
		this.bays = bays;
		this.ram = ram;
		this.thrusters = thrusters;
	}

	/** Whether it has thrusters (it flies: fly, flyTo, hover, land). */
	public boolean thrusters() {
		return thrusters;
	}

	@Override
	public int bays() {
		return bays;
	}

	@Override
	public int ram() {
		return ram;
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
		return ordinal() + 1;
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
