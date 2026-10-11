package com.payonel.modularcomputers.device;

import com.payonel.modularcomputers.bus.Device;
import com.payonel.modularcomputers.bus.DeviceType;

/** A wireless network card in an expansion slot. It doesn't network yet; it's on the bus. */
public final class NetworkCard implements Device {
	@Override
	public DeviceType type() {
		return DeviceType.NETWORK;
	}
}
