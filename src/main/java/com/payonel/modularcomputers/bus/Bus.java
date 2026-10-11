package com.payonel.modularcomputers.bus;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The physical bus: the real devices wired to a computer, in bus order, each at a stable
 * address (a slot's path like {@code "sata:0"}, or a wired block's {@code "cable:<id>"}). Walking
 * the bus means going through this list. The CPU and memory aren't on it: they're the machine
 * itself, as on a real PC. The order is set by {@link BusOrder}.
 */
public final class Bus {
	private Map<String, Device> devices;
	private BusStatus status = BusStatus.EMPTY;
	private int changes;

	/** A bus of these devices, addressed by position (for tests and before the first real order). */
	public Bus(List<Device> devices) {
		this.devices = numbered(devices);
	}

	public List<Device> devices() {
		return List.copyOf(devices.values());
	}

	/** Addresses, in bus order. */
	public List<String> addresses() {
		return List.copyOf(devices.keySet());
	}

	public Optional<Device> device(String address) {
		return Optional.ofNullable(devices.get(address));
	}

	/** Counts changes to which devices are on the bus: hot-plug, for the bus's interrupt. */
	public int changes() {
		return changes;
	}

	/** How full the bus is, and whether devices are waiting for room. */
	public BusStatus status() {
		return status;
	}

	public void setStatus(BusStatus status) {
		this.status = status;
	}

	/** Replaces the devices, by address, in bus order: a part went in or out, a cable was plugged, a reorder. */
	public void setDevices(Map<String, Device> devices) {
		Map<String, Device> next = new LinkedHashMap<>(devices);
		if (!next.equals(this.devices)) {
			changes++;
		}
		this.devices = next;
	}

	/** Replaces the devices, addressed by position. */
	public void setDevices(List<Device> devices) {
		setDevices(numbered(devices));
	}

	private static Map<String, Device> numbered(List<Device> devices) {
		Map<String, Device> numbered = new LinkedHashMap<>();
		for (int i = 0; i < devices.size(); i++) {
			numbered.put("device:" + i, devices.get(i));
		}
		return numbered;
	}
}
