package com.payonel.modularcomputers.device;

import com.payonel.modularcomputers.bus.BusPower;
import com.payonel.modularcomputers.bus.InterruptLine;
import com.payonel.modularcomputers.runtime.Arguments;
import com.payonel.modularcomputers.runtime.Method;
import com.payonel.modularcomputers.runtime.Result;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A motion sensor card (user design, 2026-10-13): events (its interrupt) for what moves within its
 * range of the block it's in: mobs and players ({@code entity}), item drops ({@code drop}), fluid
 * newly flowing ({@code fluid}), doors, trapdoors and gates opening or closing ({@code door}). Each
 * event says where, relative to the block ({@code x, y, z}). Its mode picks what it reports; its range
 * (1 to {@link #MAX_RANGE}) costs power each tick, more the farther it looks.
 */
public final class MotionSensorCard extends Card {
	public static final int MAX_RANGE = 32;
	public static final List<String> MODES = List.of("all", "entities", "drops", "blocks");
	/** Its draw a tick: a little, and more for each block of range. */
	public static final int BASE_DRAW = 1;
	public static final int DRAW_PER_RANGE = 1;
	private final InterruptLine line = new InterruptLine();
	private final EventQueue events = new EventQueue(line);
	private final BusPower power = new BusPower(BASE_DRAW + 8 * DRAW_PER_RANGE);
	private String mode = "all";
	private int range = 8;

	public MotionSensorCard() {
		super(CardKind.MOTION_SENSOR);
	}

	public int range() {
		return range;
	}

	/** Something moved, seen in the world: {@code kind} entity, drop, fluid or door. Kept if its mode wants it. */
	public void sense(String kind, Map<String, Object> event) {
		boolean wanted = switch (mode) {
			case "entities" -> kind.equals("entity");
			case "drops" -> kind.equals("drop");
			case "blocks" -> kind.equals("fluid") || kind.equals("door");
			default -> true;
		};
		if (wanted) {
			var full = new java.util.LinkedHashMap<String, Object>();
			full.put("name", "motion");
			full.put("kind", kind);
			full.putAll(event);
			events.add(full);
		}
	}

	@Method(doc = "setMode(mode): what it reports: all (the default), entities (mobs and players), drops (items), or blocks (fluid flowing, doors)")
	public Result setMode(Arguments args) {
		String wanted = args.checkString(0).toLowerCase(Locale.ROOT);
		if (!MODES.contains(wanted)) {
			return Result.fail("no such mode: " + wanted);
		}
		mode = wanted;
		return Result.ok();
	}

	@Method(doc = "getMode(): what it reports")
	public Result getMode(Arguments args) {
		return Result.ok(mode);
	}

	@Method(doc = "setRange(blocks): how far it looks, 1 to " + MAX_RANGE + "; more costs more power a tick")
	public Result setRange(Arguments args) {
		range = (int) Math.clamp(args.checkInteger(0), 1, MAX_RANGE);
		power.setDraw(BASE_DRAW + range * DRAW_PER_RANGE);
		return Result.ok((long) range);
	}

	@Method(doc = "getRange(): how far it looks, in blocks")
	public Result getRange(Arguments args) {
		return Result.ok((long) range);
	}

	@Method(doc = "pull(): the oldest event, as {name, kind, type, x, y, z} (where, relative to the block it's in), or nil")
	public Result pull(Arguments args) {
		return Result.ok(events.pull());
	}

	@Override
	public BusPower power() {
		return power;
	}

	@Override
	public String streamMethod() {
		return "pull";
	}

	/** Its events waiting, and the mode and range the program set. */
	@Override
	public Map<String, Object> saved() {
		Map<String, Object> saved = new java.util.LinkedHashMap<>();
		events.save(saved);
		saved.put("mode", mode);
		saved.put("range", (long) range);
		return saved;
	}

	@Override
	public void restore(Map<String, Object> saved) {
		events.restore(saved);
		if (saved.get("mode") instanceof String kept && MODES.contains(kept)) {
			mode = kept;
		}
		if (saved.get("range") instanceof Long kept) {
			range = (int) Math.clamp(kept, 1, MAX_RANGE);
			power.setDraw(BASE_DRAW + range * DRAW_PER_RANGE);
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

	@Override
	public void powerOff() {
		events.clear();
	}
}
