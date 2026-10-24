package com.payonel.modularcomputers.client.sound;

import com.payonel.modularcomputers.sound.ModSounds;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * A Sound Card's speaker in the world: a sound at a place, fading with distance as any does, whose
 * samples come from its stream, not a file. Each loader asks a sound for its stream its own way
 * (NeoForge's {@code getStream}, Fabric's {@code getAudioStream}), both answered here: neither is
 * the plain game's, so neither is marked as overriding.
 */
final class SpeakerSound extends AbstractSoundInstance {
	private final SpeakerStream stream;

	SpeakerSound(Vec3 at, SpeakerStream stream) {
		super(ModSounds.SOUND_CARD, SoundSource.RECORDS, RandomSource.create());
		this.stream = stream;
		this.x = at.x;
		this.y = at.y;
		this.z = at.z;
		this.attenuation = Attenuation.LINEAR;
	}

	/** NeoForge's. */
	public CompletableFuture<AudioStream> getStream(SoundBufferLibrary buffers, Sound sound, boolean looping) {
		return CompletableFuture.completedFuture(stream);
	}

	/** Fabric API's. */
	public CompletableFuture<AudioStream> getAudioStream(SoundBufferLibrary buffers, Identifier id, boolean looping) {
		return CompletableFuture.completedFuture(stream);
	}
}
