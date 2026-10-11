package com.payonel.modularcomputers.runtime;

import com.payonel.modularcomputers.bus.BusStatus;
import java.util.List;
import java.util.Optional;

/**
 * The motherboard API: everything a program on the CPU can do. A Lua runtime exposes these as
 * Lua functions; mock programs (plain Java) call them directly.
 * <p>
 * Devices are addressed by stable address: a slot's path in the case ({@code "sata:0"}), a
 * wired block's id ({@code "cable:<id>"}), or a virtual device's name ({@code "console"}).
 * {@link #list()} gives the physical bus's addresses in bus order.
 * <p>
 * Every call can be a point where the machine is paused until a later tick (each machine gets a
 * fixed number of calls a tick); the program never notices, except as time passing.
 */
public interface Board {
	/** The interrupt source for the bus itself: devices attached or removed. */
	String BUS = "bus";

	/** The devices on the physical bus, in bus order. Reading it clears the bus's interrupt. */
	List<String> list();

	/** How full the bus is: capacity, saved, missing, waiting, overflow. */
	BusStatus status();

	/** What kind of device is at {@code address} ("display", "storage", ... or "console"). */
	Optional<String> type(String address);

	/** The methods the device at {@code address} offers, in name order. */
	List<String> methods(String address);

	Optional<String> doc(String address, String method);

	/** Calls a device's method, right away. Failures (no such device, bad arguments) are soft. */
	Result invoke(String address, String method, Object... args);

	/**
	 * Waits until a device raises an interrupt, or {@code seconds} pass. Returns the addresses
	 * with an interrupt pending, in bus order ({@link #BUS} first), or none on a timeout. An
	 * interrupt stays pending until the program reads the device, as with a real interrupt line.
	 */
	List<String> waitForInterrupt(double seconds);

	/** Ticks since the machine was powered on. */
	long uptime();
}
