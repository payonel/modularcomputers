package com.payonel.modularcomputers.display;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DisplayLayoutTest {
	private static final double ASPECT = Resolution.DEFAULT.aspectRatio();

	@ParameterizedTest
	@CsvSource({"427, 240", "320, 240", "240, 427", "960, 540", "200, 200"})
	void buttonsSitRightOfTheDisplayFromItsTopWithoutOverlapping(int width, int height) {
		DisplayLayout layout = DisplayLayout.of(width, height, ASPECT, 0.9, 20, 4);
		Viewport display = layout.display();
		Viewport button = layout.powerButton();
		assertThat(button.x()).isEqualTo(display.right() + 4);
		assertThat(button.y()).isEqualTo(display.y());
		assertThat(button.right()).isLessThanOrEqualTo(width);
		assertThat(button.bottom()).isLessThanOrEqualTo(height);
		Viewport toggle = layout.viewToggle();
		assertThat(toggle.x()).isEqualTo(button.x());
		assertThat(toggle.y()).isEqualTo(button.bottom() + 4);
		assertThat(toggle.bottom()).isLessThanOrEqualTo(height);
		// Display stays centered.
		assertThat(Math.abs(display.x() - (width - display.right()))).isLessThanOrEqualTo(1);
	}

	@ParameterizedTest
	@CsvSource({"427, 240", "960, 540"})
	void wideWindowsKeepTheFullDisplaySize(int width, int height) {
		// The button fits in the margin, so the display is as large as without one.
		assertThat(DisplayLayout.of(width, height, ASPECT, 0.9, 20, 4).display())
			.isEqualTo(Viewport.fit(width, height, ASPECT, 0.9));
	}
}
