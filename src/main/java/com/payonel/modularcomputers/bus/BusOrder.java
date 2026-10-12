package com.payonel.modularcomputers.bus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The order of a computer's devices on its bus, by key: a case slot's path (like {@code "edp"}
 * or {@code "sata:0"}), or an external device's {@code "id:<uuid>"}.
 * <p>
 * <b>Internal devices</b> (built-ins and parts in the case's slots) come first, in a fixed
 * order: the slots' own. A slot's position is its address, as on real hardware.
 * <p>
 * <b>External devices</b> (by cable or Bluetooth) follow, in a saved list the player can
 * reorder, like UEFI's boot order: devices are kept by id, so one that's late to load (its
 * chunk) or briefly gone keeps its place, and shows as offline meanwhile. A device seen for the
 * first time goes to the end. The list holds at most {@link #externalCapacity()}: when it's full, a
 * new device takes the place of the one missing longest; with none missing, it waits (it isn't
 * on the bus). The player can forget an offline device.
 * <p>
 * The player can also disconnect an external device: it stays listed, in its place, but isn't
 * on the bus.
 */
public final class BusOrder {
	/** An external device in the saved list, and the game time it was last seen on the bus. */
	public record Entry(String key, long lastSeen) {
	}

	private List<String> internal = List.of();
	private final List<Entry> external = new ArrayList<>();
	private final Set<String> present = new HashSet<>();
	private final Set<String> disconnected = new LinkedHashSet<>();
	private int waiting;
	private int capacity;

	/**
	 * {@code capacity}: how many devices the bus holds, the case's own and external ones together
	 * (the CPU's). The case's own come first; external ones get what they leave.
	 */
	public BusOrder(int capacity) {
		setCapacity(capacity);
	}

	/** Changes how many devices the bus holds; the saved list shrinks to fit at the next {@link #sync}. */
	public void setCapacity(int capacity) {
		this.capacity = Math.max(0, capacity);
	}

	public int capacity() {
		return capacity;
	}

	/** Room for external devices: what the case's own devices leave of the capacity. */
	public int externalCapacity() {
		return Math.max(0, capacity - internal.size());
	}

	/** How full the bus is (see {@link BusStatus}). */
	public BusStatus status() {
		long missing = external.stream().filter(entry -> !present.contains(entry.key())).count();
		return new BusStatus(capacity, connected().size(), external.size(), (int) missing, waiting + overCapacity(), externalCapacity());
	}

	/** Every listed device in order: internal ones, then the saved external ones, online or not. */
	public List<String> keys() {
		List<String> keys = new ArrayList<>(internal);
		external.forEach(entry -> keys.add(entry.key()));
		return keys;
	}

	/**
	 * The devices on the bus: internal, then external ones that are online and connected, up to
	 * the capacity (a smaller CPU's bus leaves the last ones off, waiting).
	 */
	public List<String> connected() {
		List<String> keys = new ArrayList<>(internal.subList(0, Math.min(internal.size(), capacity)));
		external.stream().map(Entry::key).filter(key -> present.contains(key) && !disconnected.contains(key))
			.limit(Math.max(0, capacity - keys.size())).forEach(keys::add);
		return keys;
	}

	/** Listed devices that are there and connected but don't fit (the capacity went down). */
	private int overCapacity() {
		long wanted = Math.min(internal.size(), capacity)
			+ external.stream().filter(entry -> present.contains(entry.key()) && !disconnected.contains(entry.key())).count();
		return (int) Math.max(0, wanted - connected().size()) + Math.max(0, internal.size() - capacity);
	}

	/** The saved external list, e.g. for saving. */
	public List<Entry> external() {
		return List.copyOf(external);
	}

	public boolean isInternal(String key) {
		return internal.contains(key);
	}

	/** Online: internal, or an external device that's there now. */
	public boolean isOnline(String key) {
		return internal.contains(key) || present.contains(key);
	}

	public boolean isConnected(String key) {
		return !disconnected.contains(key);
	}

	/** The disconnected devices, offline ones included, e.g. for saving. */
	public List<String> disconnected() {
		return List.copyOf(disconnected);
	}

	/** How many external devices are there but not on the bus, because it's full. */
	public int waiting() {
		return waiting;
	}

	/** Connects or disconnects a listed external device. Returns false if it isn't one or nothing changed. */
	public boolean setConnected(String key, boolean connected) {
		if (index(key).isEmpty()) {
			return false;
		}
		return connected ? disconnected.remove(key) : disconnected.add(key);
	}

	/** Drops an offline external device from the list. Returns false if it isn't one. */
	public boolean forget(String key) {
		if (present.contains(key) || index(key).isEmpty()) {
			return false;
		}
		external.remove((int) index(key).get());
		disconnected.remove(key);
		return true;
	}

	/**
	 * Matches the order to the devices attached now, at game time {@code now}. {@code internal}
	 * lists the case's devices in their fixed order; {@code attached} lists the external ones,
	 * in the order new ones should be added (nearest first).
	 */
	public void sync(List<String> internal, List<String> attached, long now) {
		this.internal = List.copyOf(internal);
		present.clear();
		present.addAll(attached);
		for (int i = 0; i < external.size(); i++) {
			if (present.contains(external.get(i).key())) {
				external.set(i, new Entry(external.get(i).key(), now));
			}
		}
		while (external.size() > externalCapacity() && bumpMissing()) {
			// the capacity went down (a smaller CPU): missing devices make room first
		}
		waiting = 0;
		for (String key : attached) {
			if (index(key).isPresent()) {
				continue;
			}
			if (external.size() >= externalCapacity() && !bumpMissing()) {
				waiting++;
				continue;
			}
			external.add(new Entry(key, now));
		}
		present.retainAll(external.stream().map(Entry::key).toList());
	}

	/** Drops the device that's been missing longest. Returns false if none is missing. */
	private boolean bumpMissing() {
		Optional<Entry> oldest = external.stream().filter(entry -> !present.contains(entry.key()))
			.min(Comparator.comparingLong(Entry::lastSeen));
		oldest.ifPresent(entry -> {
			external.remove(entry);
			disconnected.remove(entry.key());
		});
		return oldest.isPresent();
	}

	/**
	 * Moves the device at {@code index} (in {@link #keys()}) one place up or down. Only external
	 * devices move, and only among themselves. Returns false if it can't move.
	 */
	public boolean move(int index, boolean up) {
		int from = index - internal.size();
		int to = up ? from - 1 : from + 1;
		if (from < 0 || from >= external.size() || to < 0 || to >= external.size()) {
			return false;
		}
		external.set(to, external.set(from, external.get(to)));
		return true;
	}

	/** Restores a saved external list and disconnections; {@link #sync} sorts out the rest. */
	public void restore(List<Entry> saved, List<String> savedDisconnected) {
		external.clear();
		for (Entry entry : saved) {
			if (index(entry.key()).isEmpty()) {
				external.add(entry);
			}
		}
		disconnected.clear();
		disconnected.addAll(savedDisconnected);
		disconnected.removeIf(key -> index(key).isEmpty());
	}

	private Optional<Integer> index(String key) {
		for (int i = 0; i < external.size(); i++) {
			if (external.get(i).key().equals(key)) {
				return Optional.of(i);
			}
		}
		return Optional.empty();
	}
}
