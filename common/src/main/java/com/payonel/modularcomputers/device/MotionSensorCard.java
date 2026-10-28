package com.payonel.modularcomputers.device;

import com.payonel.modularcomputers.bus.BusPower;
import com.payonel.modularcomputers.bus.InterruptLine;
import com.payonel.modularcomputers.runtime.Arguments;
import com.payonel.modularcomputers.runtime.Method;
import com.payonel.modularcomputers.runtime.Result;
import java.util.Map;

/**
 * A motion sensor card (user design, 2026-10-13; simplified 2026-10-21): an event (its interrupt)
 * for each mob that moves within {@link #RANGE} blocks of the block it's in, players too: which
 * mob ({@code mob}, its type: {@code "minecraft:pig"}) and where, in its computer's frame ({@code
 * forward, right, up}: a block's front and right as {@link Face} names them, a robot's heading and
 * its own right; as the energy field card's). Its events wait in a buffer of its own, as typed keys
 * do, until a program reads them, one each. It has no settings: finer kinds of motion (fluids, drops, filters) may make a
 * tier 2 (user, 2026-10-21).
 */
public final class MotionSensorCard extends Card {
	/** How far it looks, in blocks. */
	public static final int RANGE = 8;
	/** Its draw a tick. */
	public static final int DRAW = 9;
	private final InterruptLine line = new InterruptLine();
	private final EventQueue events = new EventQueue(line, this::changed, this::isAccepting);
	private final BusPower power = new BusPower(DRAW);

	public MotionSensorCard() {
		super(CardKind.MOTION_SENSOR);
	}

	/** A mob moved, seen in the world: {mob, forward, right, up}. */
	public void sense(Map<String, Object> event) {
		events.add(event);
	}

	@Method(doc = "pull(): the oldest motion, as {mob, forward, right, up} (its type, and where, in its computer's frame: forward out of its front, right, up; a robot's own), or nil")
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

	/** Its events waiting. */
	@Override
	public Map<String, Object> saved() {
		Map<String, Object> saved = new java.util.LinkedHashMap<>();
		events.save(saved);
		return saved;
	}

	@Override
	public void restore(Map<String, Object> saved) {
		events.restore(saved);
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

	@Override
	public void powerOff() {
		events.clear();
	}
}
