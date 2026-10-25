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
	/** A sound card: a DAC, playing the samples programs write (OSS's /dev/dsp in mcos). */
	SOUND,
	/** An audio sensor: the sounds it hears near it, as the game's subtitles report them (motion's naming). */
	HEARING,
	/** An operator card: server commands, creative only. */
	OPERATOR,
	/** An internet card: HTTP(S) requests to the real internet. */
	INTERNET,
	/** A Bluetooth card: the link it makes to another block's board. */
	BLUETOOTH,
	/** A motion sensor: what moves near it (user, 2026-10-21: motion, not just "sensor"). */
	MOTION,
	/** A weather sensor: the weather where it is. */
	WEATHER,
	/** Something that moves things in the world (the robotic piston). */
	ACTUATOR,
	/** Something that makes things (the photomask, which prints chips). */
	FABRICATOR,
	/** A Disk Station: a dock that formats, wipes, clones and installs disks. */
	DISK_STATION,
	/** A robot's robotics device: its body's motors (a golem's legs), soldered onto its board. */
	ROBOTICS,
	/** A charging pad: charges a robot standing on it. */
	CHARGER,
	/** A crafting card: storage and a crafting grid, a robot's to work. */
	CRAFTING,
}
