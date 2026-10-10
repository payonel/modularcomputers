package com.payonel.modularcomputers.display;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TextBufferTest {
	private final TextBuffer buffer = new TextBuffer(new Resolution(5, 2));

	@Test
	void startsBlank() {
		assertThat(buffer.isBlank()).isTrue();
		assertThat(buffer.row(0)).isEqualTo("     ");
	}

	@Test
	void writeClipsAtTheEdges() {
		buffer.write(3, 0, "abc");
		buffer.write(-1, 1, "xyz");
		buffer.write(0, 2, "off the bottom");
		assertThat(buffer.row(0)).isEqualTo("   ab");
		assertThat(buffer.row(1)).isEqualTo("yz   ");
	}

	@Test
	void clearBlanksEverything() {
		buffer.write(0, 0, "hi");
		assertThat(buffer.isBlank()).isFalse();
		buffer.clear();
		assertThat(buffer.isBlank()).isTrue();
	}

	@Test
	void setIgnoresCellsOutsideTheBuffer() {
		buffer.set(4, 1, 'x');
		buffer.set(5, 0, 'y');
		buffer.set(0, -1, 'y');
		assertThat(buffer.rows()).containsExactly("     ", "    x");
	}

	@Test
	void scrollUpMovesOnlyTheGivenRows() {
		TextBuffer tall = new TextBuffer(new Resolution(1, 3));
		tall.setRows(java.util.List.of("a", "b", "c"));
		tall.scrollUp(2, TextBuffer.DEFAULT_FOREGROUND, TextBuffer.DEFAULT_BACKGROUND);
		assertThat(tall.rows()).containsExactly("b", " ", "c");
	}

	@Test
	void setRowsReplacesAndClips() {
		buffer.write(0, 0, "old");
		buffer.setRows(java.util.List.of("new text", "", "dropped"));
		assertThat(buffer.rows()).containsExactly("new t", "     ");
	}

	@Test
	void cellsKeepTheirColors() {
		buffer.set(1, 1, 'q', 0x123456, 0x654321);
		assertThat(buffer.foreground(1, 1)).isEqualTo(0x123456);
		assertThat(buffer.background(1, 1)).isEqualTo(0x654321);
		assertThat(buffer.foreground(0, 0)).isEqualTo(TextBuffer.DEFAULT_FOREGROUND);
	}

	@Test
	void aColoredBackgroundIsNotBlank() {
		buffer.fill(0, 0, 1, 1, TextBuffer.DEFAULT_FOREGROUND, 0x808080);
		assertThat(buffer.isBlank()).isFalse();
		buffer.clear();
		assertThat(buffer.isBlank()).isTrue();
	}

	@Test
	void colorsRoundTripForSaving() {
		buffer.set(4, 1, 'z', 0x010203, 0x040506);
		TextBuffer copy = new TextBuffer(new Resolution(5, 2));
		copy.setColors(buffer.foregrounds(), buffer.backgrounds());
		assertThat(copy.foreground(4, 1)).isEqualTo(0x010203);
		assertThat(copy.background(4, 1)).isEqualTo(0x040506);
		copy.setColors(new int[3], new int[3]);
		assertThat(copy.foreground(4, 1)).as("wrong sizes are ignored").isEqualTo(0x010203);
	}

	@Test
	void resolutionMustBePositive() {
		assertThatThrownBy(() -> new Resolution(0, 5)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void aspectRatioUsesGlyphSize() {
		// 50 cells of 8px by 16 rows of 16px.
		assertThat(Resolution.DEFAULT.aspectRatio()).isEqualTo(400.0 / 256.0);
	}
}
