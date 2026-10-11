package com.payonel.modularcomputers.runtime;

import com.payonel.modularcomputers.bus.BusStatus;
import java.util.List;
import java.util.Optional;

/**
 * The motherboard API: everything a program on the CPU can do. A Lua runtime exposes these as
 * Lua functions; mock programs (plain Java) call them directly.
 * <p>
 * Devices are addressed three ways, as on a real system:
 * <ul>
 * <li>a case slot's path, fixed by the slot: {@code "edp"} (the built-in display, by its
 * embedded DisplayPort connector), {@code "kbd"}, {@code "eeprom"}, {@code "ups"},
 * {@code "sata:0"}, {@code "exp:2"};</li>
 * <li>a device's id, fixed once known, however it's connected (cable, Bluetooth, a hub, or a
 * case slot): {@code "id:<uuid>"};</li>
 * <li>a virtual device's name, fixed by convention: {@code "console"}.</li>
 * </ul>
 * {@link #list()} gives the physical bus's addresses in bus order: paths for the case's devices,
 * ids for external ones. A card in a case slot answers to its id as well.
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
