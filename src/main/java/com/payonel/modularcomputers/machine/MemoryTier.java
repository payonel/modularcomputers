package com.payonel.modularcomputers.machine;

/** What a RAM tier brings (user design, 2026-10-11): more space. */
public enum MemoryTier {
	TIER_1(256 << 10, 0),
	TIER_2(512 << 10, 16),
	TIER_3(1 << 20, 64);

	private final int bytes;
	/** Device data it holds for the program (its ring, user design 2026-10-13): a tier 1 has none, so device reads go to the device, slowly. */
	private final int ring;

	public int ring() {
		return ring;
	}

	MemoryTier(int bytes, int ring) {
		this.ring = ring;
		this.bytes = bytes;
	}

	public int bytes() {
		return bytes;
	}

	/** 1, 2 or 3. */
	public int number() {
		return ordinal() + 1;
	}
}
