package com.payonel.modularcomputers.sound;

import static com.payonel.modularcomputers.ModularComputers.id;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/** Sound events. The files are in assets/modularcomputers/sounds; scripts/art makes them. */
public final class ModSounds {
	/** A short, low, buzzy beep: something went wrong (e.g. power pressed with no CPU). */
	public static final SoundEvent ERROR_BEEP = register("error_beep");
	/** The error beep sagging deeper and fading out: the case ran out of power and the computer died. */
	public static final SoundEvent POWER_DOWN = register("power_down");
	/** A soft hum beat: an external device joined the bus. */
	public static final SoundEvent DEVICE_CONNECT = register("device_connect");
	/** A deeper, rougher hum: a device was wired up but the bus is full. */
	public static final SoundEvent BUS_OVERFLOW = register("bus_overflow");
	/** Parts seating into and coming out of the motherboard (see scripts/art/part_sounds.py). */
	public static final SoundEvent CPU_INSERT = register("cpu_insert");
	public static final SoundEvent CPU_REMOVE = register("cpu_remove");
	public static final SoundEvent EEPROM_INSERT = register("eeprom_insert");
	public static final SoundEvent EEPROM_REMOVE = register("eeprom_remove");
	public static final SoundEvent RAM_INSERT = register("ram_insert");
	public static final SoundEvent RAM_REMOVE = register("ram_remove");
	public static final SoundEvent DISK_INSERT = register("disk_insert");
	public static final SoundEvent DISK_REMOVE = register("disk_remove");
	public static final SoundEvent CARD_INSERT = register("card_insert");
	public static final SoundEvent CARD_REMOVE = register("card_remove");
	/** A tiny zap as a backup battery connects. */
	public static final SoundEvent BATTERY_INSERT = register("battery_insert");
	public static final SoundEvent BATTERY_REMOVE = register("battery_remove");
	/** A Sound Card's speaker: what it plays comes from its stream, not a file (its file only stands in). */
	public static final SoundEvent SOUND_CARD = register("sound_card");

	private ModSounds() {
	}

	/** Forces the static registrations above. */
	public static void init() {
	}

	private static SoundEvent register(String name) {
		Identifier soundId = id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, soundId, SoundEvent.createVariableRangeEvent(soundId));
	}
}
