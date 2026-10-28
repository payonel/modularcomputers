package com.payonel.modularcomputers.device;

import com.payonel.modularcomputers.bus.InterruptLine;

import com.payonel.modularcomputers.bus.BusPower;
import com.payonel.modularcomputers.bus.Device;
import com.payonel.modularcomputers.bus.DeviceType;
import com.payonel.modularcomputers.power.PowerUnits;
import com.payonel.modularcomputers.runtime.Arguments;
import com.payonel.modularcomputers.runtime.Method;
import com.payonel.modularcomputers.runtime.Result;
import java.util.List;

/**
 * A robotic piston on the bus (user design, 2026-10-13): it pushes on any of its six faces but the
 * one with its cable port, as a real piston pushes, by the side's name. {@code push} is a pulse: the
 * arm goes out and comes back on its own; {@code extend} leaves it out until {@code retract}. Every
 * move costs power, whether it moved anything or not; extending an arm that's out already is free.
 * <p>
 * It's buffered ({@link Buffered}): each move is a command, ending in its event once the arm has
 * moved ({@link #tick}, its block's): a push once the arm is back. A move that can't start (the
 * cable's side, nothing that moves, no arm out to retract) is refused at once. Every move of a
 * method ends in its event, an extend whose arm is out already too (at once): a program waiting for
 * one never has to know which it was.
 */
public final class RoboticPiston implements Device, Buffered {
	/** Where its front is: the face opposite its cable port (null until its block says). */
	private java.util.function.Supplier<Face> front = () -> null;

	public void frontFrom(java.util.function.Supplier<Face> front) {
		this.front = front;
	}
	/** What a push costs, moved or not: five ticks of a running CPU. */
	public static final int PUSH_COST = 5 * PowerUnits.CPU;
	/** Why a push or an extend can't start, and the side: an arm out there, or what won't move. */
	public static final String CANNOT_PUSH = "blocked";
	/** Why a move can't start on its cable port's side, and the side: no arm there. */
	public static final String CABLE = "cable";
	/** Why a retract can't start, and the side: no arm out there. */
	public static final String NOT_EXTENDED = "not extended";
	/** A vanilla piston's move, in ticks: an extend's or a retract's time. */
	public static final int MOVE_TICKS = 2;
	/** A push's time: out, and back (its block's arm time). */
	public static final int PUSH_TICKS = com.payonel.modularcomputers.block.RoboticPistonBlock.ARM_TICKS + MOVE_TICKS;
	private final BusPower power = new BusPower(0);
	private final InterruptLine line = new InterruptLine();
	private boolean accepting;
	private final EventQueue events = new EventQueue(line, () -> {
	}, () -> accepting);
	/** The move running: its method, and the ticks till it's done (0: none). */
	private String moving;
	private int left;

	/** How the arm on a side moves. */
	public enum Motion {
		/** Out and back on its own. */
		PUSH,
		/** Out, and it stays out. */
		EXTEND,
		/** Back in. */
		RETRACT
	}

	/** What a move came to. */
	public enum Moved {
		/** It moved. */
		DONE,
		/** Extend: the arm was out already (nothing moved, nothing to pay). */
		ALREADY_OUT,
		/** It couldn't push (an arm out there, what won't move). */
		CANNOT_PUSH,
		/** The side is its cable port's: no arm there. */
		CABLE,
		/** Retract: no arm out there. */
		NOT_EXTENDED
	}

	/** Moves an arm in the world. */
	public interface Arms {
		Moved move(String side, Motion motion);
	}

	private Arms arms = (side, motion) -> motion == Motion.RETRACT ? Moved.NOT_EXTENDED : Moved.CANNOT_PUSH;

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

	@Method(doc = "push(side): pushes as a piston does on that side (north, south, east, west, up or down; or front, back, left, right: its front is opposite its cable), and the arm comes back on its own. A command: true at once (its event: push, once the arm is back), or false and why it can't (cable: the side, its cable port's; blocked: the side, an arm out there, or something that won't move)")
	public Result push(Arguments args) {
		return move(args, Motion.PUSH);
	}

	@Method(doc = "extend(side): pushes on that side and leaves the arm out until retract(side). A command: true at once (its event: extend, once it's out; at once when it's out already: free), or false and why (blocked: the side)")
	public Result extend(Arguments args) {
		return move(args, Motion.EXTEND);
	}

	@Method(doc = "retract(side): takes the arm on that side back in (a sticky piston pulls what's in front of it). A command: true at once (its event: retract, once it's in), or false and why (not extended: the side)")
	public Result retract(Arguments args) {
		return move(args, Motion.RETRACT);
	}

	private Result move(Arguments args, Motion motion) {
		String side;
		try {
			side = Face.named(args.checkString(0), front.get()).id();
		} catch (IllegalArgumentException e) {
			return Result.fail(e.getMessage());
		}
		Moved moved = arms.move(side, motion);
		if (moved != Moved.ALREADY_OUT) {
			power.charge(PUSH_COST);
		}
		String method = motion.name().toLowerCase(java.util.Locale.ROOT);
		return switch (moved) {
			case DONE -> {
				moving = method;
				left = motion == Motion.PUSH ? PUSH_TICKS : MOVE_TICKS;
				yield Result.ok(true);
			}
			case ALREADY_OUT -> {
				events.ended(method, true, null, null); // (out already: done as it starts)
				yield Result.ok(true);
			}
			case CANNOT_PUSH -> Result.fail(CANNOT_PUSH + ": " + side);
			case CABLE -> Result.fail(CABLE + ": " + side);
			case NOT_EXTENDED -> Result.fail(NOT_EXTENDED + ": " + side);
		};
	}

	@Override
	public java.util.Set<String> free() {
		return java.util.Set.of("pull");
	}

	@Override
	public String refusal(String method) {
		return moving != null ? BUSY : null;
	}

	/** Its block's tick: a move running counts down, and ends. */
	public void tick() {
		if (moving != null && --left <= 0) {
			String method = moving;
			moving = null;
			events.ended(method, true, null, null);
		}
	}

	@Method(doc = "pull(): its oldest event, a move's end ({event, ok}), or nil")
	public Result pull(Arguments args) {
		return Result.ok(events.pull());
	}

	@Override
	public String streamMethod() {
		return "pull";
	}

	@Override
	public boolean interruptPending() {
		return events.pending();
	}

	@Override
	public InterruptLine interruptLine() {
		return line;
	}

	@Override
	public void accepting(boolean accepting) {
		this.accepting = accepting;
		if (!accepting) {
			events.clear(); // (its program's gone: what it held for it goes, but a load's at its first reset; a move under way finishes in the world)
		}
	}

	/** Its events waiting and the move under way (a save isn't a power cut: it ends after the load, its program hearing). */
	@Override
	public java.util.Map<String, Object> saved() {
		java.util.Map<String, Object> saved = new java.util.LinkedHashMap<>();
		events.save(saved);
		if (moving != null) {
			saved.put("moving", moving);
			saved.put("left", (long) left);
		}
		return saved;
	}

	@Override
	public void restore(java.util.Map<String, Object> saved) {
		events.restore(saved);
		moving = saved.get("moving") instanceof String was ? was : null;
		left = saved.get("left") instanceof Long ticks ? (int) (long) ticks : 0;
	}
}
