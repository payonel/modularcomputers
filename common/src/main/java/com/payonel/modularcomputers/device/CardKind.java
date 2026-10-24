package com.payonel.modularcomputers.device;

import com.payonel.modularcomputers.bus.DeviceType;

/** The expansion cards there are, and what each reports itself as on the bus. */
public enum CardKind {
	/** A wired network card: frames over the cables ({@link NetworkCard}). */
	NETWORK(DeviceType.NETWORK),
	/** A wireless one: frames through the air, without cables ({@link NetworkCard}). */
	/** WLAN cards, by tier: their reach is OpenComputers' (16 blocks, 400). */
	TIER1_WLAN(DeviceType.NETWORK),
	TIER2_WLAN(DeviceType.NETWORK),
	BLUETOOTH(DeviceType.BLUETOOTH),
	/** Emits and detects redstone where it's installed (a computer, or an expansion hub). */
	REDSTONE(DeviceType.REDSTONE),
	SOUND(DeviceType.SOUND),
	/** HTTP and HTTPS to the real internet (OpenComputers' internet card). */
	INTERNET(DeviceType.INTERNET),
	MOTION_SENSOR(DeviceType.MOTION),
	AUDIO_SENSOR(DeviceType.HEARING),
	WEATHER_SENSOR(DeviceType.WEATHER);

	private final DeviceType type;

	CardKind(DeviceType type) {
		this.type = type;
	}

	public DeviceType type() {
		return type;
	}
}
