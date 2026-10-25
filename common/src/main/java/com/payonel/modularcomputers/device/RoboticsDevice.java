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
 * arrived}, {@code blocked} and why, {@code turned}, {@code stopped}. It senses the six spaces round
 * it, named in its own frame ({@link #FACES}: it knows no compass), and works with what's in its
 * hand: uses it on a space (places a block, opens a door, presses a button), breaks a block with it
 * (in a player's time: {@code broke}, or {@code failed} and why), and moves it in and out of a
 * container. Its state is what it's doing now. A body ({@link Body}: a golem) does the work, and
 * tells it how it went. Every block walked, and every action, costs power.
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
	public static final String BREAKING = "breaking";
	/** What an action with its hand costs (a use, a swing, a move to or from a container): a running CPU's tick. */
	public static final int ACTION_COST = PowerUnits.CPU;
	/** The spaces round it, in its own frame: its left and right its own, as it turns and walks. */
	public static final java.util.List<String> FACES = java.util.List.of("front", "back", "left", "right", "up", "down");
	/** Where a container is when no face is given: in front of it. */
	public static final String CONTAINER_FACE = "front";

	/** What does the moving: a robot's body in the world. */
	public interface Body {
		/**
		 * Starts walking to the point {@code forward}, {@code right} and {@code up} blocks from it, in
		 * its own frame; null when it started, else why not.
		 */
		String walk(double forward, double right, double up);

		/** Starts turning {@code degrees} (to its right; negative: its left); null, or why not. */
		String turn(double degrees);

		/** Stops at once, where it is (a block it was breaking left as it is). */
		void stop();

		/** What's in the space at {@code face} (one of {@link #FACES}), or in all six by face (null). */
		default Result scan(String face) {
			return Result.fail("no body");
		}

		/** What its hand holds (an item's table; {@code off}: its off hand), or nil. */
		default Result held(boolean off) {
			return Result.fail("no body");
		}

		/** Swaps what its hands hold. */
		default Result swap() {
			return Result.fail("no body");
		}

		/** Uses what it holds on the space at {@code face}, as a player's right click: what it did, or false and why. */
		default Result use(String face) {
			return Result.fail("no body");
		}

		/** Swings what it holds at {@code face}: "attacked" (a mob there), "breaking" (the block there: its event, broke or failed), or false and why. */
		default Result swing(String face) {
			return Result.fail("no body");
		}

		/** What the container at {@code face} holds: its slots with items in, each an item's table and its slot. */
		default Result contents(String face) {
			return Result.fail("no body");
		}

		/**
		 * Puts up to {@code count} of what it holds into the container at {@code face} (its slot
		 * {@code to}, from 1; 0: where it fits): true and 0 when none is left, else false and how many are.
		 */
		default Result store(String face, int count, int to) {
			return Result.fail("no body");
		}

		/** Takes up to {@code count} from the container at {@code face} (its slot {@code slot}, from 1; 0: the first it can) into its hand: how many came. */
		default Result take(String face, int slot, int count) {
			return Result.fail("no body");
		}
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

	@Method(doc = "scan([face]): what's in the space at a face (front, back, left, right, up or down: its own), or with none, in all six by face: {kind = \"air\"}, {kind = \"block\", name, state, hardness, solid, container}, {kind = \"fluid\", name, amount, source}, or {kind = \"entity\", name, entities, block}")
	public Result scan(Arguments args) {
		String face = args.count() > 0 && args.get(0) != null ? face(args, 0) : null;
		return body.scan(face);
	}

	@Method(doc = "held([hand]): what its hand holds (\"off\": its off hand, where it catches what it breaks with nowhere else to put it), {name, count, damage, maxDamage, label}, or nil")
	public Result held(Arguments args) {
		Object hand = args.count() > 0 ? args.get(0) : null;
		if (hand != null && !"off".equals(hand) && !"main".equals(hand)) {
			throw new Arguments.ArgumentError("a hand expected (main or off), got " + hand);
		}
		return body.held("off".equals(hand));
	}

	@Method(doc = "swap(): swaps what its hands hold (its main hand works; its off hand catches), as a player's swap key")
	public Result swap(Arguments args) {
		return body.swap();
	}

	@Method(doc = "use(face): uses what it holds on the space at a face, as a player's right click: places a block, opens a door, presses a button, uses a tool or a bucket; what it did (\"placed\", \"used\"), or false and why")
	public Result use(Arguments args) {
		String face = face(args, 0);
		Result result = body.use(face);
		power.charge(ACTION_COST);
		return result;
	}

	@Method(doc = "swing(face): swings what it holds at a face, as a player's left click: a mob there is hit (\"attacked\", as hard as a player's swing: full strength once the weapon has recovered); else the block there starts breaking, in a player's time (a pickaxe for stone, an axe for wood): \"breaking\" (its event: broke, with the block's name and whether it kept all it dropped, its storage first, else its off hand, kept; or failed and why); or false and why")
	public Result swing(Arguments args) {
		String face = face(args, 0);
		Result result = body.swing(face);
		if (!result.failed()) {
			power.charge(ACTION_COST);
			if (BREAKING.equals(result.first())) {
				state(BREAKING);
			}
		}
		return result;
	}

	@Method(doc = "contents([face]): what the container at a face (in front when none is given) holds: a list of its slots with something in, each {slot, name, count, ...}")
	public Result contents(Arguments args) {
		return body.contents(args.count() > 0 && args.get(0) != null ? face(args, 0) : CONTAINER_FACE);
	}

	@Method(doc = "store([face][, count[, to]]): puts what it holds (or count of it) into the container at a face (in front when none is given), into its slot to or where it fits: true, 0 when none of it is left in its hand; false and how many are, when the container's full; or false and why (no container there)")
	public Result store(Arguments args) {
		int at = args.count() > 0 && args.get(0) instanceof String ? 1 : 0;
		String face = at == 1 ? face(args, 0) : CONTAINER_FACE;
		int count = args.count() > at && args.get(at) != null ? count(args, at) : Integer.MAX_VALUE;
		int to = args.count() > at + 1 && args.get(at + 1) != null ? count(args, at + 1) : 0;
		power.charge(ACTION_COST);
		return body.store(face, count, to);
	}

	@Method(doc = "take([face][, slot[, count]]): takes items from the container at a face (in front when none is given) into its hand (from a slot, or the first it can; all it can hold, or count): how many came (0: none it could), or false and why")
	public Result take(Arguments args) {
		int at = args.count() > 0 && args.get(0) instanceof String ? 1 : 0;
		String face = at == 1 ? face(args, 0) : CONTAINER_FACE;
		int slot = args.count() > at && args.get(at) != null ? count(args, at) : 0;
		int count = args.count() > at + 1 && args.get(at + 1) != null ? count(args, at + 1) : Integer.MAX_VALUE;
		power.charge(ACTION_COST);
		return body.take(face, slot, count);
	}

	/** The face named by argument {@code n}: one of {@link #FACES}. */
	private static String face(Arguments args, int n) {
		Object name = args.get(n);
		if (!(name instanceof String face) || !FACES.contains(face)) {
			throw new Arguments.ArgumentError("a face expected (front, back, left, right, up or down), got " + name);
		}
		return face;
	}

	/** A positive whole number, argument {@code n}. */
	private static int count(Arguments args, int n) {
		double value = args.checkNumber(n);
		if (value < 1 || value != Math.floor(value)) {
			throw new Arguments.ArgumentError("a count of 1 or more expected, got " + args.get(n));
		}
		return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
	}

	@Method(doc = "get(): {state}: what it's doing now (idle, walking, turning or breaking)")
	public Result get(Arguments args) {
		Map<String, Object> now = new LinkedHashMap<>();
		now.put("state", state);
		return Result.ok(now);
	}

	@Method(doc = "set(action): does an action, as a table: {action = \"walk\", forward, right, up}, {action = \"turn\", way}, {action = \"use\", face}, {action = \"swing\", face}, or {action = \"stop\"}")
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
			case "use", "swing" -> {
				rest.add(action.get("face"));
				yield action.get("action").equals("use") ? use(new Arguments(rest)) : swing(new Arguments(rest));
			}
			case "stop" -> stop(new Arguments(rest));
			default -> Result.fail("not an action: " + action.get("action") + " (walk, turn, use, swing or stop)");
		};
	}

	private static Object number(Object value) {
		return value == null ? 0L : value;
	}

	@Method(doc = "pull(): its oldest event, {event} (arrived, turned, stopped), {event = \"broke\", block, kept, dropped}, {event = \"blocked\" or \"failed\", why}, or nil")
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

	/**
	 * The body broke the block it was breaking ({@code block}: its name); {@code dropped}: how many of
	 * what it dropped stayed in the world (no room in its storage, nor its off hand).
	 */
	public void broke(String block, int dropped) {
		Map<String, Object> event = new LinkedHashMap<>();
		event.put("event", "broke");
		event.put("block", block);
		event.put("kept", dropped == 0);
		if (dropped > 0) {
			event.put("dropped", dropped);
		}
		state(IDLE);
		events.add(event);
	}

	/** The body couldn't go on breaking ({@code why}: the block changed, the tool went). */
	public void failed(String why) {
		event("failed", why);
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
