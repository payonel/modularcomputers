package com.payonel.modularcomputers.device;

import com.payonel.modularcomputers.bus.InterruptLine;
import com.payonel.modularcomputers.runtime.Arguments;
import com.payonel.modularcomputers.runtime.Method;
import com.payonel.modularcomputers.runtime.Result;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A redstone card (user design, 2026-10-13): it sets the redstone signal on any face of the block
 * it's in (a computer, a hub), 0 to 15, and reads what comes in on each face; a change coming in is
 * an event (its interrupt). Sides are world directions.
 */
public final class RedstoneCard extends Card {
	public static final List<String> SIDES = List.of("down", "up", "north", "south", "west", "east");
	private final InterruptLine line = new InterruptLine();
	/** It works while a running computer has it on its bus (it draws nothing to speak of). */
	private final com.payonel.modularcomputers.bus.BusPower power = new com.payonel.modularcomputers.bus.BusPower(0);
	private final EventQueue events = new EventQueue(line, this::changed);
	private final int[] input = new int[6];
	private final int[] output = new int[6];
	/** Tells the world its outputs changed (the block it's in updates its neighbours). */
	private Runnable onOutput = () -> {
	};

	public RedstoneCard() {
		super(CardKind.REDSTONE);
	}

	public void onOutput(Runnable onOutput) {
		this.onOutput = onOutput;
	}

	/** What it puts out on a side (index as {@link #SIDES}). */
	public int output(int side) {
		return output[side];
	}

	/** What came in on a side, seen in the world: a change is an event. */
	public void sense(int side, int value) {
		if (input[side] != value) {
			Map<String, Object> event = new LinkedHashMap<>();
			event.put("name", "redstone");
			event.put("side", SIDES.get(side));
			event.put("old", (long) input[side]);
			event.put("new", (long) value);
			input[side] = value;
			events.add(event);
		}
	}

	private static int side(Arguments args) {
		int side = SIDES.indexOf(args.checkString(0).toLowerCase(Locale.ROOT));
		if (side < 0) {
			throw new IllegalArgumentException("no such side: " + args.checkString(0));
		}
		return side;
	}

	@Method(doc = "getInput(side): the signal coming in on that side, 0 to 15")
	public Result getInput(Arguments args) {
		return Result.ok((long) input[side(args)]);
	}

	@Method(doc = "getOutput(side): the signal it puts out on that side")
	public Result getOutput(Arguments args) {
		return Result.ok((long) output[side(args)]);
	}

	@Method(doc = "setOutput(side, value): puts out a signal on that side, 0 to 15; returns what it was")
	public Result setOutput(Arguments args) {
		int side = side(args);
		int value = (int) Math.clamp(args.checkInteger(1), 0, 15);
		int was = output[side];
		if (was != value) {
			output[side] = value;
			changed();
			onOutput.run();
		}
		return Result.ok((long) was);
	}

	@Method(doc = "pull(): the oldest change that came in, as {name, side, old, new}, or nil")
	public Result pull(Arguments args) {
		return Result.ok(events.pull());
	}

	@Override
	public com.payonel.modularcomputers.bus.BusPower power() {
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
		saved.put("input", java.util.Arrays.stream(input).mapToObj(v -> (Object) (long) v).toList());
		saved.put("output", java.util.Arrays.stream(output).mapToObj(v -> (Object) (long) v).toList());
		return saved;
	}


	@Override
	public void restore(Map<String, Object> saved) {
		events.restore(saved);
		restoreSides(saved.get("input"), input);
		restoreSides(saved.get("output"), output);
	}

	private static void restoreSides(Object kept, int[] sides) {
		if (kept instanceof java.util.List<?> values) {
			for (int i = 0; i < sides.length && i < values.size(); i++) {
				sides[i] = values.get(i) instanceof Long v ? (int) Math.clamp(v, 0, 15) : 0;
			}
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

	/** Power's gone: its outputs go to 0. */
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
