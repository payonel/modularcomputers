package com.payonel.modularcomputers.device;

import com.payonel.modularcomputers.bus.BusPower;
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
import java.util.function.IntUnaryOperator;

/**
 * A redstone card (user design, 2026-10-13; its table API 2026-10-21): it puts out a redstone
 * signal on any face of the block it's in (a computer, a hub), 0 to 15, and reads what's on each
 * face; a change coming in is an event (its interrupt). A block may hold several: it puts out the
 * strongest of theirs on each face.
 * <p>
 * Programs see the faces as a table, by name: {@code {north = {level = 7, powered = 7}, ...}}.
 * {@code powered} is the signal this card puts out there; {@code level} the strongest there,
 * coming in or put out by any card in the block (so a second card reads {@code level = 15,
 * powered = 0} beside a first that puts out 15). {@code get} and {@code set} answer the whole table;
 * {@code set} takes the same table and changes only what it names: {@code powered} (a level is what's
 * detected, so it's ignored). A change costs power; without power, it puts out nothing.
 */
public final class RedstoneCard extends Card {
	/** The faces, as programs name them. */
	public static final List<String> SIDES = List.of("north", "south", "east", "west", "top", "bottom");
	/** A redstone signal's strength, at most (Minecraft's 0 to 15). */
	public static final int MAX_SIGNAL = 15;
	/** What changing a face's signal costs: a tick of a running CPU. */
	public static final int CHANGE_COST = PowerUnits.CPU;
	private final InterruptLine line = new InterruptLine();
	/** It works while a running computer has it on its bus (it draws nothing to speak of at rest). */
	private final BusPower power = new BusPower(0);
	private final EventQueue events = new EventQueue(line, this::changed, this::isAccepting);
	private final int[] output = new int[SIDES.size()];
	/** What it last saw coming in on each face (from outside the block): a change is an event. */
	private final int[] input = new int[SIDES.size()];
	/** What the block's cards put out on a face, the strongest: this card alone, until the block says. */
	private IntUnaryOperator block = side -> output[side];
	/** Tells the world its outputs changed (the block it's in updates its neighbours). */
	private Runnable onOutput = () -> {
	};

	public RedstoneCard() {
		super(CardKind.REDSTONE);
	}

	public void onOutput(Runnable onOutput) {
		this.onOutput = onOutput;
	}

	/** How the block finds what its cards put out on a face (the strongest). */
	public void block(IntUnaryOperator block) {
		this.block = block;
	}

	/** What it puts out on a face (index as {@link #SIDES}). */
	public int output(int side) {
		return output[side];
	}

	/** What came in on a face, seen in the world: a change is an event. */
	public void sense(int side, int value) {
		if (input[side] != value) {
			Map<String, Object> event = new LinkedHashMap<>();
			event.put("name", "redstone");
			event.put("side", SIDES.get(side));
			event.put("old", (long) input[side]);
			event.put("new", (long) value);
			input[side] = value;
			changed();
			events.add(event);
		}
	}

	@Method(doc = "get([sides]): every face as {level (the signal there, coming in or put out by the block's cards), powered (this card's)} by name (north, south, east, west, top, bottom); with a table, only the faces it names (as keys or as a list)")
	public Result get(Arguments args) {
		Map<?, ?> asked = args.optTable(0);
		return Result.ok(sides(asked == null ? allSides() : named(asked)));
	}

	@Method(doc = "set(sides): sets each face named: powered, the signal it puts out (0 to 15); level is what's detected, so it's ignored; returns every face, as get()")
	public Result set(Arguments args) {
		Map<Integer, Integer> settings = new LinkedHashMap<>();
		for (Map.Entry<?, ?> entry : args.checkTable(0).entrySet()) {
			int side = side(entry.getKey());
			if (!(entry.getValue() instanceof Map<?, ?> fields)) {
				throw new Arguments.ArgumentError(SIDES.get(side) + ": table expected");
			}
			Object value = fields.get("powered");
			if (value != null) {
				if (!(value instanceof Long || value instanceof Double d && d == Math.rint(d))) {
					throw new Arguments.ArgumentError(SIDES.get(side) + ": powered: integer expected");
				}
				settings.put(side, (int) Math.clamp(((Number) value).longValue(), 0, MAX_SIGNAL));
			}
		}
		// All checked: now it changes.
		boolean changedOutput = false;
		for (Map.Entry<Integer, Integer> setting : settings.entrySet()) {
			if (output[setting.getKey()] != setting.getValue()) {
				power.charge(CHANGE_COST);
				output[setting.getKey()] = setting.getValue();
				changedOutput = true;
			}
		}
		if (changedOutput) {
			changed();
			onOutput.run();
		}
		return Result.ok(sides(allSides()));
	}

	@Method(doc = "pull(): the oldest change that came in, as {name, side, old, new}, or nil")
	public Result pull(Arguments args) {
		return Result.ok(events.pull());
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

	/** The faces a table names: its string keys, and the names in its list (other values ignored: not filters). */
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
			fields.put("level", (long) Math.max(input[side], Math.max(output[side], block.applyAsInt(side))));
			fields.put("powered", (long) output[side]);
			sides.put(SIDES.get(side), fields);
		}
		return sides;
	}

	@Override
	public BusPower power() {
		return power;
	}

	@Override
	public String streamMethod() {
		return "pull";
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

	/** It stopped taking data: its events go. */
	@Override
	protected void dropData() {
		events.clear();
	}

	@Override
	public boolean interruptPending() {
		return events.pending();
	}

	@Override
	public InterruptLine interruptLine() {
		return line;
	}

	/** Power's gone: it puts out nothing. */
	@Override
	public void powerOff() {
		boolean any = false;
		for (int i = 0; i < output.length; i++) {
			any |= output[i] != 0;
			output[i] = 0;
		}
		events.clear();
		if (any) {
			changed();
			onOutput.run();
		}
	}
}
