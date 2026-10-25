package com.payonel.modularcomputers.client.sound;

import com.payonel.modularcomputers.entity.BotGolem;
import com.payonel.modularcomputers.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** A bot golem's thrusters' grumbling roar, looping while they fire and following it as it flies. */
public final class ThrusterRoar extends AbstractTickableSoundInstance {
	private final BotGolem golem;

	private ThrusterRoar(BotGolem golem) {
		super(ModSounds.THRUSTER_ROAR, SoundSource.NEUTRAL, RandomSource.create());
		this.golem = golem;
		this.looping = true;
		this.delay = 0;
		follow();
	}

	/** Starts a golem's roar (BotGolem's client hook), as its thrusters start firing. */
	public static void start(BotGolem golem) {
		Minecraft.getInstance().getSoundManager().play(new ThrusterRoar(golem));
	}

	private void follow() {
		x = golem.getX();
		y = golem.getY();
		z = golem.getZ();
	}

	@Override
	public void tick() {
		if (golem.isRemoved() || !golem.thrusting()) {
			stop();
			return;
		}
		follow();
	}
}
