package com.payonel.modularcomputers.device;

import com.payonel.modularcomputers.bus.BusPower;
import com.payonel.modularcomputers.bus.Device;
import com.payonel.modularcomputers.bus.DeviceType;
import com.payonel.modularcomputers.bus.InterruptLine;
import com.payonel.modularcomputers.power.PowerUnits;
import com.payonel.modularcomputers.runtime.Arguments;
import com.payonel.modularcomputers.runtime.Method;
import com.payonel.modularcomputers.runtime.Result;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A robot's robotics device (user design, 2026-10-24): its body's motors, soldered onto its board,
 * the only way it moves (a robot never acts on its own). It walks to a point given relative to
 * itself, in its own frame (forward, right, up: it knows nothing of where it is, as a robot
 * without a GPS), turns in place, and stops; each ends in an event (its interrupt): {@code
 * arrived}, {@code blocked} and why, {@code turned}, {@code stopped}. Its state is what it's doing
 * now. A body ({@link Body}: a golem) does the moving, and tells it how it went. Every block walked
 * costs power.
 */
public final class RoboticsDevice implements Device {
	/** What walking a block costs: a running CPU's tick. */
	public static final int WALK_COST = PowerUnits.CPU;
	/** What a turn costs, however far: a running CPU's tick. */
	public static final int TURN_COST = PowerUnits.CPU;
	/** The farthest a walk may go, in blocks each way (the game's pathfinding range, about). */
	public static final double MAX_WALK = 64;
	public static final String IDLE = "idle";
	public static final String WALKING = "walking";
	public static final String TURNING = "turning";

	/** What does the moving: a robot's body in the world. */
	public interface Body {
		/**
		 * Starts walking to the point {@code forward}, {@code right} and {@code up} blocks from it, in
		 * its own frame; null when it started, else why not.
		 */
		String walk(double forward, double right, double up);

		/** Starts turning {@code degrees} (to its right; negative: its left); null, or why not. */
		String turn(double degrees);

		/** Stops at once, where it is. */
		void stop();
	}

	private final InterruptLine line = new InterruptLine();
	private final BusPower power = new BusPower(0);
	private final EventQueue events = new EventQueue(line, this::changed, () -> this.accepting);
	private Body body = new Body() {
		@Override
		public String walk(double forward, double right, double up) {
			return "no body";
		}

		@Override
		public String turn(double degrees) {
			return "no body";
		}

		@Override
		public void stop() {
		}
	};
	private String state = IDLE;
	private boolean accepting;
	/** A walk or turn was under way at the save it was loaded from: its event is due. */
	private boolean interrupted;
	private Runnable onChange = () -> {
	};

	@Override
	public DeviceType type() {
		return DeviceType.ROBOTICS;
	}

	/** Its body: what moves (a golem). */
	public void moveWith(Body body) {
		this.body = body;
	}

	@Method(doc = "walk(forward[, right[, up]]): walks to the point that many blocks from it, in its own frame (forward the way it faces, right to its right, up), by the way it finds; true at once (its event says how it went: arrived, or blocked and why), or false and why it can't start. A walk or turn under way is given up")
	public Result walk(Arguments args) {
		double forward = args.checkNumber(0);
		double right = args.count() > 1 ? args.checkNumber(1) : 0;
		double up = args.count() > 2 ? args.checkNumber(2) : 0;
		if (Math.abs(forward) > MAX_WALK || Math.abs(right) > MAX_WALK || Math.abs(up) > MAX_WALK) {
			return Result.fail("too far: at most " + (int) MAX_WALK + " blocks each way");
		}
		String why = body.walk(forward, right, up);
		if (why != null) {
			return Result.fail(why);
		}
		state(WALKING);
		return Result.ok(true);
	}

	@Method(doc = "turn(way): turns in place, \"left\", \"right\" or \"around\", or a number of degrees (to its right; negative, its left); true at once (its event: turned), or false and why")
	public Result turn(Arguments args) {
		double degrees;
		Object way = args.get(0);
		if (way instanceof String name) {
			degrees = switch (name) {
				case "left" -> -90;
				case "right" -> 90;
				case "around" -> 180;
				default -> Double.NaN;
			};
			if (Double.isNaN(degrees)) {
				return Result.fail("not a way to turn: " + name + " (left, right, around, or degrees)");
			}
		} else {
			degrees = args.checkNumber(0);
		}
		String why = body.turn(degrees);
		if (why != null) {
			return Result.fail(why);
		}
		power.charge(TURN_COST);
		state(TURNING);
		return Result.ok(true);
	}

	@Method(doc = "stop(): stops walking or turning, where it is (its event: stopped, when it was)")
	public Result stop(Arguments args) {
		body.stop();
		if (!state.equals(IDLE)) {
			event("stopped", null);
		}
		return Result.ok();
	}

	@Method(doc = "get(): {state}: what it's doing now (idle, walking or turning)")
	public Result get(Arguments args) {
		Map<String, Object> now = new LinkedHashMap<>();
		now.put("state", state);
		return Result.ok(now);
	}

	@Method(doc = "set(action): does an action, as a table: {action = \"walk\", forward, right, up}, {action = \"turn\", way}, or {action = \"stop\"}")
	public Result set(Arguments args) {
		Object value = args.get(0);
		if (!(value instanceof Map<?, ?> action)) {
			return Result.fail("an action table expected");
		}
		java.util.List<Object> rest = new java.util.ArrayList<>();
		return switch (String.valueOf(action.get("action"))) {
			case "walk" -> {
				rest.add(number(action.get("forward")));
				rest.add(number(action.get("right")));
				rest.add(number(action.get("up")));
				yield walk(new Arguments(rest));
			}
			case "turn" -> {
				rest.add(action.get("way"));
				yield turn(new Arguments(rest));
			}
			case "stop" -> stop(new Arguments(rest));
			default -> Result.fail("not an action: " + action.get("action") + " (walk, turn or stop)");
		};
	}

	private static Object number(Object value) {
		return value == null ? 0L : value;
	}

	@Method(doc = "pull(): its oldest event, {event} (arrived, turned, stopped) or {event = \"blocked\", why}, or nil")
	public Result pull(Arguments args) {
		return Result.ok(events.pull());
	}

	/** The body walked {@code blocks} more (its cost). */
	public void walked(double blocks) {
		power.charge((int) Math.ceil(blocks * WALK_COST));
	}

	/** The body got where it was walking to. */
	public void arrived() {
		event("arrived", null);
	}

	/** The body can't go on walking ({@code why}: no way there, stuck). */
	public void blocked(String why) {
		event("blocked", why);
	}

	/** The body finished turning. */
	public void turned() {
		event("turned", null);
	}

	/** What it's doing now. */
	public String state() {
		return state;
	}

	private void event(String name, String why) {
		Map<String, Object> event = new LinkedHashMap<>();
		event.put("event", name);
		if (why != null) {
			event.put("why", why);
		}
		state(IDLE);
		events.add(event);
	}

	private void state(String now) {
		if (!state.equals(now)) {
			state = now;
			changed();
		}
	}

	private void changed() {
		onChange.run();
	}

	@Override
	public void onChange(Runnable changed) {
		this.onChange = changed;
	}

	@Override
	public BusPower power() {
		return power;
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

	/** It takes data only while a program runs on its board: else its events go, and it stops. */
	@Override
	public void accepting(boolean accepting) {
		this.accepting = accepting;
		if (accepting && interrupted) {
			interrupted = false;
			blocked(INTERRUPTED);
		}
		if (!accepting) {
			events.clear();
			body.stop();
			state(IDLE);
		}
	}

	@Override
	public void powerOff() {
		accepting(false);
	}

	/**
	 * Its events waiting, and what it was doing: a walk or turn under way doesn't go on after a load
	 * (the game keeps no path), so it ends then, blocked, and a program waiting on it hears.
	 */
	@Override
	public Map<String, Object> saved() {
		Map<String, Object> saved = new LinkedHashMap<>();
		events.save(saved);
		saved.put("state", state);
		return saved;
	}

	/** Why a walk or turn under way at a save ended at the load. */
	public static final String INTERRUPTED = "interrupted";

	@Override
	public void restore(Map<String, Object> saved) {
		events.restore(saved);
		// (its event comes as the program goes on: it takes data then)
		interrupted = saved.get("state") instanceof String was && !was.equals(IDLE);
	}
}
