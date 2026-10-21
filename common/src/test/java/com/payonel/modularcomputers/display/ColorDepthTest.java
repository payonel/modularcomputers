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
		assertThat(ColorDepth.TRUE_COLOR.glass()).isZero();
	}
}
