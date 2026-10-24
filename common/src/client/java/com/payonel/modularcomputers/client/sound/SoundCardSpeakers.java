package com.payonel.modularcomputers.client.sound;

import com.payonel.modularcomputers.network.SoundCardPayload;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * What the Sound Cards near this player are playing: each card's speakers (one for mono, at its
 * block; two for stereo, its left and right, {@link #SPEAKER_OFFSET} either side of the block's
 * center, as seen facing its front), fed what the server sends. A card starts once
 * {@link #START_SECONDS} have come (so the network's unevenness doesn't stutter it), and starts
 * again after its speakers went quiet; a new format, or a moved card, gets new speakers.
 */
public final class SoundCardSpeakers {
	/** What's buffered before a card's speakers start, in seconds. */
	static final double START_SECONDS = 0.15;
	/** How far each stereo speaker is from its block's center, in blocks. */
	static final double SPEAKER_OFFSET = 0.5;
	private static final Map<UUID, Playing> CARDS = new HashMap<>();

	private SoundCardSpeakers() {
	}

	/** A card's speakers: where, in what format, and their streams and sounds (once started). */
	private static final class Playing {
		final SoundCardPayload where;
		final SpeakerStream[] streams;
		SpeakerSound[] sounds;

		Playing(SoundCardPayload where) {
			this.where = where;
			this.streams = new SpeakerStream[where.format().channels()];
			for (int i = 0; i < streams.length; i++) {
				streams[i] = new SpeakerStream(where.format().rate());
			}
		}

		boolean ended() {
			for (SpeakerStream stream : streams) {
				if (stream.ended()) {
					return true;
				}
			}
			return false;
		}
	}

	/** Client thread: what a card played on the server. */
	public static void receive(SoundCardPayload payload) {
		Playing playing = CARDS.get(payload.card());
		if (playing == null || playing.ended() || !playing.where.format().equals(payload.format()) || !playing.where.pos().equals(payload.pos())
			|| playing.where.left() != payload.left()) {
			stop(playing);
			playing = new Playing(payload);
			CARDS.put(payload.card(), playing);
		}
		short[][] samples = payload.format().decode(payload.data());
		for (int channel = 0; channel < playing.streams.length; channel++) {
			playing.streams[channel].add(samples[channel]);
		}
		if (playing.sounds == null && playing.streams[0].waitingSeconds() >= START_SECONDS) {
			start(playing);
		}
	}

	private static void start(Playing playing) {
		Vec3 center = Vec3.atCenterOf(playing.where.pos());
		var sounds = Minecraft.getInstance().getSoundManager();
		playing.sounds = new SpeakerSound[playing.streams.length];
		for (int channel = 0; channel < playing.streams.length; channel++) {
			Vec3 at = center;
			if (playing.streams.length == 2 && playing.where.left() >= 0) {
				Vec3 left = Direction.values()[playing.where.left()].getUnitVec3().scale(SPEAKER_OFFSET);
				at = channel == 0 ? center.add(left) : center.subtract(left); // (WAV's order: left, then right)
			}
			playing.sounds[channel] = new SpeakerSound(at, playing.streams[channel]);
			sounds.play(playing.sounds[channel]);
		}
	}

	private static void stop(Playing playing) {
		if (playing != null && playing.sounds != null) {
			for (SpeakerSound sound : playing.sounds) {
				Minecraft.getInstance().getSoundManager().stop(sound);
			}
		}
	}

	/** Where the speakers playing now are (the sound engine has them): for tests, and to look at. */
	public static java.util.List<Vec3> playing() {
		var sounds = Minecraft.getInstance().getSoundManager();
		java.util.List<Vec3> at = new java.util.ArrayList<>();
		for (Playing playing : CARDS.values()) {
			if (playing.sounds != null) {
				for (SpeakerSound sound : playing.sounds) {
					if (sounds.isActive(sound)) {
						at.add(new Vec3(sound.getX(), sound.getY(), sound.getZ()));
					}
				}
			}
		}
		return at;
	}

	/** The world went away: every card's speakers stop. */
	public static void clear() {
		CARDS.values().forEach(SoundCardSpeakers::stop);
		CARDS.clear();
	}
}
