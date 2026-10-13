package com.payonel.modularcomputers.device;

import com.payonel.modularcomputers.bus.BusPower;
import com.payonel.modularcomputers.bus.Device;
import com.payonel.modularcomputers.bus.DeviceType;
import com.payonel.modularcomputers.bus.InterruptLine;
import com.payonel.modularcomputers.display.Resolution;
import com.payonel.modularcomputers.display.TextBuffer;
import com.payonel.modularcomputers.display.VtDecoder;
import com.payonel.modularcomputers.power.PowerUnits;
import com.payonel.modularcomputers.runtime.Arguments;
import com.payonel.modularcomputers.runtime.Method;
import com.payonel.modularcomputers.runtime.Result;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

/**
 * A terminal on the bus: a text screen and a keyboard in one device, like a VT100 on a serial
 * line. Programs only write text to it: escape sequences in the text move the cursor, set
 * colors, erase and scroll, decoded by the terminal itself ({@link VtDecoder}). Writes are raw:
 * LF only moves down, unless the program sets new line mode ({@code ESC[20h}); the console's
 * tty settings don't apply to direct writes.
 * <p>
 * What it sends back waits in its queue until read: keys typed at it, and its answers to
 * reports (the cursor position for {@code ESC[6n}). While anything waits, its interrupt is on
 * (when it's in the console's group, the console's is instead). Typing comes from the screen
 * view: only the terminal a player has open gets the keys.
 * <p>
 * It's bus-powered ({@link #DRAW}, plus {@link #WRITE_COST} a write): with no running computer
 * supplying it, it's dark.
 */
public final class Terminal implements Device {
	/** What a terminal draws each tick it has power: a tenth of a running CPU. */
	public static final int DRAW = PowerUnits.TERMINAL;
	/** What one write costs, flat. */
	public static final int WRITE_COST = PowerUnits.TERMINAL_WRITE;

	private final BusPower power = new BusPower(DRAW);
	private final InterruptLine interruptLine = new InterruptLine();
	/** Sends what it shows to players; set by the block it's in. */
	private Runnable flusher = () -> {
	};
	private final TextBuffer buffer;
	private final Deque<String> input = new ArrayDeque<>();
	private final VtDecoder decoder;

	private final TerminalTier tier;
	/** Where its label is (its item's anvil name), read when asked: no copy of it here. */
	private java.util.function.Supplier<String> label = () -> null;

	/** A tier 3 terminal (every color). */
	public Terminal(Resolution resolution) {
		this(resolution, TerminalTier.TIER_3);
	}

	public Terminal(Resolution resolution, TerminalTier tier) {
		this.tier = tier;
		this.buffer = new TextBuffer(resolution);
		this.decoder = new VtDecoder(buffer, input::addLast, tier.depth(), tier.attributes());
	}

	public TerminalTier tier() {
		return tier;
	}

	public TextBuffer buffer() {
		return buffer;
	}

	public VtDecoder decoder() {
		return decoder;
	}

	/** Text down the line to the terminal, decoded as it arrives. Each write costs power. */
	public void write(String text) {
		power.charge(WRITE_COST);
		decoder.write(text);
	}

	@Override
	public BusPower power() {
		return power;
	}

	/** A key typed at the terminal: it waits in the queue, and the interrupt line wakes its computers. */
	public void press(String key) {
		input.addLast(key);
		interruptLine.raise();
	}

	@Override
	public String streamMethod() {
		return "read";
	}

	@Override
	public InterruptLine interruptLine() {
		return interruptLine;
	}

	/** How the block it's in sends what it shows to players. */
	public void setFlusher(Runnable flusher) {
		this.flusher = flusher;
	}

	@Override
	public void flush() {
		flusher.run();
	}

	/** Takes the oldest key typed (or report), if any. */
	public Optional<String> nextKey() {
		return Optional.ofNullable(input.pollFirst());
	}

	public boolean hasKeys() {
		return !input.isEmpty();
	}

	// ---- device methods ---------------------------------------------------------------------

	@Method(doc = "write(text): sends text to the terminal; escape sequences (VT100/xterm) move the cursor, set colors, erase")
	public Result write(Arguments args) {
		write(args.checkString(0));
		return Result.ok();
	}

	@Method(doc = "read(): the oldest key typed at this terminal (or a report it answered), or nil")
	public Result read(Arguments args) {
		return Result.ok(nextKey().orElse(null));
	}

	@Method(doc = "getSize(): columns, rows")
	public Result getSize(Arguments args) {
		return Result.ok(buffer.resolution().columns(), buffer.resolution().rows());
	}

	@Override
	public DeviceType type() {
		return DeviceType.TERMINAL;
	}

	public void labelFrom(java.util.function.Supplier<String> label) {
		this.label = label;
	}

	@Override
	public String label() {
		return label.get();
	}

	@Override
	public java.util.Map<String, Object> info() {
		return java.util.Map.of("tier", (long) tier.number(), "width", (long) buffer.resolution().columns(),
			"height", (long) buffer.resolution().rows(), "colors", tier.depth().colors());
	}

	@Override
	public boolean interruptPending() {
		return hasKeys();
	}

	/** Keys (and answers to reports) waiting to be read outlast a save. */
	@Override
	public java.util.Map<String, Object> saved() {
		return input.isEmpty() ? java.util.Map.of() : java.util.Map.of("input", java.util.List.copyOf(input));
	}

	@Override
	public void restore(java.util.Map<String, Object> saved) {
		input.clear();
		if (saved.get("input") instanceof java.util.List<?> keys) {
			keys.forEach(key -> input.addLast(String.valueOf(key)));
		}
	}

	/** Power's gone: a blank screen, the cursor home, default modes, nothing waiting. */
	@Override
	public void powerOff() {
		decoder.reset();
		input.clear();
	}
}
