package com.payonel.modularcomputers.machine;

/**
 * What a CPU tier brings (user design, 2026-10-11): how many devices its bus holds (its "address
 * width": bus capacity comes entirely from the CPU), and what it draws, in power units a tick,
 * while it executes and while it waits for an interrupt. Halted, every tier draws nothing.
 * Higher tiers draw more running and less waiting, as real chips with deeper idle states do. The
 * case's own devices count against the bus as much as external ones (user): a tier 1's 4 hold the
 * essentials (an EEPROM, a battery, a disk, and one card or external device), not a full case.
 */
public enum CpuTier {
	TIER_1(100, 50, 4),
	TIER_2(150, 20, 16),
	TIER_3(200, 5, 64);

	private final int running;
	private final int waiting;
	private final int busCapacity;

	CpuTier(int running, int waiting, int busCapacity) {
		this.running = running;
		this.waiting = waiting;
		this.busCapacity = busCapacity;
	}

	/** Power units a tick while it executes. */
	public int running() {
		return running;
	}

	/** Power units a tick while it waits for an interrupt. */
	public int waiting() {
		return waiting;
	}

	/** How many devices its bus holds, the case's own included. */
	public int busCapacity() {
		return busCapacity;
	}

	public int number() {
		return ordinal() + 1;
	}
}
