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

/**
 * A robotic piston on the bus (user design, 2026-10-13): it pushes on any of its six faces but the
 * one with its cable port, as a real piston pushes, by the side's name. {@code push} is a pulse: the
 * arm goes out and comes back on its own; {@code extend} leaves it out until {@code retract}. Every
 * move costs power, whether it moved anything or not.
 */
public final class RoboticPiston implements Device {
	/** The sides, as programs name them (world directions). */
	public static final List<String> SIDES = List.of("down", "up", "north", "south", "west", "east");
	/** What a push costs, moved or not: five ticks of a running CPU. */
	public static final int PUSH_COST = 5 * PowerUnits.CPU;
	public static final String CANNOT_PUSH = "cannot push";
	public static final String NOT_EXTENDED = "not extended";
	private final BusPower power = new BusPower(0);

	/** How the arm on a side moves. */
	public enum Motion {
		/** Out and back on its own. */
		PUSH,
		/** Out, and it stays out. */
		EXTEND,
		/** Back in. */
		RETRACT
	}

	/** Moves an arm in the world: null when it did, else why not. */
	public interface Arms {
		String move(String side, Motion motion);
	}

	private Arms arms = (side, motion) -> motion == Motion.RETRACT ? NOT_EXTENDED : CANNOT_PUSH;

	@Override
	public DeviceType type() {
		return DeviceType.ACTUATOR;
	}

	@Override
	public BusPower power() {
		return power;
	}

	/** How its arms move, in the world (its block). */
	public void moveWith(Arms arms) {
		this.arms = arms;
	}

	@Method(doc = "push(side): pushes as a piston does on that side (down, up, north, south, west or east), and the arm comes back on its own; true, or false and \"cannot push\" (the cable's side, an arm out there, or something that won't move)")
	public Result push(Arguments args) {
		return move(args, Motion.PUSH);
	}

	@Method(doc = "extend(side): pushes on that side and leaves the arm out until retract(side); true (also when it's out already), or false and \"cannot push\"")
	public Result extend(Arguments args) {
		return move(args, Motion.EXTEND);
	}

	@Method(doc = "retract(side): takes the arm on that side back in (a sticky piston pulls what's in front of it); true, or false and \"not extended\"")
	public Result retract(Arguments args) {
		return move(args, Motion.RETRACT);
	}

	private Result move(Arguments args, Motion motion) {
		String side = args.checkString(0).toLowerCase(Locale.ROOT);
		if (!SIDES.contains(side)) {
			return Result.fail("no such side: " + side);
		}
		power.charge(PUSH_COST);
		String failed = arms.move(side, motion);
		return failed == null ? Result.ok(true) : Result.fail(failed);
	}
}
