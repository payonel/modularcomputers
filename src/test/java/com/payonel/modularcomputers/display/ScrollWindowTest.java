package com.payonel.modularcomputers.display;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ScrollWindowTest {
	@Test
	void aShortListDoesNotScroll() {
		ScrollWindow window = new ScrollWindow(2, 10);
		assertThat(window.scrolls()).isFalse();
		assertThat(window.rowsShown(0)).isEqualTo(2);
		assertThat(window.clamp(5)).isZero();
		assertThat(window.thumbLength(100)).isEqualTo(100);
	}

	@Test
	void aFullListFitsExactly() {
		ScrollWindow window = new ScrollWindow(10, 10);
		assertThat(window.scrolls()).isFalse();
		assertThat(window.rowsShown(0)).isEqualTo(10);
	}

	@Test
	void aLongListScrollsWithAProportionalThumb() {
		ScrollWindow window = new ScrollWindow(20, 10);
		assertThat(window.scrolls()).isTrue();
		assertThat(window.maxOffset()).isEqualTo(10);
		assertThat(window.thumbLength(100)).isEqualTo(50);
		assertThat(window.thumbStart(0, 100)).isZero();
		assertThat(window.thumbStart(10, 100)).isEqualTo(50);
		assertThat(window.thumbStart(5, 100)).isEqualTo(25);
		assertThat(window.rowsShown(10)).isEqualTo(10);
		assertThat(window.clamp(-3)).isZero();
		assertThat(window.clamp(99)).isEqualTo(10);
	}

	@Test
	void theThumbNeverGetsTooSmallToGrab() {
		assertThat(new ScrollWindow(1000, 10).thumbLength(100)).isEqualTo(6);
	}
}
