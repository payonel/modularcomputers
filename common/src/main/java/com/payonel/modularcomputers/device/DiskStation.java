package com.payonel.modularcomputers.device;

import com.payonel.modularcomputers.bus.BusPower;
import com.payonel.modularcomputers.bus.Device;
import com.payonel.modularcomputers.bus.DeviceType;
import com.payonel.modularcomputers.power.PowerUnits;
import com.payonel.modularcomputers.runtime.Arguments;
import com.payonel.modularcomputers.runtime.Method;
import com.payonel.modularcomputers.runtime.Result;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A Disk Station on the bus (user design): a dock for disks, to format, wipe, clone and install
 * them without crafting a disk alone, as a drive duplicator does. Its target disk is the one it works
 * on; its source, optional, is only read (a write blocker's role, as forensic docks have), for
 * cloning. Both are on the bus too, beside it. Every action is a method and a line written to its
 * file ({@code set}), the same as its GUI's buttons; each costs power, the bus's or its battery's.
 * Disks go in and come out by hand, in its GUI.
 */
public final class DiskStation implements Device {
	/** What an action costs: a second of a running CPU. */
	public static final int ACTION_COST = 20 * PowerUnits.CPU;
	/** What it draws at rest, a tick. */
	public static final int DRAW = 1;
	public static final String TARGET = "target";
	public static final String SOURCE = "source";
	public static final String NO_DISK = "no disk";
	public static final String NO_SOURCE = "no source disk";
	public static final String NO_POWER = "no power";
	/** The image a disk gets when it's installed or updated: mcos. */
	public static final String IMAGE = "mcos";
	/** The actions, as {@code set} takes them (and the GUI's buttons are). */
	public static final List<String> ACTIONS = List.of("format", "wipe", "clone", "install", "update");

	/** What its block does for it: its slots, its power, the world. */
	public interface Bay {
		/** The disk in a slot ({@link #TARGET}, {@link #SOURCE}), or null. */
		Disk disk(String which);

		/** Pays for an action: the bus's power when it has some, else its battery's; false: neither has enough. */
		boolean pay(int cost);

		/** Copies an image's files into a disk's folder (over what's there, when {@code replace}): what it wrote. */
		List<Path> copyImage(String image, Path folder, boolean replace);
	}

	private final BusPower power = new BusPower(DRAW);
	private final Bay bay;

	public DiskStation(Bay bay) {
		this.bay = bay;
	}

	@Override
	public BusPower power() {
		return power;
	}

	@Override
	public DeviceType type() {
		return DeviceType.DISK_STATION;
	}

	private static Arguments none() {
		return new Arguments(List.of());
	}

	/** Runs an action on the target disk, paid for. */
	private Result onTarget(java.util.function.Function<Disk, Result> action) {
		Disk target = bay.disk(TARGET);
		if (target == null) {
			return Result.fail(NO_DISK);
		}
		if (!bay.pay(ACTION_COST)) {
			return Result.fail(NO_POWER);
		}
		return action.apply(target);
	}

	@Method(doc = "format(): a standard filesystem on its disk (hfs), empty: everything on it is gone")
	public Result format(Arguments args) {
		return onTarget(disk -> disk.format(Disk.HFS));
	}

	@Method(doc = "wipe(): its disk without a filesystem (raw, read and written by offset): everything on it is gone")
	public Result wipe(Arguments args) {
		return onTarget(disk -> disk.format(Disk.RAW));
	}

	@Method(doc = "clone(): its disk becomes a copy of the source disk (its mode and contents); refused when they don't fit")
	public Result clone(Arguments args) {
		Disk source = bay.disk(SOURCE);
		if (source == null) {
			return Result.fail(NO_SOURCE);
		}
		return onTarget(disk -> disk.cloneFrom(source));
	}

	@Method(doc = "install(): its disk formatted, then mcos copied onto it (the mod's latest)")
	public Result install(Arguments args) {
		return onTarget(disk -> {
			Result formatted = disk.format(Disk.HFS);
			return formatted.failed() ? formatted : disk.copyIn(folder -> bay.copyImage(IMAGE, folder, false));
		});
	}

	@Method(doc = "update(): mcos's files on its disk written over with the mod's latest (/etc too); everything else left")
	public Result update(Arguments args) {
		return onTarget(disk -> disk.copyIn(folder -> bay.copyImage(IMAGE, folder, true)));
	}

	@Method(doc = "get(): {target = disk, source = disk} (a disk: {tier, mode, used, capacity, label}, or nothing for an empty slot)")
	public Result get(Arguments args) {
		Map<String, Object> state = new LinkedHashMap<>();
		for (String which : List.of(TARGET, SOURCE)) {
			Disk disk = bay.disk(which);
			if (disk != null) {
				Map<String, Object> about = new LinkedHashMap<>();
				about.put("tier", (long) disk.tier().ordinal() + 1);
				about.put("mode", disk.getMode(none()).first());
				about.put("used", disk.getUsed(none()).first());
				about.put("capacity", disk.getCapacity(none()).first());
				if (disk.label() != null) {
					about.put("label", disk.label());
				}
				state.put(which, about);
			}
		}
		return Result.ok(state);
	}

	@Method(doc = "set(action): {action = format | wipe | clone | install | update}: does it, as its method does")
	public Result set(Arguments args) {
		Object action = args.checkTable(0).get("action");
		if (!(action instanceof String name) || !ACTIONS.contains(name)) {
			return Result.fail("no such action: " + action + " (" + String.join(", ", ACTIONS) + ")");
		}
		return act(name);
	}

	/** An action by its name (a GUI button's, or set's). */
	public Result act(String action) {
		Arguments none = none();
		return switch (action) {
			case "format" -> format(none);
			case "wipe" -> wipe(none);
			case "clone" -> clone(none);
			case "install" -> install(none);
			case "update" -> update(none);
			default -> Result.fail("no such action: " + action);
		};
	}
}
