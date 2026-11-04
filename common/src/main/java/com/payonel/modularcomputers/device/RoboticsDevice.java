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
 * {@code fly} leaves its legs standing where it is and fires beams from their sockets (it walks
 * nowhere, works as a player off the ground does, and can be shoved, while it does), {@code land} brings it down onto its legs. A body ({@link Body}:
 * a golem) does the work, and tells it how it went. Every block walked, every action and every tick
 * its thrusters fire costs power.
 * <p>
 * It's buffered ({@link Buffered}): one command at a time. A command that runs over time (a walk,
 * a turn, a flight, a landing, a block's breaking) ends in its event, named after its method:
 * {@code {event = "walk", ok = false, why = "blocked", name = "minecraft:oak_log", face = "front"}}. Its commands run
 * from its <b>plan</b>, where the last one left it: a golem shoved off it in the air holds its plan
 * (it flies back, raising {@code shoved}), and a positional command given while it's off its plan (a
 * shove, a stop mid-walk) sends it back first ({@code return}'s event), refused busy until it's
 * there; falling on its legs (a block gone from under it) it's busy till it lands, its plan following
 * it down ({@code landed}). Held in a field (any field) its robotics freezes ({@code frozen}, {@code thawed}): what it
 * was doing pauses, commands are refused {@code held}, and the field's carry moves its origin with
 * it. {@code get}, {@code pull} and {@code stop} are free.
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
	/** What it's doing while a field holds it: its robotics frozen. */
	public static final String HELD = "held";
	/** What it's doing while it falls on its legs (nothing it was asked: a block gone under it). */
	public static final String FALLING = "falling";
	/**
	 * The event of whatever happened to it that no program asked for (the user's, 2026-10-29: one
	 * type, so a program tells at once what it asked for from what came to it), {@code what} saying it.
	 */
	public static final String NOTICE = "notice";
	/** A notice's {@code why}: shoved off where its last command left it (and how far: {@code drift}), once the shove's over. */
	public static final String SHOVE = "shove";
	/**
	 * A notice's {@code why}: a fall nothing asked for, said as it begins and as it lands (the user's,
	 * 2026-11-04): {@code drift}, how far it is off where its last command left it, and {@code below},
	 * the block under it then, as it is.
	 */
	public static final String FALL = "fall";
	/** Its commands taken while it falls (the user's): a tier 2 catches itself on its thrusters, its legs dropped. */
	private static final java.util.Set<String> CATCHES = java.util.Set.of("fly", "flyTo");
	/** A notice's {@code what}: a field took hold of it, or let it go. */
	public static final String FROZEN = "frozen";
	public static final String THAWED = "thawed";
	/**
	 * The commands that act where its last command left it and answer at once (the user's: positional,
	 * always): given off its spot ({@link Body#offSpot}), each fails, {@link Buffered#DRIFTED}, doing
	 * nothing; never a way back (the last two a crafting card's in it: they act where it is too).
	 */
	private static final java.util.Set<String> POSITIONAL = java.util.Set.of("use", "scan", "contents", "store", "take", "drop", "pickup");
	/** The commands over time, taken while held (they wait in stasis till it's let go); every other is refused held then. */
	private static final java.util.Set<String> OVER_TIME = java.util.Set.of("walk", "walkTo", "fly", "flyTo", "turn", "land");
	/** The commands that act there over time without moving it: given off its spot, each starts and ends at once, drifted. */
	private static final java.util.Set<String> POSITIONAL_OVER_TIME = java.util.Set.of("turn", "swing");
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

		/**
		 * The degrees to turn (to its right; negative: its left) from the heading its last command left
		 * it to face {@code way}, one of the world's (north, south, east, west); NaN when it can't tell.
		 */
		default double toward(Face way) {
			return Double.NaN;
		}

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
		 * Its plan becomes where it is, a command cut short (a stop, a load): the point it's at, on its legs
		 * as in the air (the user's: a stop squares nothing), its heading the nearest right angle; in a
		 * field, its plan as it was (the field's carry moves it).
		 */
		default void replanHere() {
			replan();
		}

		/** Whether it's falling on its legs, nothing it was asked (a block gone from under it). */
		default boolean falling() {
			return false;
		}

		/**
		 * Whether it's off where its last command left it, so a positional command there could act
		 * otherwise (the user's): out of that block (its middle's, so half a block either way, up and
		 * down too, is still on it), or facing another right angle.
		 */
		default boolean offSpot() {
			return false;
		}

		/** How far a block it's breaking has got, 0 to 1 (0: none). */
		default double breaking() {
			return 0;
		}

		/** A break given again after a load goes on from as far as it had got (0 to 1). */
		default void resumeBreaking(double progress) {
		}

		/** Whether a field holds it: its robotics frozen. */
		default boolean frozen() {
			return false;
		}

		/** Whether a field holds it now, the moment it takes hold (its body notices at its tick: {@link #frozen}). */
		default boolean held() {
			return frozen();
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

		/**
		 * Starts coming down onto its legs, below it (its event: landed, or blocked): null, or why not;
		 * {@code force}, with none there, straight down onto the ground, legless, still running.
		 */
		default String land(boolean force) {
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
	/** The command running (its method's name; null: none). */
	private String active;
	/** The last command the board gave it and its arguments, as given (the board's {@link #called}). */
	private String calledMethod;
	private java.util.List<Object> calledArgs = java.util.List.of();
	/** The command running's arguments, and where its last command left it as it started (its progress is from there). */
	private java.util.List<Object> activeArgs = java.util.List.of();
	private Map<?, ?> startedAt;
	/** A stop's call, taken before its body stopped. */
	private Map<String, Object> stopCall;
	private boolean accepting;
	/** The command under way at the save it was loaded from, to go on with once its program takes data (null: none), its arguments, and how far a break had got. */
	private String resume;
	private java.util.List<Object> resumeArgs = java.util.List.of();
	private double resumeBreaking;
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

	@Method(doc = "walk(forward[, right[, up]]): walks that far from where its last command left it, in its own frame (forward the way it faces, right to its right, up its elevation's change, whole blocks), facing as it was. It must end on a block's middle, whole blocks from its origin (within a 4096th), else it's refused (not an integer: forward 4.4): from a block's middle whole numbers do, and after a stop the rest of the way. Without pathfinding (the default) it goes straight, forward then right, without turning (it walks backwards, or sideways), stepping freely within its elevation (onto a slab, over carpet), up a block at a time (a jump) only while up is above, down at most 3 at a time only while up is below, never into lava, fire or deep water; with pathfinding it finds its way. With none, it goes back to where its last command left it. A command: true at once (its event: walk, ok, or not and why, on the last block's middle it reached: blocked, with block, what stopped it, air too, a drop it can't take or an elevation it couldn't reach, and face, its own that met it), or false and why it can't start")
	public Result walk(Arguments args) {
		double[] by = numbers(args);
		if (Math.abs(by[0]) > MAX_WALK || Math.abs(by[1]) > MAX_WALK || Math.abs(by[2]) > MAX_WALK) {
			return Result.fail(TOO_FAR + (int) MAX_WALK);
		}
		Move move = args.count() == 0 ? new Move(by, null) : squared(by, false); // (none: back to where its last command left it)
		if (move.why() != null) {
			return Result.fail(move.why());
		}
		Result waits = waitsHeld("walk", args);
		return waits != null ? waits : start("walk", body.walk(move.by()[0], move.by()[1], move.by()[2]));
	}

	/**
	 * How near a whole block a move's end counts as squared, in blocks (the user's): the game's own grain
	 * for where an entity is, as its moves reach players (VecDeltaCodec's steps: a 4096th of a block).
	 */
	public static final double SQUARED = 1.0 / 4096;
	/** Why a move wouldn't end squared, or a point given isn't whole: which coordinate, from its origin, and the number. */
	public static final String NOT_AN_INTEGER = "not an integer: ";
	/** The coordinates, as a failure names them. */
	private static final String[] AXES = {"forward", "right", "up"};
	/** How finely a failure says a number: to a thousandth. */
	private static final int FAILURE_DECIMALS = 3;

	/** A move's numbers (forward, right, up) in its own frame, ready for its body, or why it can't be made. */
	private record Move(double[] by, String why) {
	}

	/** Arguments 0 to 2 (forward, right, up; 0 when not given). */
	private static double[] numbers(Arguments args) {
		double[] at = new double[3];
		for (int i = 0; i < at.length; i++) {
			at[i] = args.count() > i && args.get(i) != null ? args.checkNumber(i) : 0;
		}
		return at;
	}

	/** Arguments 0 to 2 as whole numbers, each within {@link #SQUARED} of one (made it), or why one isn't. */
	private static Move integers(Arguments args) {
		double[] at = numbers(args);
		for (int i = 0; i < at.length; i++) {
			double whole = Math.rint(at[i]);
			if (Math.abs(at[i] - whole) >= SQUARED) {
				return new Move(null, NOT_AN_INTEGER + AXES[i] + " " + said(at[i]));
			}
			at[i] = whole;
		}
		return new Move(at, null);
	}

	/**
	 * A move {@code by} (forward, right, up) in its own frame, from where its last command left it,
	 * which must end squared (the user's): on a block's middle, a walk's up a whole change of elevation
	 * (the block its feet are in), a flight's up its feet's height whole from its origin, each within
	 * {@link #SQUARED} of whole. Its numbers made to end exactly there, or why not (where it would end).
	 */
	private Move squared(double[] by, boolean flight) {
		Result from = body.planned();
		Map<?, ?> at = from.failed() || !(from.first() instanceof Map<?, ?> map) ? Map.of() : map;
		double forward = coordinate(at, "forward");
		double right = coordinate(at, "right");
		double up = coordinate(at, "up");
		int quarter = (int) Math.floorMod(Math.round(coordinate(at, "heading") / 90), 4);
		// Its frame into the origin's: its heading a right angle to the origin's right.
		int cos = QUARTER_COS[quarter];
		int sin = QUARTER_COS[(quarter + 3) % 4];
		double[] end = {forward + by[0] * cos - by[1] * sin, right + by[0] * sin + by[1] * cos, flight ? up + by[2] : by[2]};
		double[] whole = new double[end.length];
		for (int i = 0; i < end.length; i++) {
			whole[i] = Math.rint(end[i]);
			if (Math.abs(end[i] - whole[i]) >= SQUARED) {
				return new Move(null, NOT_AN_INTEGER + AXES[i] + " " + said(end[i]));
			}
		}
		double ahead = whole[0] - forward;
		double aside = whole[1] - right;
		return new Move(new double[] {ahead * cos + aside * sin, aside * cos - ahead * sin, flight ? whole[2] - up : whole[2]}, null);
	}

	/** A quarter turn's cosine, for each of the four (0, 90, 180, 270 degrees). */
	private static final int[] QUARTER_COS = {1, 0, -1, 0};

	private static double coordinate(Map<?, ?> at, String name) {
		return at.get(name) instanceof Number number ? number.doubleValue() : 0;
	}

	/** A number as a failure says it: whole when it is, else to {@link #FAILURE_DECIMALS} places. */
	private static String said(double value) {
		return java.math.BigDecimal.valueOf(value).setScale(FAILURE_DECIMALS, java.math.RoundingMode.HALF_EVEN).stripTrailingZeros().toPlainString();
	}
	/** Why a walk or a flight is too far, and how far it may go. */
	public static final String TOO_FAR = "too far: ";

	/** A command that runs over time started (null: it did; else why not, refused): true, and it's busy till it ends. */
	private Result start(String method, String why) {
		if (why != null) {
			return Result.fail(why);
		}
		begin(method);
		return Result.ok(true);
	}

	/** A command begins: its arguments (as the board gave them) and where it starts from are kept, for its call. */
	private void begin(String method) {
		active = method;
		activeArgs = method.equals(calledMethod) ? calledArgs : java.util.List.of();
		Result planned = body.planned();
		startedAt = planned.failed() ? null : (Map<?, ?>) planned.first();
		changed();
	}

	/**
	 * A command over time given while a field holds it (the user's): it's taken, and waits in stasis,
	 * its call kept, nothing said till it's let go, then it goes (a stop meanwhile ends it); null, it
	 * isn't held.
	 */
	private Result waitsHeld(String method, Arguments args) {
		if (!body.held()) {
			return null;
		}
		begin(method);
		resume = method;
		resumeArgs = EventQueue.given(args.values());
		waitingHeld = true;
		return Result.ok(true);
	}

	/** A command taken while held, waiting to go when it's let go. */
	private boolean waitingHeld;

	/** The field let it go, and it's down: it says so, and a command taken while held goes now. */
	public void thawed() {
		notice(THAWED, null);
		if (waitingHeld) {
			waitingHeld = false;
			active = null;
			resume(false);
		}
	}

	@Override
	public void called(String method, java.util.List<Object> args) {
		calledMethod = method;
		calledArgs = EventQueue.given(args);
	}

	/**
	 * The command running's call: {args, progress}, its arguments as given and how far it got, in
	 * their terms (the user's): a walk's or a flight's {forward, right, up} done, in its own frame
	 * as it began; a walkTo's or flyTo's where it is, from its origin; a turn's degrees turned; a
	 * landing's blocks come down; a swing's break so far, 0 to 1.
	 */
	private Map<String, Object> call() {
		Object progress = 0L;
		Result now = body.position();
		if (startedAt != null && !now.failed() && now.first() instanceof Map<?, ?> at) {
			double df = number(at.get("forward")) - number(startedAt.get("forward"));
			double dr = number(at.get("right")) - number(startedAt.get("right"));
			double du = number(at.get("up")) - number(startedAt.get("up"));
			double heading = Math.toRadians(number(startedAt.get("heading")));
			progress = switch (String.valueOf(active)) {
				case "walk", "fly" -> java.util.List.of(tidy(df * Math.cos(heading) + dr * Math.sin(heading)),
					tidy(-df * Math.sin(heading) + dr * Math.cos(heading)), tidy(du));
				case "walkTo", "flyTo" -> java.util.List.of(tidy(number(at.get("forward"))), tidy(number(at.get("right"))), tidy(number(at.get("up"))));
				case "turn" -> tidy(net.minecraft.util.Mth.wrapDegrees(number(at.get("heading")) - number(startedAt.get("heading"))));
				case "land" -> tidy(-du);
				case "swing" -> tidy(body.breaking());
				default -> 0L;
			};
		}
		return EventQueue.call(activeArgs, progress);
	}

	private static double number(Object value) {
		return value instanceof Number n ? n.doubleValue() : 0;
	}

	/** A measure as said: a whole number as one, else to a thousandth. */
	private static Object tidy(double value) {
		double kept = Math.rint(value * FALL_GRAIN) / FALL_GRAIN;
		return kept == Math.rint(kept) ? (Object) (long) kept : kept;
	}

	/**
	 * The command running ended: its plan is where it is now (one cut short, by a stop or a load:
	 * where it is, its heading squared), and its event says how it went.
	 */
	private void end(boolean ok, String why, Object result) {
		end(ok, why, null, null, result);
	}

	/** A command's end, its way back's too ({@code block}, {@code face}: a {@link #BLOCKED}'s). */
	private void end(boolean ok, String why, String block, String face, Object result) {
		String method = active;
		if (method == null) {
			return;
		}
		if (waitingHeld) { // (taken while held, ended before it went: a stop)
			waitingHeld = false;
			resume = null;
		}
		Map<String, Object> call = STOPPED.equals(why) && stopCall != null ? stopCall
			: STOPPED.equals(why) || BLOCKED.equals(why) || INTERRUPTED.equals(why) || LOST.equals(why) ? call() : null; // (before its plan moves)
		stopCall = null;
		active = null;
		if (STOPPED.equals(why) || INTERRUPTED.equals(why)) {
			body.replanHere();
		} else {
			body.replan();
		}
		changed();
		if (STOPPED.equals(why)) {
			events.stopped(method, call);
		} else if (INTERRUPTED.equals(why)) {
			events.interrupted(method, call);
		} else if (LOST.equals(why)) {
			events.lost(method, lostName, lostId, lostAt, call);
		} else if (BLOCKED.equals(why)) {
			events.blocked(method, block, face, call);
		} else {
			events.ended(method, ok, why, result);
		}
	}

	@Method(doc = "walkTo(forward[, right[, up]]): walks to the block that many whole blocks from its origin (not an integer: when one isn't), in the origin's frame (as position gives them), as walk does. A command: true at once (its event: walkTo, ok, or not and why), or false and why")
	public Result walkTo(Arguments args) {
		Move to = integers(args);
		if (to.why() != null) {
			return Result.fail(to.why());
		}
		Result waits = waitsHeld("walkTo", args);
		return waits != null ? waits : start("walkTo", body.walkTo(to.by()[0], to.by()[1], to.by()[2]));
	}

	@Method(doc = "flyTo(forward[, right[, up]]): (a tier 2 golem's) flies to the point that many whole blocks from its origin (not an integer: when one isn't), in the origin's frame (as position gives them), as fly does. A command: true at once (its event: flyTo, ok, or not and why), or false and why")
	public Result flyTo(Arguments args) {
		args.checkNumber(0);
		Move to = integers(args);
		if (to.why() != null) {
			return Result.fail(to.why());
		}
		Result waits = waitsHeld("flyTo", args);
		return waits != null ? waits : start("flyTo", body.flyTo(to.by()[0], to.by()[1], to.by()[2]));
	}

	@Method(doc = "position(): where its last command left it (what its next command goes from), from its origin, in the origin's frame: {forward, right, up, heading} (blocks; heading in degrees to the origin's right: 0, 90, 180 or 270). A command: it waits behind the one running. (Where it really is, a shove away from there included: get.) The origin is set where it stands when it's first powered on, in each dimension it first comes to, and by setPosition; kept through reboots and shoves, each dimension's its own, carried with it by a field; lost only when it's picked up")
	public Result position(Arguments args) {
		return body.planned();
	}

	@Method(doc = "setPosition([forward[, right[, up[, heading]]]]): says where it is now, in whole blocks from its origin, facing heading (degrees to the origin's right: 0, 90, 180 or 270), its origin (in this dimension) moved to make it so: position then gives exactly these; all 0 when not given: its origin where it is. It moves onto its block's middle and turns to the nearest right angle first. (A place known in another frame, a GPS's fix say, gives position that frame.)")
	public Result setPosition(Arguments args) {
		Move given = integers(args);
		if (given.why() != null) {
			return Result.fail(given.why());
		}
		long[] at = {(long) given.by()[0], (long) given.by()[1], (long) given.by()[2]};
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

	/** The world's ways a turn can face (the user's, 2026-10-29: a golem may know north). */
	private static final java.util.Set<String> CARDINALS = java.util.Set.of("north", "south", "east", "west");

	@Method(doc = "turn(way): turns a right angle in place, \"left\" or \"right\", or two, \"around\"; or to face the world's way, \"north\", \"south\", \"east\" or \"west\" (facing it already: no turn, its event all the same). A command: true at once (its event: turn), or false and why")
	public Result turn(Arguments args) {
		double degrees;
		Object way = args.get(0);
		if (way instanceof String name) {
			degrees = switch (name) {
				case "left" -> -90;
				case "right" -> 90;
				case "around" -> 180;
				default -> CARDINALS.contains(name) ? body.toward(Face.of(name)) : Double.NaN;
			};
		} else {
			degrees = Double.NaN;
		}
		if (Double.isNaN(degrees)) {
			return Result.fail("not a way: " + way);
		}
		Result waits = waitsHeld("turn", args);
		if (waits != null) {
			return waits;
		}
		if (body.offSpot()) {
			return drifted("turn");
		}
		Result started = start("turn", body.turn(degrees));
		if (!started.failed()) {
			power.charge(TURN_COST);
		}
		return started;
	}

	@Method(doc = "stop(): stops what it's doing at once, where it is, no centering (flying, it hovers there; a block it was breaking is left as it is; falling, its fall goes on): the command running ends, stopped. Where its last command left it becomes where it is now, a command running or not (the only thing that moves it there: a shove or a fall doesn't): its next command goes on from exactly there, on its legs as in the air (its heading the nearest right angle), no squaring first. Falling, a fly() then catches it there. Never waits; answers where it really stopped, {forward, right, up, heading} from its origin")
	public Result stop(Arguments args) {
		stopCall = active == null ? null : call(); // (how far it got, before its body stops: a break's progress goes with it)
		if (!body.falling()) {
			body.stop(); // (falling, nothing of its own moves: gravity's fall goes on, its speed kept)
		}
		end(false, STOPPED, null);
		body.replanHere(); // (where it is: its next command goes on from here, a command cut short or none)
		Result at = body.position();
		return at.failed() ? Result.ok(true) : Result.ok(at.first());
	}

	@Method(doc = "scan([face]): every thing in the space at a face (front, back, left, right, up or down: its own), a list, or with none, the six spaces by face, a list each: its creatures and items first, each {kind = \"entity\", name, id, at}, then the space's own block, always one (air too), {kind = \"block\", name, id = \"\", at, state, hardness, solid, container}, {kind = \"fluid\", name, id = \"\", at, amount, source} or {kind = \"air\", name, id = \"\", at}: name what it is (its registry id), id its own (a block has none), at where it is, {forward, right, up} in its frame; as the field card's scan gives things")
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

	@Method(doc = "swing(face): swings what it holds at a face, as a player's left click: a mob there is hit (\"attacked\", as hard as a player's swing: full strength once the weapon has recovered); else the block there starts breaking, as a player breaks it (its tool matters: a pickaxe for stone, an axe for wood): \"breaking\", a command running till it's broken, a break of the place as a player's is (another block pushed in, it goes on into it; another item in its hand, it starts again; held in a field, it pauses) (its event: swing, ok and result {name, kept, dropped}: the block's name and whether it kept all it dropped, its storage first, else its off hand; or not and why: lost, air there now, its name, id \"\" and where it was, or not broken); or false and why")
	public Result swing(Arguments args) {
		String face = face(args, 0);
		if (body.offSpot()) {
			return drifted("swing");
		}
		Result result = body.swing(face);
		if (!result.failed()) {
			power.charge(ACTION_COST);
			if (BREAKING.equals(result.first())) {
				begin("swing");
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

	@Method(doc = "fly(forward[, right[, up]]): (a tier 2 golem's) flies on its thrusters that far from where its last command left it, in its own frame, without turning, straight there (never through blocks; with pathfinding, round them); its legs, if it stands on them, are left standing where it was, and its thrusters fire beams from their sockets. In the air it walks nowhere, uses what it holds as a player off the ground does, and can be shoved (it flies back to where its last command left it). It must end on a squared point, whole blocks from its origin, its feet's height too (within a 4096th), else it's refused (not an integer: up 1.5). With no arguments, it takes off where it stands and holds there, or goes back to where its last command left it. A command: true at once (its event: fly, ok, or not and why), or false and why")
	public Result fly(Arguments args) {
		double[] by = numbers(args);
		if (Math.abs(by[0]) > MAX_FLIGHT || Math.abs(by[1]) > MAX_FLIGHT || Math.abs(by[2]) > MAX_FLIGHT) {
			return Result.fail(TOO_FAR + (int) MAX_FLIGHT);
		}
		Move move = args.count() == 0 ? new Move(by, null) : squared(by, true); // (none: off where it stands, or back to where its last command left it)
		if (move.why() != null) {
			return Result.fail(move.why());
		}
		Result waits = waitsHeld("fly", args);
		return waits != null ? waits : start("fly", body.fly(move.by()[0], move.by()[1], move.by()[2]));
	}

	@Method(doc = "land([force]): (a tier 2 golem's) a move, from where its last command left it: comes down onto its legs, standing below there however far down (within three blocks to the side), and onto them; force, with none there, comes straight down onto the ground beneath it without them, still running (its thrusters off; it walks nowhere, and flies again to fetch them), as safely as a crash would, but on. A command: true at once (its event: land, ok, or not and why: blocked, down, at once, without force finding no legs below by what it found there instead, with force finding nothing under it down to the world's bottom by air; it goes on hovering), or false and why (on legs)")
	public Result land(Arguments args) {
		boolean force = args.count() > 0 && args.get(0) != null && args.checkBoolean(0);
		Result waits = waitsHeld("land", args);
		return waits != null ? waits : start("land", body.land(force));
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

	@Method(doc = "get(): live, never waiting: {state, legs, pathfinding, position, planned, drift}: what it's doing now (idle, walking, turning, breaking, flying, hovering, landing, returning, falling or held), whether it stands on its legs, whether it finds its way round things, where it really is and where its last command left it (planned; each as position gives it), and how far it's away from there ({forward, right, up}; none when it's there)")
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

	@Method(doc = "pull(): its oldest event, or nil: a command's end, {event = method, ok, why, result} (walk, walkTo, fly, flyTo, turn, land, swing), or a notice of what happened to it that no program asked for: {event = \"notice\", why = \"shove\", drift} once a shove's over, {event = \"notice\", why = \"fall\", drift, below} as a fall begins and as it lands, or {event = \"notice\", what} for a field's hold (frozen, thawed)")
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

	/**
	 * A block stopped the body ({@code block}: its id, null when none can be named; {@code face}: its
	 * face that met it, its own: front, back, left, right, up or down).
	 */
	public void blocked(String block, String face) {
		end(false, BLOCKED, block, face, null);
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
		result.put("name", block); // (a thing's name: its registry id, the user's)
		result.put("kept", dropped == 0);
		if (dropped > 0) {
			result.put("dropped", (long) dropped);
		}
		end(true, null, result);
	}

	/**
	 * What a command was acting on is gone (the user's: lost): {@code name} what it was (its registry
	 * id), {@code id} its own id ("" for a block: blocks have none), {@code at} where it last was,
	 * {forward, right, up} in its own frame from where its last command left it.
	 */
	public void lost(String name, String id, Map<String, Object> at) {
		lostName = name;
		lostId = id;
		lostAt = at;
		end(false, LOST, null);
	}

	/** A lost end's what, its id and where (for its event). */
	private String lostName;
	private String lostId;
	private Map<String, Object> lostAt;

	/** A face as where its block is, {forward, right, up} in its own frame (a block from its spot). */
	public static Map<String, Object> toward(String face) {
		long forward = "front".equals(face) ? 1 : "back".equals(face) ? -1 : 0;
		long right = "right".equals(face) ? 1 : "left".equals(face) ? -1 : 0;
		long up = "up".equals(face) ? 1 : "down".equals(face) ? -1 : 0;
		return Map.of("forward", forward, "right", right, "up", up);
	}

	/** The body couldn't go on ({@code why}: the game refused a break done). */
	public void failed(String why) {
		end(false, why, null);
	}

	/** What happened to it that no program asked for ({@code what}: {@link #FROZEN}, {@link #THAWED}), as a {@link #NOTICE}. */
	public void notice(String what, Map<String, Object> fields) {
		Map<String, Object> event = new LinkedHashMap<>();
		event.put("event", NOTICE);
		event.put("what", what);
		if (fields != null) {
			event.putAll(fields);
		}
		events.add(event);
	}

	/** A field took hold of it: it says so, and what it was doing pauses (a break too: no tools in a field, its break going on once it's let go). */
	public void frozen() {
		notice(FROZEN, null); // (a command under way pauses, a swing too: the user's)
	}

	/**
	 * Shoved off where its last command left it (its body found it so, once it's over: hovering, or
	 * standing idle on its legs): it says so, and nothing more (the user's: no way back of its own; a
	 * program corrects it, walk() or fly(), or takes where it is, stop()).
	 */
	public void drifted(Map<String, Object> drift) {
		if (active != null) {
			return;
		}
		said(SHOVE, Map.of("drift", drift));
	}

	/** A positional command over time given off its spot: it starts and ends at once, drifted, its drift and its call. */
	private Result drifted(String method) {
		Map<?, ?> drift = body.drift(); // (before it begins: running, it has none)
		begin(method);
		Map<String, Object> call = call();
		active = null;
		changed();
		events.drifted(method, drift == null ? Map.of("forward", 0L, "right", 0L, "up", 0L) : drift, call);
		return Result.ok(true);
	}

	/**
	 * A fall nothing asked for (a block gone from under it), as it begins and as it lands: it says so
	 * each time, its drift then (in its origin's frame) and the block under it (its landing's news
	 * ends a wait for it). A fall it caught itself (a flight) has no landing to say: that flight's
	 * own end says it.
	 */
	public void fallBegins(Map<String, Object> drift, String below) {
		fallSaid = accepting; // (begun with no program to take it, a golem set down settling: its landing isn't said either)
		if (fallSaid) {
			sayFall(drift, below);
		}
	}

	/** The fall's landing: said only for a fall whose start was said (in pairs). */
	public void fallEnds(Map<String, Object> drift, String below) {
		if (fallSaid) {
			fallSaid = false;
			sayFall(drift, below);
		}
	}

	private void sayFall(Map<String, Object> drift, String below) {
		Map<String, Object> fields = new LinkedHashMap<>();
		fields.put("drift", drift);
		fields.put("below", below);
		said(FALL, fields);
	}

	/** A fall's start was said, its landing to come (a caught fall's never does: the next fall's start clears it). */
	private boolean fallSaid;

	/** A fall it caught itself (a flight taken mid-fall): no landing to say, its flight's end says it. */
	public void caught() {
		fallSaid = false;
	}

	/** A notice whose kind is its why (the shape notices are being made: shove, fall). */
	private void said(String why, Map<String, Object> fields) {
		Map<String, Object> event = new LinkedHashMap<>();
		event.put("event", NOTICE);
		event.put("why", why);
		event.putAll(fields);
		events.add(event);
	}

	/** How finely a measure is said: to a thousandth of a block (or a degree). */
	private static final double FALL_GRAIN = 1000;

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
			default -> active; // (no other runs over time)
		};
	}

	@Override
	public java.util.Set<String> free() {
		return java.util.Set.of("get", "pull", "stop");
	}

	/**
	 * Why it can't take {@code method} now: held (a field holds it), busy (a command runs, or it's
	 * falling), or drifted (a positional command answering at once, given off its spot).
	 */
	@Override
	public String refusal(String method) {
		if (active != null) {
			return BUSY; // (one at a time: one running, paused in a field or not)
		}
		if (body.held()) {
			return OVER_TIME.contains(method) ? null : HELD; // (over time it's taken and waits in stasis; at once it can't act: held, the user's)
		}
		if (body.falling() && !CATCHES.contains(method)) {
			return BUSY; // (its landing's news ends the wait)
		}
		if (POSITIONAL.contains(method) && body.offSpot()) {
			return DRIFTED; // (positional, always: off its spot it can't act where it should, and never goes back by itself)
		}
		return null;
	}

	/** Whether it's running a command now (its body's ticks ask: a stop, a freeze). */
	@Override
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
		if (accepting && resume != null && !waitingHeld) {
			resume(true); // (the command under way at the save goes on, the user's)
		}
		if (!accepting) {
			events.clear();
			resume = null; // (a fresh start: an old program's command isn't the next one's)
			waitingHeld = false;
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
	 * Its events waiting, and the command it was running, with its arguments (and a break's
	 * progress): it goes on after the load (the user's), given again as it was, from where its last
	 * command left it, so it ends where it would have; one that can't ends interrupted.
	 */
	@Override
	public Map<String, Object> saved() {
		Map<String, Object> saved = new LinkedHashMap<>();
		events.save(saved);
		if (active != null) {
			saved.put("active", active);
			saved.put("args", new java.util.ArrayList<>(activeArgs));
			if ("swing".equals(active)) {
				saved.put("breaking", body.breaking());
			}
		}
		return saved;
	}

	/** The command under way at the save, given again (its call as it was): else, it can't go on, interrupted. */
	private void resume(boolean afterLoad) {
		String method = resume;
		java.util.List<Object> args = resumeArgs;
		resume = null;
		calledMethod = method;
		calledArgs = args;
		Result again;
		try {
			again = com.payonel.modularcomputers.runtime.DeviceMethods.invoke(this, method, new Arguments(new java.util.ArrayList<>(args)));
		} catch (RuntimeException e) {
			again = Result.fail(String.valueOf(e.getMessage()));
		}
		if (!again.failed() && method.equals(active)) {
			if ("swing".equals(method)) {
				body.resumeBreaking(resumeBreaking); // (its break as far as it had got)
			}
			return;
		}
		begin(method);
		Map<String, Object> call = call();
		active = null;
		changed();
		if (afterLoad) {
			events.interrupted(method, call); // (the fallback: it couldn't go on after the load)
		} else {
			events.ended(method, false, again.error(), null); // (let go, it couldn't start: why, as its call would have answered)
		}
	}


	@Override
	public void restore(Map<String, Object> saved) {
		events.restore(saved);
		// (it goes on as the program goes on: it takes data then)
		resume = saved.get("active") instanceof String was ? was : null;
		resumeArgs = saved.get("args") instanceof java.util.List<?> args ? java.util.List.copyOf(new java.util.ArrayList<Object>(args)) : java.util.List.of();
		resumeBreaking = saved.get("breaking") instanceof Number n ? n.doubleValue() : 0;
	}
}
