package com.payonel.modularcomputers.device;

import com.payonel.modularcomputers.bus.Device;
import com.payonel.modularcomputers.bus.DeviceType;

/** The UPS battery, on the bus so programs can ask about power. It has no API yet. */
public final class UpsBattery implements Device {
	@Override
	public DeviceType type() {
		return DeviceType.POWER;
	}
}
