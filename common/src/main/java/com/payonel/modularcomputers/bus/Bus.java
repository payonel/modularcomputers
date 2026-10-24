package com.payonel.modularcomputers.bus;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The physical bus: the real devices wired to a computer, in bus order, each at a stable
 * address: a case slot's path ({@code "sata:0"}, {@code "edp"}), or an external device's id
 * ({@code "id:<uuid>"}), however it's connected. A device with an id in a case slot answers to
 * both, like Linux's {@code by-path} and {@code by-id} names for one disk. Walking the bus means
 * going through this list. The CPU and memory aren't on it: they're the machine
 * itself, as on a real PC. The order is set by {@link BusOrder}.
 */
public final class Bus {
	private Map<String, Device> devices = Map.of();
	/** Other names for devices on the bus (a card's id), each to its address. */
	private Map<String, String> aliases = Map.of();
	private BusStatus status = BusStatus.EMPTY;
	private int changes;

	/** An empty bus: its motherboard puts the devices on it ({@code Motherboard.layout}), as a case's parts and cables give them. */
	public Bus() {
	}

	public List<Device> devices() {
		return List.copyOf(devices.values());
	}

	/** Each device with its address, in bus order, with no copy made (the interrupt check runs every turn). */
	public void forEach(java.util.function.BiConsumer<String, Device> action) {
		devices.forEach(action);
	}

	/** Addresses, in bus order. */
	public List<String> addresses() {
		return List.copyOf(devices.keySet());
	}

	/** The device at an address, or at another name for one (a card's id). */
	public Optional<Device> device(String address) {
		return Optional.ofNullable(devices.get(aliases.getOrDefault(address, address)));
	}

	/** The id ({@code id:<uuid>}) of the device at {@code address}, or null if it has none. */
	public String id(String address) {
		if (address.startsWith("id:")) {
			return address;
		}
		return aliases.entrySet().stream().filter(alias -> alias.getValue().equals(address))
			.map(Map.Entry::getKey).findFirst().orElse(null);
	}

	/** The address of the device at an address or another name for one, if it's on the bus. */
	public Optional<String> address(String name) {
		String address = aliases.getOrDefault(name, name);
		return devices.containsKey(address) ? Optional.of(address) : Optional.empty();
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

	/**
	 * Replaces the devices, by address, in bus order (a part went in or out, a cable was plugged, a
	 * reorder), with other names for some of them: {@code aliases} maps each to an address.
	 */
	public void setDevices(Map<String, Device> devices, Map<String, String> aliases) {
		this.aliases = Map.copyOf(aliases);
		Map<String, Device> next = new LinkedHashMap<>(devices);
		if (!next.equals(this.devices)) {
			changes++;
		}
		this.devices = next;
	}
}
