package com.payonel.modularcomputers.device;

/** What a hard disk tier brings (user design, 2026-10-11): more space, and less power, at higher tiers. */
public enum DiskTier {
	TIER_1(1 << 20, 8),
	TIER_2(4 << 20, 5),
	TIER_3(16 << 20, 2);

	private final int bytes;
	private final int draw;

	DiskTier(int bytes, int draw) {
		this.bytes = bytes;
		this.draw = draw;
	}

	/** How much it holds, in bytes. */
	public int bytes() {
		return bytes;
	}

	/** Power units a tick while it has power. */
	public int draw() {
		return draw;
	}
}
