package com.payonel.modularcomputers.console;

import com.payonel.modularcomputers.bus.Bus;
import com.payonel.modularcomputers.bus.Device;
import com.payonel.modularcomputers.display.Display;
import com.payonel.modularcomputers.display.Resolution;
import java.util.ArrayList;
import java.util.List;

/**
 * The console on the virtual bus: always there, so firmware can just print. Like UEFI's
 * console splitter, it writes to every display on the physical bus and drops output when
 * there are none.
 * <p>
 * Its size is the smallest columns and rows among the displays, so console text fits on all
 * of them; with no displays it reports {@link #HEADLESS}. It keeps the cursor: text wraps at
 * the right edge, and a new line at the bottom scrolls the console area up.
 */
public final class ConsoleSplitter {
	/** The size reported when no display is attached; never 0x0, so callers need no special case. */
	public static final Resolution HEADLESS = Resolution.DEFAULT;

	private final Bus bus;
	private int column;
	private int row;

	public ConsoleSplitter(Bus bus) {
		this.bus = bus;
	}

	public Resolution size() {
		List<Display> displays = displays();
		if (displays.isEmpty()) {
			return HEADLESS;
		}
		int columns = Integer.MAX_VALUE;
		int rows = Integer.MAX_VALUE;
		for (Display display : displays) {
			columns = Math.min(columns, display.buffer().resolution().columns());
			rows = Math.min(rows, display.buffer().resolution().rows());
		}
		return new Resolution(columns, rows);
	}

	public int column() {
		return column;
	}

	public int row() {
		return row;
	}

	/** Writes text at the cursor; {@code \n} starts a new line. */
	public void write(String text) {
		Resolution size = size();
		List<Display> displays = displays();
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '\n') {
				newLine(size, displays);
				continue;
			}
			if (column >= size.columns()) {
				newLine(size, displays);
			}
			for (Display display : displays) {
				display.buffer().set(column, row, c);
			}
			column++;
		}
	}

	/** Writes a line of text and moves to the start of the next line. */
	public void print(String line) {
		write(line + "\n");
	}

	/** Back to the top-left corner, e.g. after power loss (the displays clear themselves). */
	public void reset() {
		column = 0;
		row = 0;
	}

	/** Restores the cursor from a save. */
	public void restoreCursor(int column, int row) {
		this.column = column;
		this.row = row;
	}

	private void newLine(Resolution size, List<Display> displays) {
		column = 0;
		row++;
		if (row >= size.rows()) {
			for (Display display : displays) {
				display.buffer().scrollUp(size.rows());
			}
			row = size.rows() - 1;
		}
	}

	private List<Display> displays() {
		List<Display> displays = new ArrayList<>();
		for (Device device : bus.devices()) {
			if (device instanceof Display display) {
				displays.add(display);
			}
		}
		return displays;
	}
}
