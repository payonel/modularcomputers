package com.payonel.modularcomputers.device;

import com.payonel.modularcomputers.bus.Device;
import com.payonel.modularcomputers.bus.DeviceType;
import com.payonel.modularcomputers.display.Resolution;
import com.payonel.modularcomputers.display.TextBuffer;
import com.payonel.modularcomputers.runtime.Arguments;
import com.payonel.modularcomputers.runtime.Method;
import com.payonel.modularcomputers.runtime.Result;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

/**
 * A terminal on the bus: a text screen and a keyboard in one device, like a VT100 on a serial
 * line. Whatever writes to its buffer shows on the screen. Keys typed at it wait in its queue
 * until read; while any wait, its interrupt is on (when it's in the console's group, the
 * console's is instead). Typing doesn't reach it yet: it will come from the screen view.
 */
public final class Terminal implements Device {
	private final TextBuffer buffer;
	private final Deque<String> keys = new ArrayDeque<>();

	public Terminal(Resolution resolution) {
		this.buffer = new TextBuffer(resolution);
	}

	public TextBuffer buffer() {
		return buffer;
	}

	/** A key typed at the terminal. */
	public void press(String key) {
		keys.addLast(key);
	}

	/** Takes the oldest key typed, if any. */
	public Optional<String> nextKey() {
		return Optional.ofNullable(keys.pollFirst());
	}

	public boolean hasKeys() {
		return !keys.isEmpty();
	}

	// ---- device methods. Columns and rows count from 1, as Lua does. -----------------------

	@Method(doc = "getResolution(): columns, rows")
	public Result getResolution(Arguments args) {
		return Result.ok(buffer.resolution().columns(), buffer.resolution().rows());
	}

	@Method(doc = "set(column, row, text): writes text from a cell rightwards, clipped at the edge")
	public Result set(Arguments args) {
		int column = (int) args.checkInteger(0) - 1;
		int row = (int) args.checkInteger(1) - 1;
		String text = args.checkString(2);
		if (row < 0 || row >= buffer.resolution().rows()) {
			return Result.ok(false);
		}
		for (int i = 0; i < text.length(); i++) {
			if (column + i >= 0 && column + i < buffer.resolution().columns()) {
				buffer.set(column + i, row, text.charAt(i));
			}
		}
		return Result.ok(true);
	}

	@Method(doc = "get(column, row): the character at a cell, its foreground and background (0xRRGGBB)")
	public Result get(Arguments args) {
		int column = (int) args.checkInteger(0) - 1;
		int row = (int) args.checkInteger(1) - 1;
		if (column < 0 || row < 0 || column >= buffer.resolution().columns() || row >= buffer.resolution().rows()) {
			return Result.fail("out of range");
		}
		return Result.ok(String.valueOf(buffer.get(column, row)), buffer.foreground(column, row), buffer.background(column, row));
	}

	@Method(doc = "fill(column, row, width, height, char): fills a rectangle with one character")
	public Result fill(Arguments args) {
		int column = (int) args.checkInteger(0) - 1;
		int row = (int) args.checkInteger(1) - 1;
		int width = (int) args.checkInteger(2);
		int height = (int) args.checkInteger(3);
		String fill = args.checkString(4);
		if (fill.length() != 1) {
			return Result.fail("fill takes one character");
		}
		for (int y = Math.max(0, row); y < Math.min(buffer.resolution().rows(), row + height); y++) {
			for (int x = Math.max(0, column); x < Math.min(buffer.resolution().columns(), column + width); x++) {
				buffer.set(x, y, fill.charAt(0));
			}
		}
		return Result.ok(true);
	}

	@Method(doc = "read(): the oldest key typed at this terminal, or nil")
	public Result read(Arguments args) {
		return Result.ok(nextKey().orElse(null));
	}

	@Override
	public DeviceType type() {
		return DeviceType.TERMINAL;
	}

	@Override
	public boolean interruptPending() {
		return hasKeys();
	}

	@Override
	public void powerOff() {
		buffer.clear();
		keys.clear();
	}
}
