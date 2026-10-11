package com.payonel.modularcomputers.bus;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The order the player has put a computer's devices in, by key (like {@code "display"},
 * {@code "sata:0"} or a cabled device's {@code "cable:<id>"}). The order is set the first time
 * a device is seen: it goes to the end. Devices keep their place while they stay attached.
 * <p>
 * A device that goes away either drops out (parts in the case: the slot is what matters) or is
 * <em>remembered</em> (cabled devices: unplugged, or in a chunk that isn't loaded), keeping its
 * place for when it's seen again. At most {@link #MAX_REMEMBERED} absent devices are
 * remembered; the earliest-listed are forgotten first.
 * <p>
 * The player can also disconnect a device: it stays listed, in its place, but isn't on the bus.
 */
public final class BusOrder {
	/** How many absent devices are remembered. */
	public static final int MAX_REMEMBERED = 32;

	/** Every device in order: present and remembered. */
	private final List<String> keys = new ArrayList<>();
	private final Set<String> present = new HashSet<>();
	private final Set<String> disconnected = new LinkedHashSet<>();

	/** The devices attached now, in order, connected or not: what the bus order panel lists. */
	public List<String> keys() {
		return keys.stream().filter(present::contains).toList();
	}

	/** The devices on the bus: attached and connected, in order. */
	public List<String> connected() {
		return keys.stream().filter(key -> present.contains(key) && !disconnected.contains(key)).toList();
	}

	/** Every device, absent ones included, e.g. for saving. */
	public List<String> all() {
		return List.copyOf(keys);
	}

	public boolean isConnected(String key) {
		return !disconnected.contains(key);
	}

	/** The disconnected devices, absent ones included, e.g. for saving. */
	public List<String> disconnected() {
		return List.copyOf(disconnected);
	}

	/** Connects or disconnects a listed device. Returns false if it isn't listed or nothing changed. */
	public boolean setConnected(String key, boolean connected) {
		if (!keys.contains(key)) {
			return false;
		}
		return connected ? disconnected.remove(key) : disconnected.add(key);
	}

	/** Matches the order to the devices now attached. Absent devices drop out. */
	public void sync(List<String> attached) {
		sync(attached, key -> false);
	}

	/**
	 * Matches the order to the devices now attached. {@code attached} lists them in their
	 * default order, which is also the order new devices are appended in. Absent devices that
	 * {@code remember} accepts keep their place.
	 */
	public void sync(List<String> attached, Predicate<String> remember) {
		present.clear();
		present.addAll(attached);
		keys.removeIf(key -> !present.contains(key) && !remember.test(key));
		for (String key : attached) {
			if (!keys.contains(key)) {
				keys.add(key);
			}
		}
		List<String> absent = keys.stream().filter(key -> !present.contains(key)).toList();
		for (int i = 0; i < absent.size() - MAX_REMEMBERED; i++) {
			keys.remove(absent.get(i));
		}
		disconnected.retainAll(keys);
	}

	/**
	 * Moves the attached device at {@code index} (in {@link #keys()}) one place up or down, past
	 * any remembered absent devices in between. Returns false if it can't move.
	 */
	public boolean move(int index, boolean up) {
		List<String> shown = keys();
		int target = up ? index - 1 : index + 1;
		if (index < 0 || index >= shown.size() || target < 0 || target >= shown.size()) {
			return false;
		}
		int from = keys.indexOf(shown.get(index));
		int to = keys.indexOf(shown.get(target));
		keys.set(to, keys.set(from, keys.get(to)));
		return true;
	}

	/**
	 * Restores a saved order and disconnections; {@link #sync} afterwards sorts out which are
	 * attached.
	 */
	public void restore(List<String> saved, List<String> savedDisconnected) {
		keys.clear();
		for (String key : saved) {
			if (!keys.contains(key)) {
				keys.add(key);
			}
		}
		present.clear();
		present.addAll(keys);
		disconnected.clear();
		disconnected.addAll(savedDisconnected);
		disconnected.retainAll(keys);
	}
}
