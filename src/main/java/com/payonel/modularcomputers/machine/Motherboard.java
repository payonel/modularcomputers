package com.payonel.modularcomputers.machine;

import com.payonel.modularcomputers.bus.Bus;
import com.payonel.modularcomputers.bus.BusOrder;
import com.payonel.modularcomputers.bus.Device;
import com.payonel.modularcomputers.bus.VirtualBus;
import com.payonel.modularcomputers.console.ConsoleSplitter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Ties a machine together: its soldered parts, the CPU socket, the EEPROM slot, the physical
 * bus with the real devices, and the virtual bus with devices that always exist (like the
 * console).
 * <p>
 * It's also the board's firmware for the bus: its <b>connectors</b> come in a fixed order, each
 * with a fixed place on the bus (the built-in terminal's, the {@code eeprom} and {@code ups}
 * slots, {@code sata:0..3}, {@code exp:0..3}), whatever case it's in; a case wires up the ones
 * it has. Every device has its own id, {@code id:<uuid>}. A slot's device is addressed by the
 * slot's path and answers to its id too, like Linux's {@code by-path} and {@code by-id} names;
 * the built-in terminal has no path, so its id is its address (programs find it as the first
 * terminal listed). External devices (by cable or Bluetooth) are addressed by their id, and kept
 * in the player's order ({@link BusOrder}).
 */
public final class Motherboard {
	/** How many devices a bus holds unless the board is set otherwise. */
	public static final int DEFAULT_BUS_CAPACITY = 32;

	/** The built-in terminal's connector. It isn't an address: the terminal is known by its id. */
	public static final String TERMINAL = "terminal";
	public static final String EEPROM = "eeprom";
	public static final String UPS = "ups";
	/** How many SATA ports, and how many expansion slots. */
	public static final int LANES = 4;
	private static final String SATA = "sata:";
	private static final String EXP = "exp:";
	private static final String ID = "id:";
	/** Every connector, in bus order: its index (from 1) is its fixed place on the bus. */
	public static final List<String> CONNECTORS = connectors();

	/** What the bus is now: the devices by address, in order, and the other names some answer to. */
	public record Layout(Map<String, Device> devices, Map<String, String> aliases) {
	}

	private final Hardware parts;
	private final Bus bus;
	private final VirtualBus virtualBus;
	private Cpu cpu;
	private Eeprom eeprom;
	private final BusOrder order = new BusOrder(DEFAULT_BUS_CAPACITY, CONNECTORS.size());
	/** What's on each connector. */
	private final Map<String, Device> plugged = new HashMap<>();
	/** The ids ({@code id:<uuid>}) of the devices on connectors, each to its connector. */
	private final Map<String, String> ids = new HashMap<>();
	/** The external devices found now, in the order new ones join (nearest first); null where they don't run here (a client). */
	private Map<String, Device> external = Map.of();

	public static String sata(int lane) {
		return SATA + lane;
	}

	public static String exp(int lane) {
		return EXP + lane;
	}

	/** A device's address by its own id, however it's connected. */
	public static String id(UUID id) {
		return ID + id;
	}

	public static boolean isSata(String address) {
		return address.startsWith(SATA);
	}

	public static boolean isExp(String address) {
		return address.startsWith(EXP);
	}

	/** A SATA port's or expansion slot's lane, from 0. */
	public static int lane(String connector) {
		return Integer.parseInt(connector.substring(connector.indexOf(':') + 1));
	}

	private static List<String> connectors() {
		List<String> connectors = new ArrayList<>(List.of(TERMINAL, EEPROM, UPS));
		for (int lane = 0; lane < LANES; lane++) {
			connectors.add(sata(lane));
		}
		for (int lane = 0; lane < LANES; lane++) {
			connectors.add(exp(lane));
		}
		return List.copyOf(connectors);
	}

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

	/** How many devices its bus holds, internal and external together: a board setting. */
	public int busCapacity() {
		return order.capacity();
	}

	public void setBusCapacity(int busCapacity) {
		order.setCapacity(busCapacity);
	}

	/** The bus order: the connectors' devices, then the external ones in the player's order. */
	public BusOrder order() {
		return order;
	}

	/**
	 * Puts a device on a connector, or takes it off ({@code device} null). {@code id} is the
	 * device's own id (a part's serial number, a built-in's id): it answers to that too.
	 */
	public void plug(String connector, Device device, UUID id) {
		ids.values().remove(connector);
		if (device == null) {
			plugged.remove(connector);
			return;
		}
		plugged.put(connector, device);
		if (id != null) {
			ids.put(id(id), connector);
		}
	}

	public void plug(String connector, Device device) {
		plug(connector, device, null);
	}

	/**
	 * The external devices found now, by {@code id:} address, nearest first. A null device is
	 * one that's there but doesn't run here (a client knows of it, from the server).
	 */
	public void setExternal(Map<String, Device> found) {
		external = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(found));
	}

	/**
	 * A device's place on the bus, from 1, as the bus order panel numbers it: a connector's is
	 * fixed (empty ones leave gaps, as PCI's slot numbers do), then the external devices follow
	 * in their order.
	 */
	public int position(String address) {
		for (int place = 0; place < CONNECTORS.size(); place++) {
			if (address.equals(address(CONNECTORS.get(place)))) {
				return place + 1;
			}
		}
		return CONNECTORS.size() + 1 + order.external().stream().map(BusOrder.Entry::key).toList().indexOf(address);
	}

	/**
	 * Works out the bus at game time {@code now}: the plugged connectors in their order, then the
	 * external devices in the player's order (missing and disconnected ones left out). Updates
	 * the bus's status; {@link Machine#rebuildBus} or {@link #rebuildBus} puts it on the bus.
	 */
	public Layout layout(long now) {
		Map<String, Device> internal = new LinkedHashMap<>();
		Map<String, String> aliases = new LinkedHashMap<>();
		for (String connector : CONNECTORS) {
			if (plugged.containsKey(connector)) {
				String address = address(connector);
				internal.put(address, plugged.get(connector));
				id(connector).filter(id -> !id.equals(address)).ifPresent(id -> aliases.put(id, address));
			}
		}
		order.sync(List.copyOf(internal.keySet()), List.copyOf(external.keySet()), now);
		bus.setStatus(order.status());
		Map<String, Device> devices = new LinkedHashMap<>();
		for (String address : order.connected()) {
			Device device = internal.containsKey(address) ? internal.get(address) : external.get(address);
			if (device != null) {
				devices.put(address, device);
			}
		}
		aliases.values().retainAll(devices.keySet());
		return new Layout(devices, aliases);
	}

	/** The id of the device on a connector. */
	private Optional<String> id(String connector) {
		return ids.entrySet().stream().filter(id -> id.getValue().equals(connector)).map(Map.Entry::getKey).findFirst();
	}

	/** The address of the device on a connector: the connector's path, or the built-in terminal's id. */
	public String address(String connector) {
		return connector.equals(TERMINAL) ? id(connector).orElse(connector) : connector;
	}

	/** Puts the layout on the bus as it is, with no hot-plug (a client's copy, which runs nothing). */
	public void rebuildBus(long now) {
		Layout layout = layout(now);
		bus.setDevices(layout.devices(), layout.aliases());
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
		plug(EEPROM, eeprom); // it's on the bus too, at its slot
	}

	/** Power is cut: every device loses its volatile state, and the console starts over. */
	public void powerOff() {
		bus.devices().forEach(Device::powerOff);
		virtualBus.console().reset();
	}
}
