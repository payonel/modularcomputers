package com.payonel.modularcomputers.client;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Util;

/**
 * The bus order panel's bulb: makes a device stand out in the world for a while (an outline
 * that pulses, seen through walls), like the identify light on a server. Only the player who
 * pressed it sees it. It's never saved: it times out, and leaving the world forgets it.
 */
public final class Identify {
	/** How long a device stays outlined. */
	public static final long DURATION_MS = 10_000;
	private static final long PULSE_MS = 1_000;
	private static final Map<BlockPos, Long> until = new HashMap<>();

	private Identify() {
	}

	/** Starts outlining the device, or stops if it already is. */
	public static void toggle(BlockPos pos) {
		if (isOn(pos)) {
			until.remove(pos);
		} else {
			until.put(pos.immutable(), Util.getMillis() + DURATION_MS);
		}
	}

	public static boolean isOn(BlockPos pos) {
		Long end = until.get(pos);
		return end != null && Util.getMillis() < end;
	}

	/** The outline's opacity now: pulses between half and full while on, 0 when off. */
	public static float pulse(BlockPos pos) {
		if (!isOn(pos)) {
			return 0;
		}
		double phase = (Util.getMillis() % PULSE_MS) / (double) PULSE_MS;
		return (float) (0.75 + 0.25 * Math.cos(phase * 2 * Math.PI));
	}

	/** Forgets everything, e.g. when leaving a world. */
	public static void clear() {
		until.clear();
	}
}
