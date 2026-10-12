package com.payonel.modularcomputers.machine;

/** What a RAM tier brings (user design, 2026-10-11): more space. */
public enum MemoryTier {
	TIER_1(256 << 10),
	TIER_2(512 << 10),
	TIER_3(1 << 20);

	private final int bytes;

	MemoryTier(int bytes) {
		this.bytes = bytes;
	}

	public int bytes() {
		return bytes;
	}
}
