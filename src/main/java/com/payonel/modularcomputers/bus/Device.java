package com.payonel.modularcomputers.bus;

/**
 * Something attached to a computer's bus. Devices describe themselves; there are no drivers,
 * so whoever walks the bus (the firmware) uses a device directly through its own type.
 */
public interface Device {
	DeviceType type();

	/** The machine lost power. Volatile state goes away (a display goes black). */
	default void powerOff() {
	}
}
