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
 * do, until a program reads them, one each. A program sets nothing on it: finer kinds of motion (fluids, drops, filters) may make a
 * tier 2 (user, 2026-10-21). How often it looks and how far a creature must move are the server's
 * settings ({@code device.motion_sensor.interval}, {@code .threshold}), read at each look.
 */
public final class MotionSensorCard extends Card {
	/** How far it looks, in blocks. */
	public static final int RANGE = 8;
	private final InterruptLine line = new InterruptLine();
	private final EventQueue events = new EventQueue(com.payonel.modularcomputers.bus.DeviceType.MOTION, line, this::changed, this::isAccepting);
	private final BusPower power = new BusPower(() -> com.payonel.modularcomputers.config.ModConfig.draws().motionSensor());

	/** Whether a sensor looks this tick of the world's, looking every {@code interval} ticks (1 or more). */
	public static boolean looks(long tick, int interval) {
		return tick % interval == 0;
	}

	/**
	 * Whether a creature at {@code at} ({x, y, z}) has moved since a look saw it at {@code was} (null:
	 * not seen then, so it has): by {@code threshold} blocks or more along any one axis. A threshold
	 * of 0 counts every creature seen, moved or not.
	 */
	public static boolean moved(double[] was, double[] at, double threshold) {
		if (was == null) {
			return true;
		}
		for (int axis = 0; axis < at.length; axis++) {
			if (Math.abs(at[axis] - was[axis]) >= threshold) {
				return true;
			}
		}
		return false;
	}

	public MotionSensorCard() {
		super(CardKind.MOTION_SENSOR);
	}

	/** A creature moved, seen in the world: {event = "motion", name, at}. */
	public void sense(Map<String, Object> event) {
		events.add(event);
	}

	@Method(doc = "pull(): the oldest motion, as {event = \"motion\", name, at} (name its type; at where, {forward, right, up}, in its computer's frame: forward out of its front, right, up; a robot's own), or nil")
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
