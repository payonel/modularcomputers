package com.payonel.modularcomputers.device;

import com.payonel.modularcomputers.bus.Device;
import com.payonel.modularcomputers.bus.DeviceType;

/** A paint gun on the bus. It will paint nearby wool; it does nothing yet. */
public final class PaintGun implements Device {
	@Override
	public DeviceType type() {
		return DeviceType.ACTUATOR;
	}
}
