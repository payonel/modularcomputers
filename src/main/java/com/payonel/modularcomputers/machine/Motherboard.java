package com.payonel.modularcomputers.machine;

import com.payonel.modularcomputers.bus.Bus;
import com.payonel.modularcomputers.bus.Device;
import com.payonel.modularcomputers.bus.VirtualBus;
import com.payonel.modularcomputers.console.ConsoleSplitter;
import java.util.Optional;

/**
 * Ties a machine together: its soldered parts, the CPU socket, the EEPROM slot, the physical
 * bus with the real devices, and the virtual bus with devices that always exist (like the
 * console).
 */
public final class Motherboard {
	private final Hardware parts;
	private final Bus bus;
	private final VirtualBus virtualBus;
	private Cpu cpu;
	private Eeprom eeprom;

	public Motherboard(Hardware parts, Bus bus) {
		this.parts = parts;
		this.bus = bus;
		this.virtualBus = new VirtualBus(new ConsoleSplitter(bus));
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

	public Optional<Cpu> cpu() {
		return Optional.ofNullable(cpu);
	}

	/** @param cpu the CPU in the socket, or null to empty it */
	public void setCpu(Cpu cpu) {
		this.cpu = cpu;
	}

	public Optional<Eeprom> eeprom() {
		return Optional.ofNullable(eeprom);
	}

	/** @param eeprom the EEPROM in the slot, or null to empty it */
	public void setEeprom(Eeprom eeprom) {
		this.eeprom = eeprom;
	}

	/** Power is cut: every device loses its volatile state, and the console starts over. */
	public void powerOff() {
		bus.devices().forEach(Device::powerOff);
		virtualBus.console().reset();
	}
}
