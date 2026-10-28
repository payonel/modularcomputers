package com.payonel.modularcomputers.device;

import com.payonel.modularcomputers.bus.BusPower;
import com.payonel.modularcomputers.bus.InterruptLine;
import com.payonel.modularcomputers.power.PowerUnits;
import com.payonel.modularcomputers.runtime.Arguments;
import com.payonel.modularcomputers.runtime.Method;
import com.payonel.modularcomputers.runtime.Result;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An energy field card (user design, 2026-10-24): it suspends a creature or an item in a sphere of
 * energy, where it can't act (it falls in place: no moving, attacking or reacting), moves it, and lets
 * it go. It never moves blocks. Positions are in its computer's frame: {@code forward} out of its
 * front, {@code right}, {@code up} (a block's right as someone facing its front sees it, as every
 * face is; a robot's its own), so a program written for one computer works however it's turned. The
 * field stays within {@link #RANGE} of its computer, but a robot holding itself carries the card
 * with it: in a golem it's how it flies. Holding costs power each tick, a move each block; without
 * power, or with no program running, it lets go. A {@link Projector} does the work in the world.
 * <p>
 * It's buffered ({@link Buffered}): {@code capture} and {@code move} are commands; a move runs
 * until it's arrived or blocked, its end its event ({@code move}). {@code scan}, {@code get},
 * {@code pull} and {@code release} are free: a program can always let go, and a move under way then
 * ends, stopped. {@code set} is free and refuses its own actions but a release, as they would be.
 */
public final class FieldCard extends Card implements Buffered {
	/** How far from its computer it reaches, in blocks: what it finds, holds, and moves to. */
	public static final double RANGE = 16;
	/** How near a point given to capture something must be, in blocks. */
	public static final double REACH = 1.5;
	/** What holding costs, a tick: two running CPUs'. */
	public static final int HOLD_COST = 2 * PowerUnits.CPU;
	/** What moving what it holds costs, a block: a running CPU's tick. */
	public static final int MOVE_COST = PowerUnits.CPU;
	public static final String IDLE = "idle";
	public static final String HOLDING = "holding";
	public static final String MOVING = "moving";
	/** What capture takes to hold its own robot. */
	public static final String SELF = "self";

	/** What makes the field in the world, round what the card's computer points it at. */
	public interface Projector {
		/** What's within {@code range} of the computer: {id, name, forward, right, up, distance} each, nearest first. */
		Result scan(double range);

		/**
		 * Holds the entity with this id (from scan), or the nearest within {@link #REACH} of the point
		 * {@code at} (forward, right, up), or its own robot ({@link #SELF}): its name, or false and why.
		 */
		Result capture(String id, double[] at);

		/** Moves the field by this much, in the computer's frame: true, or false and why (too far). */
		Result move(double forward, double right, double up);

		/** Lets what it holds go. */
		void release();

		/** What it holds and where, {name, forward, right, up}, or null while it holds nothing. */
		Map<String, Object> where();

		/** Why it's lost what it held (its field gone, out of reach), or null: asked each tick while it holds. */
		String check();
	}

	private final InterruptLine line = new InterruptLine();
	private final EventQueue events = new EventQueue(line, this::changed, this::isAccepting);
	private final BusPower power = new BusPower(0);
	private Projector projector;
	private String state = IDLE;
	/** Blocks moved since last paid for. */
	private double moved;

	public FieldCard() {
		super(CardKind.ENERGY_FIELD);
	}

	/** What makes its fields. */
	public void projectorIn(Projector projector) {
		this.projector = projector;
	}

	private Result noProjector() {
		return projector == null ? Result.fail("no projector") : null;
	}

	@Override
	public java.util.Set<String> free() {
		return java.util.Set.of("scan", "get", "pull", "release", "set");
	}

	@Override
	public String refusal(String method) {
		return state.equals(MOVING) ? BUSY : null;
	}

	/** Each server tick, powered or not: a field held costs its power, and with none it lets go. */
	public void tick(boolean powered) {
		if (state.equals(IDLE)) {
			return;
		}
		if (!powered) {
			lost("no power");
			return;
		}
		String why = projector != null ? projector.check() : null;
		if (why != null) {
			lost(why);
			return;
		}
		power.charge(HOLD_COST);
	}

	/** What it holds moved {@code blocks} (paid for as it goes). */
	public void movedBy(double blocks) {
		moved += blocks;
		if (moved >= 1) {
			power.charge((int) Math.floor(moved) * MOVE_COST);
			moved -= Math.floor(moved);
		}
	}

	@Method(doc = "scan([range]): what's within range (16 blocks at most) of its computer: a list, nearest first, each {id, name, forward, right, up, distance}: its id (for capture), what it is (minecraft:pig, an item's name), where it is in its computer's frame (forward out of its front, right, up)")
	public Result scan(Arguments args) {
		Result no = noProjector();
		if (no != null) {
			return no;
		}
		double range = args.count() > 0 && args.get(0) != null ? args.checkNumber(0) : RANGE;
		return projector.scan(Math.min(RANGE, Math.max(0, range)));
	}

	@Method(doc = "capture(target): suspends a creature or an item in its field, where it can't act: an id from scan, \"self\" (a robot holding itself), or a point, forward[, right[, up]], the nearest within a block and a half of it; what it holds (its name), or false and why")
	public Result capture(Arguments args) {
		Result no = noProjector();
		if (no != null) {
			return no;
		}
		if (!state.equals(IDLE)) {
			return Result.fail("holding");
		}
		Result held;
		if (args.get(0) instanceof String id) {
			held = projector.capture(id, null);
		} else if (args.get(0) instanceof java.util.List<?> point) {
			// (a point as a list: what set's capture takes)
			return capture(new Arguments(new java.util.ArrayList<>(point)));
		} else {
			double[] at = {args.checkNumber(0), args.count() > 1 ? args.checkNumber(1) : 0, args.count() > 2 ? args.checkNumber(2) : 0};
			held = projector.capture(null, at);
		}
		if (!held.failed()) {
			state(HOLDING);
		}
		return held;
	}

	@Method(doc = "move(forward[, right[, up]]): moves its field, and what it holds, by that much in its computer's frame, ten blocks a second, never through blocks. A command: true at once (its event: move, ok, or not and why: blocked: the block), or false and why it can't start (not holding, too far)")
	public Result move(Arguments args) {
		Result no = noProjector();
		if (no != null) {
			return no;
		}
		if (state.equals(IDLE)) {
			return Result.fail("not holding");
		}
		Result started = projector.move(args.checkNumber(0), args.count() > 1 ? args.checkNumber(1) : 0, args.count() > 2 ? args.checkNumber(2) : 0);
		if (!started.failed()) {
			state(MOVING);
		}
		return started;
	}

	@Method(doc = "release(): lets what it holds go (it falls from where it is); never waits: a move under way ends, stopped")
	public Result release(Arguments args) {
		boolean wasMoving = state.equals(MOVING);
		if (projector != null && !state.equals(IDLE)) {
			projector.release();
		}
		state(IDLE);
		if (wasMoving) {
			events.ended("move", false, "stopped", null);
		}
		return Result.ok();
	}

	@Method(doc = "get(): {state, name, forward, right, up}: what it's doing (idle, holding or moving), and what it holds and where, in its computer's frame")
	public Result get(Arguments args) {
		Map<String, Object> now = new LinkedHashMap<>();
		now.put("state", state);
		if (projector != null && !state.equals(IDLE)) {
			Map<String, Object> where = projector.where();
			if (where != null) {
				now.putAll(where);
			}
		}
		return Result.ok(now);
	}

	@Method(doc = "set(action): does an action, as a table: {action = \"capture\", target}, {action = \"move\", forward, right, up}, or {action = \"release\"} (its file's lines are these)")
	public Result set(Arguments args) {
		if (!(args.get(0) instanceof Map<?, ?> action)) {
			return Result.fail("not a table");
		}
		java.util.List<Object> rest = new java.util.ArrayList<>();
		String name = String.valueOf(action.get("action"));
		if (!name.equals("release") && refusal(name) != null) {
			return Result.fail(refusal(name)); // (set is free, so a release goes through; its other actions are commands)
		}
		return switch (name) {
			case "capture" -> {
				rest.add(action.get("target"));
				yield capture(new Arguments(rest));
			}
			case "move" -> {
				for (String key : new String[] {"forward", "right", "up"}) {
					rest.add(action.get(key) == null ? 0L : action.get(key));
				}
				yield move(new Arguments(rest));
			}
			case "release" -> release(new Arguments(rest));
			default -> Result.fail("not an action: " + action.get("action"));
		};
	}

	@Method(doc = "pull(): its oldest event, or nil: a move's end, {event = \"move\", ok, why}, or {event = \"lost\", why} (what it held is gone, out of reach, or its power is)")
	public Result pull(Arguments args) {
		return Result.ok(events.pull());
	}

	/** Its field got where it was moving. */
	public void arrived() {
		if (state.equals(MOVING)) {
			state(HOLDING);
			events.ended("move", true, null, null);
		}
	}

	/** Its field's move was stopped. */
	public void blocked(String why) {
		if (state.equals(MOVING)) {
			state(HOLDING);
			events.ended("move", false, why, null);
		}
	}

	/** It lost what it held ({@code why}): it holds nothing now. */
	public void lost(String why) {
		if (state.equals(IDLE)) {
			return;
		}
		boolean wasMoving = state.equals(MOVING);
		if (projector != null) {
			projector.release();
		}
		state(IDLE);
		if (wasMoving) {
			events.ended("move", false, why, null); // (the move it was making ends with it)
		}
		event("lost", why);
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
		events.add(event);
	}

	private void state(String now) {
		if (!state.equals(now)) {
			state = now;
			changed();
		}
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

	/** No program runs on its computer: it lets go, and its events go (but those a load brought, at its first reset). */
	@Override
	protected void dropData() {
		if (events.clear()) {
			interrupted = false;
			interruptedMove = false;
		}
		if (projector != null && !state.equals(IDLE)) {
			projector.release();
		}
		state(IDLE);
	}

	@Override
	public void powerOff() {
		dropData();
	}

	/** Its events waiting (a field isn't saved: one held at a save is gone at the load, and its event says so). */
	@Override
	public Map<String, Object> saved() {
		Map<String, Object> saved = new LinkedHashMap<>();
		events.save(saved);
		saved.put("holding", !state.equals(IDLE));
		saved.put("moving", state.equals(MOVING));
		return saved;
	}

	/** Why a field held at a save is gone at the load (and a move it was making ended). */
	public static final String INTERRUPTED = "interrupted";
	private boolean interrupted;
	private boolean interruptedMove;

	@Override
	public void restore(Map<String, Object> saved) {
		events.restore(saved);
		interrupted = Boolean.TRUE.equals(saved.get("holding"));
		interruptedMove = Boolean.TRUE.equals(saved.get("moving"));
	}

	@Override
	public void accepting(boolean accepting) {
		super.accepting(accepting);
		if (accepting && interrupted) {
			interrupted = false;
			if (interruptedMove) { // (the move it was making ends first, as a live loss's does)
				interruptedMove = false;
				events.ended("move", false, INTERRUPTED, null);
			}
			event("lost", INTERRUPTED);
		}
	}
}
