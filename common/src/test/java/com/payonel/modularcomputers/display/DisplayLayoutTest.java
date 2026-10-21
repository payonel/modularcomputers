package com.payonel.modularcomputers.display;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DisplayLayoutTest {
	private static final double ASPECT = Resolution.DEFAULT.aspectRatio();
	private static final int BORDER = 6;

	private static DisplayLayout layout(int width, int height) {
		return DisplayLayout.of(width, height, ASPECT, 0.9, BORDER, 20, 4);
	}

	@ParameterizedTest
	@CsvSource({"427, 240", "320, 240", "240, 427", "960, 540", "200, 200"})
	void bezelFramesTheDisplayAndStaysCentered(int width, int height) {
		DisplayLayout layout = layout(width, height);
		Viewport display = layout.display();
		Viewport bezel = layout.bezel();
		assertThat(bezel.x()).isEqualTo(display.x() - BORDER);
		assertThat(bezel.y()).isEqualTo(display.y() - BORDER);
		assertThat(bezel.right()).isEqualTo(display.right() + BORDER);
		assertThat(bezel.bottom()).isEqualTo(display.bottom() + BORDER);
		assertThat(Math.abs(display.x() - (width - display.right()))).isLessThanOrEqualTo(1);
		assertThat(Math.abs(display.y() - (height - display.bottom()))).isLessThanOrEqualTo(1);
		assertThat((double) display.width() / display.height()).isCloseTo(ASPECT, within(0.03));
	}

	@ParameterizedTest
	@CsvSource({"427, 240", "320, 240", "240, 427", "960, 540", "200, 200"})
	void buttonsSitRightOfTheBezelFromItsTopWithoutOverlapping(int width, int height) {
		DisplayLayout layout = layout(width, height);
		Viewport bezel = layout.bezel();
		Viewport button = layout.powerButton();
		Viewport toggle = layout.viewToggle();
		assertThat(button.x()).isEqualTo(bezel.right() + 4);
		assertThat(button.y()).isEqualTo(bezel.y());
		assertThat(toggle.x()).isEqualTo(button.x());
		assertThat(toggle.y()).isEqualTo(button.bottom() + 4);
		assertThat(button.right()).isLessThanOrEqualTo(width);
		assertThat(toggle.bottom()).isLessThanOrEqualTo(height);
	}

	@ParameterizedTest
	@CsvSource({"427, 240", "960, 540"})
	void onWideWindowsTheBezelFillsTheHeightShare(int width, int height) {
		Viewport bezel = layout(width, height).bezel();
		assertThat(bezel.height()).isBetween((int) (height * 0.9) - 2, (int) (height * 0.9));
	}
}
