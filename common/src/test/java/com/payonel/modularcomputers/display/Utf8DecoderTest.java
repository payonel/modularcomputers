package com.payonel.modularcomputers.display;

import static org.assertj.core.api.Assertions.assertThat;

import com.payonel.modularcomputers.device.Terminal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The terminal decodes UTF-8 (the user's: every tier) and lays characters out by their widths, as
 * xterm does: a wide one in two cells, a combining mark on the cell before, each code point its own
 * width; and DEC's character sets. Text comes as a program writes it: bytes.
 */
class Utf8DecoderTest {
	private static final String ESC = "\u001B";
	private final TextBuffer buffer = new TextBuffer(new Resolution(8, 3));
	private final List<String> replies = new ArrayList<>();
	private final VtDecoder vt = new VtDecoder(buffer, replies::add);

	/** As a program writes: its text's UTF-8 bytes. */
	private void write(String text) {
		vt.write(Terminal.utf8(text));
	}

	private int cell(int column, int row) {
		return buffer.get(column, row);
	}

	@Test
	void aMultiByteCharacterIsOneCell() {
		write("é━x");
		assertThat(cell(0, 0)).isEqualTo('é');
		assertThat(cell(1, 0)).as("box drawing: 3 bytes, a cell").isEqualTo(0x2501);
		assertThat(cell(2, 0)).isEqualTo('x');
		assertThat(vt.column()).isEqualTo(3);
	}

	@Test
	void aWideCharacterTakesTwoCells() {
		write("a中b");
		assertThat(cell(1, 0)).isEqualTo(0x4E2D);
		assertThat(cell(2, 0)).as("its right half").isEqualTo(TextBuffer.WIDE);
		assertThat(cell(3, 0)).isEqualTo('b');
		assertThat(vt.column()).isEqualTo(4);
	}

	@Test
	void anEmojiIsWideAndAboveTheBasicPlane() {
		write("😀!"); // 😀, U+1F600: four bytes
		assertThat(cell(0, 0)).isEqualTo(0x1F600);
		assertThat(cell(1, 0)).isEqualTo(TextBuffer.WIDE);
		assertThat(cell(2, 0)).isEqualTo('!');
	}

	@Test
	void aCombiningMarkJoinsTheCellBefore() {
		write("éx"); // e and a combining acute: é in one cell
		assertThat(cell(0, 0)).isEqualTo('e');
		assertThat(buffer.marks(0, 0)).isEqualTo("́");
		assertThat(cell(1, 0)).isEqualTo('x');
		assertThat(vt.column()).isEqualTo(2);
	}

	@Test
	void emojiSequencesAreEachCodePointsOwnWidth() {
		write("👨‍👩"); // 👨 ZWJ 👩: two wide, the joiner a mark
		assertThat(cell(0, 0)).isEqualTo(0x1F468);
		assertThat(buffer.marks(0, 0)).as("the joiner").isEqualTo("‍");
		assertThat(cell(2, 0)).isEqualTo(0x1F469);
		assertThat(vt.column()).isEqualTo(4);
	}

	@Test
	void aWideCharacterThatDoesntFitWrapsFirst() {
		write("1234567中");
		assertThat(cell(7, 0)).as("the last cell left as it was").isEqualTo(' ');
		assertThat(cell(0, 1)).isEqualTo(0x4E2D);
		assertThat(cell(1, 1)).isEqualTo(TextBuffer.WIDE);
	}

	@Test
	void writingOverHalfAWideCharacterBlanksTheOtherHalf() {
		write("中文");
		write(ESC + "[1;2Hx"); // over 中's right half
		assertThat(cell(0, 0)).isEqualTo(' ');
		assertThat(cell(1, 0)).isEqualTo('x');
		assertThat(cell(2, 0)).isEqualTo(0x6587);
		write(ESC + "[1;3Hy"); // over 文's left half
		assertThat(cell(2, 0)).isEqualTo('y');
		assertThat(cell(3, 0)).isEqualTo(' ');
	}

	@Test
	void deletingACharacterThatSplitsAWideOneBlanksIt() {
		write("a中b" + ESC + "[1;2H" + ESC + "[P"); // delete at 中: its right half moves under the cursor alone
		assertThat(cell(1, 0)).isEqualTo(' ');
		assertThat(cell(2, 0)).isEqualTo('b');
	}

	@Test
	void badBytesShowAsTheReplacementCharacter() {
		vt.write("aÿbÃ"); // 0xFF is never UTF-8; C3 starts a character that never ends
		vt.write("c");
		assertThat(cell(0, 0)).isEqualTo('a');
		assertThat(cell(1, 0)).isEqualTo(0xFFFD);
		assertThat(cell(2, 0)).isEqualTo('b');
		assertThat(cell(3, 0)).as("cut short").isEqualTo(0xFFFD);
		assertThat(cell(4, 0)).isEqualTo('c');
	}

	@Test
	void anOverlongEncodingIsRefused() {
		vt.write("À¯"); // '/' in two bytes: not allowed
		assertThat(cell(0, 0)).isEqualTo(0xFFFD);
	}

	@Test
	void decGraphicsDrawLinesAndSoSiShift() {
		vt.write(ESC + "(0lqk" + ESC + "(B" + "q");
		assertThat(new int[] {cell(0, 0), cell(1, 0), cell(2, 0), cell(3, 0)}).containsExactly(0x250C, 0x2500, 0x2510, 'q');
		vt.write("\r\n" + ESC + ")0a\u000Ex\u000Fx"); // G1 graphics, SO into it, SI out
		assertThat(cell(0, 1)).isEqualTo('a');
		assertThat(cell(1, 1)).isEqualTo(0x2502);
		assertThat(cell(2, 1)).isEqualTo('x');
	}

	@Test
	void theUkSetsHashIsAPound() {
		vt.write(ESC + "(A#" + ESC + "(B#");
		assertThat(cell(0, 0)).isEqualTo(0x00A3);
		assertThat(cell(1, 0)).isEqualTo('#');
	}

	@Test
	void decscSavesTheCharacterSets() {
		vt.write(ESC + "(0" + ESC + "7" + ESC + "(B" + ESC + "8q");
		assertThat(cell(0, 0)).isEqualTo(0x2500);
	}

	@Test
	void aRowsTextComesBackAsItsCellsWithWideAndMarkedOnes() {
		write("a中é" + ESC + "(0q");
		TextBuffer copy = new TextBuffer(new Resolution(8, 3));
		copy.setRows(buffer.rows());
		for (int column = 0; column < 8; column++) {
			assertThat(copy.get(column, 0)).isEqualTo(buffer.get(column, 0));
			assertThat(copy.marks(column, 0)).isEqualTo(buffer.marks(column, 0));
		}
	}

	@Test
	void aSavedDecoderHalfThroughACharacterCarriesOn() {
		vt.write("ä¸"); // 中's first two bytes
		TextBuffer copyBuffer = new TextBuffer(new Resolution(8, 3));
		VtDecoder loaded = new VtDecoder(copyBuffer, replies::add);
		loaded.restore(vt.state(), vt.pendingParams(), vt.mainScreen());
		loaded.write("­"); // its last
		assertThat(copyBuffer.get(0, 0)).isEqualTo(0x4E2D);
	}

	@Test
	void widthsAreWcwidths() {
		assertThat(CharWidth.of('a')).isEqualTo(1);
		assertThat(CharWidth.of(0x4E2D)).as("CJK").isEqualTo(2);
		assertThat(CharWidth.of(0x1F600)).as("an emoji").isEqualTo(2);
		assertThat(CharWidth.of(0x0301)).as("a combining mark").isZero();
		assertThat(CharWidth.of(0x200D)).as("ZWJ").isZero();
		assertThat(CharWidth.of(0x00AD)).as("the soft hyphen shows, as glibc has it").isEqualTo(1);
		assertThat(CharWidth.of(0x2500)).as("box drawing").isEqualTo(1);
	}
}
