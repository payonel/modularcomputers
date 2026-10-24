package com.payonel.modularcomputers.device;

import com.payonel.modularcomputers.bus.BusPower;
import com.payonel.modularcomputers.bus.InterruptLine;
import com.payonel.modularcomputers.runtime.Arguments;
import com.payonel.modularcomputers.runtime.Method;
import com.payonel.modularcomputers.runtime.Result;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A sound card (user design, 2026-10-24: phase 1, its DAC): programs write samples to its buffer
 * and it plays them at its format's rate, as a real card's DAC plays what DMA hands it. It decodes
 * IMA ADPCM itself (a Sound Blaster did). Its buffer holds {@link #BUFFER_SECONDS}; a write takes
 * what fits and says how much (a handshake: nothing dropped), and once a refused writer has room
 * again, its interrupt says so. It plays by game time: each tick it releases what's due
 * ({@link #play}), which its block sends to the players near it; mono comes from its block, stereo
 * from two speakers, its left and right.
 */
public final class SoundCard extends Card {
	/** What its buffer holds, in seconds of the format. */
	public static final double BUFFER_SECONDS = 1.0;
	/** Its draw a tick. */
	public static final int DRAW = 6;
	/** Game ticks a second. */
	private static final int TICKS_PER_SECOND = 20;
	private final InterruptLine line = new InterruptLine();
	private final BusPower power = new BusPower(DRAW);
	private SoundFormat format = SoundFormat.DEFAULT;
	private byte[] buffer = new byte[capacity(format)];
	private int start;
	private int buffered;
	/** A write was refused (full): its interrupt says when there's room. */
	private boolean waiting;
	/** A program waits for what's queued to play out ({@link #sync}): its interrupt says when it has. */
	private boolean syncing;
	/** The game time it's played up to, or -1: not playing. */
	private long playedTo = -1;
	/** Bytes due but not yet a whole unit. */
	private double due;

	public SoundCard() {
		super(CardKind.SOUND);
	}

	private static int capacity(SoundFormat format) {
		int units = Math.max(2, (int) (format.bytesPerSecond() * BUFFER_SECONDS / format.unit()));
		return units * format.unit();
	}

	public SoundFormat format() {
		return format;
	}

	/** It's playing: what it released has gone on without a gap. */
	public boolean playing() {
		return playedTo >= 0;
	}

	private int room() {
		return buffer.length - buffered;
	}

	/** What's due by game time {@code now}, whole units, out of its buffer: what players hear next; null for nothing. */
	public byte[] play(long now) {
		if (buffered < format.unit()) {
			playedTo = -1;
			due = 0;
			return null;
		}
		long elapsed = playedTo < 0 ? 1 : now - playedTo;
		playedTo = now;
		due += elapsed * format.bytesPerSecond() / TICKS_PER_SECOND;
		int units = (int) Math.min(due / format.unit(), buffered / format.unit());
		if (units == 0) {
			return null;
		}
		byte[] out = new byte[units * format.unit()];
		for (int i = 0; i < out.length; i++) {
			out[i] = buffer[(start + i) % buffer.length];
		}
		start = (start + out.length) % buffer.length;
		buffered -= out.length;
		due = buffered < format.unit() ? 0 : due - out.length;
		if (interruptPending()) {
			line.raise(); // room again for the writer it refused, or it played out for one syncing
		}
		return out;
	}

	@Method(doc = "setFormat({rate, encoding, channels, block}): what it plays (any left out stay): rate 8000, 11025, 22050 or 44100; encoding \"u8\", \"s16le\" or \"ima_adpcm\"; channels 1 or 2; block, an ADPCM block's bytes (WAV's block align). What's buffered goes")
	public Result setFormat(Arguments args) {
		Map<?, ?> table = args.checkTable(0);
		SoundFormat next = new SoundFormat(
			table.get("rate") instanceof Number rate ? rate.intValue() : format.rate(),
			table.get("encoding") instanceof String encoding ? encoding : format.encoding(),
			table.get("channels") instanceof Number channels ? channels.intValue() : format.channels(),
			table.get("block") instanceof Number block ? block.intValue()
				: (table.get("encoding") instanceof String encoding && encoding.equals(SoundFormat.IMA_ADPCM) && format.block() == 0
					? SoundFormat.DEFAULT_BLOCK * (table.get("channels") instanceof Number channels ? channels.intValue() : format.channels()) : format.block()));
		if (!next.encoding().equals(SoundFormat.IMA_ADPCM)) {
			next = new SoundFormat(next.rate(), next.encoding(), next.channels(), 0);
		}
		String invalid = next.invalid();
		if (invalid != null) {
			return Result.fail(invalid);
		} else if (next.equals(format)) {
			return Result.ok(); // (the same: what's queued plays on)
		}
		format = next;
		buffer = new byte[capacity(format)];
		drop();
		return Result.ok();
	}

	@Method(doc = "getFormat(): {rate, encoding, channels, block}")
	public Result getFormat(Arguments args) {
		Map<String, Object> table = new LinkedHashMap<>();
		table.put("rate", (long) format.rate());
		table.put("encoding", format.encoding());
		table.put("channels", (long) format.channels());
		table.put("block", (long) format.block());
		return Result.ok(table);
	}

	@Method(doc = "write(data): queues bytes to play; returns how many it took (fewer, or 0, when its buffer is full: its interrupt says when there's room)")
	public Result write(Arguments args) {
		String data = args.checkString(0);
		syncing = false;
		int count = Math.min(data.length(), room());
		for (int i = 0; i < count; i++) {
			buffer[(start + buffered + i) % buffer.length] = (byte) data.charAt(i);
		}
		buffered += count;
		waiting = count < data.length();
		return Result.ok((long) count);
	}

	@Method(doc = "getDelay(): the bytes queued, not yet played (OSS's SNDCTL_DSP_GETODELAY)")
	public Result getDelay(Arguments args) {
		return Result.ok((long) buffered);
	}

	@Method(doc = "sync(): true once what's queued has played out; false: not yet, and its interrupt says when (OSS's SNDCTL_DSP_SYNC)")
	public Result sync(Arguments args) {
		syncing = buffered >= format.unit();
		return Result.ok(!syncing);
	}

	@Method(doc = "reset(): what's queued is dropped, at once (OSS's SNDCTL_DSP_RESET)")
	public Result reset(Arguments args) {
		drop();
		return Result.ok();
	}

	private void drop() {
		start = 0;
		buffered = 0;
		playedTo = -1;
		due = 0;
		if (waiting || syncing) {
			line.raise(); // (room: a writer waiting may go on; nothing left for one syncing)
		}
	}

	@Override
	public BusPower power() {
		return power;
	}

	@Override
	public boolean interruptPending() {
		return waiting && room() >= buffer.length / 2 || syncing && buffered < format.unit();
	}

	@Override
	public InterruptLine interruptLine() {
		return line;
	}

	@Override
	protected void dropData() {
		drop();
	}

	@Override
	public void powerOff() {
		drop();
		waiting = false;
		syncing = false;
	}
}
