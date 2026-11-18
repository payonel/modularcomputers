package com.payonel.modularcomputers.device;

import com.payonel.modularcomputers.config.ModConfig;

/**
 * What a hard disk tier brings (user design, 2026-10-11): more space, and by default less power
 * ({@code power.draw.disk}), at higher tiers.
 */
public enum DiskTier {
	TIER_1(1 << 20),
	TIER_2(4 << 20),
	TIER_3(16 << 20);

	private final int bytes;

	DiskTier(int bytes) {
		this.bytes = bytes;
	}

	/** How much it holds, in bytes. */
	public int bytes() {
		return bytes;
	}

	/** Power units a tick while it has power (its setting as loaded). */
	public int draw() {
		return ModConfig.draws().disk().get(ordinal());
	}

	/** 1, 2 or 3. */
	public int number() {
		return ordinal() + 1;
	}
}
