package com.payonel.modularcomputers.bus;

import java.util.List;

/**
 * The physical bus: the real devices wired to a computer, in slot order. Walking the bus means
 * going through this list. The CPU and memory aren't on it: they're the machine itself, as on
 * a real PC.
 */
public record Bus(List<Device> devices) {
	public Bus {
		devices = List.copyOf(devices);
	}
}
