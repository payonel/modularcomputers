package com.payonel.modularcomputers.board;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Where each part is (user, 2026-10-24): a part (a disk, a card, an EEPROM, a screen, a board) is
 * one thing, in one place, by its id, as a real one is. Creative can copy an item with all it holds
 * (a case with its parts, a disk): the copy is the same part, so it isn't made a second device; it
 * waits, as a part that isn't there, until the one in the world goes. Never a new id for it (the
 * user's: OpenComputers did, and lost which was which). Nothing is saved: holds are claimed afresh
 * as things load, each with its server, so a world left in the same game holds nothing in the next.
 */
public final class PartHolds {
	private record Hold(Object holder, Object server) {
	}

	private static final Map<UUID, Hold> HOLDS = new HashMap<>();
	private static long version;

	private PartHolds() {
	}

	/** {@code holder} takes part {@code id}, on {@code server}: whether it has it (free, its own already, or another server's left over). */
	public static synchronized boolean claim(UUID id, Object holder, Object server) {
		Hold hold = HOLDS.get(id);
		if (hold != null && hold.holder() != holder && hold.server() == server) {
			return false;
		}
		HOLDS.put(id, new Hold(holder, server));
		return true;
	}

	/** Whether part {@code id} is somewhere on {@code server} now (another holder's, or anyone's). */
	public static synchronized boolean held(UUID id, Object server) {
		Hold hold = HOLDS.get(id);
		return hold != null && hold.server() == server;
	}

	/** {@code holder} has exactly {@code ids} now: it lets go of the rest. */
	public static synchronized void keep(Object holder, Set<UUID> ids) {
		if (HOLDS.entrySet().removeIf(entry -> entry.getValue().holder() == holder && !ids.contains(entry.getKey()))) {
			version++;
		}
	}

	/** {@code holder} lets go of everything (it unloaded, or went away). */
	public static synchronized void releaseAll(Object holder) {
		keep(holder, Set.of());
	}

	/** Changes each time a part is let go: a copy waiting for it may take it now. */
	public static synchronized long version() {
		return version;
	}
}
