package com.payonel.modularcomputers.machine;

/**
 * What a RAM tier brings (user design, 2026-10-11): more space, doubling a tier. Tier 1 holds mcos
 * to its shell (user, 2026-10-20: 1 MiB, the others scaled as before; it needs about 492 KiB).
 */
public enum MemoryTier {
	TIER_1(1 << 20, 0),
	TIER_2(2 << 20, 2),
	TIER_3(4 << 20, 4);

	private final int bytes;
	/**
	 * What a stick adds to the firmware's inbox, for each device (user design, 2026-10-13): pieces
	 * of device data machine.lua keeps for the program. A tier 1 adds none.
	 */
	private final int inbox;

	MemoryTier(int bytes, int inbox) {
		this.bytes = bytes;
		this.inbox = inbox;
	}

	public int inbox() {
		return inbox;
	}

	public int bytes() {
		return bytes;
	}

	/** 1, 2 or 3. */
	public int number() {
		return ordinal() + 1;
	}
}
