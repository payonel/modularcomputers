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
 * without a GPS), turns in place, and stops. It senses the six spaces round it, named in its own
 * frame ({@link #FACES}: it knows no compass), and works with what's in its hand: uses it on a space
 * (places a block, opens a door, presses a button), breaks a block with it (in a player's time), and
 * moves it in and out of a container. It flies on its thrusters (the user's design, 2026-10-25):
 * {@code fly} leaves its legs standing where it is and fires beams from their sockets (no tools, and
 * it can be shoved, while it does), {@code land} brings it down onto its legs. A body ({@link Body}:
 * a golem) does the work, and tells it how it went. Every block walked, every action and every tick
 * its thrusters fire costs power.
 * <p>
 * It's buffered ({@link Buffered}): one command at a time. A command that runs over time (a walk,
 * a turn, a flight, a landing, a block's breaking) ends in its event, named after its method:
 * {@code {event = "walk", ok = false, why = "blocked: minecraft:oak_log front"}}. Its commands run
 * from its <b>plan</b>, where the last one left it: a golem shoved off it in the air holds its plan
 * (it flies back, raising {@code shoved}), and a positional command given while it's off its plan (a
 * shove, a stop mid-walk) sends it back first ({@code return}'s event), refused busy until it's
 * there; falling on its legs (a block gone from under it) it's busy till it lands, its plan following
 * it down ({@code fell}). Held in a field (any field) its robotics freezes ({@code frozen}, {@code thawed}): what it
 * was doing pauses, commands are refused {@code held}, and the field's carry moves its origin with
 * it. {@code get}, {@code pull}, {@code stop} and {@code set} are free ({@code set} refuses its own
 * actions, but a stop, while it's busy).
 */
public final class RoboticsDevice implements Device, Buffered {
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
	/** What it's doing while it goes back to its plan (a shove, a stop off it). */
	public static final String RETURNING = "returning";
	/** What it's doing while a field holds it: its robotics frozen. */
	public static final String HELD = "held";
	/** What it's doing while it falls on its legs (nothing it was asked: a block gone under it). */
	public static final String FALLING = "falling";
	/** The name of its own way back to its plan, as an event (not a method). */
	public static final String RETURN = "return";
	/** Why a command ended when a stop cut it short. */
	public static final String STOPPED = "stopped";
	/** The commands that act where it is: given while it's off its plan, it goes back first. */
	private static final java.util.Set<String> POSITIONAL = java.util.Set.of("turn", "land", "use", "swing", "scan", "contents", "store", "take",
		"drop", "pickup"); // (the last two a crafting card's in it: they act where it is too)
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

		/** Stops at once, where it is, no centering (a block it was breaking left as it is). */
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

		/** Where it really is from its origin, in the origin's frame: {forward, right, up, heading}. */
		default Result position() {
			return Result.fail("no position");
		}

		/** Its plan: where its last command left it, from its origin: {forward, right, up, heading}. */
		default Result planned() {
			return position();
		}

		/** How far its body is off its plan, {forward, right, up} (the origin's frame), or null when it's on it. */
		default Map<String, Object> drift() {
			return null;
		}

		/** Its plan becomes where it is now (a command ended there). */
		default void replan() {
		}

		/**
		 * Its plan becomes where it is, a command cut short (a stop, a load): on its legs the block it's
		 * nearest (a block's middle, a right angle: its plan's always so on the ground), in the air the
		 * point it's at; in a field, its plan as it was (the field's carry moves it).
		 */
		default void replanHere() {
			replan();
		}

		/** Whether it's falling on its legs, nothing it was asked (a block gone from under it). */
		default boolean falling() {
			return false;
		}

		/** Starts going back to its plan (walking, or in the air flying): null, or why not (its event: return). */
		default String returnToPlan() {
			return "no position";
		}

		/** A stop: it holds where it stopped (in the air: no flying back to its plan) until its next command. */
		default void holdHere() {
		}

		/** Whether a field holds it: its robotics frozen. */
		default boolean frozen() {
			return false;
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
	/** The command running (its method's name, or {@link #RETURN}; null: none). */
	private String active;
	private boolean accepting;
	/** The command under way at the save it was loaded from (its end is due: interrupted), or null. */
	private String interrupted;
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

	@Method(doc = "walk(forward[, right[, up]]): walks to the block that many whole blocks from it, in its own frame (forward the way it faces, right to its right, up its elevation's change), onto its middle, facing as it was. Without pathfinding (the default) it goes straight, forward then right, without turning (it walks backwards, or sideways), stepping freely within its elevation (onto a slab, over carpet), up a block at a time (a jump) only while up is above, down at most 3 at a time only while up is below, never into lava, fire or deep water; with pathfinding it finds its way. Measured from its plan (where its last command left it); with none, it goes back to its plan. A command: true at once (its event: walk, ok, or not and why, on the last block's middle it reached), or false and why it can't start")
	public Result walk(Arguments args) {
		double[] at = whole(args);
		if (at == null) {
			return Result.fail(NOT_WHOLE);
		}
		if (Math.abs(at[0]) > MAX_WALK || Math.abs(at[1]) > MAX_WALK || Math.abs(at[2]) > MAX_WALK) {
			return Result.fail(TOO_FAR + (int) MAX_WALK);
		}
		return start("walk", body.walk(at[0], at[1], at[2]));
	}

	/** Why a walk's or position's numbers aren't whole blocks. */
	public static final String NOT_WHOLE = "not whole";
	/** Why a walk or a flight is too far, and how far it may go. */
	public static final String TOO_FAR = "too far: ";

	/** A command that runs over time started (null: it did; else why not, refused): true, and it's busy till it ends. */
	private Result start(String method, String why) {
		if (why != null) {
			return Result.fail(why);
		}
		active = method;
		changed();
		return Result.ok(true);
	}

	/**
	 * The command running ended: its plan is where it is now (one cut short, by a stop or a load:
	 * where it is, on its legs the block it's nearest), and its event says how it went.
	 */
	private void end(boolean ok, String why, Object result) {
		String method = active;
		if (method == null) {
			return;
		}
		active = null;
		if (STOPPED.equals(why) || INTERRUPTED.equals(why)) {
			body.replanHere();
		} else {
			body.replan();
		}
		changed();
		events.ended(method, ok, why, result);
	}

	@Method(doc = "walkTo(forward[, right[, up]]): walks to the block that many whole blocks from its origin, in the origin's frame (as position gives them), as walk does. A command: true at once (its event: walkTo, ok, or not and why), or false and why")
	public Result walkTo(Arguments args) {
		double[] at = whole(args);
		if (at == null) {
			return Result.fail(NOT_WHOLE);
		}
		return start("walkTo", body.walkTo(at[0], at[1], at[2]));
	}

	@Method(doc = "flyTo(forward[, right[, up]]): (a tier 2 golem's) flies to the point that many blocks from its origin, in the origin's frame (as position gives them), as fly does. A command: true at once (its event: flyTo, ok, or not and why), or false and why")
	public Result flyTo(Arguments args) {
		double forward = args.checkNumber(0);
		double right = args.count() > 1 ? args.checkNumber(1) : 0;
		double up = args.count() > 2 ? args.checkNumber(2) : 0;
		return start("flyTo", body.flyTo(forward, right, up));
	}

	@Method(doc = "position(): its plan, where its last command left it, from its origin, in the origin's frame: {forward, right, up, heading} (blocks; heading in degrees to the origin's right: 0, 90, 180 or 270). A command: it waits behind the one running. (Where it really is, a shove off its plan included: get.) The origin is set where it stands when it's first powered on, in each dimension it first comes to, and by setPosition; kept through reboots and shoves, each dimension's its own, carried with it by a field; lost only when it's picked up")
	public Result position(Arguments args) {
		return body.planned();
	}

	@Method(doc = "setPosition([forward[, right[, up[, heading]]]]): says where it is now, in whole blocks from its origin, facing heading (degrees to the origin's right: 0, 90, 180 or 270), its origin (in this dimension) moved to make it so: position then gives exactly these; all 0 when not given: its origin where it is. It moves onto its block's middle and turns to the nearest right angle first. (A place known in another frame, a GPS's fix say, gives position that frame.)")
	public Result setPosition(Arguments args) {
		long[] at = new long[3];
		for (int i = 0; i < at.length; i++) {
			double value = args.count() > i && args.get(i) != null ? args.checkNumber(i) : 0;
			if (value != Math.rint(value)) {
				return Result.fail(NOT_WHOLE + ": " + args.get(i));
			}
			at[i] = (long) value;
		}
		double heading = args.count() > 3 && args.get(3) != null ? args.checkNumber(3) : 0;
		if (heading != Math.rint(heading) || Math.floorMod((long) heading, 90) != 0) {
			return Result.fail("not a right angle: " + args.get(3));
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

	@Method(doc = "turn(way): turns a right angle in place, \"left\" or \"right\", or two, \"around\". A command: true at once (its event: turn), or false and why")
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
			return Result.fail("not a way: " + way);
		}
		Result started = start("turn", body.turn(degrees));
		if (!started.failed()) {
			power.charge(TURN_COST);
		}
		return started;
	}

	@Method(doc = "stop(): stops what it's doing at once, where it is, no centering (flying, it hovers there; a block it was breaking is left as it is): the command running ends, stopped. Its plan becomes where it stopped (on its legs, the block it's nearest: its next positional command steps onto its middle first). Never waits; answers where it really stopped, {forward, right, up, heading} from its origin")
	public Result stop(Arguments args) {
		body.stop();
		end(false, STOPPED, null);
		Result at = body.position();
		return at.failed() ? Result.ok(true) : Result.ok(at.first());
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

	@Method(doc = "swing(face): swings what it holds at a face, as a player's left click: a mob there is hit (\"attacked\", as hard as a player's swing: full strength once the weapon has recovered); else the block there starts breaking, as a player breaks it (its tool matters: a pickaxe for stone, an axe for wood): \"breaking\", a command running till it's broken (its event: swing, ok and result {block, kept, dropped}: the block's name and whether it kept all it dropped, its storage first, else its off hand; or not and why); or false and why")
	public Result swing(Arguments args) {
		String face = face(args, 0);
		Result result = body.swing(face);
		if (!result.failed()) {
			power.charge(ACTION_COST);
			if (BREAKING.equals(result.first())) {
				active = "swing";
				changed();
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

	@Method(doc = "fly(forward[, right[, up]]): (a tier 2 golem's) flies on its thrusters to the point that many blocks from it, in its own frame (from its block's middle), without turning, straight there (never through blocks; with pathfinding, round them); its legs, if it stands on them, are left standing where it was, and its thrusters fire beams from their sockets. In the air it uses no tools (buttons and doors yes) and can be shoved (it flies back to its plan). Measured from its plan; with no arguments, it takes off where it stands and holds there, or goes back to its plan. A command: true at once (its event: fly, ok, or not and why), or false and why")
	public Result fly(Arguments args) {
		double forward = args.count() > 0 && args.get(0) != null ? args.checkNumber(0) : 0;
		double right = args.count() > 1 && args.get(1) != null ? args.checkNumber(1) : 0;
		double up = args.count() > 2 && args.get(2) != null ? args.checkNumber(2) : 0;
		if (Math.abs(forward) > MAX_FLIGHT || Math.abs(right) > MAX_FLIGHT || Math.abs(up) > MAX_FLIGHT) {
			return Result.fail(TOO_FAR + (int) MAX_FLIGHT);
		}
		return start("fly", body.fly(forward, right, up));
	}

	@Method(doc = "land(): (a tier 2 golem's) comes down onto its legs, standing below it (within three blocks to the side), and onto them. A command: true at once (its event: land, ok, or not and why), or false and why (no legs below: it goes on hovering)")
	public Result land(Arguments args) {
		return start("land", body.land());
	}

	/** The body got down onto its legs. */
	public void landed() {
		end(true, null, null);
	}

	/** Its thrusters fired a tick (flying or hovering): their cost. */
	public void thrusted() {
		power.charge(THRUST_COST);
	}

	/** The body hovers again (a flight given up, a landing blocked). */
	public void hovering() {
		changed();
	}

	@Method(doc = "get(): live, never waiting: {state, legs, pathfinding, position, planned, drift}: what it's doing now (idle, walking, turning, breaking, flying, hovering, landing, returning, falling or held), whether it stands on its legs, whether it finds its way round things, where it really is and its plan (each as position gives it), and how far it's off its plan ({forward, right, up}; none when it's on it)")
	public Result get(Arguments args) {
		Map<String, Object> now = new LinkedHashMap<>();
		now.put("state", state());
		now.put("legs", body.legs());
		now.put("pathfinding", body.pathfinding());
		Result position = body.position();
		if (!position.failed()) {
			now.put("position", position.first());
		}
		Result planned = body.planned();
		if (!planned.failed()) {
			now.put("planned", planned.first());
		}
		Map<String, Object> drift = body.drift();
		if (drift != null) {
			now.put("drift", drift);
		}
		return Result.ok(now);
	}

	@Method(doc = "set(action): does an action, as a table (its file's lines are these): {action = \"walk\", \"walkTo\", \"fly\" or \"flyTo\", forward, right, up}, {action = \"turn\", way}, {action = \"use\", face}, {action = \"swing\", face}, {action = \"pathfinding\", on}, {action = \"land\"}, {action = \"stop\"}, {action = \"setPosition\", forward, right, up, heading}; as the method does, waiting as it would (a stop never waits)")
	public Result set(Arguments args) {
		Object value = args.get(0);
		if (!(value instanceof Map<?, ?> action)) {
			return Result.fail("not a table");
		}
		String name = String.valueOf(action.get("action"));
		String method = name.equals("pathfinding") ? "setPathfinding" : name;
		if (!method.equals("stop")) {
			String refused = refusal(method); // (set is free, so a stop goes through; its other actions are commands)
			if (refused != null) {
				return Result.fail(refused);
			}
		}
		java.util.List<Object> rest = new java.util.ArrayList<>();
		return switch (name) {
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
			default -> Result.fail("not an action: " + action.get("action"));
		};
	}

	private static Object number(Object value) {
		return value == null ? 0L : value;
	}

	@Method(doc = "pull(): its oldest event, or nil: a command's end, {event = method, ok, why, result} (walk, walkTo, fly, flyTo, turn, land, swing), its way back to its plan's ({event = \"return\", ok, why}), or news: {event = \"shoved\", drift}, {event = \"fell\", by}, {event = \"frozen\"}, {event = \"thawed\"}")
	public Result pull(Arguments args) {
		return Result.ok(events.pull());
	}

	/** The body walked {@code blocks} more (its cost). */
	public void walked(double blocks) {
		power.charge((int) Math.ceil(blocks * WALK_COST));
	}

	/** The body got where it was going (a walk, a flight, its way back to its plan). */
	public void arrived() {
		end(true, null, null);
	}

	/** The body can't go on ({@code why}, dry: blocked: minecraft:oak_log front, no path). */
	public void blocked(String why) {
		end(false, why, null);
	}

	/** The body finished turning. */
	public void turned() {
		end(true, null, null);
	}

	/**
	 * The body broke the block it was breaking ({@code block}: its name); {@code dropped}: how many of
	 * what it dropped stayed in the world (no room in its storage, nor its off hand).
	 */
	public void broke(String block, int dropped) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("block", block);
		result.put("kept", dropped == 0);
		if (dropped > 0) {
			result.put("dropped", (long) dropped);
		}
		end(true, null, result);
	}

	/** The body couldn't go on breaking ({@code why}: the block changed, the tool went). */
	public void failed(String why) {
		end(false, why, null);
	}

	/** News from the body (not a command's end): {@code shoved} and its drift, {@code frozen}, {@code thawed}. */
	public void news(String name, Map<String, Object> fields) {
		Map<String, Object> event = new LinkedHashMap<>();
		event.put("event", name);
		if (fields != null) {
			event.putAll(fields);
		}
		events.add(event);
	}

	/** A field took hold of it: a block it was breaking is left (no tools in a field), and it says so. */
	public void frozen() {
		if ("swing".equals(active)) {
			end(false, HELD, null);
		}
		news("frozen", null);
	}

	/** Shoved off its plan in the air (its body found it so, hovering): it says so, and flies back, holding its plan. */
	public void drifted(Map<String, Object> drift) {
		if (active != null) {
			return;
		}
		news("shoved", Map.of("drift", drift));
		String why = body.returnToPlan();
		if (why == null) {
			active = RETURN;
			changed();
		} else {
			events.ended(RETURN, false, why, null); // (it can't get back: it says so once, and holds where it is)
			body.holdHere();
		}
	}

	/**
	 * It came down on its legs from a fall nothing asked for (a block gone from under it), by
	 * {@code blocks}: its plan followed it down (its odometry felt it), and it says so, when it fell
	 * far enough to tell ({@link #MIN_FALL}) or a command waited for it to land (its news ends that
	 * wait).
	 */
	public void fell(double blocks) {
		if (Math.abs(blocks) < MIN_FALL && !waitedOnFall) {
			return; // (a settling, as it's set down: nothing to say)
		}
		waitedOnFall = false;
		double kept = Math.rint(blocks * FALL_GRAIN) / FALL_GRAIN;
		news("fell", Map.of("by", kept == Math.rint(kept) ? (Object) (long) kept : kept));
	}

	/** The least fall it says, in blocks (a settling as it's set down isn't one). */
	public static final double MIN_FALL = 0.05;
	/** How finely a fall's distance is said: to a thousandth of a block. */
	private static final double FALL_GRAIN = 1000;
	/** A command was refused while it fell: its landing's news must come, however short the fall. */
	private boolean waitedOnFall;

	/** What it's doing now. */
	public String state() {
		if (body.frozen()) {
			return HELD;
		}
		if (active == null) {
			return body.falling() ? FALLING : body.hovering() ? HOVERING : IDLE;
		}
		return switch (active) {
			case "walk", "walkTo" -> WALKING;
			case "fly", "flyTo" -> FLYING;
			case "turn" -> TURNING;
			case "land" -> LANDING;
			case "swing" -> BREAKING;
			case RETURN -> RETURNING;
			default -> active; // (no other runs over time)
		};
	}

	@Override
	public java.util.Set<String> free() {
		return java.util.Set.of("get", "pull", "stop", "set");
	}

	/**
	 * Why it can't take {@code method} now: held (a field holds it), busy (a command runs), or busy
	 * while it goes back to its plan, which a positional command given off it starts.
	 */
	@Override
	public String refusal(String method) {
		if (body.frozen()) {
			return HELD;
		}
		if (active != null) {
			return BUSY;
		}
		if (body.falling()) {
			waitedOnFall = true;
			return BUSY; // (its landing's news ends the wait)
		}
		if (POSITIONAL.contains(method) && body.drift() != null) {
			String why = body.returnToPlan();
			if (why != null) {
				return why; // (it can't get back to its plan: the command can't run where it should)
			}
			active = RETURN; // (its way back first: the command waits for it)
			changed();
			return BUSY;
		}
		return null;
	}

	/** Whether it's running a command now (its body's ticks ask: a stop, a freeze). */
	public boolean busy() {
		return active != null;
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
		if (accepting && interrupted != null) {
			active = interrupted;
			interrupted = null;
			blocked(INTERRUPTED);
		}
		if (!accepting) {
			events.clear();
			interrupted = null; // (a fresh start: an old program's end isn't the next one's)
			body.stop();
			body.release(); // (its thrusters stop with its program: what they held up falls)
			active = null;
			changed();
		}
	}

	@Override
	public void powerOff() {
		accepting(false);
	}

	/**
	 * Its events waiting, and the command it was running: one under way doesn't go on after a load
	 * (the game keeps no path), so it ends then, interrupted, and a program waiting on it hears.
	 */
	@Override
	public Map<String, Object> saved() {
		Map<String, Object> saved = new LinkedHashMap<>();
		events.save(saved);
		if (active != null) {
			saved.put("active", active);
		}
		return saved;
	}

	/** Why a walk or turn under way at a save ended at the load. */
	public static final String INTERRUPTED = "interrupted";

	@Override
	public void restore(Map<String, Object> saved) {
		events.restore(saved);
		// (its event comes as the program goes on: it takes data then)
		interrupted = saved.get("active") instanceof String was ? was : null;
	}
}
