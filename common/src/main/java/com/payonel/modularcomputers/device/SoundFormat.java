package com.payonel.modularcomputers.device;

import java.util.List;

/**
 * What a Sound Card plays (OSS's settings): samples a second ({@code rate}), how they're encoded,
 * how many channels, and an ADPCM block's size in bytes (WAV's block align; 0 for PCM). A card powers
 * on at OSS's default: 8000 a second, unsigned 8-bit, mono, so a raw file plays as written.
 */
public record SoundFormat(int rate, String encoding, int channels, int block) {
	/** Unsigned 8-bit samples (OSS's AFMT_U8). */
	public static final String U8 = "u8";
	/** Signed 16-bit samples, little-endian (AFMT_S16_LE). */
	public static final String S16LE = "s16le";
	/** IMA ADPCM, in WAV's blocks (AFMT_IMA_ADPCM). */
	public static final String IMA_ADPCM = "ima_adpcm";
	public static final List<String> ENCODINGS = List.of(U8, S16LE, IMA_ADPCM);
	/** The rates it plays: the telephone's, and CD audio's and its halves. */
	public static final List<Integer> RATES = List.of(8000, 11025, 22050, 44100);
	/** Mono or stereo. */
	public static final int MAX_CHANNELS = 2;
	/** An ADPCM block's size if a program doesn't say (a common one: 512 bytes a channel). */
	public static final int DEFAULT_BLOCK = 512;
	/** The largest ADPCM block it takes. */
	public static final int MAX_BLOCK = 8192;
	/** OSS's default. */
	public static final SoundFormat DEFAULT = new SoundFormat(8000, U8, 1, 0);

	/** Why it isn't one the card plays, or null. */
	public String invalid() {
		if (!RATES.contains(rate)) {
			return "unsupported rate: " + rate;
		} else if (!ENCODINGS.contains(encoding)) {
			return "unsupported encoding: " + encoding;
		} else if (channels < 1 || channels > MAX_CHANNELS) {
			return "unsupported channels: " + channels;
		} else if (encoding.equals(IMA_ADPCM) && (block <= ImaAdpcm.HEADER * channels || block > MAX_BLOCK || block % (4 * channels) != 0)) {
			return "bad ADPCM block size: " + block;
		}
		return null;
	}

	/** The smallest whole piece of it, in bytes: a frame (a sample of each channel), or an ADPCM block. */
	public int unit() {
		return switch (encoding) {
			case U8 -> channels;
			case S16LE -> 2 * channels;
			default -> block;
		};
	}

	/** Samples (of each channel) a unit holds. */
	public int samplesPerUnit() {
		return encoding.equals(IMA_ADPCM) ? ImaAdpcm.samplesPerBlock(block, channels) : 1;
	}

	/** How many bytes of it play a second. */
	public double bytesPerSecond() {
		return (double) rate * unit() / samplesPerUnit();
	}

	/** Whole units of it decoded to 16-bit samples, a channel each. */
	public short[][] decode(byte[] data) {
		int units = data.length / unit();
		int per = samplesPerUnit();
		short[][] out = new short[channels][units * per];
		for (int u = 0; u < units; u++) {
			int at = u * unit();
			if (encoding.equals(IMA_ADPCM)) {
				short[][] block = ImaAdpcm.decode(data, at, this.block, channels);
				for (int channel = 0; channel < channels; channel++) {
					System.arraycopy(block[channel], 0, out[channel], u * per, per);
				}
			} else {
				for (int channel = 0; channel < channels; channel++) {
					out[channel][u] = encoding.equals(U8)
						? (short) (((data[at + channel] & 0xFF) - 128) << 8)
						: (short) ((data[at + 2 * channel] & 0xFF) | (data[at + 2 * channel + 1] << 8));
				}
			}
		}
		return out;
	}
}
