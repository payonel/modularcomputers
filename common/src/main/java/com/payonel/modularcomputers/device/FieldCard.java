package com.payonel.modularcomputers.device;

import com.payonel.modularcomputers.bus.BusPower;
import com.payonel.modularcomputers.bus.InterruptLine;
import com.payonel.modularcomputers.power.PowerUnits;
import com.payonel.modularcomputers.runtime.Arguments;
import com.payonel.modularcomputers.runtime.Method;
import com.payonel.modularcomputers.runtime.Result;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An energy field card (user design, 2026-10-24): it suspends a creature or an item in a sphere of
 * energy, where it can't act (it falls in place: no moving, attacking or reacting), moves it, and lets
 * it go. It never moves blocks. Positions are in its computer's frame: {@code forward} out of its
 * front, {@code right}, {@code up} (a block's own right, as every face is; a
 * robot's its own), so a program written for one computer works however it's turned. The
 * field stays within {@link #RANGE} of its computer, but a robot holding itself carries the card
 * with it: in a golem it's how it flies. Holding costs power each tick, a move each block; without
 * power, or with no program running, it lets go. A {@link Projector} does the work in the world.
 * <p>
 * It's buffered ({@link Buffered}): {@code capture}, {@code move} and {@code release} are commands
 * (a release waits for a move under way, the user's: one door, a stop is a stop); a move runs until
 * it's arrived or blocked, its end its event ({@code move}). {@code scan}, {@code get}, {@code pull}
 * and {@code stop} are free: a stop ends a move under way at once, stopped, holding on; to let go at
 * once, stop then release.
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

		/** Moves the field by this much, in the computer's frame (within its reach: {@link #beyondReach}): true, or false and why. */
		Result move(double forward, double right, double up);

		/** Whether a move this much would take what it holds past its reach: the face of it that meets its edge, or null (within it). */
		default String beyondReach(double forward, double right, double up) {
			return null;
		}

		/** Whether what it holds is past its reach now: the face of it past the edge, or null (within it, or nothing held). */
		default String outOfReach() {
			return null;
		}

		/** Lets what it holds go. */
		void release();

		/** Stops a move under way, holding what it holds where it is. */
		void stop();

		/** What it holds, last known: {name, id, at = {forward, right, up}} in its computer's frame (null: nothing held). */
		default Map<String, Object> lastKnown() {
			return null;
		}

		/** What it holds isn't loaded now (its hold kept, nothing met): the face a move this way is blocked on; null: it's loaded, or nothing's held. */
		default String unloadedFace(double forward, double right, double up) {
			return null;
		}

		/** What it holds and where, {name, forward, right, up}, or null while it holds nothing. */
		Map<String, Object> where();

		/** Why it's lost what it held (its field gone: lost; out of reach: blocked), or null: asked each tick while it holds. */
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
		return java.util.Set.of("scan", "get", "pull", "stop");
	}

	@Override
	public String refusal(String method) {
		return busy() ? BUSY : null;
	}

	/** A move under way (its only command that runs over time). */
	@Override
	public boolean busy() {
		return state.equals(MOVING);
	}

	/** Each server tick, powered or not: a field held costs its power, and with none it lets go. */
	public void tick(boolean powered) {
		if (state.equals(IDLE)) {
			return;
		}
		if (!powered) {
			lost(POWEROUT);
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

	@Method(doc = "scan([range]): what's within range (16 blocks at most) of its computer, nearest first: a list of things, each {kind = \"entity\", name, id, at}: what it is (its registry id: minecraft:pig, an item's name), its own id (for capture), where it is, {forward, right, up} in its computer's frame (forward out of its front); as a robot's scan gives things")
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
			// (a point as a list: forward, right, up)
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

	@Method(doc = "move(forward[, right[, up]]): moves its field, and what it holds, by that much in its computer's frame, never through blocks. A command: true at once (its event: move, ok, or not and why: blocked, with name, the block in the way (\"\" for none: past its reach, or what it holds not loaded), and face, the side of what it holds that met it, in its computer's frame; lost; powerout; stopped; each with its call), or false and why it can't start (not holding)")
	public Result move(Arguments args) {
		Result no = noProjector();
		if (no != null) {
			return no;
		}
		if (state.equals(IDLE)) {
			return Result.fail("not holding");
		}
		double forward = args.checkNumber(0);
		double right = args.count() > 1 ? args.checkNumber(1) : 0;
		double up = args.count() > 2 ? args.checkNumber(2) : 0;
		String beyond = projector.beyondReach(forward, right, up); // (past its reach: blocked at once, not moved, the user's; no block to name)
		Result started = beyond != null ? Result.ok(true) : projector.move(forward, right, up);
		if (!started.failed()) {
			moveArgs = "move".equals(calledMethod) ? calledArgs : List.of();
			movedFrom = projector.where();
			state(MOVING);
			String unloaded = beyond != null ? beyond : projector.unloadedFace(forward, right, up);
			if (unloaded != null) {
				blocked(null, unloaded); // (past its reach, or what it holds isn't loaded: no one block to name, the user's)
			}
		}
		return started;
	}

	@Method(doc = "release(): lets what it holds go (it falls from where it is). A command: after a move under way (to let go at once, stop first); true")
	public Result release(Arguments args) {
		if (projector != null && !state.equals(IDLE)) {
			projector.release();
		}
		state(IDLE);
		return Result.ok();
	}

	@Method(doc = "stop(): ends a move under way at once, where it is (its event: move, not ok, stopped), holding on to what it holds; never waits: true")
	public Result stop(Arguments args) {
		if (state.equals(MOVING)) {
			if (projector != null) {
				projector.stop();
			}
			state(HOLDING);
			events.stopped("move", call());
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

	@Method(doc = "pull(): its oldest event, or nil: a move's end, {event = \"move\", ok, why}, or a notice of its letting go on its own: {event = \"notice\", why = \"lost\", name, id, at} (what it held is gone), {event = \"notice\", why = \"blocked\", name = \"\", face} (out of its reach: the side of what it held past the edge), {event = \"notice\", why = \"powerout\"}")
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

	/** Its field's move was stopped by a block ({@code block}: its id, null when none can be named; {@code face}: the face of what it holds that met it, in its computer's frame). */
	public void blocked(String block, String face) {
		if (state.equals(MOVING)) {
			state(HOLDING);
			events.blocked("move", block, face, call());
		}
	}

	/** It lost what it held ({@code why}): it holds nothing now. */
	public void lost(String why) {
		if (state.equals(IDLE)) {
			return;
		}
		boolean wasMoving = state.equals(MOVING);
		Map<String, Object> last = projector != null ? projector.lastKnown() : null; // (before it lets go: what it held, and where)
		Map<String, Object> call = wasMoving ? call() : null;
		String face = BLOCKED.equals(why) && projector != null ? projector.outOfReach() : null; // (before it lets go: the side of it past its reach)
		if (projector != null) {
			projector.release();
		}
		state(IDLE);
		if (LOST.equals(why)) {
			// What it held is gone (the user's: lost): what it was, its id, where it last was.
			String name = last == null ? "" : String.valueOf(last.get("name"));
			String id = last == null ? "" : String.valueOf(last.get("id"));
			@SuppressWarnings("unchecked")
			Map<String, Object> at = last == null ? Map.of("forward", 0L, "right", 0L, "up", 0L) : (Map<String, Object>) last.get("at");
			if (wasMoving) {
				events.lost("move", name, id, at, call); // (the move it was making ends with it)
			}
			events.lostNotice(name, id, at);
			return;
		}
		if (BLOCKED.equals(why)) {
			// Out of its reach (the user's: blocked): no block to name, the side of what it held past the edge.
			if (wasMoving) {
				events.blocked("move", null, face, call); // (the move it was making ends with it)
			}
			events.blockedNotice(null, face);
			return;
		}
		// Its power gone (the user's: powerout).
		if (wasMoving) {
			events.powerout("move", call); // (the move it was making ends with it)
		}
		events.poweroutNotice();
	}

	/** The last command the board gave it and its arguments (the board's {@link #called}); the move under way's, and where what it holds was as it began. */
	private String calledMethod;
	private List<Object> calledArgs = List.of();
	private List<Object> moveArgs = List.of();
	private Map<String, Object> movedFrom;

	@Override
	public void called(String method, List<Object> args) {
		calledMethod = method;
		calledArgs = EventQueue.given(args);
	}

	/** The move under way's call: its arguments as given, and {forward, right, up} it moved, in its computer's frame. */
	private Map<String, Object> call() {
		Object progress = List.of(0L, 0L, 0L);
		Map<String, Object> now = projector == null ? null : projector.where();
		if (movedFrom != null && now != null) {
			progress = List.of(moved(now, "forward"), moved(now, "right"), moved(now, "up"));
		}
		return EventQueue.call(moveArgs, progress);
	}

	private Object moved(Map<String, Object> now, String axis) {
		double by = ((Number) now.get(axis)).doubleValue() - ((Number) movedFrom.get(axis)).doubleValue();
		double kept = Math.rint(by * 10) / 10; // (to a tenth, as its positions are)
		return kept == Math.rint(kept) ? (Object) (long) kept : kept;
	}

	/** What it's doing now. */
	public String state() {
		return state;
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

	/** No program runs on its computer: it lets go, and its events go (but what a load brought, its hold too, at its first reset). */
	@Override
	protected void dropData() {
		if (!events.clear()) {
			return; // (a load's: its program goes on)
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

	/** Its events waiting, and its hold (its record's id: the hold itself is the world's, {@code FieldHolds}) and a move under way: they go on after a load (the user's). */
	@Override
	public Map<String, Object> saved() {
		Map<String, Object> saved = new LinkedHashMap<>();
		events.save(saved);
		saved.put("state", state);
		if (hold != null) {
			saved.put("hold", hold.toString());
		}
		if (state.equals(MOVING)) {
			saved.put("args", new java.util.ArrayList<>(moveArgs));
		}
		return saved;
	}

	@Override
	public void restore(Map<String, Object> saved) {
		events.restore(saved);
		state = saved.get("state") instanceof String was ? was : IDLE;
		hold = saved.get("hold") instanceof String id ? java.util.UUID.fromString(id) : null;
		moveArgs = saved.get("args") instanceof List<?> args ? List.copyOf(new java.util.ArrayList<Object>(args)) : List.of();
		if (hold == null) {
			state = IDLE;
		}
	}

	/** Its hold: its record's id in the world's {@code FieldHolds} (null: it holds nothing). */
	private java.util.UUID hold;

	public java.util.UUID hold() {
		return hold;
	}

	public void hold(java.util.UUID hold) {
		this.hold = hold;
	}
}
