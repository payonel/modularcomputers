package com.payonel.modularcomputers.display;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class ViewportTest {
	@Test
	void wideWindowIsLimitedByHeight() {
		Viewport viewport = Viewport.fit(1000, 400, 1.5, 0.9);
		assertThat(viewport.height()).isEqualTo(360);
		assertThat(viewport.width()).isEqualTo(540);
		assertThat(viewport.x()).isEqualTo(230);
		assertThat(viewport.y()).isEqualTo(20);
	}

	@Test
	void tallWindowIsLimitedByWidth() {
		Viewport viewport = Viewport.fit(400, 1000, 1.5, 0.9);
		assertThat(viewport.width()).isEqualTo(360);
		assertThat(viewport.height()).isEqualTo(240);
		assertThat(viewport.y()).isEqualTo(380);
	}

	@Test
	void keepsAspectRatioAndStaysCentered() {
		Viewport viewport = Viewport.fit(427, 240, Resolution.DEFAULT.aspectRatio(), 0.9);
		assertThat((double) viewport.width() / viewport.height()).isCloseTo(Resolution.DEFAULT.aspectRatio(), within(0.02));
		assertThat(viewport.x() * 2 + viewport.width()).isBetween(426, 427);
		assertThat(viewport.y() * 2 + viewport.height()).isBetween(239, 240);
	}
}
