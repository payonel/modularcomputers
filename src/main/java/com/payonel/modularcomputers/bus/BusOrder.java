package com.payonel.modularcomputers.bus;

import java.util.ArrayList;
import java.util.List;

/**
 * The order the player has put a computer's devices in, by key (like {@code "display"} or
 * {@code "sata:0"}). Devices keep their place while they stay attached; a newly attached
 * device goes to the end; a removed one drops out.
 */
public final class BusOrder {
	private final List<String> keys = new ArrayList<>();

	public List<String> keys() {
		return List.copyOf(keys);
	}

	/**
	 * Matches the order to the devices now attached. {@code present} lists them in their
	 * default order, which is also the order new devices are appended in.
	 */
	public void sync(List<String> present) {
		keys.retainAll(present);
		for (String key : present) {
			if (!keys.contains(key)) {
				keys.add(key);
			}
		}
	}

	/** Moves the device at {@code index} one place up or down. Returns false if it can't move. */
	public boolean move(int index, boolean up) {
		int target = up ? index - 1 : index + 1;
		if (index < 0 || index >= keys.size() || target < 0 || target >= keys.size()) {
			return false;
		}
		keys.set(target, keys.set(index, keys.get(target)));
		return true;
	}

	/** Restores a saved order; {@link #sync} afterwards drops keys that are no longer attached. */
	public void restore(List<String> saved) {
		keys.clear();
		for (String key : saved) {
			if (!keys.contains(key)) {
				keys.add(key);
			}
		}
	}
}
