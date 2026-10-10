package com.payonel.modularcomputers.sound;

import static com.payonel.modularcomputers.ModularComputers.id;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/** Sound events. The files are in assets/modularcomputers/sounds; see scripts/art for how they're made. */
public final class ModSounds {
	/** A short, low, buzzy beep: something went wrong (e.g. power pressed with no CPU). */
	public static final SoundEvent ERROR_BEEP = register("error_beep");

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
