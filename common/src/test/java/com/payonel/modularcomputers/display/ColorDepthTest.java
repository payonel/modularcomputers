package com.payonel.modularcomputers.display;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** A monochrome terminal's glass glows its phosphor's one color; color terminals show the colors they're sent. */
class ColorDepthTest {
	@Test
	void monochromeGlowsGreenOnGreenGlass() {
		assertThat(ColorDepth.MONOCHROME.glow(TextBuffer.DEFAULT_FOREGROUND)).isEqualTo(ColorDepth.PHOSPHOR);
		assertThat(ColorDepth.MONOCHROME.glass()).isEqualTo(ColorDepth.PHOSPHOR_GLASS);
		assertThat(ColorDepth.SIXTEEN.glow(0xFF0000)).isEqualTo(0xFF0000);
		assertThat(ColorDepth.TRUE_COLOR.glass()).as("never pure black").isEqualTo(ColorDepth.TRUE_COLOR_GLASS);
		assertThat(ColorDepth.TRUE_COLOR.glow(TextBuffer.DEFAULT_FOREGROUND)).as("nor pure white").isEqualTo(ColorDepth.TRUE_COLOR_WHITE);
		assertThat(ColorDepth.SIXTEEN.glow(TextBuffer.DEFAULT_BACKGROUND)).isEqualTo(ColorDepth.SIXTEEN_GLASS);
		assertThat(ColorDepth.TRUE_COLOR.glow(0x123456)).as("a program's own colors as they are").isEqualTo(0x123456);
	}
}
