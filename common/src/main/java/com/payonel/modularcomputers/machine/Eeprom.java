package com.payonel.modularcomputers.machine;

import com.payonel.modularcomputers.bus.Device;
import com.payonel.modularcomputers.bus.DeviceType;
import com.payonel.modularcomputers.runtime.Arguments;
import com.payonel.modularcomputers.runtime.Method;
import com.payonel.modularcomputers.runtime.Result;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An EEPROM, as the machine sees it. The CPU loads its program from the motherboard's EEPROM slot
 * at power-on (a fixed place, so it never walks the bus for it); it's also on the bus, so programs
 * can find it to read or reflash it. Flashing takes effect at the next power-on.
 * <p>
 * Its contents (its image) are plain text: what {@code get} and
 * {@code set} read and write, like {@code flashrom -r} and {@code -w} on a board's SPI flash, one
 * character a byte (ISO-8859-1, as Lua's strings are). It keeps no copy: every read and write goes
 * straight to its {@link Storage}, in a case the EEPROM item in the slot (user, 2026-10-13: read and
 * write directly, no extra layers). No size limit for now (user, 2026-10-12): the whole OS lives on
 * it until EEPROMs hold a bootloader and the OS is installed on a disk. The CPU runs it as Lua.
 * Tests and tools can also attach a Java
 * program object directly ({@link #flash}), like an in-circuit programmer; the CPU then runs that
 * instead.
 */
public final class Eeprom implements Device {
	/** Where the EEPROM's contents are: in a case, its item. */
	public interface Storage {
		String read();

		void write(String image);

		/** Its NVRAM: its boot variables (none, for a chip with nowhere to keep them). */
		default BootVariables nvram() {
			return BootVariables.EMPTY;
		}

		/** Writes its NVRAM (checked already). */
		default void nvram(BootVariables vars) {
		}

		/** Its label (its item's), or null. */
		default String label() {
			return null;
		}

		/** Gives the chip a label (null: none); false if it can't have one. */
		default boolean relabel(String label) {
			return false;
		}
	}

	/** Storage of its own, for a bare chip (tests, a bench). */
	private static final class Memory implements Storage {
		private String image;
		private BootVariables nvram = BootVariables.EMPTY;

		@Override
		public BootVariables nvram() {
			return nvram;
		}

		@Override
		public void nvram(BootVariables vars) {
			this.nvram = vars;
		}

		Memory(String image) {
			this.image = image == null ? "" : image;
		}

		@Override
		public String read() {
			return image;
		}

		@Override
		public void write(String image) {
			this.image = image;
		}
	}

	private final Storage storage;
	private Program attached;
	/**
	 * UEFI's BootCurrent: the boot entry the bootloader booted ({@link BootVariables#NONE}: none, it
	 * booted by its fallback, or nothing has said). Volatile, as UEFI's: the device's, not the chip's
	 * NVRAM, gone when the power goes, kept through a save.
	 */
	private int bootCurrent = BootVariables.NONE;

	/** An EEPROM whose contents are in {@code storage}. */
	public Eeprom(Storage storage) {
		this.storage = storage;
	}

	/** A bare chip holding {@code image}. */
	public Eeprom(String image) {
		this(new Memory(image));
	}

	/** A bare chip with a Java program attached (tests, tools), and nothing in its image. */
	public Eeprom(Program program) {
		this("");
		this.attached = program;
	}

	/** The Java program attached to it, if any: the CPU runs it in place of the image. */
	public java.util.Optional<Program> attached() {
		return java.util.Optional.ofNullable(attached);
	}

	/** Attaches a Java program to the chip (tests, tools), as an in-circuit programmer would. */
	public void flash(Program program) {
		this.attached = program;
	}

	/** What it holds, read from its storage. */
	public String image() {
		String image = storage.read();
		return image == null ? "" : image;
	}

	@Method(doc = "get(): the chip's contents")
	public Result get(Arguments args) {
		return Result.ok(image());
	}

	@Method(doc = "set(data): writes the chip's contents; takes effect at the next power-on")
	public Result set(Arguments args) {
		storage.write(args.checkString(0));
		return Result.ok();
	}

	// ---- its NVRAM: the boot variables (BootVariables) --------------------------------------

	/** Its boot variables, as its storage holds them. */
	public BootVariables nvram() {
		return storage.nvram();
	}

	@Method(doc = "getBoot(): the NVRAM's boot variables: {entries = {{number, disk, path, label}, ...} by number, order = {numbers}, next = a number or nil}")
	public Result getBoot(Arguments args) {
		BootVariables vars = storage.nvram();
		List<Map<String, Object>> entries = new ArrayList<>();
		vars.entries().forEach((number, entry) -> {
			Map<String, Object> each = new LinkedHashMap<>();
			each.put("number", (long) number);
			each.put("disk", entry.disk());
			each.put("path", entry.path());
			each.put("label", entry.label());
			entries.add(each);
		});
		Map<String, Object> boot = new LinkedHashMap<>();
		boot.put("entries", entries);
		boot.put("order", vars.order().stream().map(Integer::longValue).toList());
		if (vars.next() != BootVariables.NONE) {
			boot.put("next", (long) vars.next());
		}
		if (bootCurrent != BootVariables.NONE) {
			boot.put("current", (long) bootCurrent);
		}
		return Result.ok(boot);
	}

	@Method(doc = "setBootCurrent([number]): the bootloader's: the boot entry it's booting, or none when it boots by its fallback; kept until the power goes")
	public Result setBootCurrent(Arguments args) {
		if (args.get(0) == null) {
			bootCurrent = BootVariables.NONE;
			return Result.ok();
		}
		long number = args.checkInteger(0);
		if (!storage.nvram().entries().containsKey((int) number) || number < 0 || number >= BootVariables.ENTRIES) {
			return Result.fail("no Boot" + BootVariables.name((int) Math.max(0, Math.min(number, 0xFFFF))));
		}
		bootCurrent = (int) number;
		return Result.ok();
	}

	/** The entry it booted, or {@link BootVariables#NONE}. */
	public int bootCurrent() {
		return bootCurrent;
	}

	@Override
	public void powerOff() {
		bootCurrent = BootVariables.NONE; // (volatile)
	}

	/** What a save keeps of it: BootCurrent (a save isn't a power cut). */
	private static final String BOOT_CURRENT = "bootCurrent";

	@Override
	public Map<String, Object> saved() {
		return bootCurrent == BootVariables.NONE ? Map.of() : Map.of(BOOT_CURRENT, (long) bootCurrent);
	}

	@Override
	public void restore(Map<String, Object> saved) {
		bootCurrent = saved.get(BOOT_CURRENT) instanceof Number number ? number.intValue() : BootVariables.NONE;
	}

	@Method(doc = "setBootEntry(number, disk[, path[, label]]): makes boot entry number (0 to 7) or replaces it: a disk by its id (id:<uuid>, there or not), the file it boots (/init.lua if none), and a label")
	public Result setBootEntry(Arguments args) {
		long number = args.checkInteger(0);
		String disk = args.checkString(1);
		String path = args.get(2) == null ? null : args.checkString(2);
		String label = args.get(3) == null ? null : args.checkString(3);
		return change(vars -> vars.withEntry(number, disk, path, label));
	}

	@Method(doc = "removeBootEntry(number): removes a boot entry, from the boot order and as BootNext too")
	public Result removeBootEntry(Arguments args) {
		long number = args.checkInteger(0);
		return change(vars -> vars.withoutEntry(number));
	}

	@Method(doc = "setBootOrder(numbers): the order boot entries are tried in, each an entry's number, none twice")
	public Result setBootOrder(Arguments args) {
		Map<?, ?> table = args.checkTable(0);
		List<Long> numbers = new ArrayList<>();
		for (long i = 1; i <= table.size(); i++) {
			if (!(table.get(i) instanceof Long number)) {
				throw new Arguments.ArgumentError("a list of entry numbers expected");
			}
			numbers.add(number);
		}
		if (numbers.size() != table.size()) {
			throw new Arguments.ArgumentError("a list of entry numbers expected");
		}
		return change(vars -> vars.withOrder(numbers));
	}

	@Method(doc = "setBootNext([number]): the entry to boot next time only (the bootloader clears it as it boots); none clears it")
	public Result setBootNext(Arguments args) {
		long number = args.get(0) == null ? BootVariables.NONE : args.checkInteger(0);
		return change(vars -> vars.withNext(number));
	}

	/** A change to its boot variables: written when it's valid, else refused with why. */
	private interface Change {
		BootVariables apply(BootVariables vars) throws BootVariables.Invalid;
	}

	private Result change(Change change) {
		try {
			storage.nvram(change.apply(storage.nvram()));
			return Result.ok();
		} catch (BootVariables.Invalid e) {
			return Result.fail(e.getMessage());
		}
	}

	@Override
	public String label() {
		return storage.label();
	}

	@Override
	public boolean relabel(String label) {
		return storage.relabel(label);
	}

	@Override
	public Map<String, Object> info() {
		return Map.of("used", (long) image().length()); // one character a byte
	}

	@Override
	public DeviceType type() {
		return DeviceType.EEPROM;
	}
}
