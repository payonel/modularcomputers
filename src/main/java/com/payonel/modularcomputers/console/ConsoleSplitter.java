package com.payonel.modularcomputers.console;

import com.payonel.modularcomputers.bus.Bus;
import com.payonel.modularcomputers.device.Terminal;
import com.payonel.modularcomputers.display.Resolution;
import com.payonel.modularcomputers.display.TextBuffer;
import com.payonel.modularcomputers.display.VtDecoder;
import com.payonel.modularcomputers.runtime.Arguments;
import com.payonel.modularcomputers.runtime.Method;
import com.payonel.modularcomputers.runtime.Result;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The console on the virtual bus: always there, so firmware can just write. It's the board's
 * firmware console, UEFI's ConSplitter: it merges the terminals in its group, sending the same text
 * to every one ({@code ConOut}) and reading the keys typed at any of them ({@code ConIn}). Its
 * writes are literal, as {@code OutputString}'s are: a new line is {@code \r\n} written out
 * (termios's {@code onlcr} and the rest of the line discipline are the OS's, on its own tty). With
 * no terminals it drops output.
 * <p>
 * <b>Its copy:</b> it decodes its own output onto its own screen too ({@link #screen()}), like a
 * Linux virtual console's buffer, and redraws that onto a terminal that joins the group, as tmux
 * or screen redraws a terminal that attaches.
 * <p>
 * <b>Its group:</b> every terminal on the bus is in it (bound) by default. A member stays a device
 * of its own, as each ConIn device keeps its own protocol under UEFI's splitter: its keys raise its
 * own interrupt as well as the console's, and a program may read it directly, racing the console
 * for its keys (each key goes to whichever reads first; user, 2026-10-13). A program can
 * {@link #unbind} one to use it on its own: the console stops writing to it and reading from it
 * (what's on it stays), and takes it back with {@link #bind}, redrawing itself onto it (names from
 * Linux's vtconsole; UEFI sets its members from NVRAM instead). An unbound terminal stays unbound
 * while it's on the bus; one that leaves and comes back is a new arrival and joins the group again.
 */
public final class ConsoleSplitter {
	/** The size reported when no terminal is attached; never 0x0, so callers need no special case. */
	public static final Resolution HEADLESS = Resolution.DEFAULT;

	private final Bus bus;
	/** What the console shows, decoded from what it wrote. */
	private final TextBuffer screen = new TextBuffer(HEADLESS);
	private final VtDecoder decoder = new VtDecoder(screen, reply -> {
		// the console's own copy answers nobody: the terminals answer for themselves
	});
	/** The bus addresses of terminals taken out of the group. */
	private final Set<String> unbound = new HashSet<>();

	public ConsoleSplitter(Bus bus) {
		this.bus = bus;
	}

	/** The smallest size among its terminals, or {@link #HEADLESS} without any. */
	public Resolution size() {
		List<Terminal> terminals = terminals();
		if (terminals.isEmpty()) {
			return HEADLESS;
		}
		int columns = Integer.MAX_VALUE;
		int rows = Integer.MAX_VALUE;
		for (Terminal terminal : terminals) {
			columns = Math.min(columns, terminal.buffer().resolution().columns());
			rows = Math.min(rows, terminal.buffer().resolution().rows());
		}
		return new Resolution(columns, rows);
	}

	/** The console's own copy of what it shows; save it with {@link #decoder()}'s state. */
	public TextBuffer screen() {
		return screen;
	}

	public VtDecoder decoder() {
		return decoder;
	}

	/** Paints the console onto a terminal that just joined, e.g. a screen plugged into a running machine. */
	public void redraw(Terminal terminal) {
		terminal.write(decoder.redraw());
	}

	/** Writes text to the console, as it is: its copy and every terminal in its group. */
	public void write(String text) {
		decoder.write(text);
		for (Terminal terminal : terminals()) {
			terminal.write(text);
		}
	}

	/** Takes the oldest waiting key from the group's terminals, in bus order. */
	public Optional<String> nextKey() {
		for (Terminal terminal : terminals()) {
			Optional<String> key = terminal.nextKey();
			if (key.isPresent()) {
				return key;
			}
		}
		return Optional.empty();
	}

	/** Keys wait at a terminal in the group: the console's interrupt. */
	public boolean interruptPending() {
		return terminals().stream().anyMatch(Terminal::hasKeys);
	}

	/** Whether the device at {@code address} is in the group (terminals are, unless unbound). */
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

	/** Power's gone: a blank copy, the cursor home, every terminal back in the group. */
	public void reset() {
		unbound.clear();
		decoder.reset();
	}

	// ---- device methods: the console as programs use it, by name on the virtual bus ----------

	@Method(doc = "write(text): writes text to every terminal in the console's group; escape sequences (VT100/xterm) move the cursor, set colors, erase")
	public Result write(Arguments args) {
		write(args.checkString(0));
		return Result.ok();
	}

	@Method(doc = "read(): the oldest key typed at any terminal in the console's group, or nil")
	public Result read(Arguments args) {
		return Result.ok(nextKey().orElse(null));
	}

	@Method(doc = "getSize(): columns, rows; the smallest of the terminals in the console's group")
	public Result getSize(Arguments args) {
		Resolution size = size();
		return Result.ok(size.columns(), size.rows());
	}

	@Method(doc = "terminals(): the terminals in the console's group, by address, in bus order")
	public Result terminals(Arguments args) {
		return Result.ok(members());
	}

	@Method(doc = "unbind(address): takes a terminal out of the console's group; the console stops writing to it and reading from it, and what's on it stays")
	public Result unbind(Arguments args) {
		return member(args.checkString(0)).map(address -> {
			unbound.add(address);
			return Result.ok(true);
		}).orElseGet(() -> Result.fail("not a terminal on the bus: " + args.checkString(0)));
	}

	@Method(doc = "bind(address): puts a terminal back in the console's group, and draws the console on it")
	public Result bind(Arguments args) {
		return member(args.checkString(0)).map(address -> {
			if (unbound.remove(address) && bus.device(address).orElse(null) instanceof Terminal terminal) {
				redraw(terminal);
			}
			return Result.ok(true);
		}).orElseGet(() -> Result.fail("not a terminal on the bus: " + args.checkString(0)));
	}

	/** The terminals in the group, in bus order. */
	private List<Terminal> terminals() {
		List<Terminal> terminals = new ArrayList<>();
		for (String address : members()) {
			bus.device(address).ifPresent(device -> terminals.add((Terminal) device));
		}
		return terminals;
	}

	/** The addresses of the group's terminals, in bus order. */
	private List<String> members() {
		return bus.addresses().stream()
			.filter(address -> isBound(address) && bus.device(address).orElse(null) instanceof Terminal)
			.toList();
	}

	/** The address of a terminal on the bus, by any of its names. */
	private Optional<String> member(String name) {
		return bus.address(name).filter(address -> bus.device(address).orElse(null) instanceof Terminal);
	}
}
