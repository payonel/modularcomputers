package com.payonel.modularcomputers.client.sound;

import com.payonel.modularcomputers.entity.EnergyField;
import com.payonel.modularcomputers.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** An energy field's soft hum, round it, looping while it's there and following it as it moves. */
public final class FieldHum extends AbstractTickableSoundInstance {
	private final EnergyField field;

	private FieldHum(EnergyField field) {
		super(ModSounds.FIELD_HUM, SoundSource.NEUTRAL, RandomSource.create());
		this.field = field;
		this.looping = true;
		this.delay = 0;
		follow();
	}

	/** Starts a field's hum (EnergyField's client hook). */
	public static void start(EnergyField field) {
		Minecraft.getInstance().getSoundManager().play(new FieldHum(field));
	}

	private void follow() {
		x = field.getX();
		y = field.getY();
		z = field.getZ();
	}

	@Override
	public void tick() {
		if (field.isRemoved()) {
			stop();
			return;
		}
		follow();
	}
}
