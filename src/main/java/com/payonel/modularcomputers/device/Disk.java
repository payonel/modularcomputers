package com.payonel.modularcomputers.device;

import com.payonel.modularcomputers.bus.Device;
import com.payonel.modularcomputers.bus.DeviceType;

/** A storage device with no size limit. It holds no data yet. */
public final class Disk implements Device {
	@Override
	public DeviceType type() {
		return DeviceType.STORAGE;
	}
}
