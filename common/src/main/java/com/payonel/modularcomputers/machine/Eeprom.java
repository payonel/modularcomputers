package com.payonel.modularcomputers.machine;

import com.payonel.modularcomputers.bus.Device;
import com.payonel.modularcomputers.bus.DeviceType;
import com.payonel.modularcomputers.runtime.Arguments;
import com.payonel.modularcomputers.runtime.Method;
import com.payonel.modularcomputers.runtime.Result;
import java.util.Map;

/**
 * An EEPROM, as the machine sees it. The CPU loads its program from the motherboard's EEPROM slot
 * at power-on (a fixed place, so it never walks the bus for it); it's also on the bus, so programs
 * can find it to read or reflash it. Flashing takes effect at the next power-on.
 * <p>
 * Its contents (its image) are plain text, whatever the CPU's architecture: what {@code get} and
 * {@code set} read and write, like {@code flashrom -r} and {@code -w} on a board's SPI flash, one
 * character a byte (ISO-8859-1, as Lua's strings are). It keeps no copy: every read and write goes
 * straight to its {@link Storage}, in a case the EEPROM item in the slot (user, 2026-10-13: read and
 * write directly, no extra layers). No size limit for now (user, 2026-10-12): the whole OS lives on
 * it until EEPROMs hold a bootloader and the OS is installed on a disk. A Lua CPU runs it as Lua; the
 * Java CPU loads the class it names ({@link Cpu#load}). Tests and tools can also attach a Java
 * program object directly ({@link #flash}), like an in-circuit programmer; the CPU then runs that
 * instead.
 */
public final class Eeprom implements Device {
	/** Where the EEPROM's contents are: in a case, its item. */
	public interface Storage {
		String read();

		void write(String image);

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
