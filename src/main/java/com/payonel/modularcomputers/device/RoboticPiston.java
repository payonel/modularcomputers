package com.payonel.modularcomputers.device;

import com.payonel.modularcomputers.bus.BusPower;
import com.payonel.modularcomputers.bus.Device;
import com.payonel.modularcomputers.bus.DeviceType;
import com.payonel.modularcomputers.power.PowerUnits;
import com.payonel.modularcomputers.runtime.Arguments;
import com.payonel.modularcomputers.runtime.Method;
import com.payonel.modularcomputers.runtime.Result;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * A robotic piston on the bus (user design, 2026-10-13): it pushes on any of its six faces but the
 * one with its cable port, as a real piston pushes, by the side's name. Every push costs power,
 * whether it moved anything or not.
 */
public final class RoboticPiston implements Device {
	/** The sides, as programs name them (world directions). */
	public static final List<String> SIDES = List.of("down", "up", "north", "south", "west", "east");
	/** What a push costs, moved or not: five ticks of a running CPU. */
	public static final int PUSH_COST = 5 * PowerUnits.CPU;
	public static final String CANNOT_PUSH = "cannot push";
	private final BusPower power = new BusPower(0);
	/** Pushes on a side, in the world: whether it did. */
	private Predicate<String> pusher = side -> false;

	@Override
	public DeviceType type() {
		return DeviceType.ACTUATOR;
	}

	@Override
	public BusPower power() {
		return power;
	}

	/** How it pushes, in the world (its block). */
	public void pushWith(Predicate<String> pusher) {
		this.pusher = pusher;
	}

	@Method(doc = "push(side): pushes as a piston does on that side (down, up, north, south, west or east); true, or false and \"cannot push\" (the cable's side, or something that won't move)")
	public Result push(Arguments args) {
		String side = args.checkString(0).toLowerCase(Locale.ROOT);
		if (!SIDES.contains(side)) {
			return Result.fail("no such side: " + side);
		}
		power.charge(PUSH_COST);
		return pusher.test(side) ? Result.ok(true) : Result.fail(CANNOT_PUSH);
	}
}
