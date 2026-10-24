package com.payonel.modularcomputers.device;

import com.payonel.modularcomputers.bus.BusPower;
import com.payonel.modularcomputers.bus.InterruptLine;
import com.payonel.modularcomputers.runtime.Arguments;
import com.payonel.modularcomputers.runtime.Method;
import com.payonel.modularcomputers.runtime.Result;
import java.util.Map;

/**
 * An audio sensor card (user design, 2026-10-24): an event (its interrupt) for each sound the world
 * plays that a player standing at the block it's in would hear (the game's own range for that
 * sound): what it sounds like, as its subtitle says it in English ({@code sound}: {@code "Pig
 * oinks"}), and where it came from, relative to the block's center ({@code x, y, z}). It's blind,
 * as the motion sensor is deaf: nothing of what made the sound (no id, no category, no mob). Its events wait in a buffer of its own, as the
 * motion sensor's do, until a program reads them, one each.
 */
public final class AudioSensorCard extends Card {
	/** Its draw a tick, as a motion sensor's. */
	public static final int DRAW = 9;
	private final InterruptLine line = new InterruptLine();
	private final EventQueue events = new EventQueue(line, this::changed, this::isAccepting);
	private final BusPower power = new BusPower(DRAW);

	public AudioSensorCard() {
		super(CardKind.AUDIO_SENSOR);
	}

	/** A sound it heard, in the world: {sound, x, y, z}. */
	public void hear(Map<String, Object> event) {
		events.add(event);
	}

	@Method(doc = "pull(): the oldest sound it heard, as {sound, x, y, z} (its subtitle, in English: \"Pig oinks\"; and where, relative to the block it's in), or nil")
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
