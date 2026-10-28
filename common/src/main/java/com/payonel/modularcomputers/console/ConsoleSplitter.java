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
 * <p>
 * <b>Its size</b> (user, 2026-10-21): one for the whole group, the largest every member can show,
 * as UEFI's splitter sets every console device to a mode they all have. Each member is capped at
 * the group's largest while it's in the group, and set to the console's size when it joins. Its
 * size is the group's largest when it's first chosen (at power-on, once it has members), and kept
 * from then on, a save included. A
 * program makes it smaller, or larger again up to that, with xterm's resize sequence, which every
 * member follows within the same cap. Taken out of the group, a terminal may be its own largest.
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
	/** The group as it was last settled ({@link #settle}). */
	private List<Terminal> group = List.of();
	/** The largest every member can show (or {@link #HEADLESS} with none): its members' cap, and its own. */
	private Resolution common = HEADLESS;
	/** Its size was chosen (with members), or restored from a save: kept from then on, within the group's largest. */
	private boolean sized;

	public ConsoleSplitter(Bus bus) {
		this.bus = bus;
		decoder.sizing(() -> common, () -> {
		});
		settle(); // (a bus may come with terminals)
	}

	/** Its size: what it and every terminal in its group show (its copy's), or {@link #HEADLESS} without any. */
	public Resolution size() {
		return group.isEmpty() ? HEADLESS : screen.resolution();
	}

	/**
	 * After its group may have changed: the group's largest is worked out again and caps every
	 * member (one that left the group is let go); a member that joined is set to the console's size,
	 * which is the group's largest when the group was empty before.
	 */
	private void settle() {
		List<Terminal> now = terminals();
		for (Terminal left : group) {
			if (!now.contains(left)) {
				left.cap(null); // its own largest again
			}
		}
		int columns = Integer.MAX_VALUE;
		int rows = Integer.MAX_VALUE;
		for (Terminal terminal : now) {
			columns = Math.min(columns, terminal.maximum().columns());
			rows = Math.min(rows, terminal.maximum().rows());
		}
		common = now.isEmpty() ? HEADLESS : new Resolution(columns, rows);
		Resolution size = !sized ? common
			: new Resolution(Math.min(screen.resolution().columns(), common.columns()), Math.min(screen.resolution().rows(), common.rows()));
		sized |= !now.isEmpty();
		decoder.resize(size);
		for (Terminal terminal : now) {
			terminal.cap(common);
			if (!group.contains(terminal) || !terminal.buffer().resolution().equals(size)) {
				terminal.resize(size);
			}
		}
		group = now;
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

	/** Takes the bytes waiting at the group's terminals (at most {@code max}, in whole keys), merged in bus order (ConIn's), or nothing. */
	public Optional<String> takeInput(long max) {
		StringBuilder bytes = new StringBuilder();
		for (Terminal terminal : terminals()) {
			long room = max - bytes.length();
			if (room <= 0) {
				break;
			}
			terminal.takeInput(room, bytes.isEmpty()).ifPresent(bytes::append);
		}
		return bytes.isEmpty() ? Optional.empty() : Optional.of(bytes.toString());
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
		settle();
	}

	/** Power's gone: a blank copy, the cursor home, every terminal back in the group, which settles afresh. */
	public void reset() {
		unbound.clear();
		decoder.reset();
		group = List.of();
		sized = false;
		settle();
	}

	/** Its copy came back from a save, at the size it had: kept. */
	public void restored() {
		sized = true;
	}

	// ---- device methods: the console as programs use it, by name on the virtual bus ----------

	@Method(doc = "write(text): writes text to every terminal in the console's group; escape sequences (VT100/xterm) move the cursor, set colors, erase")
	public Result write(Arguments args) {
		write(args.checkString(0));
		return Result.ok();
	}

	@Method(doc = "read([max]): the bytes waiting at the terminals in the console's group (at most max), merged, or nil")
	public Result read(Arguments args) {
		return Result.ok(takeInput(args.optInteger(0, Long.MAX_VALUE)).orElse(null));
	}

	@Method(doc = "getSize(): columns, rows (what every terminal in the console's group shows); then the largest it can be made, columns and rows")
	public Result getSize(Arguments args) {
		Resolution size = size();
		return Result.ok(size.columns(), size.rows(), common.columns(), common.rows());
	}

	@Method(doc = "terminals(): the terminals in the console's group, by address, in bus order")
	public Result terminals(Arguments args) {
		return Result.ok(members());
	}

	@Method(doc = "unbind(address): takes a terminal out of the console's group; the console stops writing to it and reading from it, and what's on it stays")
	public Result unbind(Arguments args) {
		return member(args.checkString(0)).map(address -> {
			unbound.add(address);
			settle();
			return Result.ok(true);
		}).orElseGet(() -> Result.fail("not a terminal: " + args.checkString(0)));
	}

	@Method(doc = "bind(address): puts a terminal back in the console's group, and draws the console on it")
	public Result bind(Arguments args) {
		return member(args.checkString(0)).map(address -> {
			if (unbound.remove(address) && bus.device(address).orElse(null) instanceof Terminal terminal) {
				settle(); // (it's set to the console's size)
				redraw(terminal);
			}
			return Result.ok(true);
		}).orElseGet(() -> Result.fail("not a terminal: " + args.checkString(0)));
	}

	/** The terminals in the group, in bus order (a plain pass: every key read and written looks). */
	private List<Terminal> terminals() {
		List<Terminal> terminals = new ArrayList<>();
		bus.forEach((address, device) -> {
			if (device instanceof Terminal terminal && isBound(address)) {
				terminals.add(terminal);
			}
		});
		return terminals;
	}

	/** The addresses of the group's terminals, in bus order. */
	private List<String> members() {
		List<String> members = new ArrayList<>();
		bus.forEach((address, device) -> {
			if (device instanceof Terminal && isBound(address)) {
				members.add(address);
			}
		});
		return members;
	}

	/** The address of a terminal on the bus, by any of its names. */
	private Optional<String> member(String name) {
		return bus.address(name).filter(address -> bus.device(address).orElse(null) instanceof Terminal);
	}
}
