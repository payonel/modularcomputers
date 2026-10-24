package com.payonel.modularcomputers.device;

/**
 * IMA ADPCM, as WAV files hold it (Microsoft's "IMA ADPCM", format 0x11; ffmpeg's
 * {@code adpcm_ima_wav}): 4 bits a sample, each a step up or down from the last, the step growing
 * and shrinking with the sound. A block holds, for each channel, a 4-byte header (its first sample,
 * 16 bits, and its step's index) and then the codes, two a byte, low nibble first; with two
 * channels, 4 bytes (8 samples) of one, then 4 of the other. A Sound Blaster decoded it in hardware,
 * as the Sound Card does.
 */
public final class ImaAdpcm {
	/** The step sizes, by index. */
	private static final int[] STEPS = {7, 8, 9, 10, 11, 12, 13, 14, 16, 17, 19, 21, 23, 25, 28, 31, 34, 37, 41, 45, 50, 55, 60, 66, 73, 80, 88,
		97, 107, 118, 130, 143, 157, 173, 190, 209, 230, 253, 279, 307, 337, 371, 408, 449, 494, 544, 598, 658, 724, 796, 876, 963, 1060, 1166,
		1282, 1411, 1552, 1707, 1878, 2066, 2272, 2499, 2749, 3024, 3327, 3660, 4026, 4428, 4871, 5358, 5894, 6484, 7132, 7845, 8630, 9493,
		10442, 11487, 12635, 13899, 15289, 16818, 18500, 20350, 22385, 24623, 27086, 29794, 32767};
	/** How each code moves the step's index. */
	private static final int[] INDEX = {-1, -1, -1, -1, 2, 4, 6, 8, -1, -1, -1, -1, 2, 4, 6, 8};
	/** A channel's header in a block, in bytes. */
	public static final int HEADER = 4;
	/** With two channels, how many bytes of one come before the other's. */
	private static final int INTERLEAVE = 4;

	private ImaAdpcm() {
	}

	/** Samples a channel has in a block of {@code block} bytes (its header's sample, then two a code byte). */
	public static int samplesPerBlock(int block, int channels) {
		return (block - HEADER * channels) * 2 / channels + 1;
	}

	/** One block decoded: each channel's samples. */
	public static short[][] decode(byte[] data, int offset, int block, int channels) {
		int count = samplesPerBlock(block, channels);
		short[][] out = new short[channels][count];
		int[] predictor = new int[channels];
		int[] index = new int[channels];
		for (int channel = 0; channel < channels; channel++) {
			int at = offset + channel * HEADER;
			predictor[channel] = (short) ((data[at] & 0xFF) | (data[at + 1] << 8));
			index[channel] = Math.clamp(data[at + 2], 0, STEPS.length - 1);
			out[channel][0] = (short) predictor[channel];
		}
		int[] filled = new int[channels];
		java.util.Arrays.fill(filled, 1);
		int at = offset + HEADER * channels;
		int end = offset + block;
		while (at < end) {
			for (int channel = 0; channel < channels && at < end; channel++) {
				int run = channels == 1 ? end - at : Math.min(INTERLEAVE, end - at);
				for (int i = 0; i < run; i++, at++) {
					int bits = data[at] & 0xFF;
					for (int nibble : new int[] {bits & 0xF, bits >> 4}) {
						if (filled[channel] < count) {
							out[channel][filled[channel]++] = step(nibble, predictor, index, channel);
						}
					}
				}
			}
		}
		return out;
	}

	private static short step(int code, int[] predictor, int[] index, int channel) {
		int step = STEPS[index[channel]];
		int diff = step >> 3;
		if ((code & 4) != 0) {
			diff += step;
		}
		if ((code & 2) != 0) {
			diff += step >> 1;
		}
		if ((code & 1) != 0) {
			diff += step >> 2;
		}
		predictor[channel] = Math.clamp((code & 8) != 0 ? predictor[channel] - diff : predictor[channel] + diff, Short.MIN_VALUE, Short.MAX_VALUE);
		index[channel] = Math.clamp(index[channel] + INDEX[code], 0, STEPS.length - 1);
		return (short) predictor[channel];
	}

	/** Encodes one channel's samples as one block's codes and header (for tests, and to check the decoder against). */
	public static byte[] encodeMono(short[] samples) {
		int codes = samples.length - 1;
		byte[] block = new byte[HEADER + (codes + 1) / 2];
		int[] predictor = {samples[0]};
		int[] index = {0};
		block[0] = (byte) samples[0];
		block[1] = (byte) (samples[0] >> 8);
		block[2] = 0;
		for (int i = 0; i < codes; i++) {
			int step = STEPS[index[0]];
			int delta = samples[i + 1] - predictor[0];
			int code = delta < 0 ? 8 : 0;
			delta = Math.abs(delta);
			for (int bit = 4; bit > 0; bit >>= 1) {
				if (delta >= step) {
					code |= bit;
					delta -= step;
				}
				step >>= 1;
			}
			step(code, predictor, index, 0); // (as the decoder will: so the error doesn't pile up)
			block[HEADER + i / 2] |= (byte) (i % 2 == 0 ? code : code << 4);
		}
		return block;
	}
}
