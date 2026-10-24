package com.payonel.modularcomputers.client.sound;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ConcurrentLinkedDeque;
import javax.sound.sampled.AudioFormat;
import net.minecraft.client.sounds.AudioStream;

/**
 * One speaker's sound, for the game's sound engine to stream (its own thread asks): 16-bit mono
 * samples as they come from the server. While none are waiting it plays a moment of silence, so a
 * gap (the server late, or the program) doesn't end the sound; once none have come for
 * {@link #IDLE_SECONDS}, it ends, and the engine lets its channel go.
 */
final class SpeakerStream implements AudioStream {
	/** Silence played when nothing's waiting, in seconds: short, so new samples follow soon. */
	static final double SILENCE_SECONDS = 0.05;
	/** How long without samples before it ends. */
	static final double IDLE_SECONDS = 2.0;
	private static final int BYTES_PER_SAMPLE = 2;
	private final AudioFormat format;
	private final ConcurrentLinkedDeque<short[]> chunks = new ConcurrentLinkedDeque<>();
	private volatile long lastSamples = System.nanoTime();
	private volatile int waiting;
	private volatile boolean ended;

	SpeakerStream(int rate) {
		this.format = new AudioFormat(rate, BYTES_PER_SAMPLE * 8, 1, true, false);
	}

	/** Samples to play next. */
	void add(short[] samples) {
		chunks.add(samples);
		waiting += samples.length;
		lastSamples = System.nanoTime();
	}

	/** Seconds of samples waiting. */
	double waitingSeconds() {
		return waiting / (double) format.getSampleRate();
	}

	/** It ended (or was closed): the engine won't ask it again. */
	boolean ended() {
		return ended;
	}

	@Override
	public AudioFormat getFormat() {
		return format;
	}

	@Override
	public ByteBuffer read(int expectedSize) {
		if (ended) {
			return null;
		}
		int wanted = Math.max(BYTES_PER_SAMPLE, expectedSize / BYTES_PER_SAMPLE * BYTES_PER_SAMPLE);
		int available = Math.min(wanted / BYTES_PER_SAMPLE, waiting);
		if (available == 0) {
			if ((System.nanoTime() - lastSamples) / 1e9 > IDLE_SECONDS) {
				ended = true;
				return null; // (once the queued buffers play out, the engine stops the channel)
			}
			int silence = (int) (format.getSampleRate() * SILENCE_SECONDS);
			return ByteBuffer.allocateDirect(silence * BYTES_PER_SAMPLE).order(ByteOrder.LITTLE_ENDIAN);
		}
		ByteBuffer out = ByteBuffer.allocateDirect(available * BYTES_PER_SAMPLE).order(ByteOrder.LITTLE_ENDIAN);
		int left = available;
		while (left > 0) {
			short[] chunk = chunks.poll();
			if (chunk == null) {
				break;
			}
			int take = Math.min(left, chunk.length);
			for (int i = 0; i < take; i++) {
				out.putShort(chunk[i]);
			}
			if (take < chunk.length) {
				chunks.addFirst(java.util.Arrays.copyOfRange(chunk, take, chunk.length)); // (the rest, next time)
			}
			left -= take;
			waiting -= take;
		}
		out.flip();
		return out;
	}

	@Override
	public void close() {
		ended = true;
		chunks.clear();
	}
}
