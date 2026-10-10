package com.payonel.modularcomputers.console;

import static org.assertj.core.api.Assertions.assertThat;

import com.payonel.modularcomputers.bus.Bus;
import com.payonel.modularcomputers.bus.Device;
import com.payonel.modularcomputers.device.Keyboard;
import com.payonel.modularcomputers.display.Display;
import com.payonel.modularcomputers.display.Resolution;
import com.payonel.modularcomputers.display.TextBuffer;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConsoleSplitterTest {
	private static ConsoleSplitter console(Device... devices) {
		return new ConsoleSplitter(new Bus(List.of(devices)));
	}

	@Test
	void withNoDisplayItHasASizeAndDropsOutput() {
		ConsoleSplitter console = console(new Keyboard());
		assertThat(console.size()).isEqualTo(ConsoleSplitter.HEADLESS);
		console.print("nobody sees this");
		assertThat(console.row()).isEqualTo(1);
	}

	@Test
	void writesToTheDisplay() {
		Display display = new Display(new Resolution(10, 3));
		ConsoleSplitter console = console(new Keyboard(), display);
		console.print("hi");
		console.write("there");
		assertThat(display.buffer().rows()).containsExactly("hi        ", "there     ", "          ");
		assertThat(console.column()).isEqualTo(5);
		assertThat(console.row()).isEqualTo(1);
	}

	@Test
	void mirrorsToEveryDisplayAtTheSmallestCommonSize() {
		Display wide = new Display(new Resolution(8, 2));
		Display tall = new Display(new Resolution(4, 3));
		ConsoleSplitter console = console(wide, tall);
		assertThat(console.size()).isEqualTo(new Resolution(4, 2));
		console.write("abcdef");
		// Wraps at 4 columns on both, even though the wide display has room.
		assertThat(wide.buffer().rows()).containsExactly("abcd    ", "ef      ");
		assertThat(tall.buffer().rows()).containsExactly("abcd", "ef  ", "    ");
	}

	@Test
	void newLineAtTheBottomScrollsTheConsoleArea() {
		Display small = new Display(new Resolution(3, 2));
		Display big = new Display(new Resolution(3, 3));
		ConsoleSplitter console = console(small, big);
		big.buffer().write(0, 2, "zzz"); // outside the console area
		console.print("one");
		console.print("two");
		console.write("3");
		assertThat(small.buffer().rows()).containsExactly("two", "3  ");
		assertThat(big.buffer().rows()).containsExactly("two", "3  ", "zzz");
		assertThat(console.row()).isEqualTo(1);
	}

	@Test
	void writesInTheCurrentColorsAndClearsTheConsoleArea() {
		Display small = new Display(new Resolution(2, 1));
		Display big = new Display(new Resolution(3, 2));
		ConsoleSplitter console = console(small, big);
		console.setColors(0x111111, 0x222222);
		console.clear();
		console.write("a");
		for (Display display : List.of(small, big)) {
			assertThat(display.buffer().get(0, 0)).isEqualTo('a');
			assertThat(display.buffer().foreground(0, 0)).isEqualTo(0x111111);
			assertThat(display.buffer().background(1, 0)).as("cleared in the console's background").isEqualTo(0x222222);
		}
		assertThat(big.buffer().background(2, 0)).as("outside the console area").isEqualTo(TextBuffer.DEFAULT_BACKGROUND);
		assertThat(big.buffer().background(0, 1)).as("outside the console area").isEqualTo(TextBuffer.DEFAULT_BACKGROUND);
	}

	@Test
	void scrollingKeepsTheConsoleBackground() {
		Display display = new Display(new Resolution(2, 2));
		ConsoleSplitter console = console(display);
		console.setColors(0x111111, 0x222222);
		console.clear();
		console.print("a");
		console.print("b");
		assertThat(display.buffer().rows()).containsExactly("b ", "  ");
		assertThat(display.buffer().background(0, 1)).isEqualTo(0x222222);
	}

	@Test
	void resetGoesBackToTheTopLeftInDefaultColors() {
		ConsoleSplitter console = console(new Display(new Resolution(5, 5)));
		console.setColors(0x111111, 0x222222);
		console.print("a");
		console.write("b");
		console.reset();
		assertThat(console.column()).isZero();
		assertThat(console.row()).isZero();
		assertThat(console.foreground()).isEqualTo(TextBuffer.DEFAULT_FOREGROUND);
		assertThat(console.background()).isEqualTo(TextBuffer.DEFAULT_BACKGROUND);
	}
}
