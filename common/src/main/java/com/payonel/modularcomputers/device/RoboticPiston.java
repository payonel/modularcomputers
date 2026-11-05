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
		/** Extend or push: the arm was out already; retract: it was in (nothing moved, nothing to pay: not a failure, as a lit lamp lit again). */
		ALREADY,
		/** It couldn't push: what's in front won't move. */
		CANNOT_PUSH,
		/** The side is its cable port's: no arm there, blocked by what's on it. */
		CABLE
	}

	/** Moves an arm in the world. */
	public interface Arms {
		Moved move(String side, Motion motion);
	}

	private Arms arms = (side, motion) -> motion == Motion.RETRACT ? Moved.ALREADY : Moved.CANNOT_PUSH;
	/** The block keeping a push on a side from moving (null: none to name), in the world (its block). */
	private java.util.function.UnaryOperator<String> blocker = side -> null;
	/** The block next to it on a side (its cable port's: what blocks a move there), in the world (its block). */
	private java.util.function.UnaryOperator<String> neighbor = side -> null;

	@Override
	public DeviceType type() {
		return DeviceType.ACTUATOR;
	}

	@Override
	public BusPower power() {
		return power;
	}

	/** The last command the board gave it and its arguments (the board's {@link #called}). */
	private String calledMethod;
	private java.util.List<Object> calledArgs = java.util.List.of();

	@Override
	public void called(String method, java.util.List<Object> args) {
		calledMethod = method;
		calledArgs = EventQueue.given(args);
	}

	/** A move's call that moved nothing: its arguments as given, progress 0. */
	private java.util.Map<String, Object> call(String method) {
		return EventQueue.call(method.equals(calledMethod) ? calledArgs : java.util.List.of(), 0L);
	}

	/** How its arms move, in the world (its block). */
	public void moveWith(Arms arms) {
		this.arms = arms;
	}

	/** What keeps a push on a side from moving, in the world (its block): a block's id, or null. */
	public void blockedBy(java.util.function.UnaryOperator<String> blocker, java.util.function.UnaryOperator<String> neighbor) {
		this.blocker = blocker;
		this.neighbor = neighbor;
	}

	@Method(doc = "push(side): pushes as a piston does on that side (north, south, east, west, up or down; or front, back, left, right: its front is opposite its cable), and the arm comes back on its own. A command: true at once (its event: push, once the arm is back; at once when its arm is out already, ok: nothing to do; or not ok, blocked: block, the first down its line that won't move, left out when no one block is to blame, and face, the side), or false and why it can't start (no such face); on its cable port's side its end is blocked at once, by its cable (air when it's linked by Bluetooth)")
	public Result push(Arguments args) {
		return move(args, Motion.PUSH);
	}

	@Method(doc = "extend(side): pushes on that side and leaves the arm out until retract(side). A command: true at once (its event: extend, once it's out; at once when it's out already, ok and free; or not ok, blocked, as push's), or false and why it can't start (no such face); on its cable port's side, blocked, as push's")
	public Result extend(Arguments args) {
		return move(args, Motion.EXTEND);
	}

	@Method(doc = "retract(side): takes the arm on that side back in (a sticky piston pulls what's in front of it). A command: true at once (its event: retract, once it's in; at once when no arm is out there, ok: nothing to do), or false and why it can't start (no such face); on its cable port's side, blocked, as push's")
	public Result retract(Arguments args) {
		return move(args, Motion.RETRACT);
	}

	private Result move(Arguments args, Motion motion) {
		String side;
		try {
			side = Face.named(args.checkString(0), front.get()).id();
		} catch (IllegalArgumentException e) {
			return Result.fail(com.payonel.modularcomputers.runtime.Failure.badArgument(1, args.checkString(0), "face"));
		}
		Moved moved = arms.move(side, motion);
		if (moved != Moved.ALREADY) {
			power.charge(PUSH_COST);
		}
		String method = motion.name().toLowerCase(java.util.Locale.ROOT);
		return switch (moved) {
			case DONE -> {
				moving = method;
				left = motion == Motion.PUSH ? PUSH_TICKS : MOVE_TICKS;
				yield Result.ok(true);
			}
			case ALREADY -> {
				events.ended(method, null); // (out already, or in: done as it starts)
				yield Result.ok(true);
			}
			case CANNOT_PUSH -> {
				// (its end at once, as every command's: blocked, the block, its face the world's way)
				events.blocked(method, blocker.apply(side), side, call(method)); // (nothing moved: progress 0)
				yield Result.ok(true);
			}
			case CABLE -> {
				// (its cable port's side: no arm there, blocked by what's on it, its cable, or air with Bluetooth)
				events.blocked(method, neighbor.apply(side), side, call(method));
				yield Result.ok(true);
			}
		};
	}

	@Override
	public java.util.Set<String> free() {
		return java.util.Set.of("pull");
	}

	@Override
	public String refusal(String method) {
		return busy() ? BUSY : null;
	}

	/** A move under way. */
	@Override
	public boolean busy() {
		return moving != null;
	}

	/** Its block's tick: a move running counts down, and ends. */
	public void tick() {
		if (moving != null && --left <= 0) {
			String method = moving;
			moving = null;
			events.ended(method, null);
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
