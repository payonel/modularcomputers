package com.payonel.modularcomputers.bus;

/** What a device on the bus says it is, the way a real device reports its class. */
public enum DeviceType {
	DISPLAY,
	KEYBOARD,
	STORAGE,
	NETWORK,
	/** Firmware (the EEPROM), listed so programs can read or reflash it. */
	FIRMWARE,
	/** Backup power (the UPS battery). */
	POWER,
	REDSTONE,
	AUDIO,
	SENSOR,
	/** Something that moves things in the world (the robotic piston). */
	ACTUATOR,
}
