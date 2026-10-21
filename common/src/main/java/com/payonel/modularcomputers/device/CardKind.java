package com.payonel.modularcomputers.device;

import com.payonel.modularcomputers.bus.DeviceType;

/** The expansion cards there are, and what each reports itself as on the bus. None work yet. */
public enum CardKind {
	WLAN(DeviceType.NETWORK),
	BLUETOOTH(DeviceType.NETWORK),
	/** Will emit and detect redstone where it's installed (a computer, or an expansion hub). */
	REDSTONE(DeviceType.REDSTONE),
	AUDIO(DeviceType.AUDIO),
	MOTION_SENSOR(DeviceType.SENSOR),
	AUDIO_SENSOR(DeviceType.SENSOR),
	WEATHER_SENSOR(DeviceType.SENSOR);

	private final DeviceType type;

	CardKind(DeviceType type) {
		this.type = type;
	}

	public DeviceType type() {
		return type;
	}
}
