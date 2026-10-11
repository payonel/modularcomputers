package com.payonel.modularcomputers.console;

import com.payonel.modularcomputers.bus.Bus;
import com.payonel.modularcomputers.bus.Device;
import com.payonel.modularcomputers.device.Keyboard;
import com.payonel.modularcomputers.display.Display;
import com.payonel.modularcomputers.display.Resolution;
import com.payonel.modularcomputers.display.TextBuffer;
import com.payonel.modularcomputers.runtime.Arguments;
import com.payonel.modularcomputers.runtime.Method;
import com.payonel.modularcomputers.runtime.Result;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The console on the virtual bus: always there, so firmware can just print. Like UEFI's
 * console splitter, it writes to every display on the physical bus and drops output when
 * there are none.
 * <p>
 * Its size is the smallest columns and rows among the displays, so console text fits on all
 * of them; with no displays it reports {@link #HEADLESS}. It keeps the cursor and the current
 * colors: text wraps at the right edge, and a new line at the bottom scrolls the console area
 * up.
 * <p>
 * Like a Linux virtual console, it keeps its own copy of what it shows (its {@link #screen()}),
 * so it can {@link #redraw} that onto a display that joins later.
 * <p>
 * <b>Its group:</b> it's the multicast device for output and input both, like UEFI's splitter
 * for {@code ConOut} and {@code ConIn}. Every display and keyboard on the bus is in it (bound) by
 * default, as fbcon binds every framebuffer. A program can {@link #unbind} one to use it on its
 * own: the console stops drawing on it (what's on it stays), and takes it back with
 * {@link #bind}, redrawing itself onto it. An unbound device stays unbound while it's on the
 * bus; one that leaves and comes back is a new arrival and joins the group again, as a
 * replugged framebuffer does in Linux.
 */
public final class ConsoleSplitter {
	/** The size reported when no display is attached; never 0x0, so callers need no special case. */
	public static final Resolution HEADLESS = Resolution.DEFAULT;

	private final Bus bus;
	/** What the console shows, in the top-left corner of its (largest possible) size. */
	private final TextBuffer screen = new TextBuffer(HEADLESS);
	private int column;
	private int row;
	private int foreground = TextBuffer.DEFAULT_FOREGROUND;
	private int background = TextBuffer.DEFAULT_BACKGROUND;
	/** The bus addresses of displays and keyboards taken out of the group. */
	private final Set<String> unbound = new HashSet<>();

	public ConsoleSplitter(Bus bus) {
		this.bus = bus;
	}

	public Resolution size() {
		List<Display> displays = displays();
		if (displays.isEmpty()) {
			return HEADLESS;
		}
		int columns = screen.resolution().columns();
		int rows = screen.resolution().rows();
		for (Display display : displays) {
			columns = Math.min(columns, display.buffer().resolution().columns());
			rows = Math.min(rows, display.buffer().resolution().rows());
		}
		return new Resolution(columns, rows);
	}

	/** The console's own copy of what it shows; save it with the cursor. */
	public TextBuffer screen() {
		return screen;
	}

	/** Draws the console onto a display that just joined, e.g. a screen plugged into a running machine. */
	public void redraw(Display display) {
		Resolution size = size();
		for (int y = 0; y < size.rows(); y++) {
			for (int x = 0; x < size.columns(); x++) {
				display.buffer().set(x, y, screen.get(x, y), screen.foreground(x, y), screen.background(x, y));
			}
		}
	}

	public int column() {
		return column;
	}

	public int row() {
		return row;
	}

	public int foreground() {
		return foreground;
	}

	public int background() {
		return background;
	}

	/** Colors (0xRRGGBB) for text written from now on. */
	public void setColors(int foreground, int background) {
		this.foreground = foreground;
		this.background = background;
	}

	/** Blanks the console area in the current colors and moves the cursor to the top left. */
	public void clear() {
		Resolution size = size();
		screen.fill(0, 0, size.columns(), size.rows(), foreground, background);
		for (Display display : displays()) {
			display.buffer().fill(0, 0, size.columns(), size.rows(), foreground, background);
		}
		column = 0;
		row = 0;
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
			screen.set(column, row, c, foreground, background);
			for (Display display : displays) {
				display.buffer().set(column, row, c, foreground, background);
			}
			column++;
		}
	}

	/** Writes a line of text and moves to the start of the next line. */
	public void print(String line) {
		write(line + "\n");
	}

	// ---- device methods: the console as programs use it, by name on the virtual bus ----------

	@Method(doc = "print(text): writes a line of text and moves to the next line")
	public Result print(Arguments args) {
		print(args.checkString(0));
		return Result.ok();
	}

	@Method(doc = "write(text): writes text at the cursor; \\n starts a new line")
	public Result write(Arguments args) {
		write(args.checkString(0));
		return Result.ok();
	}

	@Method(doc = "clear(): fills the console with the current background, cursor to the top left")
	public Result clear(Arguments args) {
		clear();
		return Result.ok();
	}

	@Method(doc = "setColors(foreground, background): colors as 0xRRGGBB, for what's written next")
	public Result setColors(Arguments args) {
		setColors((int) args.checkInteger(0), (int) args.checkInteger(1));
		return Result.ok();
	}

	@Method(doc = "outputs(): the displays in the console's group, by address, in bus order")
	public Result outputs(Arguments args) {
		return Result.ok(members(Display.class));
	}

	@Method(doc = "inputs(): the keyboards in the console's group, by address, in bus order")
	public Result inputs(Arguments args) {
		return Result.ok(members(Keyboard.class));
	}

	@Method(doc = "unbind(address): takes a display or keyboard out of the console's group; what's on a display stays")
	public Result unbind(Arguments args) {
		return member(args.checkString(0)).map(address -> {
			unbound.add(address);
			return Result.ok(true);
		}).orElseGet(() -> Result.fail("not a display or keyboard on the bus: " + args.checkString(0)));
	}

	@Method(doc = "bind(address): puts a display or keyboard back in the console's group; a display gets the console drawn on it")
	public Result bind(Arguments args) {
		return member(args.checkString(0)).map(address -> {
			if (unbound.remove(address) && bus.device(address).orElse(null) instanceof Display display) {
				redraw(display);
			}
			return Result.ok(true);
		}).orElseGet(() -> Result.fail("not a display or keyboard on the bus: " + args.checkString(0)));
	}

	/** Whether the device at {@code address} is in the group (displays and keyboards are, unless unbound). */
	public boolean isBound(String address) {
		return !unbound.contains(address);
	}

	/**
	 * The bus changed: devices that left are forgotten, so if they come back they're new
	 * arrivals, in the group again.
	 */
	public void busChanged() {
		unbound.retainAll(bus.addresses());
	}

	@Method(doc = "getSize(): columns, rows; the smallest of the displays in the console's group")
	public Result getSize(Arguments args) {
		Resolution size = size();
		return Result.ok(size.columns(), size.rows());
	}

	/** Back to the top-left corner and default colors, e.g. after power loss (the displays clear themselves). */
	public void reset() {
		unbound.clear();
		screen.clear();
		column = 0;
		row = 0;
		foreground = TextBuffer.DEFAULT_FOREGROUND;
		background = TextBuffer.DEFAULT_BACKGROUND;
	}

	/** Restores the cursor and colors from a save. */
	public void restore(int column, int row, int foreground, int background) {
		this.column = column;
		this.row = row;
		this.foreground = foreground;
		this.background = background;
	}

	private void newLine(Resolution size, List<Display> displays) {
		column = 0;
		row++;
		if (row >= size.rows()) {
			screen.scrollUp(size.rows(), foreground, background);
			for (Display display : displays) {
				display.buffer().scrollUp(size.rows(), foreground, background);
			}
			row = size.rows() - 1;
		}
	}

	/** The displays in the group, in bus order. */
	private List<Display> displays() {
		List<Display> displays = new ArrayList<>();
		for (String address : members(Display.class)) {
			bus.device(address).ifPresent(device -> displays.add((Display) device));
		}
		return displays;
	}

	/** The addresses of the group's devices of one kind, in bus order. */
	private List<String> members(Class<? extends Device> kind) {
		return bus.addresses().stream()
			.filter(address -> isBound(address) && kind.isInstance(bus.device(address).orElse(null)))
			.toList();
	}

	/** The address of a display or keyboard on the bus, by any of its names. */
	private Optional<String> member(String name) {
		return bus.address(name).filter(address -> {
			Device device = bus.device(address).orElse(null);
			return device instanceof Display || device instanceof Keyboard;
		});
	}
}
