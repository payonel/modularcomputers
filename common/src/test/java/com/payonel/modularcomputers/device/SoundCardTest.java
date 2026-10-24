package com.payonel.modularcomputers.device;

import static org.assertj.core.api.Assertions.assertThat;

import com.payonel.modularcomputers.runtime.Arguments;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The Sound Card's DAC: its formats and their decoding, its buffer's handshake, and its playing by game time. */
class SoundCardTest {
	/** Samples ADPCM takes to grow its step from the smallest to a loud sound's. */
	private static final int RAMP = 32;

	private static Arguments args(Object... values) {
		return new Arguments(List.of(values));
	}

	private static String bytes(int length) {
		return "x".repeat(length);
	}

	@Test
	void imaAdpcmDecodesWhatItEncodesCloselyAndABlockHoldsItsHeadersSampleAndTwoACodeByte() {
		short[] sine = new short[ImaAdpcm.samplesPerBlock(256, 1)];
		for (int i = 0; i < sine.length; i++) {
			sine[i] = (short) (Math.sin(i / 8.0) * 12000);
		}
		byte[] block = ImaAdpcm.encodeMono(sine);
		assertThat(block).hasSize(256);
		short[] back = ImaAdpcm.decode(block, 0, 256, 1)[0];
		assertThat(back).hasSize(505);
		assertThat(back[0]).as("the header's sample, exactly").isEqualTo(sine[0]);
		for (int i = RAMP; i < sine.length; i++) { // (its step starts at its smallest, and grows to the sound)
			assertThat(Math.abs(back[i] - sine[i])).as("sample " + i).isLessThan(1500);
		}
	}

	@Test
	void aStereoAdpcmBlockTakesFourBytesOfEachChannelInTurn() {
		short[] left = new short[17];
		short[] right = new short[17];
		for (int i = 0; i < left.length; i++) {
			left[i] = (short) (i * 300);
			right[i] = (short) (-i * 300);
		}
		byte[] l = ImaAdpcm.encodeMono(left);
		byte[] r = ImaAdpcm.encodeMono(right);
		byte[] block = new byte[2 * ImaAdpcm.HEADER + 16];
		System.arraycopy(l, 0, block, 0, ImaAdpcm.HEADER);
		System.arraycopy(r, 0, block, ImaAdpcm.HEADER, ImaAdpcm.HEADER);
		for (int group = 0; group < 2; group++) { // 8 codes (4 bytes) of the left, then of the right
			System.arraycopy(l, ImaAdpcm.HEADER + 4 * group, block, 2 * ImaAdpcm.HEADER + 8 * group, 4);
			System.arraycopy(r, ImaAdpcm.HEADER + 4 * group, block, 2 * ImaAdpcm.HEADER + 8 * group + 4, 4);
		}
		short[][] both = ImaAdpcm.decode(block, 0, block.length, 2);
		assertThat(both[0]).isEqualTo(ImaAdpcm.decode(l, 0, l.length, 1)[0]);
		assertThat(both[1]).isEqualTo(ImaAdpcm.decode(r, 0, r.length, 1)[0]);
	}

	@Test
	void pcmDecodesToSixteenBitsAChannel() {
		assertThat(new SoundFormat(8000, SoundFormat.U8, 1, 0).decode(new byte[] {(byte) 128, (byte) 255, 0})[0])
			.containsExactly(0, 127 << 8, -128 << 8);
		short[][] stereo = new SoundFormat(8000, SoundFormat.S16LE, 2, 0).decode(new byte[] {1, 0, (byte) 0xFF, (byte) 0xFF});
		assertThat(stereo[0]).containsExactly(1);
		assertThat(stereo[1]).containsExactly(-1);
	}

	@Test
	void itStartsAtOssDefaultsAndRefusesWhatItCantPlay() {
		SoundCard card = new SoundCard();
		assertThat(card.format()).isEqualTo(SoundFormat.DEFAULT);
		assertThat(card.setFormat(args(Map.of("rate", 12345L))).error()).startsWith("unsupported rate");
		assertThat(card.setFormat(args(Map.of("encoding", "mp3"))).error()).startsWith("unsupported encoding");
		assertThat(card.setFormat(args(Map.of("channels", 3L))).error()).startsWith("unsupported channels");
		assertThat(card.setFormat(args(Map.of("encoding", SoundFormat.IMA_ADPCM, "block", 3L))).error()).startsWith("bad ADPCM block");
		assertThat(card.setFormat(args(Map.of("encoding", SoundFormat.IMA_ADPCM))).failed()).isFalse();
		assertThat(card.format().block()).as("a block size if none is said").isEqualTo(SoundFormat.DEFAULT_BLOCK);
	}

	@Test
	void aWriteTakesWhatFitsAndItsInterruptSaysWhenThereIsRoomAgain() {
		SoundCard card = new SoundCard(); // 8000 bytes a second: its buffer holds a second
		assertThat(card.write(args(bytes(6000))).first()).isEqualTo(6000L);
		assertThat(card.write(args(bytes(6000))).first()).as("what fits").isEqualTo(2000L);
		assertThat(card.interruptPending()).as("full").isFalse();
		assertThat(card.play(0)).as("a tick's worth").hasSize(400);
		assertThat(card.interruptPending()).isFalse();
		card.play(10); // half a second more
		assertThat(card.interruptPending()).as("room for half its buffer: the writer may go on").isTrue();
		assertThat(card.write(args(bytes(100))).first()).isEqualTo(100L);
		assertThat(card.interruptPending()).as("taken").isFalse();
	}

	@Test
	void itPlaysByGameTimeWholeUnitsAndSyncSaysWhenItsPlayedOut() {
		SoundCard card = new SoundCard();
		card.setFormat(args(Map.of("rate", 22050L, "encoding", SoundFormat.S16LE, "channels", 2L)));
		card.write(args(bytes(4 * 2205))); // a tenth of a second
		assertThat(card.sync(args()).first()).isEqualTo(false);
		assertThat(card.play(100)).as("a twentieth of a second, whole frames").hasSize(4 * 1102);
		assertThat(card.playing()).isTrue();
		assertThat(card.play(101).length + 4 * 1102).as("the rest by the next tick").isEqualTo(4 * 2205);
		assertThat(card.interruptPending()).as("played out: one syncing hears").isTrue();
		assertThat(card.sync(args()).first()).isEqualTo(true);
		assertThat(card.play(102)).isNull();
		assertThat(card.playing()).isFalse();
	}

	@Test
	void theSameFormatKeepsWhatsQueuedAndANewOneDropsIt() {
		SoundCard card = new SoundCard();
		card.write(args(bytes(100)));
		card.setFormat(args(Map.of("rate", 8000L)));
		assertThat(card.getDelay(args()).first()).isEqualTo(100L);
		card.setFormat(args(Map.of("rate", 11025L)));
		assertThat(card.getDelay(args()).first()).isEqualTo(0L);
	}
}
