package com.payonel.modularcomputers.machine;

import com.payonel.modularcomputers.bus.Device;
import com.payonel.modularcomputers.bus.DeviceType;

/**
 * A firmware chip. The CPU loads its program from the motherboard's EEPROM slot at power-on (a
 * fixed place, so it never walks the bus for it); it's also on the bus, so programs can find it
 * to read or reflash it. Flashing takes effect at the next power-on.
 */
public final class Eeprom implements Device {
	private Program program;

	public Eeprom(Program program) {
		this.program = program;
	}

	public Program program() {
		return program;
	}

	/** Writes a new program onto the chip, as reflashing firmware does. */
	public void flash(Program program) {
		this.program = program;
	}

	@Override
	public DeviceType type() {
		return DeviceType.FIRMWARE;
	}
}
