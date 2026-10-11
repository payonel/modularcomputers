package com.payonel.modularcomputers.bus;

/**
 * How full a bus is, for programs as much as the player: its {@code capacity} (a motherboard
 * setting), how many devices are on it, how many external devices its saved order holds and how
 * many of those are {@code missing} (offline), and how many are {@code waiting}: wired up, but
 * not on the bus because it's full.
 */
public record BusStatus(int capacity, int devices, int saved, int missing, int waiting) {
	public static final BusStatus EMPTY = new BusStatus(0, 0, 0, 0, 0);

	/** Devices are waiting for room: the bus's warning state. */
	public boolean overflow() {
		return waiting > 0;
	}
}
