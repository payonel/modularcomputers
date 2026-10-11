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

	/** The machine lost power. Volatile state goes away (a display goes black). */
	default void powerOff() {
	}
}
