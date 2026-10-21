package com.payonel.modularcomputers.device;

import com.payonel.modularcomputers.bus.BusPower;
import com.payonel.modularcomputers.bus.Device;
import com.payonel.modularcomputers.bus.DeviceType;
import com.payonel.modularcomputers.bus.InterruptLine;
import com.payonel.modularcomputers.power.PowerUnits;
import com.payonel.modularcomputers.runtime.Arguments;
import com.payonel.modularcomputers.runtime.Method;
import com.payonel.modularcomputers.runtime.Result;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A redstone terminal, tier 1 (user design, 2026-10-20): a block on the cable network whose six
 * sides each are a cable side or a redstone side. A side with a cable against it is a cable side
 * (it joins the network), unless its port is closed; any other side is a redstone side: it puts out
 * the signal a program set and reads what comes in. Changing a signal costs power; what's put out
 * holds when the power's gone, as a latch does. A change coming in on a redstone side is an event.
 * <p>
 * Programs see the sides as a table, by name: {@code {north = {mode = "redstone", level = 7,
 * powered = 7}, ...}}. {@code mode} is {@code "cable"} or {@code "redstone"}; {@code level} is a
 * cable side's cable (1) or the signal on a redstone side (0 to 15): the strongest there, coming in
 * or its own (user, 2026-10-20: the level detected, powered or not); {@code powered} the signal the
 * terminal puts out there (0 on a cable side: it puts out nothing there). {@code get} and
 * {@code set} answer the whole table; {@code set} takes the same table, and changes only what it
 * names: {@code powered} turns a side's signal on (its level is what's detected, so {@code set}
 * ignores it).
 */
public final class RedstoneTerminal implements Device {
	/** The sides, as programs name them. */
	public static final List<String> SIDES = List.of("north", "south", "east", "west", "top", "bottom");
	/** A redstone signal's strength, at most (Minecraft's 0 to 15). */
	public static final int MAX_SIGNAL = 15;
	/** What changing a side's signal costs: a tick of a running CPU. */
	public static final int CHANGE_COST = PowerUnits.CPU;
	public static final String CABLE = "cable";
	public static final String REDSTONE = "redstone";
	/** A cable side's level: a cable is there. */
	public static final long CABLE_LEVEL = 1;
	/** The colors a tier 2 terminal will read on its own (a bundled cable's channels); not this tier's. */
	private static final Set<String> COLORS = Set.of("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
		"light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");

	/** What the terminal sees of the world, and changes in it (its block). */
	public interface World {
		/** A cable side: something that carries the connection is against it, and its port is open. */
		boolean cable(int side);

		/** The signal coming in on a side. */
		int input(int side);

		/** Opens or closes a side's cable port (closed: no cable joins there). */
		void port(int side, boolean open);

		/** What it puts out changed: the world's neighbours look again. */
		void outputChanged();
	}

	/** Pays for a change: from its own battery, or from the bus. */
	public interface Payer {
		void pay(int cost);
	}

	private final InterruptLine line = new InterruptLine();
	private final BusPower power = new BusPower(0);
	private final EventQueue events = new EventQueue(line, this::changed, this::isAccepting);
	private final int[] output = new int[SIDES.size()];
	/** What it last saw coming in on each side: a change from it is an event. */
	private final int[] input = new int[SIDES.size()];
	private World world = new World() {
		@Override
		public boolean cable(int side) {
			return false;
		}

		@Override
		public int input(int side) {
			return input[side];
		}

		@Override
		public void port(int side, boolean open) {
		}

		@Override
		public void outputChanged() {
		}
	};
	private Payer payer = power::charge;
	private boolean accepting;
	private Runnable changed = () -> {
	};

	@Override
	public DeviceType type() {
		return DeviceType.REDSTONE;
	}

	@Override
	public Map<String, Object> info() {
		return Map.of("tier", 1L);
	}

	public void world(World world) {
		this.world = world;
	}

	public void payWith(Payer payer) {
		this.payer = payer;
	}

	@Override
	public BusPower power() {
		return power;
	}

	/** What it puts out on a side (index as {@link #SIDES}), whatever the side is now. */
	public int output(int side) {
		return output[side];
	}

	/** Whether a side is a redstone side now. */
	public boolean redstone(int side) {
		return !world.cable(side);
	}

	/** What came in on a redstone side, seen in the world: a change is an event. */
	public void sense(int side, int value) {
		if (input[side] != value) {
			Map<String, Object> event = new LinkedHashMap<>();
			event.put("name", "redstone");
			event.put("side", SIDES.get(side));
			event.put("old", (long) input[side]);
			event.put("new", (long) value);
			input[side] = value;
			changed.run();
			events.add(event);
		}
	}

	@Method(doc = "get([sides]): every side as {mode, level (a redstone side's signal, coming in or its own), powered (its own)} by name (north, south, east, west, top, bottom); with a table, only the sides it names (as keys or as a list)")
	public Result get(Arguments args) {
		Map<?, ?> asked = args.optTable(0);
		return Result.ok(sides(asked == null ? allSides() : named(asked)));
	}

	@Method(doc = "set(sides): sets each side named: mode (\"cable\" or \"redstone\": a cable side's port open or closed) and powered (the signal it puts out, 0 to 15; ignored on a cable side); level is what's detected, so it's ignored; returns every side, as get()")
	public Result set(Arguments args) {
		Map<?, ?> table = args.checkTable(0);
		Map<Integer, Setting> settings = new LinkedHashMap<>();
		for (Map.Entry<?, ?> entry : table.entrySet()) {
			int side = side(entry.getKey());
			if (!(entry.getValue() instanceof Map<?, ?> fields)) {
				throw new Arguments.ArgumentError(SIDES.get(side) + ": table expected");
			}
			settings.put(side, setting(SIDES.get(side), fields));
		}
		// All checked: now it changes, modes first (a side's signal counts where it's a redstone side).
		settings.forEach((side, setting) -> {
			if (setting.mode() != null) {
				world.port(side, setting.mode().equals(CABLE));
			}
		});
		boolean changedOutput = false;
		for (Map.Entry<Integer, Setting> entry : settings.entrySet()) {
			int side = entry.getKey();
			Integer powered = entry.getValue().powered();
			if (powered != null && redstone(side) && output[side] != powered) {
				payer.pay(CHANGE_COST);
				output[side] = powered;
				changedOutput = true;
			}
		}
		if (changedOutput) {
			changed.run();
			world.outputChanged();
		}
		return Result.ok(sides(allSides()));
	}

	@Method(doc = "pull(): the oldest change that came in on a redstone side, as {name, side, old, new}, or nil")
	public Result pull(Arguments args) {
		return Result.ok(events.pull());
	}

	/** One side's setting from a program: null where it said nothing. */
	private record Setting(String mode, Integer powered) {
	}

	private static Setting setting(String name, Map<?, ?> fields) {
		String mode = null;
		Object asked = fields.get("mode");
		if (asked != null) {
			if (!(asked instanceof String text) || !(text.equals(CABLE) || text.equals(REDSTONE))) {
				boolean color = asked instanceof String text && COLORS.contains(text);
				throw new Arguments.ArgumentError(name + ": " + (color ? "a tier 1 redstone terminal has no color mode" : "no such mode: " + asked));
			}
			mode = text;
		}
		Integer powered = null;
		Object value = fields.get("powered");
		if (value != null) {
			if (!(value instanceof Long || value instanceof Double d && d == Math.rint(d))) {
				throw new Arguments.ArgumentError(name + ": powered: integer expected");
			}
			powered = (int) Math.clamp(((Number) value).longValue(), 0, MAX_SIGNAL);
		}
		return new Setting(mode, powered); // (a level given is what get answered: detected, not set)
	}

	private static int side(Object name) {
		int side = name instanceof String text ? SIDES.indexOf(text) : -1;
		if (side < 0) {
			throw new Arguments.ArgumentError("no such side: " + name);
		}
		return side;
	}

	private static Set<Integer> allSides() {
		Set<Integer> all = new LinkedHashSet<>();
		for (int side = 0; side < SIDES.size(); side++) {
			all.add(side);
		}
		return all;
	}

	/** The sides a table names: its string keys, and the names in its list (other values ignored: not filters). */
	private static Set<Integer> named(Map<?, ?> table) {
		Set<Integer> sides = new LinkedHashSet<>();
		for (Map.Entry<?, ?> entry : table.entrySet()) {
			sides.add(side(entry.getKey() instanceof String ? entry.getKey() : entry.getValue()));
		}
		return sides;
	}

	private Map<String, Object> sides(Set<Integer> which) {
		Map<String, Object> sides = new LinkedHashMap<>();
		for (int side : which) {
			Map<String, Object> fields = new LinkedHashMap<>();
			boolean redstone = redstone(side);
			fields.put("mode", redstone ? REDSTONE : CABLE);
			fields.put("level", redstone ? (long) Math.max(world.input(side), output[side]) : CABLE_LEVEL);
			fields.put("powered", redstone ? (long) output[side] : 0L);
			sides.put(SIDES.get(side), fields);
		}
		return sides;
	}

	@Override
	public String streamMethod() {
		return "pull";
	}

	@Override
	public void onChange(Runnable changed) {
		this.changed = changed;
	}

	private void changed() {
		changed.run();
	}

	private boolean isAccepting() {
		return accepting;
	}

	@Override
	public void accepting(boolean accepting) {
		this.accepting = accepting;
		if (!accepting) {
			events.clear();
		}
	}

	@Override
	public boolean interruptPending() {
		return events.pending();
	}

	@Override
	public InterruptLine interruptLine() {
		return line;
	}

	/** Power's gone: what it puts out holds (a latch); only its events go. */
	@Override
	public void powerOff() {
		events.clear();
	}

	/** Its events waiting, what it puts out, and what it saw coming in (so a load isn't a change). */
	@Override
	public Map<String, Object> saved() {
		Map<String, Object> saved = new LinkedHashMap<>();
		events.save(saved);
		saved.put("input", Arrays.stream(input).mapToObj(v -> (Object) (long) v).toList());
		saved.put("output", Arrays.stream(output).mapToObj(v -> (Object) (long) v).toList());
		return saved;
	}

	@Override
	public void restore(Map<String, Object> saved) {
		events.restore(saved);
		restoreSides(saved.get("input"), input);
		restoreSides(saved.get("output"), output);
	}

	private static void restoreSides(Object kept, int[] sides) {
		if (kept instanceof List<?> values) {
			for (int i = 0; i < sides.length && i < values.size(); i++) {
				sides[i] = values.get(i) instanceof Long v ? (int) Math.clamp(v, 0, MAX_SIGNAL) : 0;
			}
		}
	}
}
