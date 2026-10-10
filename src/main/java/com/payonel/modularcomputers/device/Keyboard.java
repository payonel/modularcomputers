package com.payonel.modularcomputers.device;

import com.payonel.modularcomputers.bus.Device;
import com.payonel.modularcomputers.bus.DeviceType;

/** A keyboard. It doesn't send input yet; it's on the bus so the firmware can see it. */
public final class Keyboard implements Device {
	@Override
	public DeviceType type() {
		return DeviceType.KEYBOARD;
	}
}
