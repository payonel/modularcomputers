package com.payonel.modularcomputers.display;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The terminal's decoder: what VT100/xterm escape sequences do to the screen. */
class VtDecoderTest {
	private static final String ESC = "\u001B";
	private final TextBuffer buffer = new TextBuffer(new Resolution(6, 3));
	private final List<String> replies = new ArrayList<>();
	private final VtDecoder vt = new VtDecoder(buffer, replies::add);

	private List<String> rows() {
		return buffer.rows();
	}

	@Test
	void textGoesAtTheCursorAndLfOnlyMovesDown() {
		vt.write("ab\ncd");
		assertThat(rows()).containsExactly("ab    ", "  cd  ", "      ");
	}

	@Test
	void crLfStartsTheNextLine() {
		vt.write("ab\r\ncd");
		assertThat(rows()).containsExactly("ab    ", "cd    ", "      ");
	}

	@Test
	void newLineModeMakesLfReturnTheCarriageToo() {
		vt.write(ESC + "[20hab\ncd" + ESC + "[20l\nef");
		assertThat(rows()).containsExactly("ab    ", "cd    ", "  ef  ");
		assertThat(vt.newLineMode()).isFalse();
	}

	@Test
	void wrapIsDeferredUntilTheNextCharacter() {
		vt.write("abcdef");
		assertThat(vt.row()).as("still on the first row after filling it").isZero();
		assertThat(vt.column()).isEqualTo(5);
		vt.write("g");
		assertThat(rows()).containsExactly("abcdef", "g     ", "      ");
	}

	@Test
	void aFullLineThenCrLfDoesntSkipALine() {
		vt.write("abcdef\r\ng");
		assertThat(rows()).containsExactly("abcdef", "g     ", "      ");
	}

	@Test
	void lfAtTheBottomScrolls() {
		vt.write("1\r\n2\r\n3\r\n4");
		assertThat(rows()).containsExactly("2     ", "3     ", "4     ");
	}

	@Test
	void cursorPositioningIsFromOne() {
		vt.write(ESC + "[2;3Hx" + ESC + "[Hy" + ESC + "[3;6Hz");
		assertThat(rows()).containsExactly("y     ", "  x   ", "     z");
	}

	@Test
	void cursorMovesStopAtTheEdges() {
		vt.write(ESC + "[2;2H" + ESC + "[9A" + "a" + ESC + "[9B" + "b" + ESC + "[9C" + "c" + ESC + "[9D" + "d");
		assertThat(rows()).containsExactly(" a    ", "      ", "d b  c");
	}

	@Test
	void eraseDisplayAndLine() {
		vt.write("aaaaaa\r\nbbbbbb\r\ncccccc");
		vt.write(ESC + "[2;3H" + ESC + "[K");
		assertThat(rows()).containsExactly("aaaaaa", "bb    ", "cccccc");
		vt.write(ESC + "[1K");
		assertThat(rows().get(1)).isEqualTo("      ");
		vt.write(ESC + "[J");
		assertThat(rows()).containsExactly("aaaaaa", "      ", "      ");
		vt.write(ESC + "[2J");
		assertThat(rows()).containsExactly("      ", "      ", "      ");
		assertThat(vt.row()).as("ED doesn't move the cursor").isEqualTo(1);
	}

	@Test
	void colorsAndErasingInTheCurrentBackground() {
		vt.write(ESC + "[31;44mx" + ESC + "[0my" + ESC + "[42m" + ESC + "[2K");
		assertThat(buffer.background(0, 0)).as("erased in the current background").isEqualTo(0x00CD00);
		vt.write(ESC + "[H" + ESC + "[1;31mr" + ESC + "[38;5;196mq" + ESC + "[38;2;1;2;3mt" + ESC + "[7mv");
		assertThat(buffer.foreground(0, 0)).as("bold brightens red").isEqualTo(0xFF0000);
		assertThat(buffer.foreground(1, 0)).isEqualTo(0xFF0000);
		assertThat(buffer.foreground(2, 0)).isEqualTo(0x010203);
		assertThat(buffer.background(3, 0)).as("reverse").isEqualTo(0x010203);
		assertThat(buffer.foreground(3, 0)).isEqualTo(0x00CD00);
	}

	@Test
	void theDefaultColorsAreWhiteOnBlack() {
		vt.write(ESC + "[35;45m" + ESC + "[39;49mx");
		assertThat(buffer.foreground(0, 0)).isEqualTo(TextBuffer.DEFAULT_FOREGROUND);
		assertThat(buffer.background(0, 0)).isEqualTo(TextBuffer.DEFAULT_BACKGROUND);
	}

	@Test
	void insertAndDeleteCharactersAndLines() {
		vt.write("abcdef\r\n123456\r\nxyz");
		vt.write(ESC + "[1;2H" + ESC + "[2@");
		assertThat(rows().get(0)).isEqualTo("a  bcd");
		vt.write(ESC + "[2P");
		assertThat(rows().get(0)).isEqualTo("abcd  ");
		vt.write(ESC + "[2;1H" + ESC + "[L");
		assertThat(rows()).containsExactly("abcd  ", "      ", "123456");
		vt.write(ESC + "[M");
		assertThat(rows()).containsExactly("abcd  ", "123456", "      ");
		vt.write(ESC + "[1;1H" + ESC + "[2X");
		assertThat(rows().get(0)).isEqualTo("  cd  ");
	}

	@Test
	void scrollRegionKeepsTheRowsOutsideIt() {
		vt.write("top" + ESC + "[2;3r" + ESC + "[3;1Hb" + "\r\nc\r\nd");
		assertThat(rows()).containsExactly("top   ", "c     ", "d     ");
		vt.write(ESC + "[2;1H" + ESC + "M");
		assertThat(rows()).as("reverse index at the region's top scrolls it down").containsExactly("top   ", "      ", "c     ");
	}

	@Test
	void saveAndRestoreTheCursorAndColors() {
		vt.write(ESC + "[2;2H" + ESC + "[32m" + ESC + "7" + ESC + "[0m" + ESC + "[Ha" + ESC + "8b");
		assertThat(rows().get(1)).isEqualTo(" b    ");
		assertThat(buffer.foreground(1, 1)).isEqualTo(0x00CD00);
	}

	@Test
	void theAlternateScreenGivesTheMainOneBack() {
		vt.write("main" + ESC + "[?1049h");
		assertThat(rows().get(0)).isEqualTo("      ");
		vt.write("alt");
		vt.write(ESC + "[?1049l");
		assertThat(rows().get(0)).isEqualTo("main  ");
		assertThat(vt.column()).as("the cursor comes back too").isEqualTo(4);
	}

	@Test
	void reportsAreSentBack() {
		vt.write(ESC + "[2;4H" + ESC + "[6n" + ESC + "[5n" + ESC + "[c");
		assertThat(replies).containsExactly(ESC + "[2;4R", ESC + "[0n", ESC + "[?6c");
	}

	@Test
	void unknownSequencesAndStringsAreIgnored() {
		vt.write(ESC + "]0;a title\u0007" + ESC + "[?2004h" + ESC + "(B" + ESC + "[4ma" + ESC + "[5 qb");
		assertThat(rows().get(0)).isEqualTo("ab    ");
	}

	@Test
	void cursorVisibility() {
		vt.write(ESC + "[?25l");
		assertThat(vt.cursorVisible()).isFalse();
		vt.write(ESC + "c");
		assertThat(vt.cursorVisible()).as("a full reset").isTrue();
	}

	@Test
	void redrawPaintsTheSameScreenOntoAnotherTerminal() {
		vt.write(ESC + "[33;44mhi" + ESC + "[0m\r\nthere" + ESC + "[?25l");
		TextBuffer other = new TextBuffer(new Resolution(6, 3));
		VtDecoder copy = new VtDecoder(other, reply -> { });
		copy.write("junk junk junk");
		copy.write(vt.redraw());
		assertThat(other.rows()).isEqualTo(buffer.rows());
		assertThat(other.foregrounds()).isEqualTo(buffer.foregrounds());
		assertThat(other.backgrounds()).isEqualTo(buffer.backgrounds());
		assertThat(List.of(copy.row(), copy.column(), copy.cursorVisible() ? 1 : 0)).containsExactly(1, 5, 0);
		copy.write("!");
		vt.write("!");
		assertThat(other.rows()).as("and carries on the same").isEqualTo(buffer.rows());
	}

	@Test
	void stateSavesAndRestores() {
		vt.write(ESC + "[2;3H" + ESC + "[1;35m" + ESC + "[20h");
		VtDecoder loaded = new VtDecoder(new TextBuffer(new Resolution(6, 3)), reply -> { });
		loaded.restore(vt.state());
		assertThat(loaded.state()).isEqualTo(vt.state());
		assertThat(loaded.newLineMode()).isTrue();
	}
}
