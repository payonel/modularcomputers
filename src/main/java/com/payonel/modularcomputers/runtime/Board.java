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
 * <li>a slot's path, fixed by the slot: {@code "eeprom"}, {@code "ups"}, {@code "sata:0"},
 * {@code "exp:2"};</li>
 * <li>a device's id, fixed once known, however it's connected (cable, Bluetooth, a hub, a
 * slot, or built in): {@code "id:<uuid>"}. Every device has one. A built-in terminal has no
 * path: it's simply the first terminal listed, as the case's devices come first;</li>
 * <li>a virtual device's name, fixed by convention: {@code "console"}.</li>
 * </ul>
 * {@link #list()} gives the physical bus's devices in bus order: each one's address (a path for
 * a device in a slot, else its id), its id, which every device has and answers to, and its type.
 * <p>
 * Every call can be a point where the machine is paused until a later tick (each machine gets a
 * fixed number of calls a tick); the program never notices, except as time passing.
 */
public interface Board {
	/** The interrupt source for the bus itself: devices attached or removed. */
	String BUS = "bus";

	/**
	 * One device on the bus: its address, its own id ({@code id:<uuid>}, which it answers to
	 * too; the same as the address for a device without a slot) and its type. A Lua runtime
	 * gives it as a table.
	 */
	record DeviceInfo(String address, String id, String type) {
	}

	/** The devices on the physical bus, in bus order. Reading it clears the bus's interrupt. */
	List<DeviceInfo> list();

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
	 * Waits until one of {@code sources} raises an interrupt (any device, the bus or a virtual
	 * device, when none are named), or {@code seconds} pass, like Linux's {@code poll()} on the
	 * files a program cares about. Returns the waited-on addresses with an interrupt pending, in
	 * bus order ({@link #BUS} first), or none on a timeout. An interrupt stays pending until the
	 * program reads the device, as with a real interrupt line, so a program that waits on
	 * everything has to read everything.
	 */
	List<String> waitForInterrupt(double seconds, String... sources);

	/** Ticks since the machine was powered on. */
	long uptime();

	/**
	 * Halts the CPU for good, like {@code cli; hlt} or Linux's {@code reboot(HALT)}: the program
	 * ends here and never returns, and power stays on (the screens keep what they show). A
	 * program must end this way: returning instead is a panic, as when Linux's init exits.
	 */
	void halt();
}
