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
 * A crafting card (user design, 2026-10-24): storage, its first {@link #GRID} slots a 3x3
 * crafting grid and the rest ({@link #STORAGE}) where what it crafts goes, on the card itself (its
 * item), so it goes where the card goes. In any computer a player crafts with it by hand, in its tab
 * of the computer's view; its methods need robotics (a robot's arms to work it): in a computer
 * without, each fails ({@link #NO_ROBOTICS}). A craft takes {@link #CRAFT_TICKS}, and keeps its
 * robot busy (it's the robot's work: one card crafts at a time). What it crafts goes to its storage,
 * and with no room there the craft fails. It also moves items between its slots, the robot's hand,
 * containers round the robot and the world (picking up, dropping). A {@link Bench} does the work
 * on the items; the card does the asking, the timing and the telling.
 */
public final class CraftingCard extends Card implements Buffered, RoboticsDevice.Part {
	/** The crafting grid's slots (1 to 9, its rows left to right, top to bottom), and the storage's after them (10 on). */
	public static final int GRID = 9;
	public static final int STORAGE = 16;
	public static final int SLOTS = GRID + STORAGE;
	/** How long a craft takes, in ticks: a player's pace at a crafting table, about. */
	public static final int CRAFT_TICKS = 10;
	/** What a craft costs: a running CPU's tick. */
	public static final int CRAFT_COST = PowerUnits.CPU;
	/** What moving items costs (a move, a pick up, a drop, a container's): a running CPU's tick. */
	public static final int MOVE_COST = PowerUnits.CPU;
	/** Why its methods fail in a computer that isn't a robot. */
	public static final String NO_ROBOTICS = "no robotics";
	public static final String IDLE = "idle";
	public static final String CRAFTING = "crafting";

	/** What works the card's items: its storage, the robot holding it, the world round it. */
	public interface Bench {
		/** Whether a robot's robotics is there to work it. */
		boolean robotics();

		/** What its slots hold: those with something in, each an item's table and its slot. */
		Result list();

		/** Crafts once from its grid, what it makes into its storage: {name, count}, or false and why. */
		Result craft();

		/** Moves up to {@code count} from one of its slots to another: how many went. */
		Result move(int from, int to, int count);

		/** Swaps one of its slots with the robot's hand. */
		Result equip(int slot);

		/** Picks up the items lying round the robot into its storage: how many. */
		Result pickup();

		/**
		 * Drops up to {@code count} from a slot ({@code slot} 0: all its storage) at {@code face}: into
		 * the container there first, the rest into the world: how many went into the world.
		 */
		Result drop(String face, int slot, int count);

		/**
		 * Puts up to {@code count} from a slot (0: all its storage) into the container at {@code face}
		 * (its slot {@code to}, from 1; 0: where they fit): true and 0 when none of it is left, else
		 * false and how many are (or false and why: no container).
		 */
		Result store(String face, int slot, int count, int to);

		/**
		 * Takes up to {@code count} from the container at {@code face} (its {@code slot}, from 1; 0:
		 * the first it can) into its slot {@code to} (0: where they fit in its storage): how many.
		 */
		Result take(String face, int slot, int count, int to);

		/** The robotics of the robot it's in (null: none): its actions are that robot's commands. */
		default RoboticsDevice robot() {
			return null;
		}
	}

	private final InterruptLine line = new InterruptLine();
	private final EventQueue events = new EventQueue(line, this::changed, this::isAccepting);
	private final BusPower power = new BusPower(0);
	private Bench bench = new Bench() {
		@Override
		public boolean robotics() {
			return false;
		}

		@Override
		public Result list() {
			return none();
		}

		@Override
		public Result craft() {
			return none();
		}

		@Override
		public Result move(int from, int to, int count) {
			return none();
		}

		@Override
		public Result equip(int slot) {
			return none();
		}

		@Override
		public Result pickup() {
			return none();
		}

		@Override
		public Result drop(String face, int slot, int count) {
			return none();
		}

		@Override
		public Result store(String face, int slot, int count, int to) {
			return none();
		}

		@Override
		public Result take(String face, int slot, int count, int to) {
			return none();
		}

		private Result none() {
			return Result.fail("no bench");
		}
	};
	/** Crafts still to make, of those asked for, and those made so far. */
	private int toCraft;
	private int crafted;
	private String craftedName;
	/** Ticks until the craft under way is done. */
	private int ticksLeft;

	public CraftingCard() {
		super(CardKind.CRAFTING);
	}

	/** What works its items. */
	public void benchIn(Bench bench) {
		this.bench = bench;
	}

	public Bench bench() {
		return bench;
	}

	/** Each server tick while it's powered: a craft under way goes on (paused while a field holds its robot), and when its time is up, it's made. */
	public void tick() {
		if (toCraft == 0 || bench.robot() != null && bench.robot().isFrozen()) {
			return;
		}
		if (--ticksLeft > 0) {
			return;
		}
		Result made = bench.craft();
		if (made.failed()) {
			finish("failed", made.error());
			return;
		}
		power.charge(CRAFT_COST);
		Map<?, ?> item = (Map<?, ?>) made.first();
		craftedName = String.valueOf(item.get("name"));
		crafted += ((Number) item.get("count")).intValue();
		if (--toCraft > 0) {
			ticksLeft = CRAFT_TICKS;
		} else {
			finish("crafted", null);
		}
	}

	private void finish(String name, String why) {
		Map<String, Object> result = new LinkedHashMap<>();
		if (craftedName != null) {
			result.put("name", craftedName);
		}
		result.put("count", (long) crafted);
		toCraft = 0;
		crafted = 0;
		craftedName = null;
		RoboticsDevice robot = bench.robot();
		if (robot != null) {
			robot.done(this); // (its robot is free again)
		}
		changed();
		events.ended("craft", "crafted".equals(name), why, result);
	}

	/** Its robot was stopped: a craft under way stops, and says so. */
	@Override
	public void robotStopped() {
		if (toCraft > 0) {
			finish("failed", RoboticsDevice.STOPPED);
		}
	}

	/** Its events and what its slots hold are free (they're the card's own); each other method is its robot's command. */
	@Override
	public java.util.Set<String> free() {
		return java.util.Set.of("pull", "list", "get");
	}

	/**
	 * Why it can't take {@code method} now: busy while it crafts, else what its robot says (busy, held;
	 * its face actions are where the robot is, so off its plan it goes back first). In a case, nothing
	 * here: its methods fail, no robotics.
	 */
	@Override
	public String refusal(String method) {
		RoboticsDevice robot = bench.robot();
		if (robot == null) {
			return null;
		}
		if (toCraft > 0) {
			return BUSY;
		}
		return robot.refusal("set".equals(method) ? "drop" : method); // (its line's set drops where the robot is)
	}

	/** Refused for its robot's sake, the robot's news ends the wait (or, while another card crafts, that card's). */
	@Override
	public Object blocker(String method) {
		RoboticsDevice robot = bench.robot();
		if (toCraft > 0 || robot == null) {
			return null;
		}
		Object behind = robot.blocker(method);
		return behind != null ? behind : robot;
	}

	/** What it's doing now. */
	public String state() {
		return toCraft > 0 ? CRAFTING : IDLE;
	}

	private Result needsRobotics() {
		return bench.robotics() ? null : Result.fail(NO_ROBOTICS);
	}

	@Method(doc = "list(): what its slots hold: a list of those with something in, each {slot, name, count, ...} (1 to 9 its crafting grid, 10 to 25 its storage)")
	public Result list(Arguments args) {
		Result no = needsRobotics();
		return no != null ? no : bench.list();
	}

	@Method(doc = "craft([count]): crafts what its grid makes, count times (1), each taking half a second, what it makes going to its storage; its robot is busy meanwhile. A command: true at once (its event: craft, ok, result {name, count}, or not and why: no recipe, no room), or false and why")
	public Result craft(Arguments args) {
		Result no = needsRobotics();
		if (no != null) {
			return no;
		}
		int count = args.count() > 0 ? count(args, 0) : 1;
		if (toCraft > 0) {
			return Result.fail(BUSY);
		}
		RoboticsDevice robot = bench.robot();
		if (robot != null) {
			String refused = robot.begin("craft", this); // (the robot's busy while it crafts, the user's)
			if (refused != null) {
				return Result.fail(refused);
			}
		}
		toCraft = count;
		crafted = 0;
		ticksLeft = CRAFT_TICKS;
		changed();
		return Result.ok(true);
	}

	@Method(doc = "move(from, to[, count]): moves items from one of its slots to another (all, or count): how many went")
	public Result move(Arguments args) {
		Result no = needsRobotics();
		if (no != null) {
			return no;
		}
		power.charge(MOVE_COST);
		return bench.move(slot(args, 0), slot(args, 1), args.count() > 2 ? count(args, 2) : Integer.MAX_VALUE);
	}

	@Method(doc = "equip(slot): swaps one of its slots with what the robot holds")
	public Result equip(Arguments args) {
		Result no = needsRobotics();
		if (no != null) {
			return no;
		}
		power.charge(MOVE_COST);
		return bench.equip(slot(args, 0));
	}

	@Method(doc = "pickup(): picks up the items lying round the robot (within a block and a half) into its storage: how many")
	public Result pickup(Arguments args) {
		Result no = needsRobotics();
		if (no != null) {
			return no;
		}
		power.charge(MOVE_COST);
		return bench.pickup();
	}

	/** Where a drop goes when no face is given: below the robot (the user's: as a hopper under it catches). */
	public static final String DROP_FACE = "down";
	/** Where a container is when no face is given: in front of the robot. */
	public static final String CONTAINER_FACE = "front";

	@Method(doc = "drop([face][, slot[, count]]): drops items at a face of the robot (front, back, left, right, up or down; below when none is given): from a slot (all, or count), or with none, everything in its storage (not its grid); a container there is filled first, the rest go into the world: how many went into the world")
	public Result drop(Arguments args) {
		Result no = needsRobotics();
		if (no != null) {
			return no;
		}
		int at = faceGiven(args) ? 1 : 0;
		String face = at == 1 ? face(args, 0) : DROP_FACE;
		power.charge(MOVE_COST);
		return bench.drop(face, given(args, at) ? slot(args, at) : 0, given(args, at + 1) ? count(args, at + 1) : Integer.MAX_VALUE);
	}

	@Method(doc = "store([face][, from[, count[, to]]]): puts items into the container at a face of the robot (in front when none is given): from one of its slots (all, or count), or with none, everything in its storage (not its grid); into the container's slot to, or where they fit: true, 0 when none of it is left; false and how many are, when there's no room; or false and why (no container there)")
	public Result store(Arguments args) {
		Result no = needsRobotics();
		if (no != null) {
			return no;
		}
		int at = faceGiven(args) ? 1 : 0;
		String face = at == 1 ? face(args, 0) : CONTAINER_FACE;
		power.charge(MOVE_COST);
		return bench.store(face, given(args, at) ? slot(args, at) : 0, given(args, at + 1) ? count(args, at + 1) : Integer.MAX_VALUE,
			given(args, at + 2) ? count(args, at + 2) : 0);
	}

	@Method(doc = "take([face][, from[, count[, to]]]): takes items from the container at a face of the robot (in front when none is given): from its slot from, or the first it can; all, or count; into its own slot to (its grid's too), or where they fit in its storage: how many came")
	public Result take(Arguments args) {
		Result no = needsRobotics();
		if (no != null) {
			return no;
		}
		int at = faceGiven(args) ? 1 : 0;
		String face = at == 1 ? face(args, 0) : CONTAINER_FACE;
		power.charge(MOVE_COST);
		return bench.take(face, given(args, at) ? count(args, at) : 0, given(args, at + 1) ? count(args, at + 1) : Integer.MAX_VALUE,
			given(args, at + 2) ? slot(args, at + 2) : 0);
	}

	@Method(doc = "set([face]): drops everything in its storage at a face (below), as drop(face) does: what its file's lines do")
	public Result set(Arguments args) {
		Result no = needsRobotics();
		if (no != null) {
			return no;
		}
		String face = given(args, 0) ? face(args, 0) : DROP_FACE;
		power.charge(MOVE_COST);
		Result dropped = bench.drop(face, 0, Integer.MAX_VALUE);
		return dropped.failed() ? dropped : Result.ok(true);
	}

	/** Whether its first argument is a face (a name: slots and counts are numbers). */
	private static boolean faceGiven(Arguments args) {
		return args.count() > 0 && args.get(0) instanceof String;
	}

	private static boolean given(Arguments args, int n) {
		return args.count() > n && args.get(n) != null;
	}

	@Method(doc = "get(): what its slots hold, as list's: its file's read")
	public Result get(Arguments args) {
		Result no = needsRobotics();
		return no != null ? no : bench.list();
	}

	@Method(doc = "pull(): its oldest event, a craft's end ({event = \"craft\", ok, why, result = {name, count}}), or nil")
	public Result pull(Arguments args) {
		return Result.ok(events.pull());
	}

	/** One of its slots, argument {@code n}: 1 to {@link #SLOTS}. */
	private static int slot(Arguments args, int n) {
		double value = args.checkNumber(n);
		if (value < 1 || value > SLOTS || value != Math.floor(value)) {
			throw new Arguments.ArgumentError("a slot from 1 to " + SLOTS + " expected, got " + args.get(n));
		}
		return (int) value;
	}

	private static int count(Arguments args, int n) {
		double value = args.checkNumber(n);
		if (value < 1 || value != Math.floor(value)) {
			throw new Arguments.ArgumentError("a count of 1 or more expected, got " + args.get(n));
		}
		return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
	}

	private static String face(Arguments args, int n) {
		Object name = args.get(n);
		if (!(name instanceof String face) || !RoboticsDevice.FACES.contains(face)) {
			throw new Arguments.ArgumentError("a face expected (front, back, left, right, up or down), got " + name);
		}
		return face;
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

	/** It stopped taking data: its events go, and a craft under way stops. */
	@Override
	protected void dropData() {
		if (events.clear()) {
			interrupted = false; // (a fresh start: an old program's end isn't the next one's)
		}
		if (toCraft > 0 && bench.robot() != null) {
			bench.robot().done(this);
		}
		toCraft = 0;
		crafted = 0;
	}

	@Override
	public void powerOff() {
		dropData();
	}

	/** Its events waiting (a craft under way doesn't go on after a load: its event says so). */
	@Override
	public Map<String, Object> saved() {
		Map<String, Object> saved = new LinkedHashMap<>();
		events.save(saved);
		saved.put("crafting", toCraft > 0);
		return saved;
	}

	/** Why a craft under way at a save ended at the load. */
	public static final String INTERRUPTED = "interrupted";
	private boolean interrupted;

	@Override
	public void restore(Map<String, Object> saved) {
		events.restore(saved);
		interrupted = Boolean.TRUE.equals(saved.get("crafting"));
	}

	@Override
	public void accepting(boolean accepting) {
		super.accepting(accepting);
		if (accepting && interrupted) {
			interrupted = false;
			finish("failed", INTERRUPTED);
		}
	}
}
