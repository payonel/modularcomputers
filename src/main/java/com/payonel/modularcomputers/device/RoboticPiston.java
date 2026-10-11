package com.payonel.modularcomputers.device;

import com.payonel.modularcomputers.bus.Device;
import com.payonel.modularcomputers.bus.DeviceType;

/** A robotic piston on the bus. It will push blocks; it does nothing yet. */
public final class RoboticPiston implements Device {
	@Override
	public DeviceType type() {
		return DeviceType.ACTUATOR;
	}
}
