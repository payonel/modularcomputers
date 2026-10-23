package com.payonel.modularcomputers.runtime;

import com.payonel.modularcomputers.bus.BusStatus;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The motherboard API: everything a program on the CPU can do. A Lua runtime exposes these as
 * Lua functions; mock programs (plain Java) call them directly.
 * <p>
 * Devices are addressed three ways, as on a real system:
 * <ul>
 * <li>a slot's path, fixed by the slot: {@code "eeprom"}, {@code "ups"}, {@code "sata:0"},
 * {@code "exp:2"};</li>
 * <li>a device's id, fixed once known, however it's connected (cable, Bluetooth, a hub, a
 * slot, or built in): {@code "id:<uuid>"}. Every device has one. A built-in terminal has no
 * path: it's simply the first terminal listed, as the case's devices come first;</li>
 * <li>a virtual device's name, fixed by convention: {@code "console"}.</li>
 * </ul>
 * {@link #list()} gives the physical bus's devices in bus order: each one's address (a path for
 * a device in a slot, else its id), its id, which every device has and answers to, and its type.
 * <p>
 * Every call can be a point where the machine is paused until a later tick (each machine gets a
 * fixed number of calls a tick); the program never notices, except as time passing.
 */
public interface Board {
	/** The interrupt source for the bus itself: devices attached or removed. */
	String BUS = "bus";

	/**
	 * One device on the bus: its address, its own id ({@code id:<uuid>}, which it answers to
	 * too; the same as the address for a device without a slot) and its type. A Lua runtime
	 * gives it as a table.
	 */
	record DeviceInfo(String address, String id, String type) {
	}

	/**
	 * A store of power, in power units: how much it holds now and when full. An infinite one (a
	 * tier 3 battery) is always full; a Lua runtime gives its numbers as {@code math.huge}.
	 */
	record Store(long stored, long capacity, boolean infinite) {
		public static final Store NONE = new Store(0, 0, false);

		public int percent() {
			return infinite ? 100 : capacity == 0 ? 0 : (int) Math.ceil(stored * 100.0 / capacity);
		}
	}

	/** A battery in a bay (from 0), like ACPI's BAT0, BAT1: its tier and its charge. */
	record BatteryInfo(int bay, int tier, Store charge) {
	}

	/**
	 * The case's power, as the firmware reports it (ACPI's battery and AC information): the case's
	 * own store, which the computer runs on, each battery in a bay, and what the computer draws
	 * now, a tick (its load).
	 */
	record PowerInfo(Store store, List<BatteryInfo> batteries, long load) {
	}

	/** The CPU in the socket (SMBIOS's processor information): its tier and how many devices its bus holds. */
	record CpuInfo(int tier, int busCapacity) {
	}

	/** A RAM stick (SMBIOS's memory device): its slot (from 0), tier and size in bytes. */
	record MemoryInfo(int slot, int tier, long capacity) {
	}

	/**
	 * The board's hardware, as SMBIOS describes it ({@code dmidecode}): the case's tier, the CPU
	 * (null without one), the RAM sticks, and the memory in all and in use ({@code /proc/meminfo}).
	 */
	record HardwareInfo(int caseTier, CpuInfo cpu, List<MemoryInfo> memory, long memoryTotal, long memoryUsed) {
	}

	/** The case's power: its store, its batteries and its load. */
	PowerInfo power();

	/** The board's hardware: case tier, CPU, RAM. */
	HardwareInfo hardware();

	/**
	 * What a device says of itself, like {@code udevadm info} or {@code lsblk}: its address, id,
	 * type and label (what the player named it, if anything), then what its kind adds (a tier, a
	 * size). A Lua runtime gives it as a table. Empty for no such device.
	 */
	Optional<Map<String, Object>> info(String address);

	/** The devices on the physical bus, in bus order. Reading it clears the bus's interrupt. */
	List<DeviceInfo> list();

	/** How full the bus is: capacity, saved, missing, waiting, overflow. */
	BusStatus status();

	/** What kind of device is at {@code address} ("display", "storage", ... or "console"). */
	Optional<String> type(String address);

	/** The methods the device at {@code address} offers, in name order. */
	List<String> methods(String address);

	Optional<String> doc(String address, String method);

	/** Calls a device's method, right away. Failures (no such device, bad arguments) are soft. */
	Result invoke(String address, String method, Object... args);

	/**
	 * Calls a method of a handle a device gave the program (an open file), right away. Soft
	 * failures as for devices; a closed handle fails.
	 */
	Result invoke(Handle handle, String method, Object... args);

	/**
	 * Waits until one of {@code sources} raises an interrupt (any device, the bus or a virtual
	 * device, when none are named), or {@code seconds} pass, like Linux's {@code poll()} on the
	 * files a program cares about. Returns the waited-on addresses with an interrupt pending, in
	 * bus order ({@link #BUS} first), or none on a timeout. An interrupt stays pending until the
	 * program reads the device, as with a real interrupt line, so a program that waits on
	 * everything has to read everything.
	 */
	List<String> waitForInterrupt(double seconds, String... sources);

	/** Ticks since the machine was powered on. */
	long uptime();

	/**
	 * Halts the CPU for good, like {@code cli; hlt} or Linux's {@code reboot(HALT)}: the program
	 * ends here and never returns, and power stays on (the screens keep what they show). A
	 * program must end this way: returning instead is a panic, as when Linux's init exits.
	 */
	void halt();

	/**
	 * Turns the machine off, as ACPI's soft-off (S5): the program ends here, and the computer is
	 * off as if its power button were pressed. Never returns.
	 */
	void poweroff();

	/** Restarts the machine: power off and on again, the CPU booting its EEPROM afresh. Never returns. */
	void reboot();

	/**
	 * The bus address of the device a name names: a card in a slot is at its slot's path and named
	 * by its id too ({@code id:<uuid>}); any other name is its own. Interrupts are raised at
	 * addresses, so a program that names a device by its id hears them through this.
	 */
	default String addressOf(String name) {
		return name;
	}

	/** The method that takes the next piece of a device's data, when it has a stream (see {@code Device.streamMethod}). */
	default Optional<String> streamMethod(String address) {
		return Optional.empty();
	}

	/**
	 * How many pieces of each device's data the firmware's inbox keeps for the program, from the RAM
	 * ({@code MemoryTier.inbox}, summed over the sticks): 0 with only tier 1 RAM, and every read of a
	 * device is a call to it.
	 */
	default int inboxSize() {
		return 0;
	}
}
