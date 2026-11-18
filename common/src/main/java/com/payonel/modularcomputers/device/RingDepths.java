package com.payonel.modularcomputers.device;

import com.payonel.modularcomputers.bus.DeviceType;
import java.util.EnumMap;
import java.util.Map;

/**
 * How deep each device's rings are (the user's, 2026-11-11): the events it keeps for its program, its
 * news window and its unread command ends alike (a network card's frames and its log too). A device
 * type's own depth when the server sets one ({@code device.ring_depth.<type>}), else everyone's
 * ({@code device.ring_depth.all}). The device is the front line against bursts, so its rings are
 * deep; mcos's own (a file's copy) are its own business, and small.
 */
public final class RingDepths {
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

	/** The depths now: the settings', their default till they load. */
	private static volatile Depths now = new Depths(com.payonel.modularcomputers.config.Defaults.Device.RingDepth.ALL, Map.of());

	private RingDepths() {
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
