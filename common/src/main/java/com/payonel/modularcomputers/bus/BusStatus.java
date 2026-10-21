package com.payonel.modularcomputers.bus;

/**
 * How full a bus is, for programs as much as the player: its {@code capacity} (the CPU's), how
 * many devices are on it, how many external devices its saved order holds and how many of those
 * are {@code missing} (offline), how many are {@code waiting}: there, but not on the bus because
 * it's full, and how many of the devices on it are the case's own ({@code internal}); the rest
 * are external.
 */
public record BusStatus(int capacity, int devices, int saved, int missing, int waiting, int internal) {
	/** External devices on the bus. */
	public int external() {
		return devices - internal;
	}

	/** Room external devices have: what the case's own leave of the capacity. */
	public int room() {
		return Math.max(0, capacity - internal);
	}

	public static final BusStatus EMPTY = new BusStatus(0, 0, 0, 0, 0, 0);

	/** Devices are waiting for room: the bus's warning state. */
	public boolean overflow() {
		return waiting > 0;
	}
}
