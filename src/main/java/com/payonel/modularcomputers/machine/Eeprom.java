package com.payonel.modularcomputers.machine;

import com.payonel.modularcomputers.bus.Device;
import com.payonel.modularcomputers.bus.DeviceType;

/**
 * A firmware chip. The CPU loads its program from the motherboard's EEPROM slot at power-on (a
 * fixed place, so it never walks the bus for it); it's also on the bus, so programs can find it
 * to read or reflash it.
 */
public record Eeprom(Program program) implements Device {
	@Override
	public DeviceType type() {
		return DeviceType.FIRMWARE;
	}
}
