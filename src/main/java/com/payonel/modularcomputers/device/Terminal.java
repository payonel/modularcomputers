package com.payonel.modularcomputers.device;

import com.payonel.modularcomputers.bus.Device;
import com.payonel.modularcomputers.bus.DeviceType;
import com.payonel.modularcomputers.display.Resolution;
import com.payonel.modularcomputers.display.TextBuffer;
import com.payonel.modularcomputers.display.VtDecoder;
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
 * (when it's in the console's group, the console's is instead). Typing doesn't reach it yet:
 * it will come from the screen view.
 */
public final class Terminal implements Device {
	private final TextBuffer buffer;
	private final Deque<String> input = new ArrayDeque<>();
	private final VtDecoder decoder;

	public Terminal(Resolution resolution) {
		this.buffer = new TextBuffer(resolution);
		this.decoder = new VtDecoder(buffer, input::addLast);
	}

	public TextBuffer buffer() {
		return buffer;
	}

	public VtDecoder decoder() {
		return decoder;
	}

	/** Text down the line to the terminal, decoded as it arrives. */
	public void write(String text) {
		decoder.write(text);
	}

	/** A key typed at the terminal. */
	public void press(String key) {
		input.addLast(key);
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

	@Override
	public boolean interruptPending() {
		return hasKeys();
	}

	/** Power's gone: a blank screen, the cursor home, default modes, nothing waiting. */
	@Override
	public void powerOff() {
		decoder.reset();
		input.clear();
	}
}
