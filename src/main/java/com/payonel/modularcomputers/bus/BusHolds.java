package com.payonel.modularcomputers.bus;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Collections;

/**
 * Which board each device is on (user, 2026-10-13): a device is on one bus at a time. Whichever
 * board has it on its bus first holds it; another board that reaches it (a second computer on the
 * same cable, or one whose chunk loaded later) finds it missing, as an absent device, until the
 * holder lets go: the device leaves its bus, or the holder unloads or loses its CPU. Then the others
 * lay out their buses again ({@link #version()}) and the first to do so takes it. Nothing is saved:
 * holds are worked out afresh as boards load, so nothing can fall out of sync.
 */
public final class BusHolds {
	private static final Map<Device, Object> HOLDERS = new IdentityHashMap<>();
	private static long version;

	private BusHolds() {
	}

	/** Whether a board other than {@code board} holds {@code device}. */
	public static synchronized boolean heldByAnother(Device device, Object board) {
		Object holder = HOLDERS.get(device);
		return holder != null && holder != board;
	}

	/** {@code board}'s bus is now {@code devices}: it holds those (that no other board holds), and lets go of the rest. */
	public static synchronized void hold(Object board, Collection<Device> devices) {
		Set<Device> keep = Collections.newSetFromMap(new IdentityHashMap<>());
		keep.addAll(devices);
		if (HOLDERS.entrySet().removeIf(entry -> entry.getValue() == board && !keep.contains(entry.getKey()))) {
			version++;
		}
		for (Device device : devices) {
			HOLDERS.putIfAbsent(device, board);
		}
	}

	/** {@code board} lets go of everything (it unloaded, or went away). */
	public static synchronized void releaseAll(Object board) {
		hold(board, Set.of());
	}

	/** Changes each time a board lets go of a device: the others may take it now. */
	public static synchronized long version() {
		return version;
	}
}
