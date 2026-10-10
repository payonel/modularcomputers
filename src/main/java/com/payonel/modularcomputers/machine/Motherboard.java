package com.payonel.modularcomputers.machine;

import com.payonel.modularcomputers.bus.Bus;
import com.payonel.modularcomputers.bus.Device;
import com.payonel.modularcomputers.bus.VirtualBus;
import com.payonel.modularcomputers.console.ConsoleSplitter;
import java.util.Optional;

/**
 * Ties a machine together: its parts (CPU, memory), the physical bus with the real devices,
 * the virtual bus with devices that always exist (like the console), and the EEPROM slot.
 */
public final class Motherboard {
	private final Hardware parts;
	private final Bus bus;
	private final VirtualBus virtualBus;
	private final Eeprom eeprom;

	/** @param eeprom what's in the EEPROM slot, or null when it's empty */
	public Motherboard(Hardware parts, Bus bus, Eeprom eeprom) {
		this.parts = parts;
		this.bus = bus;
		this.virtualBus = new VirtualBus(new ConsoleSplitter(bus));
		this.eeprom = eeprom;
	}

	public Hardware parts() {
		return parts;
	}

	public Bus bus() {
		return bus;
	}

	public VirtualBus virtualBus() {
		return virtualBus;
	}

	public Optional<Eeprom> eeprom() {
		return Optional.ofNullable(eeprom);
	}

	/** Power is cut: every device loses its volatile state, and the console starts over. */
	public void powerOff() {
		bus.devices().forEach(Device::powerOff);
		virtualBus.console().reset();
	}
}
