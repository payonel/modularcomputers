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
 * What it sends back waits in its queue until read (all of it at once, as bytes down a serial line): keys typed at it, and its answers to
 * reports (the cursor position for {@code ESC[6n}). While anything waits, its interrupt is on
 * (in the console's group, the console's is too, and both reads take from this one queue: whichever
 * reads first gets the key, as under UEFI's splitter). Typing comes from the screen
 * view: only the terminal a player has open gets the keys.
 * <p>
 * It's bus-powered ({@link #DRAW}, plus {@link #WRITE_COST} a write): with no running computer
 * supplying it, it's dark.
 * <p>
 * <b>Scrollback</b> (user, 2026-10-15): lines that scroll off the top of its main screen are kept
 * ({@link #SCROLLBACK_LINES}), and Shift+PageUp/PageDown (or the mouse wheel) in the screen view
 * scroll its glass back over them ({@link #scrollView}), as a terminal emulator does: the history
 * is the terminal's, never the tty's or the program's (Linux's tty layer keeps no output; xterm,
 * or the kernel's own VT for its console, keep it where the screen is drawn), and those keys never
 * go down the line. A key typed or anything written brings the view back to the bottom (xterm's
 * scrollKey and scrollTtyOutput). The alternate screen saves no lines; {@code ESC[3J} clears them.
 * They're not saved with the block (lost when it unloads, as a terminal's memory at power-off).
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
	/**
	 * How many keys (and report answers) wait, at most (user, 2026-10-13: devices keep their own
	 * rings). Past it a key is dropped, as a PC's keyboard buffer drops what it has no room for
	 * (and beeps: no bell here yet); what waits isn't overwritten.
	 */
	public static final int INPUT_CAPACITY = 256;
	private final VtDecoder decoder;

	/**
	 * The cursor as players see it: where it is, whether it shows (the program hasn't hidden it,
	 * the terminal has power, its glass isn't scrolled back), and its shape (DECSCUSR's: even
	 * styles are steady, odd ones blink; 0-2 a block, 3-4 an underline, 5-6 a bar).
	 */
	public record Cursor(int column, int row, boolean shown, int style) {
		public static final Cursor HIDDEN = new Cursor(0, 0, false, 0);

		public boolean blinks() {
			return style == 0 || style % 2 == 1;
		}

		public boolean block() {
			return style <= 2;
		}

		public boolean underline() {
			return style == 3 || style == 4;
		}
	}

	/** What players were last shown of the cursor: on the server, what was last sent; on a client, what came. */
	private Cursor shownCursor = Cursor.HIDDEN;

	/** The cursor as it is now (on the server, where the terminal decodes). */
	public Cursor cursor() {
		int column = Math.min(decoder.column(), buffer.resolution().columns() - 1);
		return new Cursor(column, decoder.row(), decoder.cursorVisible() && power.powered() && viewOffset == 0, decoder.cursorStyle());
	}

	public Cursor shownCursor() {
		return shownCursor;
	}

	public void showCursor(Cursor cursor) {
		shownCursor = cursor;
	}

	/** Lines kept as they scroll off the top: four screens or so. */
	public static final int SCROLLBACK_LINES = 200;
	/** A line of the scrollback: its characters and colors. */
	private record Line(String text, int[] foregrounds, int[] backgrounds) {
	}
	private final Deque<Line> history = new ArrayDeque<>();
	/** How many lines back the glass shows (0: the screen as it is). */
	private int viewOffset;
	/** The screen as it is while the glass shows the scrollback; null at the bottom. */
	private VtDecoder.Screen live;

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
		this.decoder = new VtDecoder(buffer, this::queue, tier.depth(), tier.attributes());
		decoder.setHistory(new VtDecoder.History() {
			@Override
			public void scrolledOff(String text, int[] foregrounds, int[] backgrounds) {
				history.addLast(new Line(text, foregrounds, backgrounds));
				while (history.size() > SCROLLBACK_LINES) {
					history.removeFirst();
				}
			}

			@Override
			public void clear() {
				history.clear();
			}
		});
	}

	public TerminalTier tier() {
		return tier;
	}

	/** What its glass shows: the screen, or the scrollback while it's scrolled back. */
	public TextBuffer buffer() {
		return buffer;
	}

	/** The screen as it is, scrolled back or not (what a save keeps). */
	public VtDecoder.Screen screen() {
		return live != null ? live : new VtDecoder.Screen(buffer.rows(), buffer.foregrounds(), buffer.backgrounds());
	}

	/** Lines kept in its scrollback. */
	public int scrollbackLines() {
		return history.size();
	}

	/** How many lines back its glass shows (0: the screen as it is). */
	public int viewOffset() {
		return viewOffset;
	}

	/**
	 * Scrolls its glass back over the scrollback ({@code lines} positive: back, older; negative: on
	 * toward the screen), as far as there are lines; at 0 it shows the screen as it is again.
	 */
	public void scrollView(int lines) {
		int target = Math.max(0, Math.min(history.size(), viewOffset + lines));
		if (target == viewOffset) {
			return;
		}
		if (viewOffset == 0) {
			live = screen();
		}
		viewOffset = target;
		if (viewOffset == 0) {
			showLive();
			return;
		}
		int columns = buffer.resolution().columns();
		int rows = buffer.resolution().rows();
		Line[] saved = history.toArray(new Line[0]);
		for (int row = 0; row < rows; row++) {
			int index = saved.length - viewOffset + row;
			if (index < saved.length) {
				buffer.setRow(row, saved[index].text(), saved[index].foregrounds(), saved[index].backgrounds());
			} else {
				int screenRow = index - saved.length;
				int[] foregrounds = java.util.Arrays.copyOfRange(live.foregrounds(), screenRow * columns, (screenRow + 1) * columns);
				int[] backgrounds = java.util.Arrays.copyOfRange(live.backgrounds(), screenRow * columns, (screenRow + 1) * columns);
				buffer.setRow(row, live.rows().get(screenRow), foregrounds, backgrounds);
			}
		}
	}

	/** Back to the bottom: the glass shows the screen as it is. */
	public void scrollToBottom() {
		if (viewOffset != 0) {
			viewOffset = 0;
			showLive();
		}
	}

	private void showLive() {
		buffer.setRows(live.rows());
		buffer.setColors(live.foregrounds(), live.backgrounds());
		live = null;
	}

	public VtDecoder decoder() {
		return decoder;
	}

	/** Text down the line to the terminal, decoded as it arrives. Each write costs power. */
	public void write(String text) {
		power.charge(WRITE_COST);
		scrollToBottom();
		decoder.write(text);
		changed.run();
	}

	/** Its block, told when what it shows or holds changes ({@link Device#onChange}). */
	private Runnable changed = () -> {
	};

	@Override
	public void onChange(Runnable changed) {
		this.changed = changed;
	}

	@Override
	public BusPower power() {
		return power;
	}

	/** A key typed at the terminal: it waits in the queue, and the interrupt line wakes its computers. */
	public void press(String key) {
		scrollToBottom();
		if (queue(key)) {
			interruptLine.raise();
		}
	}

	/** Whether it takes keys now (see {@link Device#accepting}): typed at a computer that's off, they go nowhere. */
	private boolean accepting;

	@Override
	public void accepting(boolean accepting) {
		this.accepting = accepting;
		if (!accepting && !input.isEmpty()) {
			input.clear();
			changed.run();
		}
	}

	/** Puts a key (or a report's answer) in the queue, if it takes keys and there's room: whether it went in. */
	private boolean queue(String key) {
		if (!accepting || input.size() >= INPUT_CAPACITY) {
			return false; // not taking data, or full: dropped
		}
		input.addLast(key);
		changed.run();
		return true;
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
		String key = input.pollFirst();
		if (key != null) {
			changed.run();
		}
		return Optional.ofNullable(key);
	}

	/**
	 * Takes what's waiting, as one string of bytes, as a read of a serial line's FIFO does: keys
	 * aren't kept apart (an arrow is just its escape sequence's bytes among the others). At most
	 * {@code max} bytes, in whole keys (a key is never split); with {@code atLeastOne}, one key even
	 * if it's longer (a key is at most a few bytes; a read must make progress).
	 */
	public Optional<String> takeInput(long max, boolean atLeastOne) {
		StringBuilder bytes = new StringBuilder();
		while (!input.isEmpty() && (bytes.length() + input.peekFirst().length() <= max || (atLeastOne && bytes.isEmpty()))) {
			bytes.append(input.pollFirst());
		}
		if (bytes.isEmpty()) {
			return Optional.empty();
		}
		changed.run();
		return Optional.of(bytes.toString());
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

	@Method(doc = "read([max]): the bytes waiting (at most max), as a serial line sends them: what was typed at this terminal (and the reports it answered), or nil")
	public Result read(Arguments args) {
		return Result.ok(takeInput(args.optInteger(0, Long.MAX_VALUE), true).orElse(null));
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
			keys.stream().limit(INPUT_CAPACITY).forEach(key -> input.addLast(String.valueOf(key)));
			changed.run();
		}
	}

	/** Power's gone: a blank screen, the cursor home, default modes, nothing waiting. */
	@Override
	public void powerOff() {
		scrollToBottom();
		history.clear();
		decoder.reset();
		input.clear();
		changed.run();
	}
}
