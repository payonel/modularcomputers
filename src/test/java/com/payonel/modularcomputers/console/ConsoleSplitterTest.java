package com.payonel.modularcomputers.console;

import static org.assertj.core.api.Assertions.assertThat;

import com.payonel.modularcomputers.bus.Bus;
import com.payonel.modularcomputers.bus.Device;
import com.payonel.modularcomputers.device.Keyboard;
import com.payonel.modularcomputers.display.Display;
import com.payonel.modularcomputers.display.Resolution;
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
	void resetGoesBackToTheTopLeft() {
		ConsoleSplitter console = console(new Display(new Resolution(5, 5)));
		console.print("a");
		console.write("b");
		console.reset();
		assertThat(console.column()).isZero();
		assertThat(console.row()).isZero();
	}
}
