package com.payonel.modularcomputers.bus;

/**
 * Something attached to a computer's bus. Devices describe themselves; there are no drivers.
 * Programs use a device through its methods (public methods annotated
 * {@link com.payonel.modularcomputers.runtime.Method}), by its address on the bus.
 */
public interface Device {
	DeviceType type();

	/**
	 * The device's interrupt line: on while it has something for the program (a key pressed, data
	 * arrived), off once the program has read it. Level-triggered, as on real hardware: the data
	 * stays in the device, not in a machine-wide queue.
	 */
	default boolean interruptPending() {
		return false;
	}

	/**
	 * Its power from the bus, if it takes any (see {@link BusPower}): it has power while a running
	 * computer has it on its bus, and owes what it draws. Null for a device that takes none; such
	 * a device just loses power when it leaves a running computer's bus, or the computer goes off.
	 */
	default BusPower power() {
		return null;
	}

	/** Its interrupt line, if it can wake a computer the moment it has data (see {@link InterruptLine}). */
	default InterruptLine interruptLine() {
		return null;
	}

	/** A program's turn changed it: push what it shows out now (to players), rather than at the next tick. */
	default void flush() {
	}

	/** It lost power. Volatile state goes away (a display goes black). */
	default void powerOff() {
	}
}
