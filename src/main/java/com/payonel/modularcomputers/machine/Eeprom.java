package com.payonel.modularcomputers.machine;

import com.payonel.modularcomputers.bus.Device;
import com.payonel.modularcomputers.bus.DeviceType;
import com.payonel.modularcomputers.runtime.Arguments;
import com.payonel.modularcomputers.runtime.Method;
import com.payonel.modularcomputers.runtime.Result;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A firmware chip. The CPU loads its program from the motherboard's EEPROM slot at power-on (a
 * fixed place, so it never walks the bus for it); it's also on the bus, so programs can find it
 * to read or reflash it. Flashing takes effect at the next power-on.
 * <p>
 * Its contents (its image) are plain text, whatever the CPU's architecture: what {@code get} and
 * {@code set} read and write, like {@code flashrom -r} and {@code -w} on a board's SPI flash, up
 * to {@link #SIZE} bytes, kept on the chip (its item). The CPU loads its program from it (a Java
 * CPU: the class it names, {@link Cpu#load}; a Lua CPU later: Lua source). Tests and tools can
 * also attach a Java program object directly ({@link #flash}), like an in-circuit programmer;
 * the CPU then runs that instead.
 */
public final class Eeprom implements Device {
	/** How many bytes it holds: 4 KiB. */
	public static final int SIZE = 4096;

	private Program attached;
	private String image = "";
	private String label;
	private Consumer<String> onWrite = image -> {
	};

	/** A chip holding {@code image}. */
	public Eeprom(String image) {
		load(image);
	}

	/** A chip with a Java program attached (tests, tools), and nothing in its image. */
	public Eeprom(Program program) {
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

	/** What the chip holds. */
	public String image() {
		return image;
	}

	/** Loads what the chip holds (from its item), without telling anyone it changed. */
	public void load(String image) {
		this.image = image == null ? "" : image;
	}

	/** Called with the new image whenever a program writes the chip, to keep it on the item. */
	public void setOnWrite(Consumer<String> onWrite) {
		this.onWrite = onWrite;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	private static int bytes(String data) {
		return data.getBytes(StandardCharsets.UTF_8).length;
	}

	@Method(doc = "get(): the chip's contents")
	public Result get(Arguments args) {
		return Result.ok(image);
	}

	@Method(doc = "set(data): writes the chip's contents (up to getSize() bytes); takes effect at the next power-on")
	public Result set(Arguments args) {
		String data = args.checkString(0);
		if (bytes(data) > SIZE) {
			return Result.fail("not enough space: " + bytes(data) + " bytes, the chip holds " + SIZE);
		}
		image = data;
		onWrite.accept(image);
		return Result.ok();
	}

	@Method(doc = "getSize(): how many bytes it holds")
	public Result getSize(Arguments args) {
		return Result.ok(SIZE);
	}

	@Override
	public String label() {
		return label;
	}

	@Override
	public Map<String, Object> info() {
		return Map.of("capacity", (long) SIZE, "used", (long) bytes(image));
	}

	@Override
	public DeviceType type() {
		return DeviceType.FIRMWARE;
	}
}
