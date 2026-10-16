package com.payonel.modularcomputers.bus;

/** What a device on the bus says it is, the way a real device reports its class. */
public enum DeviceType {
	/** A text screen and a keyboard in one. */
	TERMINAL,
	STORAGE,
	NETWORK,
	/**
	 * The EEPROM: the chip the CPU boots, its code user code (the user can change it), listed so
	 * programs can read or reflash it. Not "firmware": that's the CPU's own layer, machine.lua,
	 * which programs can't reach (user, 2026-10-15).
	 */
	EEPROM,
	REDSTONE,
	AUDIO,
	SENSOR,
	/** Something that moves things in the world (the robotic piston). */
	ACTUATOR,
	/** Something that makes things (the photomask, which prints chips). */
	FABRICATOR,
}
