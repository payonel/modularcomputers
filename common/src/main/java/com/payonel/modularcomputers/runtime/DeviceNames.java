package com.payonel.modularcomputers.runtime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The one place a device's names meet. A device on the bus has an address (a part: its connector's
 * path, {@code exp:0}) and may answer to other names (a part's id, {@code id:<uuid>}: see
 * {@link Board#addressOf}); a program picks one (mcos names every device by its id). Everything the
 * host keeps for the program about a device (a wait's sources, the streams it reads, its rings)
 * is kept by the program's name, as the program sent it. Interrupts are the one thing that comes
 * from the bus by address: each goes through {@link #named} before anything compares it with the
 * program's names, so no other code ever compares names across that line.
 * <p>
 * A program says it uses a name by using it; {@link #use} records it, from either thread.
 */
public final class DeviceNames {
	private final Set<String> used = ConcurrentHashMap.newKeySet();

	/** The program named a device so (a wait, a read, a ring). */
	public void use(String name) {
		used.add(name);
	}

	public void useAll(Collection<String> names) {
		used.addAll(names);
	}

	/** Raised addresses, each by the name the program uses for its device (its address itself, when the program used no other). */
	public List<String> named(Board board, List<String> raised) {
		if (raised.isEmpty() || used.isEmpty()) {
			return raised;
		}
		Map<String, String> byAddress = new HashMap<>();
		for (String name : used) {
			String address = board.addressOf(name); // (now: a device can move to another connector)
			if (!address.equals(name)) {
				byAddress.putIfAbsent(address, name);
			}
		}
		if (byAddress.isEmpty()) {
			return raised;
		}
		List<String> named = new ArrayList<>(raised.size());
		for (String address : raised) {
			named.add(byAddress.getOrDefault(address, address));
		}
		return named;
	}

	/**
	 * Which of {@code sources} have an interrupt raised, by the name each was asked by: a direct
	 * wait's answer ({@link Board#waitForInterrupt}), every raised one when none are named.
	 */
	public static List<String> ready(Board board, List<String> raised, List<String> sources) {
		if (sources.isEmpty()) {
			return raised;
		}
		List<String> ready = new ArrayList<>();
		for (String address : raised) {
			for (String source : sources) {
				if (board.addressOf(source).equals(address) && !ready.contains(source)) {
					ready.add(source);
				}
			}
		}
		return ready;
	}
}
