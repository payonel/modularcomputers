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
	void resolutionMustBePositive() {
		assertThatThrownBy(() -> new Resolution(0, 5)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void aspectRatioUsesGlyphSize() {
		// 50 cells of 8px by 16 rows of 16px.
		assertThat(Resolution.DEFAULT.aspectRatio()).isEqualTo(400.0 / 256.0);
	}
}
