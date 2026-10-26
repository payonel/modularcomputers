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
 * container. It flies on its thrusters (the user's design, 2026-10-25): {@code fly} and {@code hover}
 * leave its legs standing where it is and fire beams from their sockets (no tools, and it can be
 * shoved, while it does), {@code land} brings it down onto its legs. Its state is what it's doing
 * now. A body ({@link Body}: a golem) does the work, and tells it how it went. Every block walked,
 * every action and every tick its thrusters fire costs power.
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
	public static final String FLYING = "flying";
	public static final String HOVERING = "hovering";
	public static final String LANDING = "landing";
	/** What its thrusters cost, each tick they fire (flying or hovering): five running CPUs' ticks. */
	public static final int THRUST_COST = 5 * PowerUnits.CPU;
	/** The farthest a flight may go, in blocks each way. */
	public static final double MAX_FLIGHT = 64;
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

		/**
		 * Starts flying on its thrusters to the point {@code forward}, {@code right} and {@code up}
		 * blocks from it, in its own frame, its legs left standing where it was if it stood on them:
		 * null when it started, else why not.
		 */
		default String fly(double forward, double right, double up) {
			return "no thrusters";
		}

		/** Starts walking to the point {@code forward}, {@code right}, {@code up} from its origin, in the origin's frame: null, or why not. */
		default String walkTo(double forward, double right, double up) {
			return "no position";
		}

		/** Starts flying to the point {@code forward}, {@code right}, {@code up} from its origin, in the origin's frame: null, or why not. */
		default String flyTo(double forward, double right, double up) {
			return "no position";
		}

		/** Where it is from its origin, in the origin's frame: {forward, right, up, heading}. */
		default Result position() {
			return Result.fail("no position");
		}

		/**
		 * Says where it is now (on its block's middle, facing the nearest right angle): {@code forward},
		 * {@code right}, {@code up} from its origin, facing {@code heading} (degrees to the origin's
		 * right, a right angle), its origin moved to make it so: null, or why not.
		 */
		default String setPosition(long forward, long right, long up, int heading) {
			return "no position";
		}

		/** Whether it finds its way round things (the game's pathfinder) or goes straight. */
		default boolean pathfinding() {
			return false;
		}

		default void pathfinding(boolean on) {
		}

		/** Holds itself where it is on its thrusters, its legs left standing if it stood on them: null, or why not. */
		default String hover() {
			return "no thrusters";
		}

		/** Starts coming down onto its legs, below it (its event: landed, or blocked): null, or why not. */
		default String land() {
			return "no thrusters";
		}

		/** Its thrusters stop: what they held up falls (the program that flew it ended). */
		default void release() {
		}

		/** Whether its thrusters hold it up, still (flying stopped: hovering). */
		default boolean hovering() {
			return false;
		}

		/** Whether it stands on its legs. */
		default boolean legs() {
			return true;
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

	@Method(doc = "walk(forward[, right[, up]]): walks to the block that many whole blocks from it, in its own frame (forward the way it faces, right to its right, up its elevation's change), onto its middle, facing as it was. Without pathfinding (the default) it goes straight, forward then right, without turning (it walks backwards, or sideways), stepping freely within its elevation (onto a slab, over carpet), up a block at a time (a jump) only while up is above, down at most 3 at a time only while up is below, never into lava, fire or deep water; with pathfinding it finds its way. True at once (its event: arrived, or blocked and why, on the last block's middle it reached), or false and why it can't start")
	public Result walk(Arguments args) {
		double[] at = whole(args);
		if (at == null) {
			return Result.fail("whole blocks expected");
		}
		if (Math.abs(at[0]) > MAX_WALK || Math.abs(at[1]) > MAX_WALK || Math.abs(at[2]) > MAX_WALK) {
			return Result.fail("too far: at most " + (int) MAX_WALK + " blocks each way");
		}
		String why = body.walk(at[0], at[1], at[2]);
		if (why != null) {
			return Result.fail(why);
		}
		state(WALKING);
		return Result.ok(true);
	}

	@Method(doc = "walkTo(forward[, right[, up]]): walks to the block that many whole blocks from its origin, in the origin's frame (as position gives them), as walk does; true at once (its event: arrived, or blocked and why), or false and why")
	public Result walkTo(Arguments args) {
		double[] at = whole(args);
		if (at == null) {
			return Result.fail("whole blocks expected");
		}
		String why = body.walkTo(at[0], at[1], at[2]);
		if (why != null) {
			return Result.fail(why);
		}
		state(WALKING);
		return Result.ok(true);
	}

	@Method(doc = "flyTo(forward[, right[, up]]): (a tier 2 golem's) flies to the point that many blocks from its origin, in the origin's frame (as position gives them), as fly does; true at once (its event: arrived, or blocked and why), or false and why")
	public Result flyTo(Arguments args) {
		double forward = args.checkNumber(0);
		double right = args.count() > 1 ? args.checkNumber(1) : 0;
		double up = args.count() > 2 ? args.checkNumber(2) : 0;
		String why = body.flyTo(forward, right, up);
		if (why != null) {
			return Result.fail(why);
		}
		state(FLYING);
		return Result.ok(true);
	}

	@Method(doc = "position(): where it is from its origin, in the origin's frame: {forward, right, up, heading} (blocks; heading in degrees to the origin's right: 0, 90, 180 or 270). The origin is set where it stands when it's first powered on, in each dimension it first comes to, and by setPosition; kept through reboots, shoves and fields, each dimension's its own; lost only when it's picked up")
	public Result position(Arguments args) {
		return body.position();
	}

	@Method(doc = "setPosition([forward[, right[, up[, heading]]]]): says where it is now, in whole blocks from its origin, facing heading (degrees to the origin's right: 0, 90, 180 or 270), its origin (in this dimension) moved to make it so: position then gives exactly these; all 0 when not given: its origin where it is. It moves onto its block's middle and turns to the nearest right angle first. (A place known in another frame, a GPS's fix say, gives position that frame.)")
	public Result setPosition(Arguments args) {
		long[] at = new long[3];
		for (int i = 0; i < at.length; i++) {
			double value = args.count() > i && args.get(i) != null ? args.checkNumber(i) : 0;
			if (value != Math.rint(value)) {
				return Result.fail("whole blocks expected, got " + args.get(i));
			}
			at[i] = (long) value;
		}
		double heading = args.count() > 3 && args.get(3) != null ? args.checkNumber(3) : 0;
		if (heading != Math.rint(heading) || Math.floorMod((long) heading, 90) != 0) {
			return Result.fail("a right angle expected (0, 90, 180 or 270), got " + args.get(3));
		}
		String why = body.setPosition(at[0], at[1], at[2], (int) Math.floorMod((long) heading, 360));
		return why == null ? Result.ok() : Result.fail(why);
	}

	@Method(doc = "setPathfinding(on): whether walk, fly, walkTo and flyTo find their way round things (the game's pathfinder: it turns as it goes, jumps and drops as it finds, and turns back as it arrives) or go straight (the default). Kept through reboots; off again when it's picked up")
	public Result setPathfinding(Arguments args) {
		body.pathfinding(args.checkBoolean(0));
		return Result.ok();
	}

	/** Arguments 0 to 2 (forward, right, up; right and up 0 when not given) as whole numbers, or null when one isn't. */
	private static double[] whole(Arguments args) {
		double[] at = new double[3];
		for (int i = 0; i < at.length; i++) {
			at[i] = args.count() > i && args.get(i) != null ? args.checkNumber(i) : 0;
			if (at[i] != Math.rint(at[i])) {
				return null;
			}
		}
		return at;
	}

	@Method(doc = "turn(way): turns a right angle in place, \"left\" or \"right\", or two, \"around\"; true at once (its event: turned), or false and why")
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
		} else {
			degrees = Double.NaN;
		}
		if (Double.isNaN(degrees)) {
			return Result.fail("not a way to turn: " + way + " (left, right or around)");
		}
		String why = body.turn(degrees);
		if (why != null) {
			return Result.fail(why);
		}
		power.charge(TURN_COST);
		state(TURNING);
		return Result.ok(true);
	}

	@Method(doc = "stop(): stops walking, turning, flying or landing, where it is (its event: stopped, when it was); flying, it hovers there")
	public Result stop(Arguments args) {
		body.stop();
		if (!state.equals(IDLE) && !state.equals(HOVERING)) {
			event("stopped", null);
		}
		if (body.hovering()) {
			state(HOVERING);
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

	@Method(doc = "fly(forward[, right[, up]]): (a tier 2 golem's) flies on its thrusters to the point that many blocks from it, in its own frame (from its block's middle), without turning, straight there (never through blocks; with pathfinding, round them); its legs, if it stands on them, are left standing where it was, and its thrusters fire beams from their sockets. Flying or hovering it uses no tools (buttons and doors yes) and can be shoved. True at once (its event: arrived, or blocked and why), or false and why")
	public Result fly(Arguments args) {
		double forward = args.checkNumber(0);
		double right = args.count() > 1 ? args.checkNumber(1) : 0;
		double up = args.count() > 2 ? args.checkNumber(2) : 0;
		if (Math.abs(forward) > MAX_FLIGHT || Math.abs(right) > MAX_FLIGHT || Math.abs(up) > MAX_FLIGHT) {
			return Result.fail("too far: at most " + (int) MAX_FLIGHT + " blocks each way");
		}
		String why = body.fly(forward, right, up);
		if (why != null) {
			return Result.fail(why);
		}
		state(FLYING);
		return Result.ok(true);
	}

	@Method(doc = "hover(): (a tier 2 golem's) holds itself where it is on its thrusters (its legs, if it stands on them, left standing there), until it flies, lands, or its program ends: then it falls, and powers off")
	public Result hover(Arguments args) {
		String why = body.hover();
		if (why != null) {
			return Result.fail(why);
		}
		state(HOVERING);
		return Result.ok(true);
	}

	@Method(doc = "land(): (a tier 2 golem's) comes down onto its legs, standing below it (within three blocks to the side), and onto them; true at once (its event: landed, or blocked and why), or false and why (no legs there: it goes on hovering)")
	public Result land(Arguments args) {
		String why = body.land();
		if (why != null) {
			return Result.fail(why);
		}
		state(LANDING);
		return Result.ok(true);
	}

	/** The body got down onto its legs. */
	public void landed() {
		event("landed", null);
	}

	/** Its thrusters fired a tick (flying or hovering): their cost. */
	public void thrusted() {
		power.charge(THRUST_COST);
	}

	/** The body hovers again (a flight given up, a landing blocked). */
	public void hovering() {
		state(HOVERING);
	}

	@Method(doc = "get(): {state, legs, pathfinding, position}: what it's doing now (idle, walking, turning, breaking, flying, hovering or landing), whether it stands on its legs, whether it finds its way round things, and where it is (as position gives it)")
	public Result get(Arguments args) {
		Map<String, Object> now = new LinkedHashMap<>();
		now.put("state", state);
		now.put("legs", body.legs());
		now.put("pathfinding", body.pathfinding());
		Result position = body.position();
		if (!position.failed()) {
			now.put("position", position.first());
		}
		return Result.ok(now);
	}

	@Method(doc = "set(action): does an action, as a table: {action = \"walk\", \"walkTo\", \"fly\" or \"flyTo\", forward, right, up}, {action = \"turn\", way}, {action = \"use\", face}, {action = \"swing\", face}, {action = \"pathfinding\", on}, or {action = \"hover\"}, {action = \"land\"}, {action = \"stop\"}, {action = \"setPosition\", forward, right, up, heading}")
	public Result set(Arguments args) {
		Object value = args.get(0);
		if (!(value instanceof Map<?, ?> action)) {
			return Result.fail("an action table expected");
		}
		java.util.List<Object> rest = new java.util.ArrayList<>();
		return switch (String.valueOf(action.get("action"))) {
			case "walk", "fly", "walkTo", "flyTo" -> {
				rest.add(number(action.get("forward")));
				rest.add(number(action.get("right")));
				rest.add(number(action.get("up")));
				Arguments to = new Arguments(rest);
				yield switch (String.valueOf(action.get("action"))) {
					case "walk" -> walk(to);
					case "fly" -> fly(to);
					case "walkTo" -> walkTo(to);
					default -> flyTo(to);
				};
			}
			case "setPosition" -> {
				rest.add(number(action.get("forward")));
				rest.add(number(action.get("right")));
				rest.add(number(action.get("up")));
				rest.add(number(action.get("heading")));
				yield setPosition(new Arguments(rest));
			}
			case "pathfinding" -> {
				rest.add(Boolean.TRUE.equals(action.get("on")));
				yield setPathfinding(new Arguments(rest));
			}
			case "hover" -> hover(new Arguments(rest));
			case "land" -> land(new Arguments(rest));
			case "turn" -> {
				rest.add(action.get("way"));
				yield turn(new Arguments(rest));
			}
			case "use", "swing" -> {
				rest.add(action.get("face"));
				yield action.get("action").equals("use") ? use(new Arguments(rest)) : swing(new Arguments(rest));
			}
			case "stop" -> stop(new Arguments(rest));
			default -> Result.fail("not an action: " + action.get("action") + " (walk, walkTo, fly, flyTo, turn, use, swing, hover, land, stop, setPosition or pathfinding)");
		};
	}

	private static Object number(Object value) {
		return value == null ? 0L : value;
	}

	@Method(doc = "pull(): its oldest event, {event} (arrived, turned, stopped, landed), {event = \"broke\", block, kept, dropped}, {event = \"blocked\" or \"failed\", why}, or nil")
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
			body.release(); // (its thrusters stop with its program: what they held up falls)
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
