package com.payonel.modularcomputers.machine;

import com.payonel.modularcomputers.config.ModConfig;

/**
 * What a CPU tier brings (user design, 2026-10-11): how many devices its bus holds (its "address
 * width": bus capacity comes entirely from the CPU), and what it draws, in power units a tick,
 * while it executes and while it waits for an interrupt (settings, {@code power.draw.cpu}, read as
 * they're loaded). Off, every tier draws nothing. By default higher tiers draw more running and
 * less waiting, as real chips with deeper idle states do. The case's own devices count against the
 * bus as much as external ones (user): a tier 1's 4 hold the essentials (an EEPROM, a battery, a
 * disk, and one card or external device), not a full case.
 */
public enum CpuTier {
	TIER_1(4),
	TIER_2(16),
	TIER_3(64);

	private final int busCapacity;

	CpuTier(int busCapacity) {
		this.busCapacity = busCapacity;
	}

	/** Power units a tick while it executes (its setting as loaded). */
	public int running() {
		return ModConfig.draws().cpu().get(ordinal()).running();
	}

	/** Power units a tick while it waits for an interrupt (its setting as loaded). */
	public int waiting() {
		return ModConfig.draws().cpu().get(ordinal()).waiting();
	}

	/** How many devices its bus holds, the case's own included. */
	public int busCapacity() {
		return busCapacity;
	}

	public int number() {
		return ordinal() + 1;
	}
}
