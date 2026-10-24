package com.payonel.modularcomputers.machine;

import com.payonel.modularcomputers.bus.Device;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A test machine's bus, built the way a case builds it (the only way tests build one): parts
 * plugged into the motherboard's connectors, each answering to its id too ({@code exp:0} and
 * {@code id:<uuid>}), and cabled devices found by their {@code id:} addresses; then the bus is
 * rebuilt, with hot-plug, through the bus order. So a test's devices have every name a real
 * case's have, and a program that names one by any of them is tested as it runs in the world.
 * <p>
 * Each call changes the bus at once. The board needs its CPU first: without one there's no bus.
 */
public final class TestBus {
	/** Each device's id, kept as an item keeps its serial number: the same device, the same id, on any bus. */
	private static final Map<Device, UUID> IDS = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
	private final Machine machine;
	private final Map<String, Device> cabled = new LinkedHashMap<>();
	private int nextSlot;
	private int nextSata;

	public TestBus(Machine machine) {
		this.machine = machine;
		if (machine.board().cpu().isEmpty()) {
			throw new IllegalStateException("a board with no CPU has no bus: set the CPU first");
		}
	}

	/**
	 * A bus of these devices, each where its name says a case puts it: {@code eeprom}, {@code sata:N}
	 * and {@code exp:N} on that connector (with an id of its own), {@code id:...} cabled. Any other
	 * name is refused: no case has it.
	 */
	public static TestBus of(Machine machine, Map<String, ? extends Device> devices) {
		TestBus bus = new TestBus(machine);
		devices.forEach((name, device) -> {
			if (name.startsWith("id:")) {
				bus.cabled.put(name, device);
			} else if (Motherboard.CONNECTORS.contains(name)) {
				if (device instanceof Eeprom eeprom && machine.board().eeprom().isEmpty()) {
					machine.board().setEeprom(eeprom);
				}
				UUID id = idOf(device);
				if (device instanceof com.payonel.modularcomputers.device.Card card) {
					card.idFrom(() -> id);
				}
				machine.board().plug(name, device, id);
			} else {
				throw new IllegalArgumentException("no case has a connector " + name + ": use one of " + Motherboard.CONNECTORS + " or an id: name");
			}
		});
		bus.rebuild();
		return bus;
	}

	/** Puts a part on a connector ({@link Motherboard#exp}, {@link Motherboard#sata}), answering to {@code id} too. Returns its {@code id:} name. */
	public String plug(String connector, Device device, UUID id) {
		IDS.put(device, id);
		if (device instanceof com.payonel.modularcomputers.device.Card card) {
			card.idFrom(() -> id); // (a part's id is its item's serial number)
		}
		machine.board().plug(connector, device, id);
		rebuild();
		return Motherboard.id(id);
	}

	/** Puts a part on a connector, with its own id (a card's, or one it keeps from now on). Returns its {@code id:} name. */
	public String plug(String connector, Device device) {
		return plug(connector, device, idOf(device));
	}

	/** A device's id: a card's own, or the one this fixture gave it (a new one the first time). */
	private static UUID idOf(Device device) {
		if (device instanceof com.payonel.modularcomputers.device.Card card && card.id() != null) {
			return card.id();
		}
		return IDS.computeIfAbsent(device, unused -> UUID.randomUUID());
	}

	/** A card in the next free expansion slot. Returns its {@code id:} name. */
	public String card(Device card) {
		return plug(Motherboard.exp(nextSlot++), card);
	}

	/** A card in the next free expansion slot, with this id. Returns its {@code id:} name. */
	public String card(Device card, UUID id) {
		return plug(Motherboard.exp(nextSlot++), card, id);
	}

	/** A disk on the next free SATA port. Returns its {@code id:} name. */
	public String disk(Device disk) {
		return plug(Motherboard.sata(nextSata++), disk);
	}

	/** The EEPROM, in its slot (on the bus) and the board's (what the CPU runs). */
	public void eeprom(Eeprom eeprom) {
		machine.board().setEeprom(eeprom);
		plug(Motherboard.EEPROM, eeprom);
	}

	/** Takes what's on a connector off. */
	public void unplug(String connector) {
		machine.board().plug(connector, null);
		rebuild();
	}

	/** A cabled device (a screen, a hub's card), found by its {@code id:} name. */
	public void cable(String name, Device device) {
		cabled.put(name, device);
		rebuild();
	}

	/** The cabled devices found now, in place of those before (nearest first). */
	public void cabled(Map<String, ? extends Device> found) {
		cabled.clear();
		cabled.putAll(found);
		rebuild();
	}

	/** A cabled device gone (its cable cut, its chunk unloaded). */
	public void uncable(String name) {
		cabled.remove(name);
		rebuild();
	}

	private void rebuild() {
		machine.board().setExternal(cabled);
		machine.rebuildBus(0);
	}
}
