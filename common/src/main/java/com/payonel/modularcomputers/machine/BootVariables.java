package com.payonel.modularcomputers.machine;

import com.payonel.modularcomputers.bus.Label;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * An EEPROM's NVRAM: its boot variables, as UEFI's (user design, 2026-10-25), a closed set of typed
 * values checked at every write, never a free store (UEFI's free store let bad writes brick laptops):
 * boot entries ({@code Boot0000}...), each a disk by its id, a file on it, and a label; the order
 * they're tried in ({@code BootOrder}); and the one to try once, next ({@code BootNext}). An entry
 * is checked for its form, not for its disk being there (as UEFI keeps a USB stick's entry while it's
 * out): a disk not on the bus is skipped at boot. Immutable: each change makes a new one.
 *
 * @param entries the entries by number
 * @param order entry numbers, each an entry's, once each
 * @param next an entry's number, or {@link #NONE}
 * @param restoreOnPowerLoss whether the computer powers itself back on when power comes back after
 *        it was lost (a BIOS setup's "restore on AC power loss", the user's: off by default)
 */
public record BootVariables(Map<Integer, Entry> entries, List<Integer> order, int next, boolean restoreOnPowerLoss) {
	/** How many entries there can be, numbered from 0. */
	public static final int ENTRIES = 8;
	/** No BootNext. */
	public static final int NONE = -1;
	/** The longest file path an entry names. */
	public static final int MAX_PATH = 128;
	/** The file an entry boots when it names none: what the factory bootloader looks for. */
	public static final String DEFAULT_PATH = "/init.lua";
	/** A disk's name in an entry: its id, as the bus names it. */
	private static final String ID_PREFIX = "id:";
	public static final BootVariables EMPTY = new BootVariables(Map.of(), List.of(), NONE, false);

	public BootVariables(Map<Integer, Entry> entries, List<Integer> order, int next) {
		this(entries, order, next, false);
	}

	/** With restore on AC power loss set. */
	public BootVariables withRestoreOnPowerLoss(boolean on) {
		return new BootVariables(entries, order, next, on);
	}

	/** A boot entry: a disk ({@code id:<uuid>}), a file on it, and a label ("" for none). */
	public record Entry(String disk, String path, String label) {
	}

	/** A change refused, and why (a device's failure: the program's to handle). */
	public static final class Invalid extends Exception {
		public Invalid(String why) {
			super(why, null, false, false);
		}
	}

	public BootVariables {
		entries = java.util.Collections.unmodifiableMap(new TreeMap<>(entries));
		order = List.copyOf(order);
	}

	/** Its entries in order of number. */
	public Map<Integer, Entry> entries() {
		return entries;
	}

	/** Adds entry {@code number} (or replaces it): {@code path} null for {@link #DEFAULT_PATH}, {@code label} null for none. */
	public BootVariables withEntry(long number, String disk, String path, String label) throws Invalid {
		int at = number(number);
		String why = diskInvalid(disk);
		if (why == null) {
			why = pathInvalid(path == null ? DEFAULT_PATH : path);
		}
		if (why == null && label != null) {
			why = Label.invalid(label);
		}
		if (why != null) {
			throw new Invalid(why);
		}
		Map<Integer, Entry> now = new TreeMap<>(entries);
		now.put(at, new Entry(disk, path == null ? DEFAULT_PATH : path, label == null ? "" : label));
		return new BootVariables(now, order, next, restoreOnPowerLoss);
	}

	/** Removes entry {@code number}, from the order and as BootNext too (as {@code efibootmgr -B} does). */
	public BootVariables withoutEntry(long number) throws Invalid {
		int at = existing(number);
		Map<Integer, Entry> now = new TreeMap<>(entries);
		now.remove(at);
		List<Integer> left = new ArrayList<>(order);
		left.remove(Integer.valueOf(at));
		return new BootVariables(now, left, next == at ? NONE : next, restoreOnPowerLoss);
	}

	/** Sets the order: each number an entry's, none twice. */
	public BootVariables withOrder(List<Long> numbers) throws Invalid {
		List<Integer> now = new ArrayList<>();
		for (long number : numbers) {
			int at = existing(number);
			if (now.contains(at)) {
				throw new Invalid("Boot" + name(at) + " is in the order twice");
			}
			now.add(at);
		}
		return new BootVariables(entries, now, next, restoreOnPowerLoss);
	}

	/** Sets BootNext (an entry's number), or clears it ({@link #NONE}). */
	public BootVariables withNext(long number) throws Invalid {
		return new BootVariables(entries, order, number == NONE ? NONE : existing(number), restoreOnPowerLoss);
	}

	/** The entries to try, in turn: BootNext's first, then the order's (each once). */
	public List<Entry> toTry() {
		List<Entry> tried = new ArrayList<>();
		if (next != NONE && entries.containsKey(next)) {
			tried.add(entries.get(next));
		}
		for (int number : order) {
			Entry entry = entries.get(number);
			if (entry != null && !tried.contains(entry)) {
				tried.add(entry);
			}
		}
		return tried;
	}

	/** An entry's number as UEFI writes it: four hex digits ({@code 0001}). */
	public static String name(int number) {
		return String.format("%04X", number);
	}

	private static int number(long number) throws Invalid {
		if (number < 0 || number >= ENTRIES) {
			throw new Invalid("an entry is numbered 0 to " + (ENTRIES - 1));
		}
		return (int) number;
	}

	private int existing(long number) throws Invalid {
		int at = number(number);
		if (!entries.containsKey(at)) {
			throw new Invalid("no Boot" + name(at));
		}
		return at;
	}

	/** Why {@code disk} can't name a disk (an id, {@code id:<uuid>}, as the bus writes it), or null. */
	public static String diskInvalid(String disk) {
		if (!disk.startsWith(ID_PREFIX)) {
			return "a disk is named by its id: id:<uuid>";
		}
		try {
			String uuid = disk.substring(ID_PREFIX.length());
			if (!UUID.fromString(uuid).toString().equals(uuid)) {
				return "not an id as the bus writes one: " + disk;
			}
		} catch (IllegalArgumentException e) {
			return "not an id: " + disk;
		}
		return null;
	}

	/** Why {@code path} can't be an entry's file (absolute, printable, at most {@link #MAX_PATH}), or null. */
	public static String pathInvalid(String path) {
		if (!path.startsWith("/")) {
			return "a path starts at the disk's top: /";
		} else if (path.length() > MAX_PATH) {
			return "a path is at most " + MAX_PATH + " characters";
		} else if (!path.chars().allMatch(c -> c > ' ' && c < 0x7F)) {
			return "a path is printable characters, without spaces";
		}
		return null;
	}
}
