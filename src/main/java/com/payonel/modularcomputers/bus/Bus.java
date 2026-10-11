package com.payonel.modularcomputers.bus;

import java.util.List;

/**
 * The physical bus: the real devices wired to a computer, in discovery order. Walking the bus
 * means going through this list. The CPU and memory aren't on it: they're the machine itself,
 * as on a real PC. The order is the player's to set (see {@link BusOrder}).
 */
public final class Bus {
	private List<Device> devices;

	public Bus(List<Device> devices) {
		this.devices = List.copyOf(devices);
	}

	public List<Device> devices() {
		return devices;
	}

	/** Replaces the devices, e.g. after a part was inserted or the player reordered them. */
	public void setDevices(List<Device> devices) {
		this.devices = List.copyOf(devices);
	}
}
