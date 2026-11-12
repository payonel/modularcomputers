package com.payonel.modularcomputers.device;

import com.payonel.modularcomputers.bus.DeviceType;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * How deep each device's rings are (the user's, 2026-11-11): the events it keeps for its program, its
 * news window and its unread command ends alike (a network card's frames and its log too). A device
 * type's own depth when the server sets one ({@code <type>_ring_depth}), else everyone's ({@code
 * ring_depth}), else {@link #DEFAULT}. The device is the front line against bursts, so its rings are
 * deep; mcos's own (a file's copy) are its own business, and small.
 */
public final class RingDepths {
	/** Every device's depth when the server sets none. */
	public static final int DEFAULT = 512;
	/** The setting for every device's depth. */
	public static final String GLOBAL_KEY = "ring_depth";
	/** The depths: everyone's, and the types that set their own. */
	public record Depths(int global, Map<DeviceType, Integer> types) {
		public Depths {
			global = Math.max(1, global);
			Map<DeviceType, Integer> kept = new EnumMap<>(DeviceType.class);
			types.forEach((type, value) -> kept.put(type, Math.max(1, value)));
			types = Map.copyOf(kept);
		}

		/** A type's depth: its own, else everyone's (null: everyone's). */
		public int of(DeviceType type) {
			return type == null ? global : types.getOrDefault(type, global);
		}
	}

	private static volatile Depths now = new Depths(DEFAULT, Map.of());

	private RingDepths() {
	}

	/** The setting for one type's depth: its name, as /sys names its class. */
	public static String key(DeviceType type) {
		return type.name().toLowerCase(Locale.ROOT) + "_ring_depth";
	}

	/** A type's depth now (null: everyone's). */
	public static int of(DeviceType type) {
		return now.of(type);
	}

	/** The server's settings (ModConfig's). */
	public static void configure(Depths depths) {
		now = depths;
	}
}
